// Soubor: src/test/java/org/example/eshop/service/CartTest.java
package org.example.eshop.service;

import org.example.eshop.model.CartItem;
import org.example.eshop.model.Coupon;
// OPRAVA: Import TaxRate
import org.example.eshop.model.TaxRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode; // Import pro jistotu, i když je v PriceConstants
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Jednoduchý Unit test - nepotřebuje Spring kontext
// Předpokládá, že Cart implementuje PriceConstants nebo má konstanty definované
class CartTest {

    private Cart cart;
    // OPRAVA: Přidány testovací sazby
    private TaxRate taxRate21;
    private TaxRate taxRate10;
    private TaxRate taxRate0;

    // OPRAVA: createTestItem nyní přijímá TaxRate objekt
    private CartItem createTestItem(String itemId, Long productId, int quantity, String priceCZK, String priceEUR, TaxRate taxRate) {
        CartItem item = new CartItem();
        item.setCartItemId(itemId);
        item.setProductId(productId);
        item.setProductName("Test Product " + productId);
        item.setQuantity(quantity);
        item.setCustom(false);
        if (priceCZK != null) item.setUnitPriceCZK(new BigDecimal(priceCZK));
        if (priceEUR != null) item.setUnitPriceEUR(new BigDecimal(priceEUR));
        // OPRAVA: Nastavení nových polí z TaxRate objektu
        if (taxRate != null) {
            item.setSelectedTaxRateId(taxRate.getId());
            item.setSelectedTaxRateValue(taxRate.getRate());
            item.setSelectedIsReverseCharge(taxRate.isReverseCharge());
        } else {
            // Fallback, pokud by sazba byla null (nemělo by nastat u reálných dat)
            item.setSelectedTaxRateId(null);
            item.setSelectedTaxRateValue(BigDecimal.ZERO);
            item.setSelectedIsReverseCharge(false);
        }
        return item;
    }

    // Pomocná metoda pro vytvoření Kupónu pro testování
    private Coupon createTestCoupon(String code, boolean isPercentage, String value, String valueCZK, String valueEUR) {
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setActive(true); // Předpokládáme aktivní pro testy výpočtů
        coupon.setPercentage(isPercentage);
        if (value != null) coupon.setValue(new BigDecimal(value));
        if (valueCZK != null) coupon.setValueCZK(new BigDecimal(valueCZK));
        if (valueEUR != null) coupon.setValueEUR(new BigDecimal(valueEUR));
        coupon.setStartDate(java.time.LocalDateTime.now().minusDays(1));
        coupon.setExpirationDate(java.time.LocalDateTime.now().plusDays(1));
        return coupon;
    }

    @BeforeEach
    void setUp() {
        cart = new Cart(); // Vytvoříme nový čistý košík před každým testem

        // OPRAVA: Inicializace testovacích sazeb
        taxRate21 = new TaxRate(); taxRate21.setId(1L); taxRate21.setRate(new BigDecimal("0.21")); taxRate21.setReverseCharge(false);
        taxRate10 = new TaxRate(); taxRate10.setId(2L); taxRate10.setRate(new BigDecimal("0.10")); taxRate10.setReverseCharge(false);
        taxRate0 = new TaxRate(); taxRate0.setId(3L); taxRate0.setRate(new BigDecimal("0.00")); taxRate0.setReverseCharge(false);
    }

    // --- Testy Základních Operací ---

    @Test
    @DisplayName("Přidání nové položky do prázdného košíku")
    void addItem_NewItem_ShouldAdd() {
        // OPRAVA: Používáme taxRate21 objekt
        CartItem item1 = createTestItem("P10-T1-S", 10L, 1, "100.00", "4.00", taxRate21);
        cart.addItem(item1);

        assertEquals(1, cart.getItemCount());
        assertEquals(1, cart.getTotalQuantity());
        assertTrue(cart.getItems().containsKey("P10-T1-S"));
        assertEquals(item1, cart.getItems().get("P10-T1-S"));
        assertTrue(cart.hasItems());
    }

    @Test
    @DisplayName("Přidání stejné položky (stejný cartItemId) navýší množství")
    void addItem_ExistingItem_ShouldIncreaseQuantity() {
        // OPRAVA: Používáme taxRate21 objekt
        CartItem item1 = createTestItem("P10-T1-S", 10L, 1, "100.00", "4.00", taxRate21);
        CartItem item2 = createTestItem("P10-T1-S", 10L, 2, "100.00", "4.00", taxRate21); // Stejné ID a sazba

        cart.addItem(item1);
        cart.addItem(item2);

        assertEquals(1, cart.getItemCount());
        assertEquals(3, cart.getTotalQuantity());
        assertNotNull(cart.getItems().get("P10-T1-S"));
        assertEquals(3, cart.getItems().get("P10-T1-S").getQuantity());
        // Ověříme, že i sazba zůstala správná
        assertEquals(taxRate21.getId(), cart.getItems().get("P10-T1-S").getSelectedTaxRateId());
        assertEquals(0, taxRate21.getRate().compareTo(cart.getItems().get("P10-T1-S").getSelectedTaxRateValue()));
    }

    @Test
    @DisplayName("Odebrání existující položky z košíku")
    void removeItem_ExistingItem_ShouldRemove() {
        CartItem item1 = createTestItem("P10-T1-S", 10L, 1, "100.00", "4.00", taxRate21);
        cart.addItem(item1);
        assertTrue(cart.hasItems());

        cart.removeItem("P10-T1-S");

        assertEquals(0, cart.getItemCount());
        assertFalse(cart.getItems().containsKey("P10-T1-S"));
        assertFalse(cart.hasItems());
    }

    @Test
    @DisplayName("Odebrání neexistující položky nic nezmění")
    void removeItem_NonExistingItem_ShouldDoNothing() {
        CartItem item1 = createTestItem("P10-T1-S", 10L, 1, "100.00", "4.00", taxRate21);
        cart.addItem(item1);
        int initialCount = cart.getItemCount();

        cart.removeItem("NEEXISTUJICI_ID");

        assertEquals(initialCount, cart.getItemCount());
        assertTrue(cart.getItems().containsKey("P10-T1-S"));
    }

    @Test
    @DisplayName("Aktualizace množství existující položky na kladnou hodnotu")
    void updateQuantity_Positive_ShouldUpdate() {
        CartItem item1 = createTestItem("P10-T1-S", 10L, 1, "100.00", "4.00", taxRate21);
        cart.addItem(item1);

        cart.updateQuantity("P10-T1-S", 5);

        assertEquals(1, cart.getItemCount());
        assertNotNull(cart.getItems().get("P10-T1-S"));
        assertEquals(5, cart.getItems().get("P10-T1-S").getQuantity());
    }

    @Test
    @DisplayName("Aktualizace množství existující položky na nulu ji odebere")
    void updateQuantity_Zero_ShouldRemove() {
        CartItem item1 = createTestItem("P10-T1-S", 10L, 3, "100.00", "4.00", taxRate21);
        cart.addItem(item1);

        cart.updateQuantity("P10-T1-S", 0);

        assertEquals(0, cart.getItemCount());
        assertFalse(cart.getItems().containsKey("P10-T1-S"));
    }

    @Test
    @DisplayName("Aktualizace množství neexistující položky nic neudělá")
    void updateQuantity_NonExisting_ShouldDoNothing() {
        cart.updateQuantity("NEEXISTUJICI_ID", 5);
        assertEquals(0, cart.getItemCount());
    }

    @Test
    @DisplayName("Vyčištění košíku")
    void clearCart_ShouldRemoveAllItems() {
        CartItem item1 = createTestItem("P10-T1-S", 10L, 1, "100.00", "4.00", taxRate21);
        CartItem item2 = createTestItem("P20-T2-C", 20L, 1, "500.00", "20.00", taxRate10); // Jiná sazba
        cart.addItem(item1);
        cart.addItem(item2);
        Coupon coupon = createTestCoupon("TEST", true, "5", null, null);
        cart.applyCoupon(coupon, "TEST");

        cart.clearCart();

        assertEquals(0, cart.getItemCount());
        assertTrue(cart.getItems().isEmpty());
        assertNull(cart.getAppliedCoupon());
        assertNull(cart.getAppliedCouponCode());
        assertFalse(cart.hasItems());
    }

    // --- Testy Výpočtů ---

    @Test
    @DisplayName("Výpočet mezisoučtu (Subtotal) v CZK pro více položek")
    void calculateSubtotal_CZK_MultipleItems() {
        CartItem item1 = createTestItem("P10-T1-S", 10L, 2, "100.00", "4.00", taxRate21); // 2 * 100 = 200.00
        CartItem item2 = createTestItem("P20-T2-C", 20L, 1, "550.50", "22.00", taxRate10); // 1 * 550.50 = 550.50
        cart.addItem(item1);
        cart.addItem(item2);

        assertEquals(0, new BigDecimal("750.50").compareTo(cart.calculateSubtotal("CZK")));
    }

    @Test
    @DisplayName("Výpočet mezisoučtu (Subtotal) v EUR pro více položek")
    void calculateSubtotal_EUR_MultipleItems() {
        CartItem item1 = createTestItem("P10-T1-S", 10L, 2, "100.00", "4.15", taxRate21); // 2 * 4.15 = 8.30
        CartItem item2 = createTestItem("P20-T2-C", 20L, 1, "550.50", "22.80", taxRate10); // 1 * 22.80 = 22.80
        cart.addItem(item1);
        cart.addItem(item2);

        assertEquals(0, new BigDecimal("31.10").compareTo(cart.calculateSubtotal("EUR")));
    }

    @Test
    @DisplayName("Výpočet mezisoučtu pro prázdný košík")
    void calculateSubtotal_EmptyCart_ShouldBeZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(cart.calculateSubtotal("CZK")));
        assertEquals(0, BigDecimal.ZERO.compareTo(cart.calculateSubtotal("EUR")));
    }

    @Test
    @DisplayName("Výpočet celkového DPH (Total VAT) v CZK s různými sazbami")
    void calculateTotalVatAmount_CZK_MixedRates() {
        CartItem item1 = createTestItem("P10-T1-S", 10L, 2, "100.00", "4.00", taxRate21); // Sub: 200.00, VAT: 200 * 0.21 = 42.00
        CartItem item2 = createTestItem("P20-T2-C", 20L, 1, "500.00", "20.00", taxRate10); // Sub: 500.00, VAT: 500 * 0.10 = 50.00
        CartItem item3 = createTestItem("P30-T3-Z", 30L, 1, "10.00", "0.40", taxRate0);  // Sub:  10.00, VAT: 10 * 0.00 =  0.00
        cart.addItem(item1);
        cart.addItem(item2);
        cart.addItem(item3);

        assertEquals(0, new BigDecimal("92.00").compareTo(cart.calculateTotalVatAmount("CZK")));
    }

    @Test
    @DisplayName("Výpočet celkového DPH pro prázdný košík")
    void calculateTotalVatAmount_EmptyCart_ShouldBeZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(cart.calculateTotalVatAmount("CZK")));
        assertEquals(0, BigDecimal.ZERO.compareTo(cart.calculateTotalVatAmount("EUR")));
    }

    @Test
    @DisplayName("Výpočet rozpisu DPH (VAT Breakdown) v CZK s různými sazbami")
    void calculateVatBreakdown_CZK_MixedRates() {
        CartItem item1 = createTestItem("P10-T1-S", 10L, 2, "100.00", "4.00", taxRate21); // Sub: 200.00, VAT 21%: 42.00
        CartItem item2 = createTestItem("P20-T2-C", 20L, 1, "500.00", "20.00", taxRate10); // Sub: 500.00, VAT 10%: 50.00
        CartItem item3 = createTestItem("P30-T1-S", 30L, 3, "50.00", "2.00", taxRate21);  // Sub: 150.00, VAT 21%: 31.50
        CartItem item4 = createTestItem("P40-T3-Z", 40L, 1, "20.00", "0.80", taxRate0);  // Sub:  20.00, VAT 0%:   0.00
        cart.addItem(item1);
        cart.addItem(item2);
        cart.addItem(item3);
        cart.addItem(item4);

        Map<BigDecimal, BigDecimal> breakdown = cart.calculateVatBreakdown("CZK");
        assertEquals(3, breakdown.size());

        // Klíč je hodnota sazby zaokrouhlená na 2 místa
        BigDecimal rateKey0 = new BigDecimal("0.00").setScale(2, RoundingMode.HALF_UP);
        BigDecimal rateKey10 = new BigDecimal("0.10").setScale(2, RoundingMode.HALF_UP);
        BigDecimal rateKey21 = new BigDecimal("0.21").setScale(2, RoundingMode.HALF_UP);

        assertEquals(0, new BigDecimal("0.00").compareTo(breakdown.getOrDefault(rateKey0, BigDecimal.valueOf(-1))));
        assertEquals(0, new BigDecimal("50.00").compareTo(breakdown.getOrDefault(rateKey10, BigDecimal.valueOf(-1))));
        assertEquals(0, new BigDecimal("73.50").compareTo(breakdown.getOrDefault(rateKey21, BigDecimal.valueOf(-1)))); // 42.00 + 31.50
    }

    @Test
    @DisplayName("Výpočet rozpisu DPH pro prázdný košík")
    void calculateVatBreakdown_EmptyCart_ShouldBeEmptyMap() {
        assertTrue(cart.calculateVatBreakdown("CZK").isEmpty());
        assertTrue(cart.calculateVatBreakdown("EUR").isEmpty());
    }


    @Test
    @DisplayName("Výpočet celkové ceny před dopravou (TotalPriceBeforeShipping) v CZK")
    void calculateTotalPriceBeforeShipping_CZK_NoDiscount() {
        CartItem item1 = createTestItem("P10-T1-S", 10L, 2, "100.00", "4.00", taxRate21); // Sub: 200.00, VAT: 42.00 => Total: 242.00
        CartItem item2 = createTestItem("P20-T2-C", 20L, 1, "500.00", "20.00", taxRate10); // Sub: 500.00, VAT: 50.00 => Total: 550.00
        cart.addItem(item1);
        cart.addItem(item2);

        // Očekáváno: Subtotal(700.00) + TotalVAT(92.00) = 792.00
        // Metoda calculateTotalPriceBeforeShipping POČÍTÁ S DPH
        assertEquals(0, new BigDecimal("792.00").compareTo(cart.calculateTotalPriceBeforeShipping("CZK")));
    }

    @Test
    @DisplayName("Výpočet celkové ceny před dopravou (TotalPriceBeforeShipping) v EUR")
    void calculateTotalPriceBeforeShipping_EUR_NoDiscount() {
        CartItem item1 = createTestItem("P10-T1-S", 10L, 2, "100.00", "4.00", taxRate21); // Sub: 8.00, VAT: 1.68 => Total: 9.68
        CartItem item2 = createTestItem("P20-T2-C", 20L, 1, "500.00", "20.00", taxRate10); // Sub: 20.00, VAT: 2.00 => Total: 22.00
        cart.addItem(item1);
        cart.addItem(item2);

        // Očekáváno: Subtotal(28.00) + TotalVAT(3.68) = 31.68
        assertEquals(0, new BigDecimal("31.68").compareTo(cart.calculateTotalPriceBeforeShipping("EUR")));
    }

    @Test
    @DisplayName("Výpočet celkové ceny před dopravou pro prázdný košík")
    void calculateTotalPriceBeforeShipping_EmptyCart_ShouldBeZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(cart.calculateTotalPriceBeforeShipping("CZK")));
        assertEquals(0, BigDecimal.ZERO.compareTo(cart.calculateTotalPriceBeforeShipping("EUR")));
    }


    // --- Testy Kupónů ---

    @Test
    @DisplayName("Použití procentuálního kupónu ovlivní cenu před dopravou")
    void calculateTotalPriceBeforeShipping_PercentageCoupon_CZK() {
        CartItem item1 = createTestItem("P10-T1-S", 10L, 2, "100.00", "4.00", taxRate21); // Sub: 200.00, VAT: 42.00
        cart.addItem(item1);
        Coupon coupon = createTestCoupon("SLEVA10", true, "10.00", null, null); // 10%
        cart.applyCoupon(coupon, "SLEVA10");

        // Subtotal = 200.00
        // Discount = 200.00 * 0.10 = 20.00
        // Total VAT = 42.00 (nemění se)
        // Expected Total Price Before Shipping = (Subtotal - Discount) + Total VAT = (200.00 - 20.00) + 42.00 = 180.00 + 42.00 = 222.00
        assertEquals(0, new BigDecimal("222.00").compareTo(cart.calculateTotalPriceBeforeShipping("CZK")));
        // Total Price Without Tax After Discount = Subtotal - Discount = 200.00 - 20.00 = 180.00
        assertEquals(0, new BigDecimal("180.00").compareTo(cart.calculateTotalPriceWithoutTaxAfterDiscount("CZK")));
    }

    @Test
    @DisplayName("Použití fixního kupónu CZK ovlivní cenu před dopravou")
    void calculateTotalPriceBeforeShipping_FixedCoupon_CZK() {
        CartItem item1 = createTestItem("P10-T1-S", 10L, 2, "100.00", "4.00", taxRate21); // Sub: 200.00, VAT: 42.00
        cart.addItem(item1);
        Coupon coupon = createTestCoupon("SLEVA50", false, null, "50.00", "2.00"); // 50 CZK
        cart.applyCoupon(coupon, "SLEVA50");

        // Subtotal = 200.00
        // Discount = 50.00
        // Total VAT = 42.00
        // Expected Total Price Before Shipping = (200.00 - 50.00) + 42.00 = 150.00 + 42.00 = 192.00
        assertEquals(0, new BigDecimal("192.00").compareTo(cart.calculateTotalPriceBeforeShipping("CZK")));
        // Total Price Without Tax After Discount = 200.00 - 50.00 = 150.00
        assertEquals(0, new BigDecimal("150.00").compareTo(cart.calculateTotalPriceWithoutTaxAfterDiscount("CZK")));
    }

    @Test
    @DisplayName("Použití fixního kupónu EUR ovlivní cenu před dopravou")
    void calculateTotalPriceBeforeShipping_FixedCoupon_EUR() {
        CartItem item1 = createTestItem("P10-T1-S", 10L, 2, "100.00", "4.00", taxRate21); // Sub: 8.00 EUR, VAT: 1.68 EUR
        cart.addItem(item1);
        Coupon coupon = createTestCoupon("SLEVA2EUR", false, null, "50.00", "2.00"); // 2 EUR
        cart.applyCoupon(coupon, "SLEVA2EUR");

        // Subtotal = 8.00
        // Discount = 2.00
        // Total VAT = 1.68
        // Expected Total Price Before Shipping = (8.00 - 2.00) + 1.68 = 6.00 + 1.68 = 7.68
        assertEquals(0, new BigDecimal("7.68").compareTo(cart.calculateTotalPriceBeforeShipping("EUR")));
        // Total Price Without Tax After Discount = 8.00 - 2.00 = 6.00
        assertEquals(0, new BigDecimal("6.00").compareTo(cart.calculateTotalPriceWithoutTaxAfterDiscount("EUR")));
    }

    @Test
    @DisplayName("Fixní kupón vyšší než mezisoučet sleví maximálně na nulu")
    void calculateDiscountAmount_FixedCouponHigherThanSubtotal() {
        CartItem item1 = createTestItem("P10-T1-S", 10L, 1, "30.00", "1.20", taxRate21); // Sub: 30.00 CZK, VAT: 6.30
        cart.addItem(item1);
        Coupon coupon = createTestCoupon("SLEVA50", false, null, "50.00", "2.00"); // 50 CZK
        cart.applyCoupon(coupon, "SLEVA50");

        // Discount = min(50.00, 30.00) = 30.00
        assertEquals(0, new BigDecimal("30.00").compareTo(cart.calculateDiscountAmount("CZK")));
        // Total Price Without Tax After Discount = 30.00 - 30.00 = 0.00
        assertEquals(0, BigDecimal.ZERO.compareTo(cart.calculateTotalPriceWithoutTaxAfterDiscount("CZK")));
        // Total Price Before Shipping = (30.00 - 30.00) + 6.30 = 0.00 + 6.30 = 6.30
        assertEquals(0, new BigDecimal("6.30").compareTo(cart.calculateTotalPriceBeforeShipping("CZK")));
    }

    @Test
    @DisplayName("Odebrání kupónu")
    void removeCoupon_ShouldClearCouponData() {
        CartItem item1 = createTestItem("P10-T1-S", 10L, 1, "100.00", "4.00", taxRate21);
        cart.addItem(item1);
        Coupon coupon = createTestCoupon("TEST", true, "5", null, null);
        cart.applyCoupon(coupon, "TEST");
        assertNotNull(cart.getAppliedCoupon());
        assertNotNull(cart.getAppliedCouponCode());
        assertTrue(cart.calculateDiscountAmount("CZK").compareTo(BigDecimal.ZERO) > 0);

        cart.removeCoupon();

        assertNull(cart.getAppliedCoupon());
        assertNull(cart.getAppliedCouponCode());
        assertEquals(0, BigDecimal.ZERO.compareTo(cart.calculateDiscountAmount("CZK")));
    }

}