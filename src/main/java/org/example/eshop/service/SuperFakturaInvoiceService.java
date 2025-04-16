package org.example.eshop.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.config.PriceConstants;
import org.example.eshop.model.*;
import org.example.eshop.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async; // <-- PŘIDAT IMPORT
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.hibernate.Hibernate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service("superFakturaInvoiceService")
public class SuperFakturaInvoiceService implements InvoiceService, PriceConstants {

    private static final Logger log = LoggerFactory.getLogger(SuperFakturaInvoiceService.class);

    // ... (ostatní fieldy a konstanty zůstávají stejné) ...
    @Value("${superfaktura.api.email}")
    private String sfApiEmail;
    @Value("${superfaktura.api.key}")
    private String sfApiKey;
    @Value("${superfaktura.api.company_id:}")
    private String sfCompanyId;
    @Value("${superfaktura.api.url:https://moje.superfaktura.cz}")
    private String sfApiUrl;

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OrderRepository orderRepository;

    // API Endpoints
    private static final String INVOICES_ENDPOINT_CREATE = "/invoices/create.json";
    private static final String INVOICES_ENDPOINT_SEND_EMAIL_ACTION = "/invoices/send";
    private static final String INVOICES_ENDPOINT_MARK_AS_SENT = "/invoices/mark_as_sent";
    private static final String INVOICES_ENDPOINT_PAY_PATTERN = "/invoice_payments/add/invoice_id:%d.json";
    private static final String INVOICES_ENDPOINT_PDF_WITH_TOKEN_PATTERN = "/invoices/pdf/%d/token:%s";

    // Ostatní konstanty
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int PROFORMA_DUE_DAYS = 7;
    private static final int FINAL_DUE_DAYS = 14;
    private static final Long TAX_DOCUMENT_SEQUENCE_ID = 342836L; // ID číselné řady pro DDKP (ověřit!)
    private static final String PAYMENT_STATUS_PAID = "PAID";

    // --- Implementace metod InvoiceService ---

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW) // Spustí se v nové transakci
    @Async // <-- PŘIDÁNA ANOTACE
    public void generateProformaInvoice(Order order) {
        // Reload je důležitý pro asynchronní metody, abychom měli čerstvá data
        Order freshOrder = reloadOrder(order.getId());
        log.info("ASYNC: Attempting to generate SuperFaktura PROFORMA invoice for order: {}. Order currency: {}", freshOrder.getOrderCode(), freshOrder.getCurrency());
        // ... (zbytek logiky metody zůstává stejný) ...
        if (!isValidForInvoice(freshOrder)) {
            log.error("ASYNC: Order {} is not valid for proforma generation.", freshOrder.getOrderCode());
            // V @Async metodě bychom neměli házet výjimku, která by nebyla zachycena, raději jen logovat
            return;
        }
        if (freshOrder.getDepositAmount() == null || freshOrder.getDepositAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("ASYNC: Proforma requested for order {} without deposit. Skipping.", freshOrder.getOrderCode());
            return;
        }
        if (freshOrder.getSfProformaInvoiceId() != null) {
            log.warn("ASYNC: Proforma already generated for order {}. SF ID: {}", freshOrder.getOrderCode(), freshOrder.getSfProformaInvoiceId());
            return;
        }

        Map<String, Object> payload = buildProformaPayload(freshOrder);
        try {
            JsonNode responseData = callSuperfakturaApi(INVOICES_ENDPOINT_CREATE, HttpMethod.POST, payload, freshOrder.getOrderCode(), "Proforma Invoice");
            if (responseData != null && responseData.has("Invoice") && responseData.get("Invoice").has("id")) {
                Long sfInvoiceId = responseData.get("Invoice").get("id").asLong();
                String sfInvoiceNumber = getInvoiceNumberFromResponse(responseData);
                String pdfUrl = getPdfDownloadUrlFromResponse(responseData, sfInvoiceId);
                log.info("ASYNC: Parsed Proforma Invoice response for order {}: ID={}, Number={}, PDF URL={}", freshOrder.getOrderCode(), sfInvoiceId, sfInvoiceNumber, pdfUrl);

                // Aktualizace objednávky v nové transakci
                updateOrderWithInvoiceData(freshOrder.getId(), sfInvoiceId, sfInvoiceNumber, pdfUrl, "proforma");

            } else {
                log.error("ASYNC: Failed to parse Proforma Invoice ID from SF response for order {}", freshOrder.getOrderCode());
            }
        } catch (Exception e) { // Chytáme obecnější Exception
            log.error("ASYNC: Failed to generate Proforma Invoice for order {}: {}", freshOrder.getOrderCode(), e.getMessage(), e);
            // Zde neodhazujeme výjimku dále, aby neovlivnila volající proces
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Async // <-- PŘIDÁNA ANOTACE
    public void generateTaxDocumentForDeposit(Order order) {
        Order freshOrder = reloadOrder(order.getId());
        log.info("ASYNC: Attempting to generate SuperFaktura TAX DOCUMENT for paid deposit for order: {}. Order currency: {}", freshOrder.getOrderCode(), freshOrder.getCurrency());
        // ... (zbytek logiky metody zůstává stejný) ...
        if (!isValidForInvoice(freshOrder)) {
            log.error("ASYNC: Order {} is not valid for Tax Document generation.", freshOrder.getOrderCode());
            return;
        }
        if (freshOrder.getDepositAmount() == null || freshOrder.getDepositAmount().compareTo(BigDecimal.ZERO) <= 0 || freshOrder.getDepositPaidDate() == null) {
            log.warn("ASYNC: Tax Document requested for order {} but deposit/paid date missing. Skipping.", freshOrder.getOrderCode());
            return;
        }
        if (freshOrder.getSfTaxDocumentId() != null) {
            log.warn("ASYNC: Tax Document already generated for order {}. SF ID: {}", freshOrder.getOrderCode(), freshOrder.getSfTaxDocumentId());
            return;
        }

        Map<String, Object> payload = buildTaxDocumentPayload(freshOrder);
        try {
            JsonNode responseData = callSuperfakturaApi(INVOICES_ENDPOINT_CREATE, HttpMethod.POST, payload, freshOrder.getOrderCode(), "Tax Document (Deposit)");
            if (responseData != null && responseData.has("Invoice") && responseData.get("Invoice").has("id")) {
                Long sfInvoiceId = responseData.get("Invoice").get("id").asLong();
                String sfInvoiceNumber = getInvoiceNumberFromResponse(responseData);
                String pdfUrl = getPdfDownloadUrlFromResponse(responseData, sfInvoiceId);
                log.info("ASYNC: Parsed Tax Document response for order {}: ID={}, Number={}, PDF URL={}", freshOrder.getOrderCode(), sfInvoiceId, sfInvoiceNumber, pdfUrl);

                // Aktualizace objednávky v nové transakci
                updateOrderWithInvoiceData(freshOrder.getId(), sfInvoiceId, sfInvoiceNumber, pdfUrl, "tax_document");

            } else {
                log.error("ASYNC: Failed to parse Tax Document ID from SF response for order {}", freshOrder.getOrderCode());
            }
        } catch (Exception e) {
            log.error("ASYNC: Failed to generate Tax Document for order {}: {}", freshOrder.getOrderCode(), e.getMessage(), e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Async // <-- PŘIDÁNA ANOTACE
    public void generateFinalInvoice(Order order) {
        Order freshOrder = reloadOrder(order.getId());
        log.info("ASYNC: Attempting to generate SuperFaktura FINAL invoice for order: {}. Order currency: {}", freshOrder.getOrderCode(), freshOrder.getCurrency());
        // ... (zbytek logiky metody zůstává stejný) ...
        if (!isValidForInvoice(freshOrder)) {
            log.error("ASYNC: Order {} is not valid for final invoice generation.", freshOrder.getOrderCode());
            return;
        }
        if (freshOrder.isFinalInvoiceGenerated() || freshOrder.getSfFinalInvoiceId() != null) {
            log.warn("ASYNC: Final invoice already generated for order {}. SF ID: {}", freshOrder.getOrderCode(), freshOrder.getSfFinalInvoiceId());
            return;
        }
        if (freshOrder.getDepositAmount() != null && freshOrder.getDepositAmount().compareTo(BigDecimal.ZERO) > 0 && freshOrder.getDepositPaidDate() == null) {
            log.error("ASYNC: Cannot generate final invoice for order {} because deposit is not paid.", freshOrder.getOrderCode());
            return; // V asynchronní metodě neodhazujeme výjimku
        }

        Map<String, Object> payload = buildFinalInvoicePayload(freshOrder);
        try {
            JsonNode responseData = callSuperfakturaApi(INVOICES_ENDPOINT_CREATE, HttpMethod.POST, payload, freshOrder.getOrderCode(), "Final Invoice");
            if (responseData != null && responseData.has("Invoice") && responseData.get("Invoice").has("id")) {
                Long sfInvoiceId = responseData.get("Invoice").get("id").asLong();
                String sfInvoiceNumber = getInvoiceNumberFromResponse(responseData);
                String pdfUrl = getPdfDownloadUrlFromResponse(responseData, sfInvoiceId);
                log.info("ASYNC: Parsed Final Invoice response for order {}: ID={}, Number={}, PDF URL={}", freshOrder.getOrderCode(), sfInvoiceId, sfInvoiceNumber, pdfUrl);

                // Aktualizace objednávky v nové transakci
                updateOrderWithInvoiceData(freshOrder.getId(), sfInvoiceId, sfInvoiceNumber, pdfUrl, "final");

            } else {
                log.error("ASYNC: Failed to parse Final Invoice ID from SF response for order {}", freshOrder.getOrderCode());
            }
        } catch (Exception e) {
            log.error("ASYNC: Failed to generate Final Invoice for order {}: {}", freshOrder.getOrderCode(), e.getMessage(), e);
        }
    }

    // --- Další Veřejné Metody ---

    @Async // <-- PŘIDÁNA ANOTACE
    public void sendInvoiceByEmail(Long sfInvoiceId, String customerEmail, String invoiceType, String orderCode) {
        if (sfInvoiceId == null || sfInvoiceId <= 0 || !StringUtils.hasText(customerEmail)) {
            log.error("ASYNC: Invalid invoice ID or customer email for sending. SF ID: {}, Email: {}", sfInvoiceId, customerEmail);
            // V @Async metodě neodhazujeme výjimku
            return;
        }
        String endpoint = INVOICES_ENDPOINT_SEND_EMAIL_ACTION;
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> emailData = new HashMap<>();
        emailData.put("invoice_id", sfInvoiceId);
        emailData.put("to", customerEmail);
        payload.put("Email", emailData);
        try {
            log.trace("ASYNC: Sending email payload to {}: {}", endpoint, objectMapper.writeValueAsString(payload));
            // Volání API, chyby jsou logovány uvnitř callSuperfakturaApi
            callSuperfakturaApi(endpoint, HttpMethod.POST, payload, orderCode, "Send Email " + invoiceType);
            log.info("ASYNC: Successfully requested sending of {} (SF ID: {}) for order {} to {}", invoiceType, sfInvoiceId, orderCode, customerEmail);
        } catch (JsonProcessingException e) {
            log.error("ASYNC: Error serializing email payload for SF ID {}: {}", sfInvoiceId, e.getMessage());
        } catch (Exception e) { // Zachytáváme obecnější Exception
            log.error("ASYNC: Error sending {} via SF API for order {}: {}", invoiceType, orderCode, e.getMessage(), e);
        }
    }

    @Async // <-- PŘIDÁNA ANOTACE
    public void markInvoiceAsSent(Long sfInvoiceId, String customerEmail, String subject) {
        if (sfInvoiceId == null || sfInvoiceId <= 0) {
            log.error("ASYNC: Invalid invoice ID for marking as sent: {}", sfInvoiceId);
            return;
        }
        String endpoint = INVOICES_ENDPOINT_MARK_AS_SENT;
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> emailData = new HashMap<>();
        emailData.put("invoice_id", sfInvoiceId);
        emailData.put("email", StringUtils.hasText(customerEmail) ? customerEmail : "neznámý@email.cz");
        emailData.put("subject", StringUtils.hasText(subject) ? subject : "Faktura odeslána");
        payload.put("InvoiceEmail", emailData);
        try {
            log.trace("ASYNC: Marking as sent payload to {}: {}", endpoint, objectMapper.writeValueAsString(payload));
            callSuperfakturaApi(endpoint, HttpMethod.POST, payload, "N/A", "Mark As Sent");
            log.info("ASYNC: Successfully marked invoice {} as sent to {}", sfInvoiceId, customerEmail);
        } catch (JsonProcessingException e) {
            log.error("ASYNC: Error serializing mark as sent payload for SF ID {}: {}", sfInvoiceId, e.getMessage());
        } catch (Exception e) {
            log.error("ASYNC: Failed to mark invoice {} as sent: {}", sfInvoiceId, e.getMessage(), e);
        }
    }

    @Async // <-- PŘIDÁNA ANOTACE
    @Transactional(propagation = Propagation.REQUIRES_NEW) // Spustí se v nové transakci
    public void markInvoiceAsPaidInSF(Long sfInvoiceId, BigDecimal amount, LocalDate paymentDate, String sfPaymentType, String orderCode) {
        if (sfInvoiceId == null || sfInvoiceId <= 0 || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || paymentDate == null) {
            log.error("ASYNC: Invalid parameters for marking invoice as paid in SF. SF Invoice ID: {}, Amount: {}, Date: {}", sfInvoiceId, amount, paymentDate);
            return;
        }

        String endpoint = String.format(INVOICES_ENDPOINT_PAY_PATTERN, sfInvoiceId);
        String requestType = "Mark Invoice Paid";
        log.info("ASYNC: Attempting to mark invoice SF ID {} as paid for order {} via API. Amount: {}, Date: {}, Type: {}", sfInvoiceId, orderCode, amount, paymentDate, sfPaymentType);

        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("invoice_id", sfInvoiceId);
        paymentData.put("payment_type", sfPaymentType != null ? sfPaymentType : "transfer");
        paymentData.put("amount", amount.setScale(PRICE_SCALE, ROUNDING_MODE));
        paymentData.put("currency", findInvoiceCurrency(sfInvoiceId, orderCode));
        paymentData.put("date", paymentDate.format(DATE_FORMATTER));

        payload.put("InvoicePayment", paymentData);

        try {
            callSuperfakturaApi(endpoint, HttpMethod.POST, payload, orderCode, requestType);
            log.info("ASYNC: Successfully requested marking invoice SF ID {} as paid for order {}", sfInvoiceId, orderCode);
        } catch (Exception e) {
            log.error("ASYNC: Failed to mark invoice SF ID {} as paid for order {}: {}", sfInvoiceId, orderCode, e.getMessage(), e);
        }
    }

    // --- Pomocné metody ---

    // Metoda pro aktualizaci objednávky po úspěšném volání API (spouští se v nové transakci)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void updateOrderWithInvoiceData(Long orderId, Long sfInvoiceId, String sfInvoiceNumber, String pdfUrl, String invoiceType) {
        log.debug("ASYNC - updateOrderWithInvoiceData: Updating order ID {} for invoice type '{}'", orderId, invoiceType);
        Order orderToUpdate = orderRepository.findById(orderId).orElse(null);
        if (orderToUpdate == null) {
            log.error("ASYNC - updateOrderWithInvoiceData: Order ID {} not found for update!", orderId);
            return;
        }

        switch (invoiceType) {
            case "proforma":
                orderToUpdate.setSfProformaInvoiceId(sfInvoiceId);
                orderToUpdate.setProformaInvoiceNumber(sfInvoiceNumber);
                orderToUpdate.setSfProformaPdfUrl(pdfUrl);
                break;
            case "tax_document":
                orderToUpdate.setSfTaxDocumentId(sfInvoiceId);
                orderToUpdate.setTaxDocumentNumber(sfInvoiceNumber);
                orderToUpdate.setSfTaxDocumentPdfUrl(pdfUrl);
                break;
            case "final":
                orderToUpdate.setSfFinalInvoiceId(sfInvoiceId);
                orderToUpdate.setFinalInvoiceNumber(sfInvoiceNumber);
                orderToUpdate.setSfFinalInvoicePdfUrl(pdfUrl);
                orderToUpdate.setFinalInvoiceGenerated(true);
                break;
            default:
                log.warn("ASYNC - updateOrderWithInvoiceData: Unknown invoice type '{}' for order ID {}", invoiceType, orderId);
                return;
        }
        orderRepository.save(orderToUpdate);
        log.info("ASYNC - updateOrderWithInvoiceData: Order ID {} updated successfully with {} details.", orderId, invoiceType);
    }


    // Metoda reloadOrder a ostatní pomocné metody (build*Payload, callSuperfakturaApi, etc.) zůstávají stejné
    // ... (vložte sem nezměněný kód pomocných metod) ...
    private Order reloadOrder(Long orderId) {
        return orderRepository.findById(orderId).map(order -> {
            Hibernate.initialize(order.getCustomer());
            Hibernate.initialize(order.getOrderItems());
            if (order.getOrderItems() != null) {
                order.getOrderItems().forEach(item -> Hibernate.initialize(item.getSelectedAddons()));
            }
            return order;
        }).orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId)); // Lambda pro orElseThrow
    }

    // Hlavní metoda pro volání SF API (zůstává z větší části stejná)
    private JsonNode callSuperfakturaApi(String endpoint, HttpMethod method, Map<String, Object> payload, String orderCode, String requestType) {
        HttpHeaders headers = prepareHeaders();
        HttpEntity<?> requestEntity;
        String jsonPayloadString = "(Payload is null)";
        if (payload != null) {
            try {
                jsonPayloadString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            } catch (JsonProcessingException e) {
                log.error("Error serializing payload for logging (Order {} - {})", orderCode, requestType, e);
                jsonPayloadString = "(Payload serialization error)";
            }
        }
        log.info("SF API Request Payload ({} - Order {}):\n{}", requestType, orderCode, jsonPayloadString);

        if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH) {
            requestEntity = new HttpEntity<>(payload, headers);
        } else {
            headers.setContentType(null); // Pro GET/DELETE se nesmí posílat Content-Type
            requestEntity = new HttpEntity<>(headers);
        }

        String url = sfApiUrl + endpoint;
        ResponseEntity<String> response;
        String responseBody = null;
        try {
            log.info("Sending {} request to SF API: URL={}, Method={}", requestType, url, method);
            response = restTemplate.exchange(url, method, requestEntity, String.class);
            responseBody = response.getBody();
            log.debug("Raw SF API Response Body for {} (Order {}): START\n{}\nEND_RESPONSE", requestType, orderCode, responseBody);

            if (response.getStatusCode().is2xxSuccessful() && responseBody != null) {
                JsonNode rootNode = objectMapper.readTree(responseBody);
                if (rootNode.has("error") && rootNode.get("error").asInt() != 0) {
                    String errorMessage = extractErrorMessage(rootNode);
                    log.error("SF API call OK (2xx) but returned error for {} (Order {}): {}", requestType, orderCode, errorMessage);
                    throw new RuntimeException("SF API reported error for " + requestType + " (Order: " + orderCode + "): " + errorMessage);
                }
                // Vrací "data" klíč pro /create, ale ne pro /pay
                if (rootNode.has("data")) {
                    log.info("{} API call successful for order {}.", requestType, orderCode);
                    return rootNode.get("data");
                } else {
                    log.info("SF API call successful (2xx) for {} (Order {}). Response has no 'data' key.", requestType, orderCode);
                    return rootNode; // Vracíme celý root pro /pay apod.
                }
            } else {
                log.error("Failed {} API call for order {}. Status: {}, Body: {}", requestType, orderCode, response.getStatusCode(), responseBody);
                throw new RuntimeException("Failed SF API call for " + requestType + ". Status: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            log.error("HttpClientError on {} API call for order {}: Status={}, Body={}", requestType, orderCode, e.getStatusCode(), e.getResponseBodyAsString(), e);
            String parsedErrorMessage = extractErrorMessageFromBody(e.getResponseBodyAsString(), e.getStatusCode());
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new RuntimeException("SF API client error for " + requestType + ": " + e.getStatusCode() + " - Check credentials/permissions. Error: " + parsedErrorMessage, e);
            }
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new EntityNotFoundException("SF API client error for " + requestType + ": 404 NOT FOUND. Error: " + parsedErrorMessage);
            }
            throw new RuntimeException("SF API client error for " + requestType + ": " + parsedErrorMessage, e);
        } catch (ResourceAccessException e) {
            log.error("Network error on {} API call for order {}: {}", requestType, orderCode, e.getMessage(), e);
            throw new RuntimeException("SF API network error for " + requestType, e);
        } catch (RestClientException e) {
            log.error("RestClientException on {} API call for order {}: {}. Resp: {}", requestType, orderCode, e.getMessage(), responseBody, e);
            throw new RuntimeException("SF API communication error for " + requestType, e);
        } catch (JsonProcessingException jsonEx) {
            log.error("Failed to parse SF JSON response for {} (Order {}): {}. Body: {}", requestType, orderCode, jsonEx.getMessage(), responseBody);
            throw new RuntimeException("Failed to parse SF response for " + requestType, jsonEx);
        } catch (Exception e) {
            log.error("Unexpected error during {} API call for order {}: {}", requestType, orderCode, e.getMessage(), e);
            throw new RuntimeException("Unexpected error during SF API call for " + requestType, e);
        }
    }

    // Ostatní pomocné metody (prepareHeaders, extractErrorMessage*, getInvoiceNumberFromResponse, getPdfDownloadUrlFromResponse) zůstávají stejné
    private HttpHeaders prepareHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String authHeader;
        String moduleName = "EshopDrevnikyJava/1.0";
        try {
            String encodedEmail = URLEncoder.encode(sfApiEmail, StandardCharsets.UTF_8);
            String encodedApiKey = URLEncoder.encode(sfApiKey, StandardCharsets.UTF_8);
            String encodedModule = URLEncoder.encode(moduleName, StandardCharsets.UTF_8);
            authHeader = String.format("SFAPI email=%s&apikey=%s&module=%s", encodedEmail, encodedApiKey, encodedModule);
            if (StringUtils.hasText(sfCompanyId)) {
                String encodedCompanyId = URLEncoder.encode(sfCompanyId, StandardCharsets.UTF_8);
                authHeader += String.format("&company_id=%s", encodedCompanyId);
            }
        } catch (Exception e) {
            log.error("Failed to URL encode SF API credentials!", e);
            throw new RuntimeException("Failed to URL encode API credentials", e);
        }
        headers.set("Authorization", authHeader);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String extractErrorMessage(JsonNode rootNode) {
        StringBuilder errors = new StringBuilder();
        if (rootNode == null) return "Unknown error (null response)";
        if (rootNode.has("error_message")) {
            JsonNode messageNode = rootNode.get("error_message");
            if (messageNode.isTextual()) {
                errors.append(messageNode.asText());
            } else if (messageNode.isObject()) {
                messageNode.fields().forEachRemaining(entry -> {
                    if (entry.getValue().isArray()) {
                        entry.getValue().forEach(item -> errors.append(entry.getKey()).append(": ").append(item.asText("N/A")).append("; "));
                    } else {
                        errors.append(entry.getKey()).append(": ").append(entry.getValue().asText("N/A")).append("; ");
                    }
                });
            } else if (messageNode.isArray() && !messageNode.isEmpty()) {
                messageNode.forEach(item -> errors.append(item.asText("N/A")).append("; "));
            }
        }
        if (rootNode.has("message") && errors.isEmpty()) {
            errors.append(rootNode.get("message").asText("Unknown error (message key)"));
        }
        if (errors.isEmpty()) {
            return rootNode.toString();
        }
        return errors.toString().trim().replaceAll("; $", "");
    }

    private String extractErrorMessageFromBody(String errorBody, HttpStatusCode status) {
        String prefix = "Status " + status.value();
        if (!StringUtils.hasText(errorBody)) {
            return prefix + " (No Response Body)";
        }
        try {
            JsonNode errorNode = objectMapper.readTree(errorBody);
            return prefix + " - " + extractErrorMessage(errorNode);
        } catch (JsonProcessingException jsonEx) {
            return prefix + " - " + errorBody;
        }
    }

    private String getInvoiceNumberFromResponse(JsonNode responseData) {
        if (responseData != null && responseData.has("Invoice")) {
            JsonNode invoiceNode = responseData.get("Invoice");
            if (invoiceNode.has("invoice_no_formatted")) return invoiceNode.get("invoice_no_formatted").asText(null);
            else if (invoiceNode.has("invoice_no")) return invoiceNode.get("invoice_no").asText(null);
        }
        return null;
    }

    private String getPdfDownloadUrlFromResponse(JsonNode responseData, Long sfInvoiceId) {
        if (sfInvoiceId == null || sfInvoiceId <= 0) return null;
        String token = null;
        if (responseData != null && responseData.has("Invoice") && responseData.get("Invoice").has("token")) {
            token = responseData.get("Invoice").get("token").asText(null);
        }
        if (StringUtils.hasText(token)) {
            String url = String.format(sfApiUrl + INVOICES_ENDPOINT_PDF_WITH_TOKEN_PATTERN, sfInvoiceId, token);
            log.debug("Constructed PDF download URL with token: {}", url);
            return url;
        } else {
            log.warn("PDF token not found in SF API response for invoice ID {}. Using web view link as fallback.", sfInvoiceId);
            String baseUrl = sfApiUrl.contains(".cz") ? "https://moje.superfaktura.cz" : "https://moja.superfaktura.sk";
            return String.format("%s/invoices/view/%d", baseUrl, sfInvoiceId);
        }
    }

    private Map<String, Object> buildProformaPayload(Order order) {
        Map<String, Object> invoiceData = buildBaseInvoiceData(order);
        invoiceData.put("type", "proforma");
        invoiceData.put("due_date", LocalDate.now().plusDays(PROFORMA_DUE_DAYS).format(DATE_FORMATTER));
        invoiceData.put("note", "Záloha za objednané zboží dle obj. č. " + order.getOrderCode() + (StringUtils.hasText(order.getNote()) ? "\nPoznámka: " + order.getNote() : ""));

        List<Map<String, Object>> items = new ArrayList<>();
        // Položka pro zálohu (použije upravenou metodu s prům. DPH a RC)
        Map<String, Object> depositItem = buildDepositInvoiceItem(order, "Záloha na dřevník dle obj. č. " + order.getOrderCode(), order.getDepositAmount());
        items.add(depositItem);

        // Vytvoření finálního payloadu pomocí nové pomocné metody
        return createFinalPayload(invoiceData, mapCustomerToClientData(order), items);
    }

    private Map<String, Object> buildTaxDocumentPayload(Order order) {
        Map<String, Object> invoiceData = buildBaseInvoiceData(order);
        LocalDate paymentDate = order.getDepositPaidDate().toLocalDate();
        invoiceData.put("type", "regular"); // DDKP je technicky "regular" faktura
        if (TAX_DOCUMENT_SEQUENCE_ID != null) invoiceData.put("sequence_id", TAX_DOCUMENT_SEQUENCE_ID); // Použijeme číselnou řadu pro DDKP
        invoiceData.put("date", paymentDate.format(DATE_FORMATTER)); // Datum vystavení = datum platby zálohy
        invoiceData.put("delivery_date", paymentDate.format(DATE_FORMATTER)); // DUZP = datum platby zálohy
        invoiceData.put("due_date", paymentDate.format(DATE_FORMATTER)); // Splatnost = datum platby zálohy
        String note = "Daňový doklad k záloze zaplacené dne " + paymentDate.format(DateTimeFormatter.ofPattern("d.M.yyyy")) + " k obj. č. " + order.getOrderCode() + ".";
        if (order.getProformaInvoiceNumber() != null)
            note += "\nVztahuje se k zálohové faktuře č. " + order.getProformaInvoiceNumber() + ".";
        invoiceData.put("note", note);
        invoiceData.put("already_paid", order.getDepositAmount()); // Uhrazeno = výše zálohy
        invoiceData.put("paid_date", paymentDate.format(DATE_FORMATTER)); // Datum úhrady

        List<Map<String, Object>> items = new ArrayList<>();
        // Položka pro DDKP (použije upravenou metodu s prům. DPH a RC)
        Map<String, Object> depositItem = buildDepositInvoiceItem(order, "Přijatá záloha k obj. č. " + order.getOrderCode(), order.getDepositAmount());
        items.add(depositItem);

        // Vytvoření finálního payloadu pomocí nové pomocné metody
        return createFinalPayload(invoiceData, mapCustomerToClientData(order), items);
    }

    private Map<String, Object> buildFinalInvoicePayload(Order order) {
        Map<String, Object> invoiceData = buildBaseInvoiceData(order);
        LocalDate today = LocalDate.now();
        LocalDate deliveryDate = order.getShippedDate() != null ? order.getShippedDate().toLocalDate() : today; // DUZP = datum odeslání nebo dnes
        invoiceData.put("type", "regular"); // Finální faktura je "regular"
        invoiceData.put("delivery_date", deliveryDate.format(DATE_FORMATTER));
        invoiceData.put("due_date", today.plusDays(FINAL_DUE_DAYS).format(DATE_FORMATTER)); // Splatnost
        String note = StringUtils.hasText(order.getNote()) ? order.getNote() : "";

        // Propojení se zálohou/DDKP
        if (order.getSfProformaInvoiceId() != null && order.getDepositPaidDate() != null) {
            invoiceData.put("proforma_id", order.getSfProformaInvoiceId()); // Propojení pro odečet zálohy v SF
            note = "Odpočet zálohy dle zálohové faktury č. " + order.getProformaInvoiceNumber() + ".\n" + note;
            log.info("Linking final invoice for order {} to Proforma ID: {}", order.getOrderCode(), order.getSfProformaInvoiceId());
        } else if (order.getSfTaxDocumentId() != null) {
            // Pokud není proforma_id, ale je DDKP, můžeme přidat info do poznámky
            note = "Vztahuje se k DDKP č. " + order.getTaxDocumentNumber() + ".\n" + note;
            log.warn("Linking final invoice using Proforma ID (if available), but Tax Document ID {} also exists for order {}.", order.getSfTaxDocumentId(), order.getOrderCode());
        }
        invoiceData.put("note", note.trim());

        // Položky faktury (použije upravenou metodu s položkami a zaokrouhlením)
        List<Map<String, Object>> itemsWithRounding = buildStandardInvoiceItemsWithRounding(order);

        // Sleva z kupónu (aplikovaná na celkovou částku PŘED DPH)
        if (order.getCouponDiscountAmount() != null && order.getCouponDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            invoiceData.put("discount_amount", order.getCouponDiscountAmount().setScale(PRICE_SCALE, ROUNDING_MODE));
            String discountNote = " Aplikována sleva z kupónu: " + (StringUtils.hasText(order.getAppliedCouponCode()) ? order.getAppliedCouponCode() : "") + " (-" + order.getCouponDiscountAmount().setScale(PRICE_SCALE, ROUNDING_MODE) + " " + order.getCurrency() + " bez DPH).";
            // Přidáme poznámku o slevě k existující poznámce
            invoiceData.put("note", (invoiceData.get("note") != null ? invoiceData.get("note") : "") + discountNote);
        }

        // Označení úhrady (already_paid)
        if ((order.getDepositAmount() == null || order.getDepositAmount().compareTo(BigDecimal.ZERO) <= 0) && PAYMENT_STATUS_PAID.equals(order.getPaymentStatus()) && order.getPaymentDate() != null) {
            // Pokud nebyla záloha A JE zaplaceno, označíme celou částku jako uhrazenou
            invoiceData.put("already_paid", order.getTotalPrice()); // Použijeme finální zaokrouhlenou cenu
            invoiceData.put("paid_date", order.getPaymentDate().toLocalDate().format(DATE_FORMATTER));
            log.info("Setting 'already_paid' to total rounded price ({}) and 'paid_date' for final invoice {} (no deposit scenario)", order.getTotalPrice(), order.getOrderCode());
        } else {
            // Pokud byla záloha, spoléháme na odečet přes propojení s proforma_id v SF
            log.info("Not setting 'already_paid' for final invoice {}. Relying on SF deduction via linked proforma_id or payment status.", order.getOrderCode());
        }

        // Vytvoření finálního payloadu pomocí nové pomocné metody
        return createFinalPayload(invoiceData, mapCustomerToClientData(order), itemsWithRounding);
    }

    private Map<String, Object> buildBaseInvoiceData(Order order) {
        Map<String, Object> d = new HashMap<>();
        d.put("order_no", order.getOrderCode());
        d.put("variable", order.getOrderCode());
        d.put("specific", String.valueOf(order.getId()));
        d.put("date", LocalDate.now().format(DATE_FORMATTER));
        d.put("payment_type", mapPaymentMethod(order.getPaymentMethod()));
        d.put("currency", order.getCurrency());
        d.put("language", EURO_CURRENCY.equals(order.getCurrency()) ? "slo" : "cze");
        d.put("rounding", "item");
        return d;
    }

    // Metoda mapCustomerToClientData - OPRAVENO trimWhitespace
    private Map<String, Object> mapCustomerToClientData(Order order) {
        Customer customer = order.getCustomer();
        Map<String, Object> data = new HashMap<>();

        // Jméno klienta pro SF
        if (StringUtils.hasText(order.getInvoiceCompanyName())) {
            data.put("name", order.getInvoiceCompanyName());
            // Kontaktní osoba u firmy
            String contactPerson = (order.getInvoiceFirstName() != null ? order.getInvoiceFirstName().trim() : "")
                    + " "
                    + (order.getInvoiceLastName() != null ? order.getInvoiceLastName().trim() : "");
            if (StringUtils.hasText(contactPerson.trim())) {
                data.put("contact_person", contactPerson.trim());
            }
        } else if (StringUtils.hasText(order.getInvoiceFirstName()) || StringUtils.hasText(order.getInvoiceLastName())) {
            // Použijeme .trim() místo trimWhitespace()
            data.put("name", ((order.getInvoiceFirstName() != null ? order.getInvoiceFirstName().trim() : "")
                    + " "
                    + (order.getInvoiceLastName() != null ? order.getInvoiceLastName().trim() : "")).trim());
        } else {
            // Fallback
            log.warn("Invoice company name and person name missing in order {}, using customer contact name as fallback for SF client name.", order.getOrderCode());
            data.put("name", customer.getFirstName() + " " + customer.getLastName());
        }

        data.put("email", customer.getEmail());
        if (StringUtils.hasText(customer.getPhone())) data.put("phone", customer.getPhone());
        data.put("address", order.getInvoiceStreet());
        data.put("city", order.getInvoiceCity());
        data.put("zip", order.getInvoiceZipCode());
        data.put("country", order.getInvoiceCountry());
        // data.put("currency", order.getCurrency()); // Měna je už v Invoice datech, zde být nemusí

        if (StringUtils.hasText(order.getInvoiceTaxId())) data.put("ico", order.getInvoiceTaxId());
        if (StringUtils.hasText(order.getInvoiceVatId())) {
            String v = order.getInvoiceVatId().toUpperCase().replaceAll("\\s+", "");
            if (v.startsWith("CZ")) data.put("dic", v);
            else if (v.startsWith("SK")) data.put("ic_dph", v);
            else {
                data.put("ic_dph", v);
                log.warn("Mapping non-SK/CZ VAT ID '{}' to 'ic_dph' for order {}.", v, order.getOrderCode());
            }
        }

        // Dodací adresa (použijeme novou metodu isAddressesMatchInOrder z Order entity)
        if (!order.isAddressesMatchInOrder()) { // Použijeme metodu z Order.java
            String dn = StringUtils.hasText(order.getDeliveryCompanyName()) ? order.getDeliveryCompanyName() :
                    ((order.getDeliveryFirstName() != null ? order.getDeliveryFirstName().trim() : "") // .trim()
                            + " " +
                            (order.getDeliveryLastName() != null ? order.getDeliveryLastName().trim() : "")).trim(); // .trim()
            if (StringUtils.hasText(dn)) data.put("delivery_name", dn);
            if (StringUtils.hasText(order.getDeliveryStreet())) data.put("delivery_address", order.getDeliveryStreet());
            if (StringUtils.hasText(order.getDeliveryCity())) data.put("delivery_city", order.getDeliveryCity());
            if (StringUtils.hasText(order.getDeliveryZipCode())) data.put("delivery_zip", order.getDeliveryZipCode());
            if (StringUtils.hasText(order.getDeliveryCountry()))
                data.put("delivery_country", order.getDeliveryCountry());
            if (StringUtils.hasText(order.getDeliveryPhone())) data.put("delivery_phone", order.getDeliveryPhone());
        }
        return data;
    }

    // Metoda pro zjištění měny faktury
    private String findInvoiceCurrency(Long sfInvoiceId, String orderCode) {
        Optional<Order> orderOpt = orderRepository.findBySfProformaInvoiceIdOrSfTaxDocumentIdOrSfFinalInvoiceId(sfInvoiceId, sfInvoiceId, sfInvoiceId);
        if (orderOpt.isPresent()) {
            String currency = orderOpt.get().getCurrency();
            log.debug("Found currency '{}' for invoice SF ID {} from order {}", currency, sfInvoiceId, orderOpt.get().getOrderCode());
            return currency;
        }
        Optional<Order> orderFallbackOpt = orderRepository.findByOrderCode(orderCode);
        if (orderFallbackOpt.isPresent()) {
            String currency = orderFallbackOpt.get().getCurrency();
            log.warn("Could not find order by SF Invoice ID {}, found by order code {}. Using currency '{}'", sfInvoiceId, orderCode, currency);
            return currency;
        }
        log.error("Could not determine currency for invoice SF ID {} (Order code '{}'). Falling back to default '{}'.", sfInvoiceId, orderCode, DEFAULT_CURRENCY);
        return DEFAULT_CURRENCY;
    }

    private java.math.BigDecimal calculateAverageTaxRate(java.util.List<OrderItem> items) {
        if (org.springframework.util.CollectionUtils.isEmpty(items)) {
            log.warn("Cannot calculate average tax rate: order items list is empty or null.");
            return java.math.BigDecimal.ZERO;
        }

        java.math.BigDecimal totalValueWithoutTax = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalTaxAmount = java.math.BigDecimal.ZERO;

        for (OrderItem item : items) {
            if (item == null) continue;
            // Použijeme celkové hodnoty vypočtené a uložené v OrderItem
            totalValueWithoutTax = totalValueWithoutTax.add(java.util.Optional.ofNullable(item.getTotalPriceWithoutTax()).orElse(java.math.BigDecimal.ZERO));
            totalTaxAmount = totalTaxAmount.add(java.util.Optional.ofNullable(item.getTotalTaxAmount()).orElse(java.math.BigDecimal.ZERO));
        }

        if (totalValueWithoutTax.compareTo(java.math.BigDecimal.ZERO) == 0) {
            log.warn("Cannot calculate average tax rate: total value without tax is zero.");
            // Pokud je základ 0, nemá smysl počítat sazbu, vrátíme 0. Může nastat u objednávek zdarma.
            // Alternativně bychom mohli vrátit sazbu první položky, pokud existuje.
            return java.math.BigDecimal.ZERO;
        }

        // Výpočet průměrné sazby: (Celkové DPH) / (Celkový základ DPH)
        // Použijeme vyšší přesnost pro dělení, abychom minimalizovali chyby zaokrouhlení
        java.math.BigDecimal averageRate = totalTaxAmount.divide(totalValueWithoutTax, 4, java.math.RoundingMode.HALF_UP);
        log.debug("Calculated average tax rate: {}", averageRate);
        return averageRate; // Vrátí sazbu jako 0.xx
    }

    private String buildItemDescription(OrderItem oi) {
        StringBuilder d = new StringBuilder();
        boolean first = true;
        if (oi.isCustomConfigured() && (oi.getLength() != null || oi.getWidth() != null || oi.getHeight() != null)) {
            d.append("Rozměry (DxHxV): ").append(oi.getLength() != null ? oi.getLength().stripTrailingZeros().toPlainString() : "-").append("x").append(oi.getWidth() != null ? oi.getWidth().stripTrailingZeros().toPlainString() : "-").append("x").append(oi.getHeight() != null ? oi.getHeight().stripTrailingZeros().toPlainString() : "-").append(" cm");
            first = false;
        }
        if (StringUtils.hasText(oi.getGlaze())) {
            if (!first) d.append(" | ");
            d.append("Lazura: ").append(oi.getGlaze());
            first = false;
        }
        if (StringUtils.hasText(oi.getRoofColor())) {
            if (!first) d.append(" | ");
            d.append("Střecha: ").append(oi.getRoofColor());
            first = false;
        }
        String design = oi.isCustomConfigured() ? oi.getDesign() : oi.getModel();
        if (StringUtils.hasText(design)) {
            if (!first) d.append(" | ");
            d.append("Design: ").append(design);
            first = false;
        }
        if (StringUtils.hasText(oi.getRoofOverstep())) {
            if (!first) d.append(" | ");
            d.append("Přesah: ").append(oi.getRoofOverstep());
            first = false;
        }
        if (Boolean.TRUE.equals(oi.getHasDivider())) {
            if (!first) d.append(" | ");
            d.append("Příčka");
            first = false;
        }
        if (Boolean.TRUE.equals(oi.getHasGutter())) {
            if (!first) d.append(" | ");
            d.append("Okap");
            first = false;
        }
        if (Boolean.TRUE.equals(oi.getHasGardenShed())) {
            if (!first) d.append(" | ");
            d.append("Zahr. domek");
            first = false;
        }
        if (!CollectionUtils.isEmpty(oi.getSelectedAddons())) {
            String addons = oi.getSelectedAddons().stream().map(a -> a.getAddonName() + (a.getQuantity() > 1 ? " (" + a.getQuantity() + "ks)" : "")).collect(Collectors.joining(", "));
            if (!first) d.append("\n");
            d.append("Doplňky: ").append(addons);
        }
        return d.toString().trim();
    }

    private boolean addressesMatchInOrder(Order order) {
        return Objects.equals(order.getInvoiceStreet(), order.getDeliveryStreet()) && Objects.equals(order.getInvoiceCity(), order.getDeliveryCity()) && Objects.equals(order.getInvoiceZipCode(), order.getDeliveryZipCode()) && Objects.equals(order.getInvoiceCountry(), order.getDeliveryCountry()) && Objects.equals(order.getInvoiceCompanyName(), order.getDeliveryCompanyName()) && Objects.equals(order.getInvoiceFirstName(), order.getDeliveryFirstName()) && Objects.equals(order.getInvoiceLastName(), order.getDeliveryLastName());
    }

    private String mapPaymentMethod(String localPaymentMethod) {
        if (localPaymentMethod == null) return "transfer";
        return switch (localPaymentMethod.toUpperCase()) {
            case "CASH_ON_DELIVERY" -> "cod";
            case "BANK_TRANSFER" -> "transfer";
            default -> "transfer";
        };
    }

    private boolean isValidForInvoice(Order order) {
        if (order == null) {
            log.error("Invoice check failed: Order is null.");
            return false;
        }
        if (order.getCustomer() == null) {
            log.error("Invoice check failed for order {}: Customer missing.", order.getOrderCode());
            return false;
        }
        if (CollectionUtils.isEmpty(order.getOrderItems())) {
            log.error("Invoice check failed for order {}: No items.", order.getOrderCode());
            return false;
        }
        if (!StringUtils.hasText(order.getInvoiceStreet()) || !StringUtils.hasText(order.getInvoiceCity()) || !StringUtils.hasText(order.getInvoiceZipCode()) || !StringUtils.hasText(order.getInvoiceCountry())) {
            log.error("Invoice check failed for order {}: Invoice address incomplete.", order.getOrderCode());
            return false;
        }
        boolean isCompany = StringUtils.hasText(order.getInvoiceCompanyName());
        if (!isCompany && (!StringUtils.hasText(order.getInvoiceFirstName()) || !StringUtils.hasText(order.getInvoiceLastName()))) {
            log.error("Invoice check failed for order {}: Invoice recipient name/company missing.", order.getOrderCode());
            return false;
        }
        return true;
    }
    // Metoda buildStandardInvoiceItemsWithRounding - OPRAVENO getShippingMethodName
    private java.util.List<java.util.Map<String, Object>> buildStandardInvoiceItemsWithRounding(Order order) {
        java.util.List<java.util.Map<String, Object>> items = new java.util.ArrayList<>();
        if (order.getOrderItems() != null) {
            for (OrderItem oi : order.getOrderItems()) {
                if (oi == null) {
                    log.warn("Null OrderItem found in order {}, skipping.", order.getOrderCode());
                    continue;
                }
                java.util.Map<String, Object> item = new java.util.HashMap<>();

                // Název a popis položky
                String itemName = org.springframework.util.StringUtils.hasText(oi.getProductName())
                        ? oi.getProductName()
                        : "Položka obj. " + order.getOrderCode();
                if (org.springframework.util.StringUtils.hasText(oi.getVariantInfo())) {
                    itemName += " (" + oi.getVariantInfo().replace("|", ", ") + ")";
                }
                item.put("name", itemName);
                item.put("description", buildItemDescription(oi));

                // Množství a jednotka
                item.put("quantity", oi.getCount());
                item.put("unit", "ks");

                // Jednotková cena bez DPH
                item.put("unit_price", java.util.Optional.ofNullable(oi.getUnitPriceWithoutTax()).orElse(java.math.BigDecimal.ZERO));

                // DPH a RC (logika zůstává)
                java.math.BigDecimal taxRateValue = java.util.Optional.ofNullable(oi.getTaxRate()).orElse(java.math.BigDecimal.ZERO);
                java.math.BigDecimal taxPct = taxRateValue.multiply(new java.math.BigDecimal("100")).setScale(2, java.math.RoundingMode.HALF_UP);
                item.put("tax", taxPct);
                if (oi.isReverseCharge()) {
                    item.put("transfer_tax_liability", 1);
                    log.debug("Setting transfer_tax_liability=1 for item '{}' (Order: {}, OrderItem ID: {}) due to Reverse Charge.",
                            oi.getProductName(), order.getOrderCode(), oi.getId());
                }

                item.put("sku", oi.getSku());
                item.put("currency", order.getCurrency());
                items.add(item);
            }
        } else {
            log.warn("Order {} has no order items to build invoice items.", order.getOrderCode());
        }

        // Přidání položky pro dopravu - OPRAVENO: Odstraněn název dopravy
        if (order.getShippingCostWithoutTax() != null && order.getShippingCostWithoutTax().compareTo(java.math.BigDecimal.ZERO) > 0) {
            java.util.Map<String, Object> shippingItem = new java.util.HashMap<>();
            // shippingItem.put("name", "Doprava" + (org.springframework.util.StringUtils.hasText(order.getShippingMethodName()) ? " (" + order.getShippingMethodName() + ")" : "")); // <-- PŮVODNÍ CHYBNÝ ŘÁDEK
            shippingItem.put("name", "Doprava"); // <-- OPRAVENO: Pouze "Doprava"
            shippingItem.put("quantity", 1);
            shippingItem.put("unit", "ks");
            shippingItem.put("unit_price", order.getShippingCostWithoutTax());
            java.math.BigDecimal shippingTaxRateValue = java.util.Optional.ofNullable(order.getShippingTaxRate()).orElse(java.math.BigDecimal.ZERO);
            java.math.BigDecimal shippingTaxPct = shippingTaxRateValue.multiply(new java.math.BigDecimal("100")).setScale(2, java.math.RoundingMode.HALF_UP);
            shippingItem.put("tax", shippingTaxPct);
            shippingItem.put("currency", order.getCurrency());
            items.add(shippingItem);
        }

        // Přidání položky pro zaokrouhlení (logika zůstává)
        if (order.getOriginalTotalPrice() != null && order.getTotalPrice() != null && order.getOriginalTotalPrice().compareTo(order.getTotalPrice()) != 0) {
            java.math.BigDecimal rounding = order.getTotalPrice().subtract(order.getOriginalTotalPrice()).setScale(PRICE_SCALE, ROUNDING_MODE);
            if (rounding.compareTo(java.math.BigDecimal.ZERO) != 0) {
                java.util.Map<String, Object> roundingItem = new java.util.HashMap<>();
                roundingItem.put("name", "Zaokrouhlení");
                roundingItem.put("quantity", 1);
                roundingItem.put("unit", "ks");
                roundingItem.put("unit_price", rounding);
                roundingItem.put("tax", java.math.BigDecimal.ZERO);
                roundingItem.put("currency", order.getCurrency());
                items.add(roundingItem);
            }
        }
        return items;
    }
    private java.util.Map<String, Object> buildDepositInvoiceItem(Order order, String itemName, java.math.BigDecimal amountWithTax) {
        java.util.Map<String, Object> depositItem = new java.util.HashMap<>();

        depositItem.put("name", itemName); // Např. "Záloha 50% na objednávku XYZ"
        depositItem.put("quantity", 1);
        depositItem.put("unit", "ks"); // Nebo "záloha"

        // Vypočteme průměrnou sazbu DPH z PŮVODNÍCH položek objednávky
        java.math.BigDecimal avgRate = calculateAverageTaxRate(order.getOrderItems()); // Metoda nyní použije sazby z OrderItem
        // Zjistíme, zda jakákoli původní položka měla RC
        boolean isAnyReverseChargeInOriginalItems = order.getOrderItems() != null &&
                order.getOrderItems().stream().anyMatch(OrderItem::isReverseCharge);

        // Vypočteme cenu bez DPH na základě průměrné sazby
        java.math.BigDecimal amountNoTax = java.math.BigDecimal.ZERO;
        java.math.BigDecimal divisor = java.math.BigDecimal.ONE.add(avgRate);
        if (divisor.compareTo(java.math.BigDecimal.ZERO) != 0) {
            amountNoTax = java.util.Optional.ofNullable(amountWithTax).orElse(java.math.BigDecimal.ZERO)
                    .divide(divisor, PRICE_SCALE, ROUNDING_MODE);
        } else {
            // Může nastat, pokud by průměrná sazba byla -100% (nemožné u DPH)
            log.warn("Cannot calculate amount without tax for deposit item in order {}: average tax rate is -100%. Using amountWithTax as amountNoTax.", order.getOrderCode());
            amountNoTax = java.util.Optional.ofNullable(amountWithTax).orElse(java.math.BigDecimal.ZERO);
        }

        java.math.BigDecimal taxPct = avgRate.multiply(new java.math.BigDecimal("100")).setScale(2, java.math.RoundingMode.HALF_UP);
        depositItem.put("unit_price", amountNoTax);
        depositItem.put("tax", taxPct); // Použijeme průměrnou sazbu
        depositItem.put("currency", order.getCurrency());

        // *** Nastavení RC pro zálohovou položku, pokud jakákoli původní položka měla RC ***
        if (isAnyReverseChargeInOriginalItems) {
            depositItem.put("transfer_tax_liability", 1);
            log.debug("Setting transfer_tax_liability=1 for deposit item (Order: {}) as at least one original item had RC.", order.getOrderCode());
        }

        // Přidáme popis zálohy
        String description = "Záloha na objednávku " + order.getOrderCode();
        if (itemName.startsWith("Záloha") && order.getTotalPrice() != null) {
            description += " (" + amountWithTax.setScale(PRICE_SCALE, ROUNDING_MODE) + " " + order.getCurrency() + " z celkové částky " + order.getTotalPrice().setScale(PRICE_SCALE, ROUNDING_MODE) + " " + order.getCurrency() + ")";
        }
        depositItem.put("description", description);

        return depositItem;
    }
    private Map<String, Object> createFinalPayload(Map<String, Object> invoiceData, Map<String, Object> clientData, List<Map<String, Object>> items) {
        Map<String, Object> finalPayload = new HashMap<>();
        finalPayload.put("Invoice", invoiceData);
        finalPayload.put("Client", clientData);
        // Položky faktury jsou vnořeny pod Invoice klíčem dle SF dokumentace v2
        if (items != null && !items.isEmpty()) {
            invoiceData.put("InvoiceItem", items); // Přidáme položky pod "Invoice"
        } else {
            // API může vyžadovat prázdný seznam, pokud nejsou žádné položky (což by nemělo nastat)
            invoiceData.put("InvoiceItem", new ArrayList<>());
            log.warn("Creating invoice payload with empty items list!");
        }
        return finalPayload;
    }

}