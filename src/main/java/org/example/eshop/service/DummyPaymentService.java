package org.example.eshop.service;

import org.example.eshop.model.Order;
import org.example.eshop.model.OrderItem;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service("paymentService")
public class DummyPaymentService implements PaymentService {

    // Procentuální výše zálohy (50%)
    private static final BigDecimal DEPOSIT_PERCENTAGE = new BigDecimal("0.50");
    private static final int SCALE = 2; // Počet desetinných míst pro zaokrouhlení

    @Override
    public BigDecimal calculateDeposit(BigDecimal totalAmount) {
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return totalAmount.multiply(DEPOSIT_PERCENTAGE).setScale(SCALE, RoundingMode.HALF_UP);
    }

    @Override
    public String determineInitialPaymentStatus(Order order) {
        if (order == null || order.getOrderItems() == null || order.getPaymentMethod() == null) {
            // Mělo by být ošetřeno už v OrderService, ale pro jistotu
            return "ERROR"; // Nebo nějaký jiný chybový stav
        }

        // Zjistíme, zda objednávka obsahuje produkt na míru
        // OPRAVENO: Přímo testujeme boolean hodnotu vrácenou isCustomConfigured()
        boolean hasCustomProduct = order.getOrderItems().stream()
                .anyMatch(OrderItem::isCustomConfigured);

        // Pokud ano, čekáme na zálohu
        if (hasCustomProduct) {
            return "AWAITING_DEPOSIT"; // Čeká na zálohu
        }

        // Jinak standardní logika podle platební metody
        switch (order.getPaymentMethod().toUpperCase()) {
            case "BANK_TRANSFER": // Platba převodem
                return "PENDING_PAYMENT"; // Čeká na platbu
            case "CASH_ON_DELIVERY": // Dobírka
                return "PENDING"; // Čeká na zpracování/odeslání (platba až při doručení)
            // Zde můžete přidat další platební metody
            // case "ONLINE_CARD":
            //     return "PENDING_PAYMENT"; // Nebo specifický stav pro online platbu
            default:
                return "PENDING"; // Výchozí bezpečný stav
        }
    }
}