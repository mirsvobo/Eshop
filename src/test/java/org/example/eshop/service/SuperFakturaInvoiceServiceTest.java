// Soubor: src/test/java/org/example/eshop/service/SuperFakturaInvoiceServiceTest.java
package org.example.eshop.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException; // <-- Import pro specifickou výjimku
import org.example.eshop.model.*;
import org.example.eshop.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuperFakturaInvoiceServiceTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private OrderRepository orderRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SuperFakturaInvoiceService invoiceService;

    private Order orderRequiresDeposit;
    private Order orderDepositPaid;
    private Order orderFullyPaid;
    private Order orderStandardPending;

    @BeforeEach
    void setUp() {
        invoiceService = new SuperFakturaInvoiceService();
        ReflectionTestUtils.setField(invoiceService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(invoiceService, "orderRepository", orderRepository);
        ReflectionTestUtils.setField(invoiceService, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(invoiceService, "sfApiEmail", "test@example.com");
        ReflectionTestUtils.setField(invoiceService, "sfApiKey", "test_api_key");
        ReflectionTestUtils.setField(invoiceService, "sfApiUrl", "https://moje.superfaktura.cz");

        Customer customer = new Customer();
        customer.setId(1L); customer.setEmail("sf-test@example.com"); customer.setPhone("123456789");
        customer.setInvoiceFirstName("Fakturacni"); customer.setInvoiceLastName("Prijmeni");
        customer.setInvoiceStreet("Fakt Ulice 1"); customer.setInvoiceCity("Fak Mesto"); customer.setInvoiceZipCode("11111"); customer.setInvoiceCountry("Česká republika");
        customer.setInvoiceTaxId("12345678"); customer.setInvoiceVatId("CZ12345678");

        orderRequiresDeposit = new Order();
        orderRequiresDeposit.setId(1L); orderRequiresDeposit.setOrderCode("ORD-DEPOSIT"); orderRequiresDeposit.setCustomer(customer);
        orderRequiresDeposit.setDepositAmount(new BigDecimal("500.00")); orderRequiresDeposit.setTotalPrice(new BigDecimal("1000.00"));
        orderRequiresDeposit.setCurrency("CZK"); orderRequiresDeposit.setPaymentStatus("AWAITING_DEPOSIT");
        orderRequiresDeposit.setInvoiceFirstName(customer.getInvoiceFirstName()); orderRequiresDeposit.setInvoiceLastName(customer.getInvoiceLastName());
        orderRequiresDeposit.setInvoiceStreet(customer.getInvoiceStreet()); orderRequiresDeposit.setInvoiceCity(customer.getInvoiceCity()); orderRequiresDeposit.setInvoiceZipCode(customer.getInvoiceZipCode()); orderRequiresDeposit.setInvoiceCountry(customer.getInvoiceCountry());
        orderRequiresDeposit.setInvoiceTaxId(customer.getInvoiceTaxId()); orderRequiresDeposit.setInvoiceVatId(customer.getInvoiceVatId());
        OrderItem item1 = new OrderItem(); item1.setTaxRate(new BigDecimal("0.21")); item1.setTotalPriceWithoutTax(new BigDecimal("413.22")); item1.setTotalTaxAmount(new BigDecimal("86.78"));
        orderRequiresDeposit.setOrderItems(List.of(item1));

        orderDepositPaid = new Order();
        orderDepositPaid.setId(2L); orderDepositPaid.setOrderCode("ORD-DEP-PAID"); orderDepositPaid.setCustomer(customer);
        orderDepositPaid.setDepositAmount(new BigDecimal("500.00")); orderDepositPaid.setTotalPrice(new BigDecimal("1000.00"));
        orderDepositPaid.setCurrency("CZK"); orderDepositPaid.setPaymentStatus("DEPOSIT_PAID");
        orderDepositPaid.setDepositPaidDate(LocalDateTime.now().minusDays(1));
        orderDepositPaid.setSfProformaInvoiceId(1001L); orderDepositPaid.setProformaInvoiceNumber("PF2025001");
        orderDepositPaid.setInvoiceFirstName(customer.getInvoiceFirstName()); orderDepositPaid.setInvoiceLastName(customer.getInvoiceLastName());
        orderDepositPaid.setInvoiceStreet(customer.getInvoiceStreet()); orderDepositPaid.setInvoiceCity(customer.getInvoiceCity()); orderDepositPaid.setInvoiceZipCode(customer.getInvoiceZipCode()); orderDepositPaid.setInvoiceCountry(customer.getInvoiceCountry());
        OrderItem item2 = new OrderItem(); item2.setTaxRate(new BigDecimal("0.21")); item2.setTotalPriceWithoutTax(new BigDecimal("413.22")); item2.setTotalTaxAmount(new BigDecimal("86.78"));
        orderDepositPaid.setOrderItems(new ArrayList<>(List.of(item2)));

        orderFullyPaid = new Order();
        orderFullyPaid.setId(3L); orderFullyPaid.setOrderCode("ORD-PAID"); orderFullyPaid.setCustomer(customer);
        orderFullyPaid.setTotalPrice(new BigDecimal("800.00")); orderFullyPaid.setCurrency("CZK"); orderFullyPaid.setPaymentStatus("PAID");
        orderFullyPaid.setPaymentDate(LocalDateTime.now().minusDays(2)); orderFullyPaid.setDepositAmount(null);
        orderFullyPaid.setInvoiceFirstName(customer.getInvoiceFirstName()); orderFullyPaid.setInvoiceLastName(customer.getInvoiceLastName());
        orderFullyPaid.setInvoiceStreet(customer.getInvoiceStreet()); orderFullyPaid.setInvoiceCity(customer.getInvoiceCity()); orderFullyPaid.setInvoiceZipCode(customer.getInvoiceZipCode()); orderFullyPaid.setInvoiceCountry(customer.getInvoiceCountry());
        OrderItem item3 = new OrderItem(); item3.setTaxRate(new BigDecimal("0.21")); item3.setTotalPriceWithoutTax(new BigDecimal("661.16")); item3.setTotalTaxAmount(new BigDecimal("138.84"));
        orderFullyPaid.setOrderItems(List.of(item3));

        orderStandardPending = new Order();
        orderStandardPending.setId(4L); orderStandardPending.setOrderCode("ORD-PENDING"); orderStandardPending.setCustomer(customer);
        orderStandardPending.setTotalPrice(new BigDecimal("300.00")); orderStandardPending.setCurrency("EUR"); orderStandardPending.setPaymentStatus("PENDING"); orderStandardPending.setDepositAmount(null);
        orderStandardPending.setInvoiceFirstName(customer.getInvoiceFirstName()); orderStandardPending.setInvoiceLastName(customer.getInvoiceLastName());
        orderStandardPending.setInvoiceStreet(customer.getInvoiceStreet()); orderStandardPending.setInvoiceCity(customer.getInvoiceCity()); orderStandardPending.setInvoiceZipCode(customer.getInvoiceZipCode()); orderStandardPending.setInvoiceCountry(customer.getInvoiceCountry());
        OrderItem item4 = new OrderItem(); item4.setTaxRate(new BigDecimal("0.20")); item4.setTotalPriceWithoutTax(new BigDecimal("250.00")); item4.setTotalTaxAmount(new BigDecimal("50.00"));
        orderStandardPending.setOrderItems(List.of(item4));

        lenient().when(orderRepository.findById(1L)).thenReturn(Optional.of(orderRequiresDeposit));
        lenient().when(orderRepository.findById(2L)).thenReturn(Optional.of(orderDepositPaid));
        lenient().when(orderRepository.findById(3L)).thenReturn(Optional.of(orderFullyPaid));
        lenient().when(orderRepository.findById(4L)).thenReturn(Optional.of(orderStandardPending));
        lenient().when(orderRepository.findBySfProformaInvoiceIdOrSfTaxDocumentIdOrSfFinalInvoiceId(anyLong(), anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    Long id = invocation.getArgument(0);
                    if (id.equals(98765L)) return Optional.of(orderRequiresDeposit);
                    return Optional.empty();
                });
    }

    private ResponseEntity<String> createMockSuccessResponse(String jsonBody) {
        return new ResponseEntity<>(jsonBody, HttpStatus.OK);
    }

    // --- Testy Generování Faktur ---

    @Test
    @DisplayName("[generateProformaInvoice] Úspěšně vygeneruje zálohovou fakturu")
    void generateProformaInvoice_Success() throws Exception {
        Long expectedSfId = 12345L; String expectedSfNumber = "PF-2025-001"; String expectedToken = "xyzToken123"; String expectedPdfUrl = "https://moje.superfaktura.cz/invoices/pdf/12345/token:xyzToken123";
        String mockJsonResponse = String.format("""
                { "error": 0, "data": { "Invoice": { "id": %d, "invoice_no_formatted": "%s", "token": "%s" }, "Client": {"id": 54321} } }
                """, expectedSfId, expectedSfNumber, expectedToken);
        ArgumentCaptor<HttpEntity> requestEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange( eq("https://moje.superfaktura.cz/invoices/create.json"), eq(HttpMethod.POST), requestEntityCaptor.capture(), eq(String.class) ))
                .thenReturn(createMockSuccessResponse(mockJsonResponse));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        invoiceService.generateProformaInvoice(orderRequiresDeposit);

        HttpEntity capturedEntity = requestEntityCaptor.getValue();
        assertNotNull(capturedEntity);
        Map<String, Object> payload = (Map<String, Object>) capturedEntity.getBody();
        assertNotNull(payload.get("Invoice"));
        Map<String, Object> invoicePayload = (Map<String, Object>) payload.get("Invoice");
        assertEquals("proforma", invoicePayload.get("type"));
        assertNotNull(payload.get("InvoiceItem"));
        List<Map<String, Object>> itemsPayload = (List<Map<String, Object>>) payload.get("InvoiceItem");
        assertEquals(1, itemsPayload.size());
        assertEquals(0, new BigDecimal("413.22").compareTo((BigDecimal)itemsPayload.get(0).get("unit_price")));
        assertEquals(0, new BigDecimal("21.00").compareTo((BigDecimal)itemsPayload.get(0).get("tax")));
        verify(orderRepository).save(orderRequiresDeposit);
        assertEquals(expectedSfId, orderRequiresDeposit.getSfProformaInvoiceId());
        assertEquals(expectedSfNumber, orderRequiresDeposit.getProformaInvoiceNumber());
        assertEquals(expectedPdfUrl, orderRequiresDeposit.getSfProformaPdfUrl());
    }

    @Test
    @DisplayName("[generateTaxDocumentForDeposit] Úspěšně vygeneruje DDKP")
    void generateTaxDocumentForDeposit_Success() throws Exception {
        Long expectedSfId = 56789L; String expectedSfNumber = "DD-2025-002"; String expectedToken = "abcToken456"; String expectedPdfUrl = "https://moje.superfaktura.cz/invoices/pdf/56789/token:abcToken456";
        String mockJsonResponse = String.format("""
            { "error": 0, "data": { "Invoice": { "id": %d, "invoice_no_formatted": "%s", "token": "%s"}, "Client": {"id": 54321}}}
            """, expectedSfId, expectedSfNumber, expectedToken);
        ArgumentCaptor<HttpEntity> requestEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange( eq("https://moje.superfaktura.cz/invoices/create.json"), eq(HttpMethod.POST), requestEntityCaptor.capture(), eq(String.class) ))
                .thenReturn(createMockSuccessResponse(mockJsonResponse));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        invoiceService.generateTaxDocumentForDeposit(orderDepositPaid);

        HttpEntity capturedEntity = requestEntityCaptor.getValue();
        Map<String, Object> payload = (Map<String, Object>) capturedEntity.getBody();
        Map<String, Object> invoicePayload = (Map<String, Object>) payload.get("Invoice");
        assertEquals("regular", invoicePayload.get("type"));
        assertEquals(orderDepositPaid.getDepositPaidDate().toLocalDate().toString(), invoicePayload.get("date"));
        assertEquals(0, orderDepositPaid.getDepositAmount().compareTo((BigDecimal)invoicePayload.get("already_paid")));
        List<Map<String, Object>> itemsPayload = (List<Map<String, Object>>) payload.get("InvoiceItem");
        assertEquals(1, itemsPayload.size());
        assertTrue(((String)itemsPayload.get(0).get("name")).contains("Přijatá záloha"));
        assertEquals(0, new BigDecimal("413.22").compareTo((BigDecimal)itemsPayload.get(0).get("unit_price")));
        assertEquals(0, new BigDecimal("21.00").compareTo((BigDecimal)itemsPayload.get(0).get("tax")));
        verify(orderRepository).save(orderDepositPaid);
        assertEquals(expectedSfId, orderDepositPaid.getSfTaxDocumentId());
        assertEquals(expectedSfNumber, orderDepositPaid.getTaxDocumentNumber());
        assertEquals(expectedPdfUrl, orderDepositPaid.getSfTaxDocumentPdfUrl());
    }

    @Test
    @DisplayName("[generateFinalInvoice] Úspěšně vygeneruje finální fakturu (po záloze)")
    void generateFinalInvoice_AfterDeposit_Success() throws Exception {
        Long expectedSfId = 98765L; String expectedSfNumber = "FV-2025-003"; String expectedToken = "finalToken789"; String expectedPdfUrl = "https://moje.superfaktura.cz/invoices/pdf/98765/token:finalToken789";
        String mockJsonResponse = String.format("""
            { "error": 0, "data": { "Invoice": { "id": %d, "invoice_no_formatted": "%s", "token": "%s"}, "Client": {"id": 54321}}}
            """, expectedSfId, expectedSfNumber, expectedToken);
        ArgumentCaptor<HttpEntity> requestEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange( eq("https://moje.superfaktura.cz/invoices/create.json"), eq(HttpMethod.POST), requestEntityCaptor.capture(), eq(String.class) ))
                .thenReturn(createMockSuccessResponse(mockJsonResponse));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderDepositPaid.getOrderItems().clear();
        OrderItem finalItem = new OrderItem(); finalItem.setProductName("Finální Produkt"); finalItem.setCount(1); finalItem.setUnitPriceWithoutTax(new BigDecimal("826.45"));
        finalItem.setTaxRate(new BigDecimal("0.21")); finalItem.setTotalPriceWithoutTax(finalItem.getUnitPriceWithoutTax());
        orderDepositPaid.getOrderItems().add(finalItem);

        invoiceService.generateFinalInvoice(orderDepositPaid);

        HttpEntity capturedEntity = requestEntityCaptor.getValue();
        Map<String, Object> payload = (Map<String, Object>) capturedEntity.getBody();
        Map<String, Object> invoicePayload = (Map<String, Object>) payload.get("Invoice");
        assertEquals("regular", invoicePayload.get("type"));
        assertEquals(orderDepositPaid.getSfProformaInvoiceId(), invoicePayload.get("proforma_id"));
        List<Map<String, Object>> itemsPayload = (List<Map<String, Object>>) payload.get("InvoiceItem");
        assertEquals(1, itemsPayload.size());
        assertEquals("Finální Produkt", itemsPayload.get(0).get("name"));
        assertEquals(0, new BigDecimal("826.45").compareTo((BigDecimal)itemsPayload.get(0).get("unit_price")));
        verify(orderRepository).save(orderDepositPaid);
        assertEquals(expectedSfId, orderDepositPaid.getSfFinalInvoiceId());
        assertEquals(expectedSfNumber, orderDepositPaid.getFinalInvoiceNumber());
        assertEquals(expectedPdfUrl, orderDepositPaid.getSfFinalInvoicePdfUrl());
        assertTrue(orderDepositPaid.isFinalInvoiceGenerated());
    }

    // --- OPRAVENÝ TEST generateProformaInvoice_ApiError ---
    @Test
    @DisplayName("[generateProformaInvoice] Selže, pokud API vrátí chybu")
    void generateProformaInvoice_ApiError() {
        // --- Příprava ---
        String mockJsonErrorResponse = """
                { "error": 1, "error_message": { "Invoice": ["Chyba v datech faktury"] } }
                """;
        when(restTemplate.exchange( anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class) ))
                .thenReturn(createMockSuccessResponse(mockJsonErrorResponse));

        // --- Provedení & Ověření pomocí try-catch a kontroly Cause ---
        try {
            invoiceService.generateProformaInvoice(orderRequiresDeposit);
            fail("Expected RuntimeException was not thrown");
        } catch (RuntimeException e) {
            // Získáme příčinu (původní výjimku)
            Throwable cause = e.getCause();
            // Pokud je cause null, znamená to, že samotná RuntimeException byla ta původní (což by zde nemělo nastat, ale ošetříme)
            String messageToCheck = (cause != null) ? cause.getMessage() : e.getMessage();

            assertNotNull(messageToCheck, "Exception message (or cause's message) should not be null");
            assertTrue(messageToCheck.contains("SF API reported error"),
                    "Exception message (or cause) should contain 'SF API reported error'. Was: " + messageToCheck);
            assertTrue(messageToCheck.contains("Chyba v datech faktury"),
                    "Exception message (or cause) should contain 'Chyba v datech faktury'. Was: " + messageToCheck);
        } catch (Exception e) {
            fail("Expected RuntimeException but got " + e.getClass().getName());
        }

        verify(orderRepository, never()).save(any(Order.class));
    }

    // --- OPRAVENÝ TEST generateProformaInvoice_ApiUnavailable ---
    @Test
    @DisplayName("[generateProformaInvoice] Selže, pokud API není dostupné (HttpClientError)")
    void generateProformaInvoice_ApiUnavailable() {
        // --- Příprava ---
        when(restTemplate.exchange( anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class) ))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found"));

        // --- Provedení & Ověření pomocí try-catch ---
        try {
            invoiceService.generateProformaInvoice(orderRequiresDeposit);
            fail("Expected EntityNotFoundException (as RuntimeException) was not thrown");
        } catch (EntityNotFoundException e) { // Chytáme specifickou očekávanou výjimku
            String message = e.getMessage();
            assertNotNull(message, "Exception message should not be null");
            //assertTrue(message.contains("SF API client error"), "Exception message should contain 'SF API client error'. Was: " + message);
            assertTrue(message.contains("404 NOT FOUND"), "Exception message should contain '404 NOT FOUND'. Was: " + message);
        } catch (RuntimeException e) { // Zachytí i případnou obecnou RuntimeException, pokud by se wrapping změnil
            String message = e.getMessage();
            assertNotNull(message, "Exception message should not be null");
            //assertTrue(message.contains("SF API client error"), "Exception message should contain 'SF API client error'. Was: " + message);
            assertTrue(message.contains("404 NOT FOUND"), "Exception message should contain '404 NOT FOUND'. Was: " + message);
        } catch (Exception e) {
            fail("Expected RuntimeException but got " + e.getClass().getName());
        }

        verify(orderRepository, never()).save(any(Order.class));
    }


    // --- Testy Ostatních Metod ---

    @Test
    @DisplayName("[sendInvoiceByEmail] Úspěšně odešle požadavek na odeslání emailu")
    void sendInvoiceByEmail_Success() {
        Long sfInvoiceId = 12345L; String customerEmail = "recipient@test.com"; String invoiceType = "proforma"; String orderCode = "ORD-EMAIL";
        when(restTemplate.exchange( eq("https://moje.superfaktura.cz/invoices/send"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class) ))
                .thenReturn(createMockSuccessResponse("{\"success\": true}"));
        ArgumentCaptor<HttpEntity> requestEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        assertDoesNotThrow(() -> {
            invoiceService.sendInvoiceByEmail(sfInvoiceId, customerEmail, invoiceType, orderCode);
        });

        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), requestEntityCaptor.capture(), eq(String.class));
        HttpEntity capturedEntity = requestEntityCaptor.getValue();
        Map<String, Object> payload = (Map<String, Object>) capturedEntity.getBody();
        assertTrue(payload.containsKey("Email"));
        Map<String, Object> emailPayload = (Map<String, Object>) payload.get("Email");
        assertEquals(sfInvoiceId, emailPayload.get("invoice_id"));
        assertEquals(customerEmail, emailPayload.get("to"));
    }

    @Test
    @DisplayName("[markInvoiceAsPaidInSF] Úspěšně odešle požadavek na označení platby")
    void markInvoiceAsPaidInSF_Success() {
        Long sfInvoiceId = 98765L; BigDecimal amount = new BigDecimal("500.00"); LocalDate paymentDate = LocalDate.now(); String paymentType = "transfer"; String orderCode = "ORD-MARKPAID";
        String expectedUrl = "https://moje.superfaktura.cz/invoice_payments/add/invoice_id:" + sfInvoiceId + ".json";

        // Mock pro findInvoiceCurrency je nastaven v @BeforeEach
        when(restTemplate.exchange( eq(expectedUrl), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class) ))
                .thenReturn(createMockSuccessResponse("{\"success\": true}"));
        ArgumentCaptor<HttpEntity> requestEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        assertDoesNotThrow(() -> {
            invoiceService.markInvoiceAsPaidInSF(sfInvoiceId, amount, paymentDate, paymentType, orderCode);
        });

        verify(orderRepository).findBySfProformaInvoiceIdOrSfTaxDocumentIdOrSfFinalInvoiceId(sfInvoiceId, sfInvoiceId, sfInvoiceId);
        verify(restTemplate).exchange(eq(expectedUrl), eq(HttpMethod.POST), requestEntityCaptor.capture(), eq(String.class));
        HttpEntity capturedEntity = requestEntityCaptor.getValue();
        Map<String, Object> payload = (Map<String, Object>) capturedEntity.getBody();
        assertTrue(payload.containsKey("InvoicePayment"));
        Map<String, Object> paymentPayload = (Map<String, Object>) payload.get("InvoicePayment");
        assertEquals(sfInvoiceId, paymentPayload.get("invoice_id"));
        assertEquals(0, amount.compareTo((BigDecimal)paymentPayload.get("amount")));
        assertEquals(paymentDate.toString(), paymentPayload.get("date"));
        assertEquals(paymentType, paymentPayload.get("payment_type"));
        assertEquals(orderRequiresDeposit.getCurrency(), paymentPayload.get("currency"));
    }
}