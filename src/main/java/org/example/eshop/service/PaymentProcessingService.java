package org.example.eshop.service; // Přejmenováno z service na Service pro konzistenci

import com.fasterxml.jackson.databind.JsonNode;
import org.example.eshop.model.Order;
import org.example.eshop.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
public class PaymentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingService.class);
    private static final String PAYMENT_STATUS_PAID = "PAID";
    private static final String PAYMENT_STATUS_DEPOSIT_PAID = "DEPOSIT_PAID";
    // @Autowired private OrderService orderService; // Pro změnu stavu objednávky
    private static final String PAYMENT_STATUS_AWAITING_DEPOSIT = "AWAITING_DEPOSIT"; // Konstanta pro porovnání
    private static final DateTimeFormatter SF_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private InvoiceService invoiceService; // Pro generování DDKP

    /**
     * Zpracuje notifikaci (webhook) o platbě ze SuperFaktury.
     * Najde odpovídající objednávku, aktualizuje její stav platby a datum,
     * a případně spustí generování Daňového dokladu k přijaté platbě (DDKP).
     *
     * @param webhookData JSON data z webhooku jako JsonNode.
     */
    @Transactional // Celé zpracování by mělo být v transakci
    public void processPaymentNotification(JsonNode webhookData) {
        // Očekávaná struktura (ověřit dle SF dokumentace webhooků!):
        // webhookData -> data -> InvoicePayment | ProformaPayment -> variable_symbol (nebo order_no), amount, date, invoice_id | proforma_id
        // nebo webhookData -> data -> Invoice | Proforma -> variable_symbol (nebo order_no), id, paid_date, amount_paid

        JsonNode data = webhookData.path("data");
        String variableSymbol = null; // VS = náš Order Code
        BigDecimal paidAmount = null;
        LocalDate paymentDate = null;
        Long sfInvoiceId = null; // ID (zálohové) faktury v SF
        boolean isProformaPayment = false;

        // Zkusit najít data o platbě - struktura se může lišit dle typu události!
        if (data.has("InvoicePayment")) {
            JsonNode paymentNode = data.path("InvoicePayment");
            variableSymbol = paymentNode.path("variable_symbol").asText(null);
            paidAmount = parseBigDecimal(paymentNode.path("amount").asText(null));
            paymentDate = parseLocalDate(paymentNode.path("date").asText(null));
            sfInvoiceId = paymentNode.path("invoice_id").asLong(0);
        } else if (data.has("ProformaPayment")) {
            JsonNode paymentNode = data.path("ProformaPayment");
            variableSymbol = paymentNode.path("variable_symbol").asText(null);
            paidAmount = parseBigDecimal(paymentNode.path("amount").asText(null));
            paymentDate = parseLocalDate(paymentNode.path("date").asText(null));
            sfInvoiceId = paymentNode.path("proforma_id").asLong(0);
            isProformaPayment = true;
        } else if (data.has("Invoice") && data.path("Invoice").has("paid_date")) {
            // Alternativní struktura, pokud webhook posílá celou fakturu
            JsonNode invoiceNode = data.path("Invoice");
            variableSymbol = invoiceNode.path("variable").asText(null); // Nebo order_no? Ověřit!
            paidAmount = parseBigDecimal(invoiceNode.path("amount_paid").asText(null));
            paymentDate = parseLocalDate(invoiceNode.path("paid_date").asText(null));
            sfInvoiceId = invoiceNode.path("id").asLong(0);
            isProformaPayment = "proforma".equalsIgnoreCase(invoiceNode.path("type").asText(""));
        }
        // TODO: Přidat zpracování pro "Proforma", pokud webhook posílá Proformu s informací o platbě

        // *** Použití StringUtils zde ***
        if (!StringUtils.hasText(variableSymbol) || paidAmount == null || paymentDate == null || sfInvoiceId == null || sfInvoiceId <= 0) {
            log.error("Received incomplete payment notification webhook: VS={}, Amount={}, Date={}, SF_ID={}. Payload: {}",
                    variableSymbol, paidAmount, paymentDate, sfInvoiceId, webhookData);
            return; // Nelze zpracovat
        }

        // Najít objednávku podle variabilního symbolu (Order Code)
        Optional<Order> orderOpt = orderRepository.findByOrderCode(variableSymbol);
        if (!orderOpt.isPresent()) {
            log.warn("Received payment notification for unknown order code (VS): {}", variableSymbol);
            return; // Objednávka nenalezena
        }
        Order order = orderOpt.get();
        log.info("Processing payment notification for order {}", order.getOrderCode());

        // Aktualizace stavu platby a data
        boolean depositMatches = order.getDepositAmount() != null && paidAmount.compareTo(order.getDepositAmount()) == 0;
        // Porovnání celkové ceny - pozor na zaokrouhlovací rozdíly? Raději porovnat s tolerancí?
        boolean totalMatches = paidAmount.compareTo(order.getTotalPrice()) == 0;
        LocalDateTime paymentDateTime = paymentDate.atStartOfDay();

        if (isProformaPayment && depositMatches && order.getDepositPaidDate() == null) {
            // Platba zálohy (zaplacena proforma)
            log.info("Processing DEPOSIT payment (Proforma) for order {}", order.getOrderCode());
            order.setPaymentStatus(PAYMENT_STATUS_DEPOSIT_PAID);
            order.setDepositPaidDate(paymentDateTime);
            orderRepository.save(order);
            log.info("Order {} payment status updated to DEPOSIT_PAID, date set to {}", order.getOrderCode(), paymentDateTime);

            // Trigger pro generování DDKP
            try {
                if (order.getSfTaxDocumentId() == null) {
                    invoiceService.generateTaxDocumentForDeposit(order);
                } else {
                    log.warn("Tax Document already exists for order {} (SF ID: {}), skipping generation based on webhook.", order.getOrderCode(), order.getSfTaxDocumentId());
                }
            } catch (Exception e) {
                log.error("Failed to trigger Tax Document generation for order {} after webhook processing: {}", order.getOrderCode(), e.getMessage());
            }
            // TODO: Změnit stav objednávky?

        } else if (!isProformaPayment && totalMatches && order.getPaymentDate() == null) {
            // Plná platba (zaplacena ostrá faktura nebo doplatek)
            log.info("Processing FULL payment for order {}", order.getOrderCode());
            order.setPaymentStatus(PAYMENT_STATUS_PAID);
            order.setPaymentDate(paymentDateTime);
            if (order.getDepositPaidDate() == null)
                order.setDepositPaidDate(paymentDateTime); // Pokud nebyla záloha, nastavíme i datum zálohy? Nebo nechat null?
            orderRepository.save(order);
            log.info("Order {} payment status updated to PAID, date set to {}", order.getOrderCode(), paymentDateTime);
            // TODO: Změnit stav objednávky?

        } else if (!isProformaPayment && depositMatches && order.getDepositPaidDate() == null && PAYMENT_STATUS_AWAITING_DEPOSIT.equals(order.getPaymentStatus())) {
            // Platba zálohy přišla na základě ostré faktury k záloze (DDKP)
            log.info("Processing DEPOSIT payment (via Tax Document) for order {}", order.getOrderCode());
            order.setPaymentStatus(PAYMENT_STATUS_DEPOSIT_PAID);
            order.setDepositPaidDate(paymentDateTime);
            orderRepository.save(order);
            log.info("Order {} payment status updated to DEPOSIT_PAID, date set to {}", order.getOrderCode(), paymentDateTime);
            // DDKP už bylo vygenerováno, když jsme poslali tuto fakturu

        } else {
            log.warn("Received payment notification for order {} with amount {} {} that doesn't match deposit ({}) or total ({}), or payment date already set. Current status: {}, Deposit paid: {}, Full paid: {}",
                    order.getOrderCode(), paidAmount, order.getCurrency(), order.getDepositAmount(), order.getTotalPrice(),
                    order.getPaymentStatus(), order.getDepositPaidDate(), order.getPaymentDate());
        }
    }

    // --- Pomocné metody pro parsování ---
    private BigDecimal parseBigDecimal(String value) {
        // *** Použití StringUtils zde ***
        if (!StringUtils.hasText(value)) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.error("Failed to parse BigDecimal from value: {}", value);
            return null;
        }
    }

    private LocalDate parseLocalDate(String value) {
        // *** Použití StringUtils zde ***
        if (!StringUtils.hasText(value)) return null;
        try {
            // Zkusit standardní ISO formát, který SF obvykle používá
            return LocalDate.parse(value, SF_DATE_FORMATTER);
        } catch (Exception e) {
            // Zkusit i jiné běžné formáty, pokud by SF posílalo něco jiného?
            try {
                return LocalDate.parse(value, DateTimeFormatter.ofPattern("d.M.yyyy"));
            } catch (Exception e2) {
                log.error("Failed to parse LocalDate from value: {}", value);
                return null;
            }
        }
    }
}