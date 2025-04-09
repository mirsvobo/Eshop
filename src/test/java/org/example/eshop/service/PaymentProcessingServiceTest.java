// Soubor: src/test/java/org/example/eshop/service/PaymentProcessingServiceTest.java
package org.example.eshop.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.eshop.model.Customer;
import org.example.eshop.model.Order;
import org.example.eshop.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentProcessingServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private InvoiceService invoiceService; // Mockujeme InvoiceService pro DDKP

    // Reálný ObjectMapper pro parsování JSON
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PaymentProcessingService paymentProcessingService;

    private Order orderAwaitingDeposit;
    private Order orderPending;
    private Order orderDepositPaid; // Pro test duplicity

    private final String orderCodeDeposit = "ORD-VS-DEPOSIT";
    private final String orderCodePending = "ORD-VS-PENDING";
    private final String orderCodeDepositPaid = "ORD-VS-DEPPAID";
    private final BigDecimal depositAmount = new BigDecimal("500.00");
    private final BigDecimal totalAmountPending = new BigDecimal("800.00");
    private final LocalDate paymentDate = LocalDate.now();


    @BeforeEach
    void setUp() {
        Customer customer = new Customer(); customer.setId(1L);

        orderAwaitingDeposit = new Order();
        orderAwaitingDeposit.setId(1L);
        orderAwaitingDeposit.setOrderCode(orderCodeDeposit);
        orderAwaitingDeposit.setCustomer(customer);
        orderAwaitingDeposit.setDepositAmount(depositAmount);
        orderAwaitingDeposit.setTotalPrice(new BigDecimal("1000.00")); // Celková cena
        orderAwaitingDeposit.setPaymentStatus("AWAITING_DEPOSIT");
        orderAwaitingDeposit.setDepositPaidDate(null);
        orderAwaitingDeposit.setSfProformaInvoiceId(101L); // Má proformu

        orderPending = new Order();
        orderPending.setId(2L);
        orderPending.setOrderCode(orderCodePending);
        orderPending.setCustomer(customer);
        orderPending.setDepositAmount(null); // Bez zálohy
        orderPending.setTotalPrice(totalAmountPending);
        orderPending.setPaymentStatus("PENDING_PAYMENT");
        orderPending.setPaymentDate(null);
        orderPending.setSfFinalInvoiceId(102L); // Má finální fakturu

        orderDepositPaid = new Order();
        orderDepositPaid.setId(3L);
        orderDepositPaid.setOrderCode(orderCodeDepositPaid);
        orderDepositPaid.setCustomer(customer);
        orderDepositPaid.setDepositAmount(depositAmount);
        orderDepositPaid.setTotalPrice(new BigDecimal("1000.00"));
        orderDepositPaid.setPaymentStatus("DEPOSIT_PAID"); // Už zaplaceno
        orderDepositPaid.setDepositPaidDate(LocalDateTime.now().minusDays(1)); // Už má datum
        orderDepositPaid.setSfProformaInvoiceId(103L);
        orderDepositPaid.setSfTaxDocumentId(203L); // Už má i DDKP


        // Základní mockování pro findByOrderCode
        lenient().when(orderRepository.findByOrderCode(orderCodeDeposit)).thenReturn(Optional.of(orderAwaitingDeposit));
        lenient().when(orderRepository.findByOrderCode(orderCodePending)).thenReturn(Optional.of(orderPending));
        lenient().when(orderRepository.findByOrderCode(orderCodeDepositPaid)).thenReturn(Optional.of(orderDepositPaid));
        lenient().when(orderRepository.findByOrderCode("UNKNOWN_VS")).thenReturn(Optional.empty());
        // Mock save
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        // Mock invoice service
        lenient().doNothing().when(invoiceService).generateTaxDocumentForDeposit(any(Order.class));
    }

    // Pomocná metoda pro vytvoření JsonNode z JSON stringu
    private JsonNode createJsonNode(String jsonString) throws Exception {
        return objectMapper.readTree(jsonString);
    }

    // --- Testy pro různé scénáře webhooků ---

    @Test
    @DisplayName("[processPaymentNotification] Úspěšně zpracuje platbu zálohy (webhook proforma.paid)")
    void processPaymentNotification_DepositPaid_ProformaWebhook_Success() throws Exception {
        // --- Příprava ---
        String proformaWebhookPayload = String.format("""
            {
                "event": "proforma.paid",
                "data": {
                    "ProformaPayment": {
                        "proforma_id": 101,
                        "variable_symbol": "%s",
                        "amount": "%s",
                        "date": "%s",
                        "currency": "CZK",
                        "payment_type": "transfer"
                    }
                }
            }
            """, orderCodeDeposit, depositAmount.toPlainString(), paymentDate.toString());
        JsonNode webhookData = createJsonNode(proformaWebhookPayload);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);

        // --- Provedení ---
        paymentProcessingService.processPaymentNotification(webhookData);

        // --- Ověření ---
        // Ověříme, že se objednávka uložila se správnými údaji
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertEquals("DEPOSIT_PAID", savedOrder.getPaymentStatus());
        assertNotNull(savedOrder.getDepositPaidDate());
        assertEquals(paymentDate, savedOrder.getDepositPaidDate().toLocalDate());
        // Ověříme, že se volalo generování DDKP
        verify(invoiceService).generateTaxDocumentForDeposit(savedOrder);
    }

    @Test
    @DisplayName("[processPaymentNotification] Úspěšně zpracuje plnou platbu (webhook invoice.paid)")
    void processPaymentNotification_FullPaid_InvoiceWebhook_Success() throws Exception {
        // --- Příprava ---
        String invoiceWebhookPayload = String.format("""
            {
                "event": "invoice.paid",
                "data": {
                    "InvoicePayment": {
                        "invoice_id": 102,
                        "variable_symbol": "%s",
                        "amount": "%s",
                        "date": "%s",
                        "currency": "EUR",
                        "payment_type": "transfer"
                    }
                }
            }
            """, orderCodePending, totalAmountPending.toPlainString(), paymentDate.toString());
        JsonNode webhookData = createJsonNode(invoiceWebhookPayload);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);

        // --- Provedení ---
        paymentProcessingService.processPaymentNotification(webhookData);

        // --- Ověření ---
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertEquals("PAID", savedOrder.getPaymentStatus());
        assertNotNull(savedOrder.getPaymentDate());
        assertEquals(paymentDate, savedOrder.getPaymentDate().toLocalDate());
        // Ověříme, že se NEvolalo generování DDKP
        verify(invoiceService, never()).generateTaxDocumentForDeposit(any(Order.class));
    }

    @Test
    @DisplayName("[processPaymentNotification] Úspěšně zpracuje plnou platbu (webhook Invoice update)")
    void processPaymentNotification_FullPaid_InvoiceUpdateWebhook_Success() throws Exception {
        // --- Příprava (Alternativní struktura webhooku) ---
        String invoiceUpdateWebhookPayload = String.format("""
            {
                "event": "invoice.update",
                "data": {
                    "Invoice": {
                        "id": 102,
                        "type": "regular",
                        "variable": "%s",
                        "amount_paid": "%s",
                        "paid_date": "%s",
                        "currency": "EUR"
                    }
                }
            }
            """, orderCodePending, totalAmountPending.toPlainString(), paymentDate.toString());
        JsonNode webhookData = createJsonNode(invoiceUpdateWebhookPayload);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);

        // --- Provedení ---
        paymentProcessingService.processPaymentNotification(webhookData);

        // --- Ověření ---
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertEquals("PAID", savedOrder.getPaymentStatus());
        assertNotNull(savedOrder.getPaymentDate());
        assertEquals(paymentDate, savedOrder.getPaymentDate().toLocalDate());
        verify(invoiceService, never()).generateTaxDocumentForDeposit(any(Order.class));
    }

    @Test
    @DisplayName("[processPaymentNotification] Ignoruje duplicitní notifikaci o platbě zálohy")
    void processPaymentNotification_DuplicateDepositPayment_Ignored() throws Exception {
        // --- Příprava ---
        // Stejný payload jako v úspěšném testu zálohy
        String proformaWebhookPayload = String.format("""
            { "event": "proforma.paid", "data": { "ProformaPayment": { "proforma_id": 103, "variable_symbol": "%s", "amount": "%s", "date": "%s" }}}
            """, orderCodeDepositPaid, depositAmount.toPlainString(), paymentDate.toString());
        JsonNode webhookData = createJsonNode(proformaWebhookPayload);

        // Objednávka už má status DEPOSIT_PAID a datum (viz setUp)

        // --- Provedení ---
        paymentProcessingService.processPaymentNotification(webhookData);

        // --- Ověření ---
        // Ověříme, že se NEVOLALO save ani generování DDKP
        verify(orderRepository, never()).save(any(Order.class));
        verify(invoiceService, never()).generateTaxDocumentForDeposit(any(Order.class));
        // Find se zavolá jednou pro načtení objednávky
        verify(orderRepository, times(1)).findByOrderCode(orderCodeDepositPaid);
    }

    @Test
    @DisplayName("[processPaymentNotification] Ignoruje notifikaci s nesprávnou částkou")
    void processPaymentNotification_AmountMismatch_Ignored() throws Exception {
        // --- Příprava ---
        BigDecimal wrongAmount = depositAmount.subtract(BigDecimal.TEN); // Částka nesedí
        String proformaWebhookPayload = String.format("""
            { "event": "proforma.paid", "data": { "ProformaPayment": { "proforma_id": 101, "variable_symbol": "%s", "amount": "%s", "date": "%s" }}}
            """, orderCodeDeposit, wrongAmount.toPlainString(), paymentDate.toString());
        JsonNode webhookData = createJsonNode(proformaWebhookPayload);

        // Objednávka je ve stavu AWAITING_DEPOSIT

        // --- Provedení ---
        paymentProcessingService.processPaymentNotification(webhookData);

        // --- Ověření ---
        // Save ani DDKP se nesmí volat
        verify(orderRepository, never()).save(any(Order.class));
        verify(invoiceService, never()).generateTaxDocumentForDeposit(any(Order.class));
        verify(orderRepository, times(1)).findByOrderCode(orderCodeDeposit);
    }

    @Test
    @DisplayName("[processPaymentNotification] Ignoruje notifikaci pro neznámou objednávku")
    void processPaymentNotification_OrderNotFound_Ignored() throws Exception {
        // --- Příprava ---
        String unknownVS = "UNKNOWN_VS";
        String webhookPayload = String.format("""
            { "event": "proforma.paid", "data": { "ProformaPayment": { "proforma_id": 999, "variable_symbol": "%s", "amount": "100.00", "date": "%s" }}}
            """, unknownVS, paymentDate.toString());
        JsonNode webhookData = createJsonNode(webhookPayload);

        // Mock find vrátí empty
        when(orderRepository.findByOrderCode(unknownVS)).thenReturn(Optional.empty());

        // --- Provedení ---
        paymentProcessingService.processPaymentNotification(webhookData);

        // --- Ověření ---
        verify(orderRepository, times(1)).findByOrderCode(unknownVS);
        verify(orderRepository, never()).save(any(Order.class));
        verify(invoiceService, never()).generateTaxDocumentForDeposit(any(Order.class));
    }

    @Test
    @DisplayName("[processPaymentNotification] Ignoruje notifikaci s neúplnými daty")
    void processPaymentNotification_IncompleteData_Ignored() throws Exception {
        // Chybí amount
        String incompleteWebhookPayload = String.format("""
            { "event": "proforma.paid", "data": { "ProformaPayment": { "proforma_id": 101, "variable_symbol": "%s", "date": "%s" }}}
            """, orderCodeDeposit, paymentDate.toString());
        JsonNode webhookData = createJsonNode(incompleteWebhookPayload);

        // --- Provedení ---
        paymentProcessingService.processPaymentNotification(webhookData);

        // --- Ověření ---
        // Nemělo by se nic stát, ani hledat objednávka
        verify(orderRepository, never()).findByOrderCode(anyString());
        verify(orderRepository, never()).save(any(Order.class));
        verify(invoiceService, never()).generateTaxDocumentForDeposit(any(Order.class));
    }

    @Test
    @DisplayName("[processPaymentNotification] Ignoruje notifikaci s neznámým typem eventu")
    void processPaymentNotification_UnknownEventType_Ignored() throws Exception {
        // --- Příprava ---
        String unknownEventWebhookPayload = String.format("""
            { "event": "invoice.created", "data": { "Invoice": { "id": 102, "variable": "%s" }}}
            """, orderCodePending);
        JsonNode webhookData = createJsonNode(unknownEventWebhookPayload);

        // --- Provedení ---
        paymentProcessingService.processPaymentNotification(webhookData);

        // --- Ověření ---
        // Nemělo by se nic stát
        verify(orderRepository, never()).findByOrderCode(anyString());
        verify(orderRepository, never()).save(any(Order.class));
        verify(invoiceService, never()).generateTaxDocumentForDeposit(any(Order.class));
    }
}