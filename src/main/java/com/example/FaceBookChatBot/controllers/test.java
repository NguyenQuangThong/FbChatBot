package com.example.FaceBookChatBot.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.messenger4j.Messenger;
import com.github.messenger4j.exception.MessengerApiException;
import com.github.messenger4j.exception.MessengerIOException;
import com.github.messenger4j.exception.MessengerVerificationException;
import com.github.messenger4j.send.MessagePayload;
import com.github.messenger4j.send.MessagingType;
import com.github.messenger4j.send.NotificationType;
import com.github.messenger4j.send.message.TextMessage;
import com.github.messenger4j.send.recipient.IdRecipient;
import com.github.messenger4j.webhook.event.TextMessageEvent;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.service.OpenAiService;
import static com.github.messenger4j.Messenger.*;
import static java.util.Optional.empty;
import static java.util.Optional.of;

@CrossOrigin("*")
@RestController
@RequestMapping("/webhook")
public class test {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${openai.apiKey}")
    private String openaiApiKey;

    @Value("${messenger4j.pageAccessToken}")
    private String pageAccessToken;

    private static final Logger logger = LoggerFactory.getLogger(test.class);

    private final Messenger messenger;

    @Autowired
    public test(final Messenger messenger) {
        this.messenger = messenger;
    }

    @GetMapping
    public ResponseEntity<String> verifyWebhook(@RequestParam(MODE_REQUEST_PARAM_NAME) final String mode,
            @RequestParam(VERIFY_TOKEN_REQUEST_PARAM_NAME) final String verifyToken,
            @RequestParam(CHALLENGE_REQUEST_PARAM_NAME) final String challenge) {
        logger.debug("Received Webhook verification request - mode: {} | verifyToken:{} | challenge: {}", mode,
                verifyToken, challenge);
        try {
            this.messenger.verifyWebhook(mode, verifyToken);
            return ResponseEntity.ok(challenge);
        } catch (MessengerVerificationException e) {
            logger.warn("Webhook verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<String> handleWebhook(@RequestBody String payload,
            @RequestHeader(SIGNATURE_HEADER_NAME) final String signature) {
        System.out.println("Inpunt: " + payload);
        try {
            JsonNode jsonNode = new ObjectMapper().readTree(payload);
            String senderId = jsonNode.get("entry").get(0).get("messaging").get(0).get("sender").get("id").asText();
            String messageText = jsonNode.get("entry").get(0).get("messaging").get(0).get("message").get("text")
                    .asText();

            // Use OpenAI to generate a response
            OpenAiService openai = new OpenAiService(openaiApiKey);
            CompletionRequest completionRequest = CompletionRequest.builder()
                    // .engine("text-davinci-002")
                    .prompt(String.format("User: %s\nAI:", messageText))
                    .maxTokens(64)
                    .n(1)
                    .temperature(0.5)
                    .model("text-davinci-002")
                    .build();

            // Send the response back to the user via Messenger
            String aiResponse = openai.createCompletion(completionRequest).getChoices().get(0).getText();
            // String endpoint =
            // String endpoint =
            // "https://graph.facebook.com/v16.0/me/messages?fields=get_started,persistent_menu,target_audience,whitelisted_domains,greeting,account_linking_url,payment_settings,home_url,ice_breakers,platform&access_token="
            // + pageAccessToken;
            // String requestBody = String.format("{\"recipient\": {\"id\": \"%s\"},
            // \"message\": {\"text\": \"%s\"}}",
            // senderId, aiResponse);
            // restTemplate.postForObject(endpoint, requestBody, String.class);
            this.messenger.onReceiveEvents(payload, of(signature), event -> {
                if (event.isTextMessageEvent()) {
                    try {
                        logger.info("0");
                        handleTextMessageEvent(senderId, aiResponse);
                        logger.info("1");
                    } catch (MessengerApiException e) {
                        logger.info("2");
                        e.printStackTrace();
                    } catch (MessengerIOException e) {
                        logger.info("3");
                        e.printStackTrace();
                    }
                } else {
                    sendTextMessageUser(senderId, "Tôi là bot chỉ có thể xử lý tin nhắn văn bản.");
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }

        return ResponseEntity.ok().build();
    }

    private void handleTextMessageEvent(String senderId, String answer)
            throws MessengerApiException, MessengerIOException {
        sendTextMessageUser(senderId, answer);

    }

    private void sendTextMessageUser(String idSender, String text) {
        try {
            final IdRecipient recipient = IdRecipient.create(idSender);
            final NotificationType notificationType = NotificationType.REGULAR;
            final String metadata = "DEVELOPER_DEFINED_METADATA";

            final TextMessage textMessage = TextMessage.create(text, empty(),
                    of(metadata));
            final MessagePayload messagePayload = MessagePayload.create(recipient,
                    MessagingType.RESPONSE, textMessage,
                    of(notificationType), empty());
            this.messenger.send(messagePayload);
        } catch (MessengerApiException | MessengerIOException e) {
            handleSendException(e);
        }
    }

    private void handleSendException(Exception e) {
        logger.error("Message could not be sent. An unexpected error occurred.", e);
    }
}
