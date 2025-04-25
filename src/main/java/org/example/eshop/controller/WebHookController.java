// src/main/java/org/example/eshop/controller/WebHookController.java

package org.example.eshop.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.eshop.service.PaymentProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks/superfaktura")
public class WebHookController {

    private static final Logger log = LoggerFactory.getLogger(WebHookController.class);

    @Autowired
    private PaymentProcessingService paymentProcessingService;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${superfaktura.webhook.secret:}")
    private String webhookSecret; // Můžeme ponechat, pokud jej chceme použít v budoucnu

    @PostMapping("/payment")
    public ResponseEntity<String> handlePaymentWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Sf-Signature", required = false) String signature,
            @RequestParam(value = "token", required = false) String token
    ) {
        log.info("Received SuperFaktura webhook payload.");
        log.debug("Webhook Payload: {}", payload);

        try {
            JsonNode rootNode = objectMapper.readTree(payload);
            String eventType = rootNode.path("event").asText();

            // 3. Zpracování podle typu události
            if ("invoice.paid".equalsIgnoreCase(eventType) || "proforma.paid".equalsIgnoreCase(eventType)) {
                log.info("Processing paid event: {}", eventType);
                paymentProcessingService.processPaymentNotification(rootNode);
                return ResponseEntity.ok("Webhook processed successfully.");
            } else {
                log.info("Received webhook event type '{}', which is not processed.", eventType);
                return ResponseEntity.ok("Webhook received but event type not processed.");
            }

        } catch (Exception e) {
            log.error("Error processing SuperFaktura webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing webhook");
        }
    }
}