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
public class WebhookController {

    @Value("${openai.apiKey}")
    private String openaiApiKey;

    @Value("${messenger4j.pageAccessToken}")
    private String pageAccessToken;

    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);

    private final Messenger messenger;

    @Autowired
    public WebhookController(final Messenger messenger) {
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
            @RequestHeader(SIGNATURE_HEADER_NAME) final String signature) throws MessengerVerificationException {

        // Send the response back to the user via Messenger
        this.messenger.onReceiveEvents(payload, of(signature), event -> {
            String senderId = event.senderId();
            if (event.isTextMessageEvent()) {
                try {
                    logger.info("0");
                    final TextMessageEvent textMessageEvent = event.asTextMessageEvent();
                    final String messageText = textMessageEvent.text();

                    // Use OpenAI to generate a response
                    OpenAiService openai = new OpenAiService(openaiApiKey);
                    CompletionRequest completionRequest = CompletionRequest.builder()
                            .prompt(messageText)
                            .temperature(0.5)
                            .model("text-davinci-003")
                            .build();
                    String aiResponse = openai.createCompletion(completionRequest).getChoices().get(0).getText();
                    handleTextMessageEvent(senderId, aiResponse);
                    logger.info("1");
                } catch (Exception e) {
                    logger.info("2");
                    e.printStackTrace();

                }
            } else {
                sendTextMessageUser(senderId,
                        "Right now I can only handle text message! Please ask Thong Nguyen to upgrade me in the future!");
            }
        });

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
