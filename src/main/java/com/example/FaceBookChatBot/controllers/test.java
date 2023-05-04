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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.messenger4j.Messenger;
import com.github.messenger4j.exception.MessengerVerificationException;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.service.OpenAiService;
import static com.github.messenger4j.Messenger.*;

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

    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);

    private final Messenger messenger;

    @Autowired
    public test(final Messenger messenger) {
        this.messenger = messenger;
    }

    @GetMapping
    public ResponseEntity<String> verifyWebhook(@RequestParam(MODE_REQUEST_PARAM_NAME) final String mode,
            @RequestParam() final String verifyToken,
            @RequestParam() final String challenge) {
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
    public ResponseEntity<String> handleWebhook(@RequestBody String payload) {
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
                    .build();

            // Send the response back to the user via Messenger
            String aiResponse = openai.createCompletion(completionRequest).getChoices().get(0).getText();
            String endpoint = "https://graph.facebook.com/v13.0/me/messages?access_token=" + pageAccessToken;
            String requestBody = String.format("{\"recipient\": {\"id\": \"%s\"}, \"message\": {\"text\": \"%s\"}}",
                    senderId, aiResponse);
            restTemplate.postForObject(endpoint, requestBody, String.class);
            System.out.println("Request body:" + requestBody);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return ResponseEntity.ok().build();
    }
}
