// Soubor: src/test/java/org/example/eshop/controller/CartControllerTest.java

package org.example.eshop.controller;

import org.example.eshop.admin.service.AddonsService;
import org.example.eshop.config.PriceConstants;
import org.example.eshop.config.SecurityTestConfig; // Import sdílené konfigurace
import org.example.eshop.dto.AddonDto;
import org.example.eshop.dto.CartItemDto;
import org.example.eshop.model.*;
import org.example.eshop.repository.DesignRepository;
import org.example.eshop.repository.GlazeRepository;
import org.example.eshop.repository.RoofColorRepository;
import org.example.eshop.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import; // Import pro @Import
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser; // Pro test kupónu
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.math.RoundingMode; // Import pro zaokrouhlení
import java.time.LocalDateTime;
import java.util.*;

// Import specific Hamcrest matchers
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.comparesEqualTo; // Pro BigDecimal

// Import specific Mockito methods and ArgumentMatchers
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*; // Můžeme použít hvězdičku zde, nebo specifické importy

// import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf; // Odstraněno
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartController.class)
@Import(SecurityTestConfig.class) // Aplikace sdílené konfigurace
class CartControllerTest implements PriceConstants { // Implementace pro konstanty měn

    @Autowired
    private MockMvc mockMvc;

    @MockBean private Cart sessionCart; // Mockujeme session bean
    @MockBean private ProductService productService;
    @MockBean private AddonsService addonsService;
    @MockBean private CouponService couponService;
    @MockBean private CustomerService customerService;
    @MockBean private DesignRepository designRepository;
    @MockBean private GlazeRepository glazeRepository;
    @MockBean private RoofColorRepository roofColorRepository;
    @MockBean private TaxRateService taxRateService;
    @MockBean private CurrencyService currencyService; // Mockujeme i CurrencyService

    // --- Testovací data (deklarace) ---
    private Product testProduct;
    private Product inactiveProduct;
    private TaxRate testTaxRate;
    private Design testDesign;
    private Glaze testGlaze;
    private RoofColor testRoofColor;
    private CartItem testCartItem;
    private Coupon testCoupon;
    private Coupon inactiveCoupon;

    @BeforeEach
    void setUp() {
        // --- Inicializace testovacích dat ---
        testTaxRate = new TaxRate(1L, "21%", new BigDecimal("0.21"), false, null);
        testDesign = new Design(); testDesign.setId(10L); testDesign.setName("Test Design");
        testGlaze = new Glaze(); testGlaze.setId(20L); testGlaze.setName("Test Glaze"); testGlaze.setPriceSurchargeCZK(BigDecimal.ZERO); testGlaze.setPriceSurchargeEUR(BigDecimal.ZERO);
        testRoofColor = new RoofColor(); testRoofColor.setId(30L); testRoofColor.setName("Test RoofColor"); testRoofColor.setPriceSurchargeCZK(BigDecimal.ZERO); testRoofColor.setPriceSurchargeEUR(BigDecimal.ZERO);

        testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setName("Test Produkt");
        testProduct.setSlug("test-produkt");
        testProduct.setActive(true);
        testProduct.setCustomisable(false);
        testProduct.setTaxRate(testTaxRate);
        testProduct.setBasePriceCZK(new BigDecimal("100.00"));
        testProduct.setBasePriceEUR(new BigDecimal("4.00"));
        testProduct.setImages(Set.of(new Image()));
        testProduct.setAvailableDesigns(Set.of(testDesign));
        testProduct.setAvailableGlazes(Set.of(testGlaze));
        testProduct.setAvailableRoofColors(Set.of(testRoofColor));

        inactiveProduct = new Product();
        inactiveProduct.setId(3L);
        inactiveProduct.setName("Neaktivní Produkt");
        inactiveProduct.setSlug("neaktivni-produkt");
        inactiveProduct.setActive(false); // Důležité - je neaktivní
        inactiveProduct.setTaxRate(testTaxRate);

        testCartItem = new CartItem();
        testCartItem.setCartItemId("P1-S-D10-G20-RC30");
        testCartItem.setProductId(1L);
        testCartItem.setProductName("Test Produkt");
        testCartItem.setQuantity(1);
        testCartItem.setUnitPriceCZK(new BigDecimal("100.00"));
        testCartItem.setUnitPriceEUR(new BigDecimal("4.00"));
        testCartItem.setTaxRatePercent(new BigDecimal("0.21"));

        testCoupon = new Coupon();
        testCoupon.setId(5L);
        testCoupon.setCode("TEST10");
        testCoupon.setActive(true);
        testCoupon.setPercentage(true);
        testCoupon.setValue(new BigDecimal("10.00"));
        testCoupon.setStartDate(LocalDateTime.now().minusDays(1));
        testCoupon.setExpirationDate(LocalDateTime.now().plusDays(1));

        inactiveCoupon = new Coupon();
        inactiveCoupon.setId(6L);
        inactiveCoupon.setCode("INACTIVE");
        inactiveCoupon.setActive(false); // Neaktivní
        inactiveCoupon.setPercentage(true);
        inactiveCoupon.setValue(new BigDecimal("5.00"));
        inactiveCoupon.setStartDate(LocalDateTime.now().minusDays(1));
        inactiveCoupon.setExpirationDate(LocalDateTime.now().plusDays(1));

        // Mockování chování sessionCart pro test zobrazení
        lenient().when(sessionCart.getItemsList()).thenReturn(List.of(testCartItem));
        lenient().when(sessionCart.hasItems()).thenReturn(true);
        lenient().when(sessionCart.getItemCount()).thenReturn(1);
        lenient().when(sessionCart.calculateSubtotal(anyString())).thenReturn(new BigDecimal("100.00"));
        lenient().when(sessionCart.calculateDiscountAmount(anyString())).thenReturn(BigDecimal.ZERO);
        lenient().when(sessionCart.calculateTotalVatAmount(anyString())).thenReturn(new BigDecimal("21.00"));
        lenient().when(sessionCart.calculateVatBreakdown(anyString())).thenReturn(Map.of(new BigDecimal("0.21").setScale(2, RoundingMode.HALF_UP), new BigDecimal("21.00")));
        lenient().when(sessionCart.calculateTotalPriceBeforeShipping(anyString())).thenReturn(new BigDecimal("121.00"));
        lenient().when(sessionCart.calculateTotalPriceWithoutTaxAfterDiscount(anyString())).thenReturn(new BigDecimal("100.00"));
        lenient().when(sessionCart.getAppliedCoupon()).thenReturn(null); // Defaultně žádný kupón

        // Mockování CurrencyService
        lenient().when(currencyService.getSelectedCurrency()).thenReturn("CZK"); // Defaultně CZK
    }

    @Test
    @DisplayName("GET /kosik - Zobrazí košík s položkami")
    void viewCart_WithItems_ShouldReturnCartView() throws Exception {
        // Mockování je nastaveno v @BeforeEach a lenient()

        mockMvc.perform(MockMvcRequestBuilders.get("/kosik"))
                .andExpect(status().isOk())
                .andExpect(view().name("kosik"))
                .andExpect(model().attributeExists("cart", "currentCurrency", "currencySymbol", "subtotal", "totalVat", "vatBreakdown", "totalPriceBeforeShipping"))
                .andExpect(model().attribute("cart", is(sessionCart)))
                .andExpect(model().attribute("currentCurrency", is("CZK")))
                .andExpect(model().attribute("subtotal", comparesEqualTo(new BigDecimal("100.00"))));

        // Upravené ověření - očekáváme 2 volání CurrencyService (Controller + Advice)
        verify(currencyService, times(2)).getSelectedCurrency(); // <-- OPRAVENO
        // Ověření ostatních volání (mělo by odpovídat logice v controlleru)
        // Počet volání hasItems závisí na implementaci controlleru A šablony, přizpůsobit dle potřeby
        verify(sessionCart, atLeastOnce()).hasItems(); // Kontrolujeme, že se volá alespoň jednou
        verify(sessionCart).calculateSubtotal("CZK");
        verify(sessionCart).calculateDiscountAmount("CZK");
        verify(sessionCart).calculateTotalVatAmount("CZK");
        verify(sessionCart).calculateVatBreakdown("CZK");
        verify(sessionCart).calculateTotalPriceBeforeShipping("CZK");
        verify(sessionCart).calculateTotalPriceWithoutTaxAfterDiscount("CZK");
        // verify(sessionCart).getItemCount(); // Záleží, zda je voláno přímo v controlleru
        verify(sessionCart).getAppliedCoupon(); // Voláno v controlleru
    }

    @Test
    @DisplayName("GET /kosik - Zobrazí prázdný košík")
    void viewCart_Empty_ShouldReturnCartViewInfo() throws Exception {
        when(sessionCart.hasItems()).thenReturn(false);
        when(sessionCart.getItemsList()).thenReturn(Collections.emptyList());
        when(sessionCart.getItemCount()).thenReturn(0);
        when(currencyService.getSelectedCurrency()).thenReturn("CZK"); // Mock currency service

        mockMvc.perform(MockMvcRequestBuilders.get("/kosik"))
                .andExpect(status().isOk())
                .andExpect(view().name("kosik"))
                .andExpect(model().attribute("cart", is(sessionCart)))
                .andExpect(content().string(containsString("Váš košík je prázdný")));

        // Upravené ověření - očekáváme počet volání hasItems dle implementace
        // Pokud controller volá hasItems() 1x a šablona také (např. v hlavním if), bude to 2x
        // Pokud šablona volá vícekrát, bude to více. Zvolíme atLeastOnce() pro flexibilitu.
        verify(sessionCart, atLeastOnce()).hasItems(); // <-- ZMĚNĚNO na atLeastOnce()
        // Ověříme i volání currency service (je volána i pro prázdný košík)
        verify(currencyService, times(2)).getSelectedCurrency(); // Controller + Advice
        verify(sessionCart, never()).calculateSubtotal(anyString()); // Subtotal se nepočítá
    }


    @Test
    @DisplayName("POST /kosik/pridat - Úspěšně přidá standardní produkt")
    void addToCart_StandardProduct_Success() throws Exception {
        long productId = 1L;
        int quantity = 2;
        long designId = 10L;
        long glazeId = 20L;
        long roofColorId = 30L;

        when(productService.getProductById(productId)).thenReturn(Optional.of(testProduct));
        when(designRepository.findById(designId)).thenReturn(Optional.of(testDesign));
        when(glazeRepository.findById(glazeId)).thenReturn(Optional.of(testGlaze));
        when(roofColorRepository.findById(roofColorId)).thenReturn(Optional.of(testRoofColor));
        doNothing().when(sessionCart).addItem(any(CartItem.class));

        mockMvc.perform(MockMvcRequestBuilders.post("/kosik/pridat")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("productId", String.valueOf(productId))
                                .param("quantity", String.valueOf(quantity))
                                .param("custom", "false")
                                .param("selectedDesignId", String.valueOf(designId))
                                .param("selectedGlazeId", String.valueOf(glazeId))
                                .param("selectedRoofColorId", String.valueOf(roofColorId))
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kosik"))
                .andExpect(flash().attributeExists("cartSuccess"));

        ArgumentCaptor<CartItem> cartItemCaptor = ArgumentCaptor.forClass(CartItem.class);
        verify(sessionCart).addItem(cartItemCaptor.capture());

        CartItem addedItem = cartItemCaptor.getValue();
        assertEquals(productId, addedItem.getProductId());
        assertEquals(quantity, addedItem.getQuantity());
        assertFalse(addedItem.isCustom());
        assertEquals(designId, addedItem.getSelectedDesignId());
        assertEquals(glazeId, addedItem.getSelectedGlazeId());
        assertEquals(roofColorId, addedItem.getSelectedRoofColorId());
        assertNotNull(addedItem.getUnitPriceCZK());
        assertNotNull(addedItem.getTaxRatePercent());
        assertNotNull(addedItem.getCartItemId());
    }

    @Test
    @DisplayName("POST /kosik/pridat - Chyba (produkt nenalezen)")
    void addToCart_ProductNotFound_ShouldRedirectWithError() throws Exception {
        long nonExistentId = 99L;
        when(productService.getProductById(nonExistentId)).thenReturn(Optional.empty());

        mockMvc.perform(MockMvcRequestBuilders.post("/kosik/pridat")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("productId", String.valueOf(nonExistentId))
                                .param("quantity", "1")
                                .param("custom", "false")
                                .param("selectedDesignId", "10")
                                .param("selectedGlazeId", "20")
                                .param("selectedRoofColorId", "30")
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/produkty")) // Nebo jiná URL podle logiky controlleru
                .andExpect(flash().attributeExists("cartError"))
                .andExpect(flash().attribute("cartError", containsString("Produkt nenalezen")));

        verify(sessionCart, never()).addItem(any());
    }

    @Test
    @DisplayName("POST /kosik/pridat - Chyba (neaktivní produkt)")
    void addToCart_InactiveProduct_ShouldRedirectWithError() throws Exception {
        long inactiveProductId = 3L;
        when(productService.getProductById(inactiveProductId)).thenReturn(Optional.of(inactiveProduct));

        mockMvc.perform(MockMvcRequestBuilders.post("/kosik/pridat")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("productId", String.valueOf(inactiveProductId))
                                .param("quantity", "1")
                                .param("custom", "false")
                                .param("selectedDesignId", "10") // Placeholder IDs
                                .param("selectedGlazeId", "20")
                                .param("selectedRoofColorId", "30")
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/produkt/" + inactiveProduct.getSlug()))
                .andExpect(flash().attributeExists("cartError"))
                .andExpect(flash().attribute("cartError", containsString("není momentálně dostupný")));

        verify(sessionCart, never()).addItem(any());
    }

    @Test
    @DisplayName("POST /kosik/aktualizovat - Úspěšně aktualizuje množství")
    void updateQuantity_Success() throws Exception {
        String cartItemId = testCartItem.getCartItemId();
        int newQuantity = 3;
        doNothing().when(sessionCart).updateQuantity(cartItemId, newQuantity);

        mockMvc.perform(MockMvcRequestBuilders.post("/kosik/aktualizovat")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("cartItemId", cartItemId)
                                .param("quantity", String.valueOf(newQuantity))
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kosik"))
                .andExpect(flash().attributeExists("cartSuccess"));

        verify(sessionCart).updateQuantity(cartItemId, newQuantity);
    }

    @Test
    @DisplayName("POST /kosik/odebrat - Úspěšně odebere položku")
    void removeItem_Success() throws Exception {
        String cartItemId = testCartItem.getCartItemId();
        doNothing().when(sessionCart).removeItem(cartItemId);

        mockMvc.perform(MockMvcRequestBuilders.post("/kosik/odebrat")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("cartItemId", cartItemId)
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kosik"))
                .andExpect(flash().attributeExists("cartSuccess"));

        verify(sessionCart).removeItem(cartItemId);
    }

    // Soubor: src/test/java/org/example/eshop/controller/CartControllerTest.java
// Metoda: applyCoupon_ValidCoupon_Success

    @Test
    @DisplayName("POST /kosik/pouzit-kupon - Úspěšně aplikuje platný kupón")
    @WithMockUser // Potřeba pro Principal
    void applyCoupon_ValidCoupon_Success() throws Exception {
        String couponCode = "TEST10";
        BigDecimal subtotal = new BigDecimal("100.00");
        when(couponService.findByCode(couponCode)).thenReturn(Optional.of(testCoupon));
        when(couponService.isCouponGenerallyValid(testCoupon)).thenReturn(true);
        when(couponService.checkMinimumOrderValue(eq(testCoupon), any(BigDecimal.class), anyString())).thenReturn(true);
        when(couponService.checkCustomerUsageLimit(any(), eq(testCoupon))).thenReturn(true);
        when(sessionCart.calculateSubtotal(anyString())).thenReturn(subtotal);
        doNothing().when(sessionCart).applyCoupon(testCoupon, couponCode);
        when(customerService.getCustomerByEmail(anyString())).thenReturn(Optional.of(new Customer()));
        when(currencyService.getSelectedCurrency()).thenReturn("CZK"); // Mock currency

        mockMvc.perform(MockMvcRequestBuilders.post("/kosik/pouzit-kupon")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("couponCode", couponCode)
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kosik"))
                .andExpect(flash().attributeExists("couponSuccess"));

        verify(couponService).findByCode(couponCode);
        verify(couponService).isCouponGenerallyValid(testCoupon);
        verify(couponService).checkMinimumOrderValue(eq(testCoupon), eq(subtotal), eq("CZK"));
        verify(couponService).checkCustomerUsageLimit(any(), eq(testCoupon));
        verify(sessionCart).applyCoupon(testCoupon, couponCode);
        // Ověření volání měny - nyní očekáváme 2 volání
        verify(currencyService, times(2)).getSelectedCurrency(); // <-- OPRAVENO
    }

// Metoda: applyCoupon_MinOrderValueNotMet_ShouldShowMessage

    @Test
    @DisplayName("POST /kosik/pouzit-kupon - Chyba (nesplněna minimální hodnota)")
    void applyCoupon_MinOrderValueNotMet_ShouldShowMessage() throws Exception {
        String couponCode = "MIN500CZK";
        Coupon minCoupon = new Coupon();
        minCoupon.setCode(couponCode);
        minCoupon.setActive(true);
        minCoupon.setStartDate(LocalDateTime.now().minusDays(1));
        minCoupon.setExpirationDate(LocalDateTime.now().plusDays(1));
        minCoupon.setMinimumOrderValueCZK(new BigDecimal("500.00"));

        BigDecimal subtotal = new BigDecimal("400.00");

        when(couponService.findByCode(couponCode)).thenReturn(Optional.of(minCoupon));
        when(couponService.isCouponGenerallyValid(minCoupon)).thenReturn(true);
        when(couponService.checkMinimumOrderValue(eq(minCoupon), eq(subtotal), eq("CZK"))).thenReturn(false);
        when(couponService.getMinimumValueString(minCoupon, "CZK")).thenReturn("500,00 Kč");
        when(sessionCart.calculateSubtotal("CZK")).thenReturn(subtotal);
        doNothing().when(sessionCart).setAttemptedCouponCode(couponCode);
        when(currencyService.getSelectedCurrency()).thenReturn("CZK"); // Mock currency

        mockMvc.perform(MockMvcRequestBuilders.post("/kosik/pouzit-kupon")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("couponCode", couponCode)
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kosik"))
                .andExpect(flash().attributeExists("couponMessage"))
                .andExpect(flash().attribute("couponMessage", containsString("minimální hodnota objednávky 500,00 Kč")));

        verify(couponService).findByCode(couponCode);
        verify(couponService).isCouponGenerallyValid(minCoupon);
        verify(couponService).checkMinimumOrderValue(eq(minCoupon), eq(subtotal), eq("CZK"));
        verify(sessionCart, never()).applyCoupon(any(), any());
        verify(sessionCart).setAttemptedCouponCode(couponCode);
        // Ověření volání měny - nyní očekáváme 2 volání
        verify(currencyService, times(2)).getSelectedCurrency(); // <-- OPRAVENO
    }

    @Test
    @DisplayName("POST /kosik/pouzit-kupon - Chyba (kupón nenalezen)")
    void applyCoupon_NotFound_ShouldShowMessage() throws Exception {
        String wrongCode = "NIC";
        when(couponService.findByCode(wrongCode)).thenReturn(Optional.empty());
        doNothing().when(sessionCart).setAttemptedCouponCode(wrongCode);

        mockMvc.perform(MockMvcRequestBuilders.post("/kosik/pouzit-kupon")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("couponCode", wrongCode)
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kosik"))
                .andExpect(flash().attributeExists("couponMessage"))
                .andExpect(flash().attribute("couponMessage", containsString("neexistuje")));

        verify(couponService).findByCode(wrongCode);
        verify(sessionCart, never()).applyCoupon(any(), any());
        verify(sessionCart).setAttemptedCouponCode(wrongCode);
    }


    @Test
    @DisplayName("POST /kosik/odebrat-kupon - Úspěšně odebere kupón")
    void removeCoupon_Success() throws Exception {
        doNothing().when(sessionCart).removeCoupon();

        mockMvc.perform(MockMvcRequestBuilders.post("/kosik/odebrat-kupon")
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kosik"))
                .andExpect(flash().attributeExists("cartSuccess"));

        verify(sessionCart).removeCoupon();
    }
}