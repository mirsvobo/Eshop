// src/test/java/org/example/eshop/controller/WebHookControllerTest.java

package org.example.eshop.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.eshop.config.SecurityTestConfig; // <--- 1. Import sdílené konfigurace
import org.example.eshop.service.CurrencyService;
import org.example.eshop.service.PaymentProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import; // <--- 2. Import pro @Import
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
// Nepotřebujeme import pro csrf()
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest(WebHookController.class)
@Import(SecurityTestConfig.class) // <--- 3. Aplikace sdílené konfigurace
class WebHookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentProcessingService paymentProcessingService;

    @MockBean // Ponecháno - může být potřeba pro GlobalModelAttributeAdvice nebo Security
    private CurrencyService currencyService;

    @Autowired
    private ObjectMapper objectMapper;

    private String validInvoicePaidPayload;
    private String validProformaPaidPayload;
    private String unknownEventPayload;

    @BeforeEach
    void setUp() {
        validInvoicePaidPayload = """
            { "event": "invoice.paid", "data": { "InvoicePayment": { "variable_symbol": "ORD123", "amount": "100.00", "date": "2025-04-09", "invoice_id": 501 } } }
            """;
        validProformaPaidPayload = """
            { "event": "proforma.paid", "data": { "ProformaPayment": { "variable_symbol": "ORD456", "amount": "50.00", "date": "2025-04-08", "proforma_id": 602 } } }
            """;
        unknownEventPayload = """
            { "event": "invoice.created", "data": { "Invoice": { "id": 703 } } }
            """;
        // Použití lenient() je dobré, pokud se metoda nemusí volat v každém testu
        lenient().doNothing().when(paymentProcessingService).processPaymentNotification(any(JsonNode.class));
    }

    @Test
    @DisplayName("POST /webhooks/superfaktura/payment - Zpracuje platný 'invoice.paid' webhook")
    void handlePaymentWebhook_InvoicePaid_ShouldCallService() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/webhooks/superfaktura/payment")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validInvoicePaidPayload)
                        // Bez .with(csrf()) - nebylo zde ani původně
                )
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Webhook processed successfully")));

        verify(paymentProcessingService, times(1)).processPaymentNotification(any(JsonNode.class));
    }

    @Test
    @DisplayName("POST /webhooks/superfaktura/payment - Zpracuje platný 'proforma.paid' webhook")
    void handlePaymentWebhook_ProformaPaid_ShouldCallService() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/webhooks/superfaktura/payment")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validProformaPaidPayload)
                        // Bez .with(csrf())
                )
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Webhook processed successfully")));

        verify(paymentProcessingService, times(1)).processPaymentNotification(any(JsonNode.class));
    }

    @Test
    @DisplayName("POST /webhooks/superfaktura/payment - Zpracuje neznámý event type bez volání service")
    void handlePaymentWebhook_UnknownEventType_ShouldReturnOkButNotProcess() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/webhooks/superfaktura/payment")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(unknownEventPayload)
                        // Bez .with(csrf())
                )
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Webhook received but event type not processed")));

        verify(paymentProcessingService, never()).processPaymentNotification(any());
    }

    @Test
    @DisplayName("POST /webhooks/superfaktura/payment - Chyba při zpracování ve service vrátí Internal Server Error")
    void handlePaymentWebhook_ServiceThrowsException_ShouldReturnInternalServerError() throws Exception {
        doThrow(new RuntimeException("Internal processing error"))
                .when(paymentProcessingService).processPaymentNotification(any(JsonNode.class));

        mockMvc.perform(MockMvcRequestBuilders.post("/webhooks/superfaktura/payment")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validInvoicePaidPayload)
                        // Bez .with(csrf())
                )
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("Error processing webhook")));

        verify(paymentProcessingService, times(1)).processPaymentNotification(any(JsonNode.class));
    }

    @Test
    @DisplayName("POST /webhooks/superfaktura/payment - Chyba při parsování JSON payloadu vrátí Internal Server Error")
    void handlePaymentWebhook_InvalidJsonPayload_ShouldReturnInternalServerError() throws Exception {
        String invalidJson = "{ not_a_valid_json ";

        mockMvc.perform(MockMvcRequestBuilders.post("/webhooks/superfaktura/payment")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidJson)
                        // Bez .with(csrf())
                )
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("Error processing webhook")));

        verify(paymentProcessingService, never()).processPaymentNotification(any());
    }
}