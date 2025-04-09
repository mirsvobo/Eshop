// Soubor: src/test/java/org/example/eshop/service/CartTest.java
package org.example.eshop.service;

import org.example.eshop.model.CartItem;
import org.example.eshop.model.Coupon;
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

    // Pomocná metoda pro vytvoření CartItem pro testování
    // Očekává taxRate jako řetězec např. "0.21" pro 21%
    private CartItem createTestItem(String itemId, Long productId, int quantity, String priceCZK, String priceEUR, String taxRate) {
        CartItem item = new CartItem();
        item.setCartItemId(itemId);
        item.setProductId(productId);
        item.setProductName("Test Product " + productId);
        item.setQuantity(quantity);
        item.setCustom(false); // Defaultně standardní produkt pro testy
        if (priceCZK != null) item.setUnitPriceCZK(new BigDecimal(priceCZK));
        if (priceEUR != null) item.setUnitPriceEUR(new BigDecimal(priceEUR));
        if (taxRate != null) item.setTaxRatePercent(new BigDecimal(taxRate)); // Uložíme sazbu jako BigDecimal
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
        // Nastavíme výchozí platná data, aby neovlivňovala testy výpočtů
        coupon.setStartDate(java.time.LocalDateTime.now().minusDays(1));
        coupon.setExpirationDate(java.time.LocalDateTime.now().plusDays(1));
        return coupon;
    }

    @BeforeEach
    void setUp() {
        cart = new Cart(); // Vytvoříme nový čistý košík před každým testem
    }

    // --- Testy Základních Operací ---

    @Test
    @DisplayName("Přidání nové položky do prázdného košíku")
    void addItem_NewItem_ShouldAdd() {
        CartItem item1 = createTestItem("P10-S", 10L, 1, "100.00", "4.00", "0.21");
        cart.addItem(item1);

        assertEquals(1, cart.getItemCount(), "Počet unikátních položek by měl být 1");
        assertEquals(1, cart.getTotalQuantity(), "Celkové množství by mělo být 1");
        assertTrue(cart.getItems().containsKey("P10-S"), "Košík by měl obsahovat klíč položky");
        assertEquals(item1, cart.getItems().get("P10-S"), "Položka v košíku by měla být ta přidaná");
        assertTrue(cart.hasItems(), "Košík by neměl být prázdný");
    }

    @Test
    @DisplayName("Přidání stejné položky (stejný cartItemId) navýší množství")
    void addItem_ExistingItem_ShouldIncreaseQuantity() {
        CartItem item1 = createTestItem("P10-S", 10L, 1, "100.00", "4.00", "0.21");
        // Položka se stejným ID, ale jiným počátečním množstvím (simuluje další přidání)
        CartItem item2 = createTestItem("P10-S", 10L, 2, "100.00", "4.00", "0.21");

        cart.addItem(item1);
        cart.addItem(item2); // Přidáme znovu stejnou položku

        assertEquals(1, cart.getItemCount(), "Košík by měl stále obsahovat 1 unikátní položku");
        assertEquals(3, cart.getTotalQuantity(), "Celkové množství by mělo být součtem (1+2=3)");
        assertNotNull(cart.getItems().get("P10-S"), "Položka by měla v košíku existovat");
        assertEquals(3, cart.getItems().get("P10-S").getQuantity(), "Množství položky by mělo být aktualizováno na 3");
    }

    @Test
    @DisplayName("Odebrání existující položky z košíku")
    void removeItem_ExistingItem_ShouldRemove() {
        CartItem item1 = createTestItem("P10-S", 10L, 1, "100.00", "4.00", "0.21");
        cart.addItem(item1);
        assertTrue(cart.hasItems(), "Košík by měl obsahovat položku před odebráním");

        cart.removeItem("P10-S");

        assertEquals(0, cart.getItemCount(), "Počet položek by měl být 0");
        assertFalse(cart.getItems().containsKey("P10-S"), "Košík by neměl obsahovat klíč odebrané položky");
        assertFalse(cart.hasItems(), "Košík by měl být prázdný");
    }

    @Test
    @DisplayName("Odebrání neexistující položky nic nezmění")
    void removeItem_NonExistingItem_ShouldDoNothing() {
        CartItem item1 = createTestItem("P10-S", 10L, 1, "100.00", "4.00", "0.21");
        cart.addItem(item1);
        int initialCount = cart.getItemCount();

        cart.removeItem("NEEXISTUJICI_ID"); // Pokus o odebrání neexistující

        assertEquals(initialCount, cart.getItemCount(), "Počet položek by se neměl změnit");
        assertTrue(cart.getItems().containsKey("P10-S"), "Původní položka by měla zůstat");
    }

    @Test
    @DisplayName("Aktualizace množství existující položky na kladnou hodnotu")
    void updateQuantity_Positive_ShouldUpdate() {
        CartItem item1 = createTestItem("P10-S", 10L, 1, "100.00", "4.00", "0.21");
        cart.addItem(item1);

        cart.updateQuantity("P10-S", 5);

        assertEquals(1, cart.getItemCount(), "Stále by měla být jedna unikátní položka");
        assertNotNull(cart.getItems().get("P10-S"), "Položka by měla existovat");
        assertEquals(5, cart.getItems().get("P10-S").getQuantity(), "Množství by mělo být 5");
    }

    @Test
    @DisplayName("Aktualizace množství existující položky na nulu ji odebere")
    void updateQuantity_Zero_ShouldRemove() {
        CartItem item1 = createTestItem("P10-S", 10L, 3, "100.00", "4.00", "0.21");
        cart.addItem(item1);

        cart.updateQuantity("P10-S", 0);

        assertEquals(0, cart.getItemCount(), "Počet položek by měl být 0");
        assertFalse(cart.getItems().containsKey("P10-S"), "Položka by měla být odebrána");
    }

    @Test
    @DisplayName("Aktualizace množství neexistující položky nic neudělá")
    void updateQuantity_NonExisting_ShouldDoNothing() {
        cart.updateQuantity("NEEXISTUJICI_ID", 5);
        assertEquals(0, cart.getItemCount(), "Košík by měl zůstat prázdný");
    }

    @Test
    @DisplayName("Vyčištění košíku")
    void clearCart_ShouldRemoveAllItems() {
        CartItem item1 = createTestItem("P10-S", 10L, 1, "100.00", "4.00", "0.21");
        CartItem item2 = createTestItem("P20-C", 20L, 1, "500.00", "20.00", "0.10");
        cart.addItem(item1);
        cart.addItem(item2);
        Coupon coupon = createTestCoupon("TEST", true, "5", null, null);
        cart.applyCoupon(coupon, "TEST"); // Přidáme i kupón

        cart.clearCart();

        assertEquals(0, cart.getItemCount(), "Počet položek by měl být 0");
        assertTrue(cart.getItems().isEmpty(), "Mapa položek by měla být prázdná");
        assertNull(cart.getAppliedCoupon(), "Kupón by měl být odstraněn");
        assertNull(cart.getAppliedCouponCode(), "Kód kupónu by měl být odstraněn");
        assertFalse(cart.hasItems(), "Košík by měl být prázdný");
    }

    // --- Testy Výpočtů ---

    @Test
    @DisplayName("Výpočet mezisoučtu (Subtotal) v CZK pro více položek")
    void calculateSubtotal_CZK_MultipleItems() {
        CartItem item1 = createTestItem("P10-S", 10L, 2, "100.00", "4.00", "0.21"); // 2 * 100 = 200.00
        CartItem item2 = createTestItem("P20-C", 20L, 1, "550.50", "22.00", "0.21"); // 1 * 550.50 = 550.50
        cart.addItem(item1);
        cart.addItem(item2);

        // Očekáváno: 200.00 + 550.50 = 750.50
        assertEquals(0, new BigDecimal("750.50").compareTo(cart.calculateSubtotal("CZK")), "Mezisoučet v CZK nesouhlasí");
    }

    @Test
    @DisplayName("Výpočet mezisoučtu (Subtotal) v EUR pro více položek")
    void calculateSubtotal_EUR_MultipleItems() {
        CartItem item1 = createTestItem("P10-S", 10L, 2, "100.00", "4.15", "0.21"); // 2 * 4.15 = 8.30
        CartItem item2 = createTestItem("P20-C", 20L, 1, "550.50", "22.80", "0.21"); // 1 * 22.80 = 22.80
        cart.addItem(item1);
        cart.addItem(item2);

        // Očekáváno: 8.30 + 22.80 = 31.10
        assertEquals(0, new BigDecimal("31.10").compareTo(cart.calculateSubtotal("EUR")), "Mezisoučet v EUR nesouhlasí");
    }

    @Test
    @DisplayName("Výpočet mezisoučtu pro prázdný košík")
    void calculateSubtotal_EmptyCart_ShouldBeZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(cart.calculateSubtotal("CZK")), "Mezisoučet prázdného košíku CZK má být 0");
        assertEquals(0, BigDecimal.ZERO.compareTo(cart.calculateSubtotal("EUR")), "Mezisoučet prázdného košíku EUR má být 0");
    }

    @Test
    @DisplayName("Výpočet celkového DPH (Total VAT) v CZK s různými sazbami")
    void calculateTotalVatAmount_CZK_MixedRates() {
        CartItem item1 = createTestItem("P10-S", 10L, 2, "100.00", "4.00", "0.21"); // Sub: 200.00, VAT: 200 * 0.21 = 42.00
        CartItem item2 = createTestItem("P20-C", 20L, 1, "500.00", "20.00", "0.10"); // Sub: 500.00, VAT: 500 * 0.10 = 50.00
        CartItem item3 = createTestItem("P30-Z", 30L, 1, "10.00", "0.40", "0.00");  // Sub:  10.00, VAT: 10 * 0.00 =  0.00
        cart.addItem(item1);
        cart.addItem(item2);
        cart.addItem(item3);

        // Očekáváno: 42.00 + 50.00 + 0.00 = 92.00
        assertEquals(0, new BigDecimal("92.00").compareTo(cart.calculateTotalVatAmount("CZK")), "Celkové DPH nesouhlasí");
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
        CartItem item1 = createTestItem("P10-S", 10L, 2, "100.00", "4.00", "0.21"); // Sub: 200.00, VAT 21%: 42.00
        CartItem item2 = createTestItem("P20-C", 20L, 1, "500.00", "20.00", "0.10"); // Sub: 500.00, VAT 10%: 50.00
        CartItem item3 = createTestItem("P30-S", 30L, 3, "50.00", "2.00", "0.21");  // Sub: 150.00, VAT 21%: 31.50
        CartItem item4 = createTestItem("P40-Z", 40L, 1, "20.00", "0.80", "0.00");  // Sub:  20.00, VAT 0%:   0.00
        cart.addItem(item1);
        cart.addItem(item2);
        cart.addItem(item3);
        cart.addItem(item4);

        Map<BigDecimal, BigDecimal> breakdown = cart.calculateVatBreakdown("CZK");
        // Očekáváme 3 sazby: 0.00, 0.10, 0.21
        assertEquals(3, breakdown.size(), "Měly by být 3 sazby DPH v rozpisu");

        // Klíč je sazba (např. 0.10), Hodnota je celkové DPH pro tuto sazbu
        // Porovnáváme klíče a hodnoty zaokrouhlené na 2 místa pro konzistenci
        assertEquals(0, new BigDecimal("0.00").compareTo(breakdown.getOrDefault(new BigDecimal("0.00").setScale(2), BigDecimal.valueOf(-1))), "Rozpis DPH 0% nesouhlasí");
        assertEquals(0, new BigDecimal("50.00").compareTo(breakdown.getOrDefault(new BigDecimal("0.10").setScale(2), BigDecimal.valueOf(-1))), "Rozpis DPH 10% nesouhlasí");
        assertEquals(0, new BigDecimal("73.50").compareTo(breakdown.getOrDefault(new BigDecimal("0.21").setScale(2), BigDecimal.valueOf(-1))), "Rozpis DPH 21% nesouhlasí (42.00 + 31.50)");
    }

    @Test
    @DisplayName("Výpočet rozpisu DPH pro prázdný košík")
    void calculateVatBreakdown_EmptyCart_ShouldBeEmptyMap() {
        assertTrue(cart.calculateVatBreakdown("CZK").isEmpty(), "Rozpis DPH prázdného košíku CZK má být prázdná mapa");
        assertTrue(cart.calculateVatBreakdown("EUR").isEmpty(), "Rozpis DPH prázdného košíku EUR má být prázdná mapa");
    }


    @Test
    @DisplayName("Výpočet celkové ceny před dopravou (TotalPriceBeforeShipping) v CZK")
    void calculateTotalPriceBeforeShipping_CZK_NoDiscount() {
        CartItem item1 = createTestItem("P10-S", 10L, 2, "100.00", "4.00", "0.21"); // Sub: 200.00, VAT: 42.00 => Total: 242.00
        CartItem item2 = createTestItem("P20-C", 20L, 1, "500.00", "20.00", "0.10"); // Sub: 500.00, VAT: 50.00 => Total: 550.00
        cart.addItem(item1);
        cart.addItem(item2);

        // Očekáváno: Subtotal(700.00) + TotalVAT(92.00) = 792.00
        assertEquals(0, new BigDecimal("792.00").compareTo(cart.calculateTotalPriceBeforeShipping("CZK")), "Celková cena před dopravou CZK nesouhlasí");
    }

    @Test
    @DisplayName("Výpočet celkové ceny před dopravou (TotalPriceBeforeShipping) v EUR")
    void calculateTotalPriceBeforeShipping_EUR_NoDiscount() {
        CartItem item1 = createTestItem("P10-S", 10L, 2, "100.00", "4.00", "0.21"); // Sub: 8.00, VAT: 1.68 => Total: 9.68
        CartItem item2 = createTestItem("P20-C", 20L, 1, "500.00", "20.00", "0.10"); // Sub: 20.00, VAT: 2.00 => Total: 22.00
        cart.addItem(item1);
        cart.addItem(item2);

        // Očekáváno: Subtotal(28.00) + TotalVAT(3.68) = 31.68
        assertEquals(0, new BigDecimal("31.68").compareTo(cart.calculateTotalPriceBeforeShipping("EUR")), "Celková cena před dopravou EUR nesouhlasí");
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
        CartItem item1 = createTestItem("P10-S", 10L, 2, "100.00", "4.00", "0.21"); // Sub: 200.00, VAT: 42.00
        cart.addItem(item1);
        Coupon coupon = createTestCoupon("SLEVA10", true, "10.00", null, null); // 10%
        cart.applyCoupon(coupon, "SLEVA10");

        // Subtotal after discount = 200.00 * (1 - 0.10) = 180.00
        // Total VAT = 42.00 (VAT se počítá z původní ceny před slevou kupónem)
        // Expected Total Price Before Shipping = 180.00 + 42.00 = 222.00
        assertEquals(0, new BigDecimal("222.00").compareTo(cart.calculateTotalPriceBeforeShipping("CZK")), "Cena před dopravou s % slevou nesouhlasí");
        assertEquals(0, new BigDecimal("180.00").compareTo(cart.calculateTotalPriceWithoutTaxAfterDiscount("CZK")), "Cena bez DPH po slevě nesouhlasí");
    }

    @Test
    @DisplayName("Použití fixního kupónu CZK ovlivní cenu před dopravou")
    void calculateTotalPriceBeforeShipping_FixedCoupon_CZK() {
        CartItem item1 = createTestItem("P10-S", 10L, 2, "100.00", "4.00", "0.21"); // Sub: 200.00, VAT: 42.00
        cart.addItem(item1);
        Coupon coupon = createTestCoupon("SLEVA50", false, null, "50.00", "2.00"); // 50 CZK
        cart.applyCoupon(coupon, "SLEVA50");

        // Subtotal after discount = 200.00 - 50.00 = 150.00
        // Total VAT = 42.00
        // Expected Total Price Before Shipping = 150.00 + 42.00 = 192.00
        assertEquals(0, new BigDecimal("192.00").compareTo(cart.calculateTotalPriceBeforeShipping("CZK")), "Cena před dopravou s fixní slevou CZK nesouhlasí");
        assertEquals(0, new BigDecimal("150.00").compareTo(cart.calculateTotalPriceWithoutTaxAfterDiscount("CZK")), "Cena bez DPH po slevě nesouhlasí");
    }

    @Test
    @DisplayName("Použití fixního kupónu EUR ovlivní cenu před dopravou")
    void calculateTotalPriceBeforeShipping_FixedCoupon_EUR() {
        CartItem item1 = createTestItem("P10-S", 10L, 2, "100.00", "4.00", "0.21"); // Sub: 8.00 EUR, VAT: 1.68 EUR
        cart.addItem(item1);
        Coupon coupon = createTestCoupon("SLEVA2EUR", false, null, "50.00", "2.00"); // 2 EUR
        cart.applyCoupon(coupon, "SLEVA2EUR");

        // Subtotal after discount = 8.00 - 2.00 = 6.00
        // Total VAT = 1.68
        // Expected Total Price Before Shipping = 6.00 + 1.68 = 7.68
        assertEquals(0, new BigDecimal("7.68").compareTo(cart.calculateTotalPriceBeforeShipping("EUR")), "Cena před dopravou s fixní slevou EUR nesouhlasí");
        assertEquals(0, new BigDecimal("6.00").compareTo(cart.calculateTotalPriceWithoutTaxAfterDiscount("EUR")), "Cena bez DPH po slevě nesouhlasí");
    }

    @Test
    @DisplayName("Fixní kupón vyšší než mezisoučet sleví maximálně na nulu")
    void calculateDiscountAmount_FixedCouponHigherThanSubtotal() {
        CartItem item1 = createTestItem("P10-S", 10L, 1, "30.00", "1.20", "0.21"); // Sub: 30.00 CZK
        cart.addItem(item1);
        Coupon coupon = createTestCoupon("SLEVA50", false, null, "50.00", "2.00"); // 50 CZK
        cart.applyCoupon(coupon, "SLEVA50");

        // Očekávaná sleva je omezena mezisoučtem
        assertEquals(0, new BigDecimal("30.00").compareTo(cart.calculateDiscountAmount("CZK")), "Sleva by měla být omezena na mezisoučet");
        // Očekávaná cena po slevě (bez DPH) by měla být nula
        assertEquals(0, BigDecimal.ZERO.compareTo(cart.calculateTotalPriceWithoutTaxAfterDiscount("CZK")), "Cena bez DPH po slevě by měla být 0");
        // Očekávaná cena před dopravou (s DPH) = (30-30) + (30*0.21) = 0 + 6.30 = 6.30
        assertEquals(0, new BigDecimal("6.30").compareTo(cart.calculateTotalPriceBeforeShipping("CZK")), "Cena před dopravou s vysokou fixní slevou nesouhlasí");
    }

    @Test
    @DisplayName("Odebrání kupónu")
    void removeCoupon_ShouldClearCouponData() {
        CartItem item1 = createTestItem("P10-S", 10L, 1, "100.00", "4.00", "0.21");
        cart.addItem(item1);
        Coupon coupon = createTestCoupon("TEST", true, "5", null, null);
        cart.applyCoupon(coupon, "TEST");
        assertNotNull(cart.getAppliedCoupon(), "Kupón by měl být aplikován");
        assertNotNull(cart.getAppliedCouponCode(), "Kód kupónu by měl být uložen");
        // Ověříme, že sleva není 0
        assertTrue(cart.calculateDiscountAmount("CZK").compareTo(BigDecimal.ZERO) > 0, "Sleva by neměla být 0");

        cart.removeCoupon();

        assertNull(cart.getAppliedCoupon(), "Kupón by měl být null po odebrání");
        assertNull(cart.getAppliedCouponCode(), "Kód kupónu by měl být null po odebrání");
        // Ověříme, že sleva je nyní 0
        assertEquals(0, BigDecimal.ZERO.compareTo(cart.calculateDiscountAmount("CZK")), "Sleva by měla být 0 po odebrání kupónu");
    }

}