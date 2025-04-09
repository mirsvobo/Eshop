package org.example.eshop.service;
import org.example.eshop.model.Order;
import java.math.BigDecimal;

public interface PaymentService {
    /**
     * Vypočítá požadovanou výši zálohy pro objednávku.
     * @param totalAmount Celková částka objednávky s DPH.
     * @return Požadovaná výše zálohy.
     */
    BigDecimal calculateDeposit(BigDecimal totalAmount);

    /**
     * Určí počáteční stav platby pro nově vytvářenou objednávku.
     * @param order Objekt objednávky (s nastavenou platební metodou a položkami).
     * @return Kód stavu platby (např. "AWAITING_DEPOSIT", "PENDING_PAYMENT", "PENDING").
     */
    String determineInitialPaymentStatus(Order order);

    // Zde mohou být další metody, např. pro zpracování webhooků z platební brány,
    // ověření stavu platby atd.
    // void processPaymentNotification(Map<String, String> notificationData);
    // boolean checkPaymentStatus(Order order);
}