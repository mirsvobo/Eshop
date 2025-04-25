package org.example.eshop.service;

import org.example.eshop.config.PriceConstants;
import org.example.eshop.model.Order;
import org.example.eshop.model.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Dummy implementace PaymentService pro testovací účely a vývoj.
 * Obsahuje logiku pro výpočet 50% zálohy se zaokrouhlením dolů na tisíce.
 */
@Service("paymentService") // Označení jako primární implementace PaymentService
public class DummyPaymentService implements PaymentService, PriceConstants { // Implementace PriceConstants

    private static final Logger log = LoggerFactory.getLogger(DummyPaymentService.class); // Přidání loggeru

    // Procentuální výše zálohy (50%)
    private static final BigDecimal DEPOSIT_PERCENTAGE = new BigDecimal("0.50");
    // Konstanta pro dělení/násobení při zaokrouhlování na tisíce
    private static final BigDecimal THOUSAND = new BigDecimal("1000");

    /**
     * Vypočítá požadovanou výši zálohy pro objednávku.
     * Záloha je 50 % z celkové částky objednávky, zaokrouhleno DOLŮ na nejbližší tisíc korun/eur.
     *
     * @param totalAmount Celková částka objednávky s DPH.
     * @return Požadovaná výše zálohy (zaokrouhlená dolů na tisíce), nebo BigDecimal.ZERO pokud je totalAmount null nebo <= 0.
     */
    @Override
    public BigDecimal calculateDeposit(BigDecimal totalAmount) {
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("CalculateDeposit: totalAmount is zero or null, returning ZERO deposit.");
            return BigDecimal.ZERO;
        }

        // 1. Vypočítat 50% z celkové částky
        BigDecimal fiftyPercent = totalAmount.multiply(DEPOSIT_PERCENTAGE);
        log.debug("CalculateDeposit: 50% of {} is {}", totalAmount, fiftyPercent);

        // 2. Zaokrouhlit výsledek DOLŮ na nejbližší tisíc
        // Dělíme 1000, zaokrouhlíme dolů na 0 des. míst, násobíme 1000
        BigDecimal roundedDeposit = fiftyPercent.divide(THOUSAND, 0, RoundingMode.DOWN)
                .multiply(THOUSAND);
        log.debug("CalculateDeposit: Rounded down to nearest thousand: {}", roundedDeposit);

        // Zajistíme, že výsledek není záporný (nemělo by nastat, ale pro jistotu)
        roundedDeposit = roundedDeposit.max(BigDecimal.ZERO);

        // Vrátíme zaokrouhlenou hodnotu se škálou pro peníze (PRICE_SCALE z PriceConstants)
        BigDecimal finalDeposit = roundedDeposit.setScale(PRICE_SCALE, ROUNDING_MODE); // Používáme konstanty
        log.info("CalculateDeposit: Final deposit amount calculated: {}", finalDeposit);
        return finalDeposit;
    }

    /**
     * Určí počáteční stav platby pro nově vytvářenou objednávku.
     * Pokud objednávka obsahuje produkt na míru a vypočtená záloha je větší než nula,
     * nastaví stav na "AWAITING_DEPOSIT". Jinak postupuje podle platební metody.
     *
     * @param order Objekt objednávky (s nastavenou platební metodou a položkami).
     * @return Kód stavu platby (např. "AWAITING_DEPOSIT", "PENDING_PAYMENT", "PENDING").
     */
    @Override
    public String determineInitialPaymentStatus(Order order) {
        if (order == null || order.getOrderItems() == null || order.getPaymentMethod() == null) {
            log.error("DetermineInitialPaymentStatus: Invalid Order object provided (null, no items, or no payment method). Returning ERROR status.");
            return "ERROR"; // Nebo vyhodit výjimku
        }

        // Zjistíme, zda objednávka obsahuje produkt na míru
        boolean hasCustomProduct = order.getOrderItems().stream()
                .anyMatch(OrderItem::isCustomConfigured); // Přímo testujeme boolean

        if (hasCustomProduct) {
            log.debug("DetermineInitialPaymentStatus: Order {} contains custom product. Calculating potential deposit.", order.getOrderCode());
            // Pokud obsahuje produkt na míru, spočítáme zálohu
            BigDecimal potentialDeposit = calculateDeposit(order.getTotalPrice());
            // Pokud je vypočtená záloha větší než nula, vyžadujeme ji
            if (potentialDeposit.compareTo(BigDecimal.ZERO) > 0) {
                log.info("DetermineInitialPaymentStatus: Deposit required for order {}. Status set to AWAITING_DEPOSIT.", order.getOrderCode());
                return "AWAITING_DEPOSIT"; // Čeká na zálohu
            } else {
                log.info("DetermineInitialPaymentStatus: Custom product present, but calculated deposit is zero for order {}. Proceeding based on payment method.", order.getOrderCode());
                // Pokud je záloha nula (např. kvůli ceně < 2000), pokračujeme dál podle plat. metody
            }
        } else {
            log.debug("DetermineInitialPaymentStatus: Order {} does not contain custom product. Proceeding based on payment method.", order.getOrderCode());
        }

        // Standardní logika podle platební metody (pokud není vyžadována záloha)
        String paymentMethod = order.getPaymentMethod().toUpperCase();
        log.debug("DetermineInitialPaymentStatus: Determining status for payment method: {}", paymentMethod);
        switch (paymentMethod) {
            case "BANK_TRANSFER":
                log.info("DetermineInitialPaymentStatus: Status for BANK_TRANSFER set to PENDING_PAYMENT for order {}.", order.getOrderCode());
                return "PENDING_PAYMENT"; // Čeká na platbu
            case "CASH_ON_DELIVERY":
                log.info("DetermineInitialPaymentStatus: Status for CASH_ON_DELIVERY set to PENDING for order {}.", order.getOrderCode());
                return "PENDING"; // Čeká na zpracování/odeslání
            // Zde můžete přidat další platební metody
            default:
                log.warn("DetermineInitialPaymentStatus: Unknown payment method '{}' for order {}. Defaulting to PENDING.", paymentMethod, order.getOrderCode());
                return "PENDING"; // Výchozí bezpečný stav
        }
    }
}