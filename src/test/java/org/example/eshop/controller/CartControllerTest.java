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
// OPRAVA: Import TaxRateRepository
import org.example.eshop.repository.TaxRateRepository;
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
    // OPRAVA: Mockujeme i TaxRateRepository
    @MockBean private TaxRateRepository taxRateRepository;
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
        // OPRAVA: Konstruktor TaxRate dle modelu
        testTaxRate = new TaxRate();
        testTaxRate.setId(1L);
        testTaxRate.setName("21%");
        testTaxRate.setRate(new BigDecimal("0.21"));
        testTaxRate.setReverseCharge(false);

        testDesign = new Design(); testDesign.setId(10L); testDesign.setName("Test Design");
        testGlaze = new Glaze(); testGlaze.setId(20L); testGlaze.setName("Test Glaze"); testGlaze.setPriceSurchargeCZK(BigDecimal.ZERO); testGlaze.setPriceSurchargeEUR(BigDecimal.ZERO);
        testRoofColor = new RoofColor(); testRoofColor.setId(30L); testRoofColor.setName("Test RoofColor"); testRoofColor.setPriceSurchargeCZK(BigDecimal.ZERO); testRoofColor.setPriceSurchargeEUR(BigDecimal.ZERO);

        testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setName("Test Produkt");
        testProduct.setSlug("test-produkt");
        testProduct.setActive(true);
        testProduct.setCustomisable(false);
        // OPRAVA: Používáme setAvailableTaxRates
        testProduct.setAvailableTaxRates(new HashSet<>(Collections.singletonList(testTaxRate)));
        testProduct.setBasePriceCZK(new BigDecimal("100.00"));
        testProduct.setBasePriceEUR(new BigDecimal("4.00"));
        Image productImage = new Image(); productImage.setUrl("/test.jpg");
        testProduct.setImages(Set.of(productImage)); // Set s jedním obrázkem
        testProduct.setAvailableDesigns(Set.of(testDesign));
        testProduct.setAvailableGlazes(Set.of(testGlaze));
        testProduct.setAvailableRoofColors(Set.of(testRoofColor));

        inactiveProduct = new Product();
        inactiveProduct.setId(3L);
        inactiveProduct.setName("Neaktivní Produkt");
        inactiveProduct.setSlug("neaktivni-produkt");
        inactiveProduct.setActive(false); // Důležité - je neaktivní
        // OPRAVA: Používáme setAvailableTaxRates
        inactiveProduct.setAvailableTaxRates(new HashSet<>(Collections.singletonList(testTaxRate)));

        testCartItem = new CartItem();
        // ID se generuje, nastavíme ho až v testu addToCart, pokud je potřeba
        testCartItem.setCartItemId("P1-T1-S-D10-G20-RC30"); // Příklad ID s TaxRate
        testCartItem.setProductId(1L);
        testCartItem.setProductName("Test Produkt");
        testCartItem.setQuantity(1);
        testCartItem.setUnitPriceCZK(new BigDecimal("100.00"));
        testCartItem.setUnitPriceEUR(new BigDecimal("4.00"));
        // OPRAVA: Nastavení nových polí pro DPH
        testCartItem.setSelectedTaxRateId(testTaxRate.getId());
        testCartItem.setSelectedTaxRateValue(testTaxRate.getRate());
        testCartItem.setSelectedIsReverseCharge(testTaxRate.isReverseCharge());

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
        // OPRAVA: Mockování metody, která používá nová pole v CartItem
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
        mockMvc.perform(MockMvcRequestBuilders.get("/kosik"))
                .andExpect(status().isOk())
                .andExpect(view().name("kosik"))
                .andExpect(model().attributeExists("cart", "currentCurrency", "currencySymbol", "subtotal", "totalVat", "vatBreakdown", "totalPriceBeforeShipping"))
                .andExpect(model().attribute("cart", is(sessionCart)))
                .andExpect(model().attribute("currentCurrency", is("CZK")))
                .andExpect(model().attribute("subtotal", comparesEqualTo(new BigDecimal("100.00"))));

        verify(currencyService, times(2)).getSelectedCurrency();
        verify(sessionCart, atLeastOnce()).hasItems();
        verify(sessionCart).calculateSubtotal("CZK");
        verify(sessionCart).calculateDiscountAmount("CZK");
        verify(sessionCart).calculateTotalVatAmount("CZK"); // Ověření volání upravené metody
        verify(sessionCart).calculateVatBreakdown("CZK");   // Ověření volání upravené metody
        verify(sessionCart).calculateTotalPriceBeforeShipping("CZK");
        verify(sessionCart).calculateTotalPriceWithoutTaxAfterDiscount("CZK");
        verify(sessionCart).getAppliedCoupon();
    }

    @Test
    @DisplayName("GET /kosik - Zobrazí prázdný košík")
    void viewCart_Empty_ShouldReturnCartViewInfo() throws Exception {
        when(sessionCart.hasItems()).thenReturn(false);
        when(sessionCart.getItemsList()).thenReturn(Collections.emptyList());
        when(sessionCart.getItemCount()).thenReturn(0);
        when(currencyService.getSelectedCurrency()).thenReturn("CZK");

        mockMvc.perform(MockMvcRequestBuilders.get("/kosik"))
                .andExpect(status().isOk())
                .andExpect(view().name("kosik"))
                .andExpect(model().attribute("cart", is(sessionCart)))
                .andExpect(content().string(containsString("Váš košík je prázdný")));

        verify(sessionCart, atLeastOnce()).hasItems();
        verify(currencyService, times(2)).getSelectedCurrency();
        verify(sessionCart, never()).calculateSubtotal(anyString());
    }


    @Test
    @DisplayName("POST /kosik/pridat - Úspěšně přidá standardní produkt")
    void addToCart_StandardProduct_Success() throws Exception {
        long productId = 1L;
        int quantity = 2;
        long designId = 10L;
        long glazeId = 20L;
        long roofColorId = 30L;
        long taxRateId = testTaxRate.getId(); // ID sazby z formuláře

        when(productService.getProductById(productId)).thenReturn(Optional.of(testProduct)); // Produkt má testTaxRate v availableTaxRates
        when(designRepository.findById(designId)).thenReturn(Optional.of(testDesign));
        when(glazeRepository.findById(glazeId)).thenReturn(Optional.of(testGlaze));
        when(roofColorRepository.findById(roofColorId)).thenReturn(Optional.of(testRoofColor));
        // OPRAVA: Mockujeme TaxRateRepository pro načtení sazby v controlleru
        when(taxRateRepository.findById(taxRateId)).thenReturn(Optional.of(testTaxRate));
        doNothing().when(sessionCart).addItem(any(CartItem.class));

        mockMvc.perform(MockMvcRequestBuilders.post("/kosik/pridat")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("productId", String.valueOf(productId))
                                .param("quantity", String.valueOf(quantity))
                                .param("custom", "false")
                                .param("selectedDesignId", String.valueOf(designId))
                                .param("selectedGlazeId", String.valueOf(glazeId))
                                .param("selectedRoofColorId", String.valueOf(roofColorId))
                                // OPRAVA: Posíláme ID sazby daně
                                .param("selectedTaxRateId", String.valueOf(taxRateId))
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
        // OPRAVA: Ověření nových polí pro DPH
        assertNotNull(addedItem.getSelectedTaxRateId());
        assertNotNull(addedItem.getSelectedTaxRateValue());
        assertEquals(taxRateId, addedItem.getSelectedTaxRateId());
        assertEquals(0, testTaxRate.getRate().compareTo(addedItem.getSelectedTaxRateValue())); // Porovnání BigDecimal
        assertNotNull(addedItem.getCartItemId()); // ID by mělo být vygenerováno

        // Ověříme volání TaxRateRepository
        verify(taxRateRepository).findById(taxRateId);
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
                                .param("selectedTaxRateId", "1") // Posíláme ID, i když produkt neexistuje
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().is3xxRedirection())
                // Redirect by měl být spíše na /produkty nebo "/"
                .andExpect(redirectedUrl("/produkty"))
                .andExpect(flash().attributeExists("cartError"))
                .andExpect(flash().attribute("cartError", containsString("Produkt nenalezen")));

        verify(sessionCart, never()).addItem(any());
        verify(taxRateRepository, never()).findById(anyLong()); // Sazba se nehledá, pokud není produkt
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
                                .param("selectedDesignId", "10")
                                .param("selectedGlazeId", "20")
                                .param("selectedRoofColorId", "30")
                                .param("selectedTaxRateId", "1") // Posíláme ID
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/produkt/" + inactiveProduct.getSlug()))
                .andExpect(flash().attributeExists("cartError"))
                .andExpect(flash().attribute("cartError", containsString("není momentálně dostupný")));

        verify(sessionCart, never()).addItem(any());
        verify(taxRateRepository, never()).findById(anyLong()); // Sazba se nehledá
    }

    @Test
    @DisplayName("POST /kosik/pridat - Chyba (nepovolená sazba DPH pro produkt)")
    void addToCart_TaxRateNotAllowed_ShouldRedirectWithError() throws Exception {
        long productId = 1L;
        int quantity = 1;
        long taxRateId = 99L; // Neexistující nebo nepovolené ID sazby
        TaxRate disallowedTaxRate = new TaxRate(); disallowedTaxRate.setId(taxRateId); disallowedTaxRate.setName("Disallowed"); disallowedTaxRate.setRate(BigDecimal.TEN);

        when(productService.getProductById(productId)).thenReturn(Optional.of(testProduct)); // testProduct má jen testTaxRate(ID 1) povolenou
        when(taxRateRepository.findById(taxRateId)).thenReturn(Optional.of(disallowedTaxRate)); // Sazba existuje, ale není v testProduct.availableTaxRates

        mockMvc.perform(MockMvcRequestBuilders.post("/kosik/pridat")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("productId", String.valueOf(productId))
                                .param("quantity", String.valueOf(quantity))
                                .param("custom", "false")
                                .param("selectedDesignId", "10")
                                .param("selectedGlazeId", "20")
                                .param("selectedRoofColorId", "30")
                                .param("selectedTaxRateId", String.valueOf(taxRateId))
                        // .with(csrf())
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/produkt/" + testProduct.getSlug()))
                .andExpect(flash().attributeExists("cartError"))
                .andExpect(flash().attribute("cartError", containsString("není pro tento produkt povolena")));

        verify(sessionCart, never()).addItem(any());
        verify(taxRateRepository).findById(taxRateId);
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
        when(currencyService.getSelectedCurrency()).thenReturn("CZK");

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
        verify(currencyService, times(2)).getSelectedCurrency();
    }

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
        when(currencyService.getSelectedCurrency()).thenReturn("CZK");

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
        verify(currencyService, times(2)).getSelectedCurrency();
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