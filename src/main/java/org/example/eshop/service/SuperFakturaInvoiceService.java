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
    // Endpoint pro označení platby (přijímá ID faktury v URL)
    private static final String INVOICES_ENDPOINT_PAY_PATTERN = "/invoice_payments/add/invoice_id:%d.json";
    private static final String INVOICES_ENDPOINT_PDF_WITH_TOKEN_PATTERN = "/invoices/pdf/%d/token:%s";

    // Ostatní konstanty
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int PROFORMA_DUE_DAYS = 7;
    private static final int FINAL_DUE_DAYS = 14;
    private static final Long TAX_DOCUMENT_SEQUENCE_ID = 342836L; // ID číselné řady pro DDKP (ověřit!)
    private static final String PAYMENT_STATUS_PAID = "PAID"; // Přidáno pro interní použití

    // --- Implementace metod InvoiceService ---

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateProformaInvoice(Order order) {
        Order freshOrder = reloadOrder(order.getId());
        log.info("Attempting to generate SuperFaktura PROFORMA invoice for order: {}. Order currency: {}", freshOrder.getOrderCode(), freshOrder.getCurrency());
        if (!isValidForInvoice(freshOrder))
            throw new IllegalStateException("Order " + freshOrder.getOrderCode() + " is not valid for proforma generation.");
        if (freshOrder.getDepositAmount() == null || freshOrder.getDepositAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Proforma requested for order {} without deposit. Skipping.", freshOrder.getOrderCode());
            return;
        }
        if (freshOrder.getSfProformaInvoiceId() != null) {
            log.warn("Proforma already generated for order {}. SF ID: {}", freshOrder.getOrderCode(), freshOrder.getSfProformaInvoiceId());
            return;
        }

        Map<String, Object> payload = buildProformaPayload(freshOrder);
        try {
            JsonNode responseData = callSuperfakturaApi(INVOICES_ENDPOINT_CREATE, HttpMethod.POST, payload, freshOrder.getOrderCode(), "Proforma Invoice");
            if (responseData != null && responseData.has("Invoice") && responseData.get("Invoice").has("id")) {
                Long sfInvoiceId = responseData.get("Invoice").get("id").asLong();
                String sfInvoiceNumber = getInvoiceNumberFromResponse(responseData);
                String pdfUrl = getPdfDownloadUrlFromResponse(responseData, sfInvoiceId);
                log.info("Parsed Proforma Invoice response for order {}: ID={}, Number={}, PDF URL={}", freshOrder.getOrderCode(), sfInvoiceId, sfInvoiceNumber, pdfUrl);
                freshOrder.setSfProformaInvoiceId(sfInvoiceId);
                freshOrder.setProformaInvoiceNumber(sfInvoiceNumber);
                freshOrder.setSfProformaPdfUrl(pdfUrl);
                orderRepository.save(freshOrder);
                log.info("Order {} updated with Proforma Invoice details.", freshOrder.getOrderCode());
            } else {
                throw new RuntimeException("Failed to parse Proforma Invoice ID from SF response for order " + freshOrder.getOrderCode());
            }
        } catch (RuntimeException e) {
            log.error("Failed to generate Proforma Invoice for order {}: {}", freshOrder.getOrderCode(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateTaxDocumentForDeposit(Order order) {
        Order freshOrder = reloadOrder(order.getId());
        log.info("Attempting to generate SuperFaktura TAX DOCUMENT for paid deposit for order: {}. Order currency: {}", freshOrder.getOrderCode(), freshOrder.getCurrency());
        if (!isValidForInvoice(freshOrder))
            throw new IllegalStateException("Order " + freshOrder.getOrderCode() + " is not valid for Tax Document generation.");
        if (freshOrder.getDepositAmount() == null || freshOrder.getDepositAmount().compareTo(BigDecimal.ZERO) <= 0 || freshOrder.getDepositPaidDate() == null) {
            log.warn("Tax Document requested for order {} but deposit/paid date missing. Skipping.", freshOrder.getOrderCode());
            return;
        }
        if (freshOrder.getSfTaxDocumentId() != null) {
            log.warn("Tax Document already generated for order {}. SF ID: {}", freshOrder.getOrderCode(), freshOrder.getSfTaxDocumentId());
            return;
        }

        Map<String, Object> payload = buildTaxDocumentPayload(freshOrder);
        try {
            JsonNode responseData = callSuperfakturaApi(INVOICES_ENDPOINT_CREATE, HttpMethod.POST, payload, freshOrder.getOrderCode(), "Tax Document (Deposit)");
            if (responseData != null && responseData.has("Invoice") && responseData.get("Invoice").has("id")) {
                Long sfInvoiceId = responseData.get("Invoice").get("id").asLong();
                String sfInvoiceNumber = getInvoiceNumberFromResponse(responseData);
                String pdfUrl = getPdfDownloadUrlFromResponse(responseData, sfInvoiceId);
                log.info("Parsed Tax Document response for order {}: ID={}, Number={}, PDF URL={}", freshOrder.getOrderCode(), sfInvoiceId, sfInvoiceNumber, pdfUrl);
                freshOrder.setSfTaxDocumentId(sfInvoiceId);
                freshOrder.setTaxDocumentNumber(sfInvoiceNumber);
                freshOrder.setSfTaxDocumentPdfUrl(pdfUrl);
                orderRepository.save(freshOrder);
                log.info("Order {} updated with Tax Document details.", freshOrder.getOrderCode());
            } else {
                throw new RuntimeException("Failed to parse Tax Document ID from SF response for order " + freshOrder.getOrderCode());
            }
        } catch (RuntimeException e) {
            log.error("Failed to generate Tax Document for order {}: {}", freshOrder.getOrderCode(), e.getMessage());
            throw e;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateFinalInvoice(Order order) {
        Order freshOrder = reloadOrder(order.getId());
        log.info("Attempting to generate SuperFaktura FINAL invoice for order: {}. Order currency: {}", freshOrder.getOrderCode(), freshOrder.getCurrency());
        if (!isValidForInvoice(freshOrder))
            throw new IllegalStateException("Order " + freshOrder.getOrderCode() + " is not valid for final invoice generation.");
        if (freshOrder.isFinalInvoiceGenerated() || freshOrder.getSfFinalInvoiceId() != null) {
            log.warn("Final invoice already generated for order {}. SF ID: {}", freshOrder.getOrderCode(), freshOrder.getSfFinalInvoiceId());
            return;
        }
        if (freshOrder.getDepositAmount() != null && freshOrder.getDepositAmount().compareTo(BigDecimal.ZERO) > 0 && freshOrder.getDepositPaidDate() == null) {
            log.error("Cannot generate final invoice for order {} because deposit is not paid.", freshOrder.getOrderCode());
            throw new IllegalStateException("Deposit must be paid before generating final invoice for order " + freshOrder.getOrderCode());
        }

        Map<String, Object> payload = buildFinalInvoicePayload(freshOrder);
        try {
            JsonNode responseData = callSuperfakturaApi(INVOICES_ENDPOINT_CREATE, HttpMethod.POST, payload, freshOrder.getOrderCode(), "Final Invoice");
            if (responseData != null && responseData.has("Invoice") && responseData.get("Invoice").has("id")) {
                Long sfInvoiceId = responseData.get("Invoice").get("id").asLong();
                String sfInvoiceNumber = getInvoiceNumberFromResponse(responseData);
                String pdfUrl = getPdfDownloadUrlFromResponse(responseData, sfInvoiceId);
                log.info("Parsed Final Invoice response for order {}: ID={}, Number={}, PDF URL={}", freshOrder.getOrderCode(), sfInvoiceId, sfInvoiceNumber, pdfUrl);
                freshOrder.setSfFinalInvoiceId(sfInvoiceId);
                freshOrder.setFinalInvoiceNumber(sfInvoiceNumber);
                freshOrder.setSfFinalInvoicePdfUrl(pdfUrl);
                freshOrder.setFinalInvoiceGenerated(true);
                orderRepository.save(freshOrder);
                log.info("Order {} updated with Final Invoice details.", freshOrder.getOrderCode());
            } else {
                throw new RuntimeException("Failed to parse Final Invoice ID from SF response for order " + freshOrder.getOrderCode());
            }
        } catch (RuntimeException e) {
            log.error("Failed to generate Final Invoice for order {}: {}", freshOrder.getOrderCode(), e.getMessage());
            throw e;
        }
    }

    // --- Další Veřejné Metody ---

    public void sendInvoiceByEmail(Long sfInvoiceId, String customerEmail, String invoiceType, String orderCode) {
        if (sfInvoiceId == null || sfInvoiceId <= 0 || !StringUtils.hasText(customerEmail)) {
            throw new IllegalArgumentException("Invalid invoice ID or customer email for sending.");
        }
        String endpoint = INVOICES_ENDPOINT_SEND_EMAIL_ACTION;
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> emailData = new HashMap<>();
        emailData.put("invoice_id", sfInvoiceId);
        emailData.put("to", customerEmail);
        payload.put("Email", emailData);
        try {
            log.trace("Sending email payload to {}: {}", endpoint, objectMapper.writeValueAsString(payload));
            callSuperfakturaApi(endpoint, HttpMethod.POST, payload, orderCode, "Send Email " + invoiceType);
            log.info("Successfully requested sending of {} (SF ID: {}) for order {} to {}", invoiceType, sfInvoiceId, orderCode, customerEmail);
        } catch (JsonProcessingException e) {
            log.error("Error serializing email payload for SF ID {}: {}", sfInvoiceId, e.getMessage());
            throw new RuntimeException("Internal error creating email request payload.", e);
        } catch (RuntimeException e) {
            throw new RuntimeException("Error sending " + invoiceType + " via SF API for order " + orderCode + ": " + e.getMessage(), e);
        }
    }

    public void markInvoiceAsSent(Long sfInvoiceId, String customerEmail, String subject) {
        if (sfInvoiceId == null || sfInvoiceId <= 0) {
            throw new IllegalArgumentException("Invalid invoice ID for marking as sent.");
        }
        String endpoint = INVOICES_ENDPOINT_MARK_AS_SENT;
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> emailData = new HashMap<>();
        emailData.put("invoice_id", sfInvoiceId);
        emailData.put("email", StringUtils.hasText(customerEmail) ? customerEmail : "neznámý@email.cz");
        emailData.put("subject", StringUtils.hasText(subject) ? subject : "Faktura odeslána");
        payload.put("InvoiceEmail", emailData);
        try {
            log.trace("Marking as sent payload to {}: {}", endpoint, objectMapper.writeValueAsString(payload));
            callSuperfakturaApi(endpoint, HttpMethod.POST, payload, "N/A", "Mark As Sent");
            log.info("Successfully marked invoice {} as sent to {}", sfInvoiceId, customerEmail);
        } catch (JsonProcessingException e) {
            log.error("Error serializing mark as sent payload for SF ID {}: {}", sfInvoiceId, e.getMessage());
            throw new RuntimeException("Internal error creating mark as sent request payload.", e);
        } catch (RuntimeException e) {
            log.error("Failed to mark invoice {} as sent: {}", sfInvoiceId, e.getMessage(), e);
            throw new RuntimeException("Error marking invoice " + sfInvoiceId + " as sent via SF API: " + e.getMessage(), e);
        }
    }

    /**
     * Označí fakturu v SuperFaktuře jako zaplacenou. Volá endpoint /invoice_payments/add/invoice_id:{id}.json.
     *
     * @param sfInvoiceId   ID faktury v SuperFaktuře.
     * @param amount        Zaplacená částka.
     * @param paymentDate   Datum platby.
     * @param sfPaymentType Kód typu platby dle SuperFaktury ("transfer", "cash", "card", ...).
     * @param orderCode     Kód objednávky (pro logování).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markInvoiceAsPaidInSF(Long sfInvoiceId, BigDecimal amount, LocalDate paymentDate, String sfPaymentType, String orderCode) {
        if (sfInvoiceId == null || sfInvoiceId <= 0 || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || paymentDate == null) {
            log.error("Invalid parameters for marking invoice as paid in SF. SF Invoice ID: {}, Amount: {}, Date: {}", sfInvoiceId, amount, paymentDate);
            throw new IllegalArgumentException("Invalid parameters for marking invoice as paid.");
        }

        String endpoint = String.format(INVOICES_ENDPOINT_PAY_PATTERN, sfInvoiceId);
        String requestType = "Mark Invoice Paid";
        log.info("Attempting to mark invoice SF ID {} as paid for order {} via API. Amount: {}, Date: {}, Type: {}", sfInvoiceId, orderCode, amount, paymentDate, sfPaymentType);

        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("invoice_id", sfInvoiceId);
        paymentData.put("payment_type", sfPaymentType != null ? sfPaymentType : "transfer");
        paymentData.put("amount", amount.setScale(PRICE_SCALE, ROUNDING_MODE));
        paymentData.put("currency", findInvoiceCurrency(sfInvoiceId, orderCode)); // Zjistíme měnu
        paymentData.put("date", paymentDate.format(DATE_FORMATTER));

        payload.put("InvoicePayment", paymentData);

        try {
            // Nepotřebujeme zpracovávat odpověď, pokud API vrací jen potvrzení
            callSuperfakturaApi(endpoint, HttpMethod.POST, payload, orderCode, requestType);
            log.info("Successfully requested marking invoice SF ID {} as paid for order {}", sfInvoiceId, orderCode);
        } catch (RuntimeException e) {
            // Logujeme, ale nehážeme dál, aby neovlivnila commit v našem systému
            log.error("Failed to mark invoice SF ID {} as paid for order {}: {}", sfInvoiceId, orderCode, e.getMessage(), e);
            // Můžeme zde zvážit přidání interní poznámky k objednávce
            // orderService.addInternalNote(orderId, "Nepodařilo se označit platbu v SuperFaktuře: " + e.getMessage());
        }
    }

    // --- Pomocné metody ---

    // Znovu načte objednávku a inicializuje potřebné kolekce
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

    // --- Build Payload metody ---
    private Map<String, Object> buildProformaPayload(Order order) {
        Map<String, Object> invoiceData = buildBaseInvoiceData(order);
        invoiceData.put("type", "proforma");
        invoiceData.put("due_date", LocalDate.now().plusDays(PROFORMA_DUE_DAYS).format(DATE_FORMATTER));
        invoiceData.put("note", "Záloha za objednané zboží dle obj. č. " + order.getOrderCode() + (StringUtils.hasText(order.getNote()) ? "\nPoznámka: " + order.getNote() : ""));
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> depositItem = buildDepositInvoiceItem(order, "Záloha na dřevník dle obj. č. " + order.getOrderCode(), order.getDepositAmount());
        items.add(depositItem);
        return buildFullPayload(invoiceData, mapCustomerToClientData(order), items);
    }

    private Map<String, Object> buildTaxDocumentPayload(Order order) {
        Map<String, Object> invoiceData = buildBaseInvoiceData(order);
        LocalDate paymentDate = order.getDepositPaidDate().toLocalDate();
        invoiceData.put("type", "regular");
        if (TAX_DOCUMENT_SEQUENCE_ID != null) invoiceData.put("sequence_id", TAX_DOCUMENT_SEQUENCE_ID);
        invoiceData.put("date", paymentDate.format(DATE_FORMATTER));
        invoiceData.put("delivery_date", paymentDate.format(DATE_FORMATTER));
        invoiceData.put("due_date", paymentDate.format(DATE_FORMATTER));
        String note = "Daňový doklad k záloze zaplacené dne " + paymentDate.format(DateTimeFormatter.ofPattern("d.M.yyyy")) + " k obj. č. " + order.getOrderCode() + ".";
        if (order.getProformaInvoiceNumber() != null)
            note += "\nVztahuje se k zálohové faktuře č. " + order.getProformaInvoiceNumber() + ".";
        invoiceData.put("note", note);
        invoiceData.put("already_paid", order.getDepositAmount());
        invoiceData.put("paid_date", paymentDate.format(DATE_FORMATTER));
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> depositItem = buildDepositInvoiceItem(order, "Přijatá záloha k obj. č. " + order.getOrderCode(), order.getDepositAmount());
        items.add(depositItem);
        return buildFullPayload(invoiceData, mapCustomerToClientData(order), items);
    }

    private Map<String, Object> buildFinalInvoicePayload(Order order) {
        Map<String, Object> invoiceData = buildBaseInvoiceData(order);
        LocalDate today = LocalDate.now();
        LocalDate deliveryDate = order.getShippedDate() != null ? order.getShippedDate().toLocalDate() : today;
        invoiceData.put("type", "regular");
        invoiceData.put("delivery_date", deliveryDate.format(DATE_FORMATTER));
        invoiceData.put("due_date", today.plusDays(FINAL_DUE_DAYS).format(DATE_FORMATTER));
        String note = StringUtils.hasText(order.getNote()) ? order.getNote() : "";
        if (order.getSfProformaInvoiceId() != null && order.getDepositPaidDate() != null) {
            invoiceData.put("proforma_id", order.getSfProformaInvoiceId());
            note = "Odpočet zálohy dle zálohové faktury č. " + order.getProformaInvoiceNumber() + ".\n" + note;
            log.info("Linking final invoice for order {} to Proforma ID: {}", order.getOrderCode(), order.getSfProformaInvoiceId());
        } else if (order.getSfTaxDocumentId() != null) {
            note = "Vztahuje se k DDKP č. " + order.getTaxDocumentNumber() + ".\n" + note;
            log.warn("Linking final invoice using Proforma ID (if available), but Tax Document ID {} also exists for order {}.", order.getSfTaxDocumentId(), order.getOrderCode());
        }
        invoiceData.put("note", note.trim());
        List<Map<String, Object>> items = buildStandardInvoiceItems(order);
        if (order.getCouponDiscountAmount() != null && order.getCouponDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            invoiceData.put("discount_amount", order.getCouponDiscountAmount());
            String discountNote = " Aplikována sleva z kupónu: " + (StringUtils.hasText(order.getAppliedCouponCode()) ? order.getAppliedCouponCode() : "") + " (-" + order.getCouponDiscountAmount().setScale(PRICE_SCALE, ROUNDING_MODE) + " " + order.getCurrency() + " bez DPH).";
            invoiceData.put("note", (invoiceData.get("note") != null ? invoiceData.get("note") : "") + discountNote);
        }
        if ((order.getDepositAmount() == null || order.getDepositAmount().compareTo(BigDecimal.ZERO) <= 0) && PAYMENT_STATUS_PAID.equals(order.getPaymentStatus()) && order.getPaymentDate() != null) {
            invoiceData.put("already_paid", order.getTotalPrice());
            invoiceData.put("paid_date", order.getPaymentDate().toLocalDate().format(DATE_FORMATTER));
            log.info("Setting 'already_paid' to total amount and 'paid_date' for final invoice {} (no deposit scenario)", order.getOrderCode());
        } else {
            log.info("Not setting 'already_paid' for final invoice {}. Relying on SF deduction via linked proforma_id.", order.getOrderCode());
        }
        return buildFullPayload(invoiceData, mapCustomerToClientData(order), items);
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

    private List<Map<String, Object>> buildStandardInvoiceItems(Order order) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (OrderItem oi : order.getOrderItems()) {
            Map<String, Object> item = new HashMap<>();
            String n = StringUtils.hasText(oi.getProductName()) ? oi.getProductName() : "Položka " + (oi.getProduct() != null ? oi.getProduct().getId() : "N/A");
            if (StringUtils.hasText(oi.getVariantInfo()) && !oi.getVariantInfo().equalsIgnoreCase("Produkt na míru"))
                n += " (" + oi.getVariantInfo() + ")";
            item.put("name", n);
            item.put("description", buildItemDescription(oi));
            item.put("quantity", oi.getCount());
            item.put("unit", "ks");
            item.put("unit_price", Optional.ofNullable(oi.getUnitPriceWithoutTax()).orElse(BigDecimal.ZERO));
            BigDecimal t = Optional.ofNullable(oi.getTaxRate()).orElse(BigDecimal.ZERO).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
            item.put("tax", t);
            item.put("sku", oi.getSku());
            item.put("currency", order.getCurrency());
            if (oi.isReverseCharge()) item.put("transfer_tax_liability", 1);
            items.add(item);
        }
        if (order.getShippingCostWithoutTax() != null && order.getShippingCostWithoutTax().compareTo(BigDecimal.ZERO) > 0) {
            Map<String, Object> si = new HashMap<>();
            si.put("name", "Doprava");
            si.put("quantity", 1);
            si.put("unit", "ks");
            si.put("unit_price", order.getShippingCostWithoutTax());
            BigDecimal st = Optional.ofNullable(order.getShippingTaxRate()).orElse(BigDecimal.ZERO).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
            si.put("tax", st);
            si.put("currency", order.getCurrency());
            items.add(si);
        }
        return items;
    }

    private Map<String, Object> buildDepositInvoiceItem(Order order, String itemName, BigDecimal amountWithTax) {
        Map<String, Object> i = new HashMap<>();
        i.put("name", itemName);
        i.put("quantity", 1);
        i.put("unit", "ks");
        BigDecimal rate = calculateAverageTaxRate(order.getOrderItems());
        BigDecimal amountNoTax = BigDecimal.ZERO;
        if (BigDecimal.ONE.add(rate).compareTo(BigDecimal.ZERO) != 0)
            amountNoTax = Optional.ofNullable(amountWithTax).orElse(BigDecimal.ZERO).divide(BigDecimal.ONE.add(rate), PRICE_SCALE, ROUNDING_MODE);
        else amountNoTax = Optional.ofNullable(amountWithTax).orElse(BigDecimal.ZERO);
        BigDecimal taxPct = rate.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        i.put("unit_price", amountNoTax);
        i.put("tax", taxPct);
        i.put("currency", order.getCurrency());
        if (itemName.startsWith("Záloha"))
            i.put("description", "Záloha 50% z " + Optional.ofNullable(order.getTotalPrice()).orElse(BigDecimal.ZERO).setScale(PRICE_SCALE, ROUNDING_MODE) + " " + order.getCurrency());
        return i;
    }

    private Map<String, Object> buildFullPayload(Map<String, Object> invoiceData, Map<String, Object> clientData, List<Map<String, Object>> items) {
        Map<String, Object> p = new HashMap<>();
        p.put("Invoice", invoiceData);
        p.put("Client", clientData);
        p.put("InvoiceItem", items);
        return p;
    }

    // Metoda mapCustomerToClientData s přidanou měnou
    private Map<String, Object> mapCustomerToClientData(Order order) {
        Customer customer = order.getCustomer();
        Map<String, Object> data = new HashMap<>();

        // --- ZMĚNA ZDE: Nastavení jména klienta pro SF ---
        // Pokud má objednávka název firmy na faktuře, použijeme ten.
        // Jinak použijeme jméno a příjmení z fakturačních údajů objednávky.
        if (StringUtils.hasText(order.getInvoiceCompanyName())) {
            data.put("name", order.getInvoiceCompanyName());
            // Volitelně: Pokud SF umožňuje kontaktní osobu u firmy, můžeme přidat:
            // data.put("contact_person", customer.getFirstName() + " " + customer.getLastName());
        } else if (StringUtils.hasText(order.getInvoiceFirstName()) || StringUtils.hasText(order.getInvoiceLastName())) {
            data.put("name", (StringUtils.trimWhitespace(order.getInvoiceFirstName()) + " " + StringUtils.trimWhitespace(order.getInvoiceLastName())).trim());
        } else {
            // Fallback, pokud by chybělo jméno i firma (nemělo by nastat)
            log.warn("Invoice company name and person name missing in order {}, using customer contact name as fallback for SF client name.", order.getOrderCode());
            data.put("name", customer.getFirstName() + " " + customer.getLastName());
        }
        // --- KONEC ZMĚNY ---

        data.put("email", customer.getEmail());
        if (StringUtils.hasText(customer.getPhone())) data.put("phone", customer.getPhone());
        data.put("address", order.getInvoiceStreet());
        data.put("city", order.getInvoiceCity());
        data.put("zip", order.getInvoiceZipCode());
        data.put("country", order.getInvoiceCountry());
        data.put("currency", order.getCurrency()); // Měna se přidává

        if (StringUtils.hasText(order.getInvoiceTaxId())) data.put("ico", order.getInvoiceTaxId());
        if (StringUtils.hasText(order.getInvoiceVatId())) {
            String v = order.getInvoiceVatId().toUpperCase().replaceAll("\\s+", "");
            if (v.startsWith("CZ")) data.put("dic", v);
            else if (v.startsWith("SK")) data.put("ic_dph", v);
            else {
                data.put("ic_dph", v);
                log.warn("Mapping non-SK/CZ VAT ID '{}' to 'ic_dph'.", v);
            }
        }

        // Dodací adresa (logika zůstává stejná, používá data z Order entity)
        if (!addressesMatchInOrder(order)) {
            String dn = StringUtils.hasText(order.getDeliveryCompanyName()) ? order.getDeliveryCompanyName() :
                    ((order.getDeliveryFirstName() != null ? order.getDeliveryFirstName() : "") + " " +
                            (order.getDeliveryLastName() != null ? order.getDeliveryLastName() : "")).trim();
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

    // Ostatní pomocné metody (beze změny)
    private BigDecimal calculateAverageTaxRate(List<OrderItem> items) {
        if (CollectionUtils.isEmpty(items)) return BigDecimal.ZERO;
        BigDecimal totalValueWithoutTax = BigDecimal.ZERO;
        BigDecimal totalTaxAmount = BigDecimal.ZERO;
        for (OrderItem item : items) {
            totalValueWithoutTax = totalValueWithoutTax.add(Optional.ofNullable(item.getTotalPriceWithoutTax()).orElse(BigDecimal.ZERO));
            totalTaxAmount = totalTaxAmount.add(Optional.ofNullable(item.getTotalTaxAmount()).orElse(BigDecimal.ZERO));
        }
        if (totalValueWithoutTax.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return totalTaxAmount.divide(totalValueWithoutTax, 4, RoundingMode.HALF_UP);
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
}