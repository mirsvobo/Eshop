package org.example.eshop.controller; // Nebo jiný balíček pro kontrolery

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.eshop.service.PaymentProcessingService; // Nová služba pro zpracování
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks/superfaktura") // Definice cesty pro webhooky
public class WebHookController {

    private static final Logger log = LoggerFactory.getLogger(WebHookController.class);

    @Autowired
    private PaymentProcessingService paymentProcessingService; // Nová služba

    @Autowired
    private ObjectMapper objectMapper; // Pro parsování JSON

    // Secret token pro základní ověření (nastavit v SF a application.properties)
    @Value("${superfaktura.webhook.secret:}")
    private String webhookSecret;

    // Endpoint pro příjem webhooků od SuperFaktury
    @PostMapping("/payment")
    public ResponseEntity<String> handlePaymentWebhook(
            @RequestBody String payload, // Přijmout raw JSON payload
            @RequestHeader(value = "X-Sf-Signature", required = false) String signature, // Pokud SF posílá podpis
            @RequestParam(value = "token", required = false) String token // Alternativně token v URL
    ) {
        log.info("Received SuperFaktura webhook payload.");
        log.debug("Webhook Payload: {}", payload); // Logovat jen při debugu!

        // 1. Ověření požadavku (základní - token)
        if (StringUtils.hasText(webhookSecret) && !webhookSecret.equals(token)) {
            log.warn("Received webhook with invalid or missing token.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }
        // TODO: Implementovat robustnější ověření (podpis, IP adresy), pokud SF nabízí

        // 2. Parsování JSON payloadu
        try {
            JsonNode rootNode = objectMapper.readTree(payload);
            String eventType = rootNode.path("event").asText(); // Získat typ události

            // 3. Zpracování podle typu události
            if ("invoice.paid".equalsIgnoreCase(eventType) || "proforma.paid".equalsIgnoreCase(eventType)) {
                log.info("Processing paid event: {}", eventType);
                // Předat relevantní data nové službě ke zpracování
                paymentProcessingService.processPaymentNotification(rootNode);
                return ResponseEntity.ok("Webhook processed successfully.");
            } else {
                log.info("Received webhook event type '{}', which is not processed.", eventType);
                return ResponseEntity.ok("Webhook received but event type not processed.");
            }

        } catch (Exception e) {
            log.error("Error processing SuperFaktura webhook: {}", e.getMessage(), e);
            // Vrátit chybu, aby SF věděla, že zpracování selhalo (může zkusit poslat znovu)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing webhook");
        }
    }
}