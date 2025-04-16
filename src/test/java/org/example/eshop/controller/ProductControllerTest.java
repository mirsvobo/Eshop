package org.example.eshop.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.eshop.admin.service.AddonsService;
import org.example.eshop.config.SecurityTestConfig;
import org.example.eshop.dto.CustomPriceRequestDto;
import org.example.eshop.model.*;
import org.example.eshop.service.CurrencyService;
import org.example.eshop.service.ProductService;
// OPRAVA: Import @NotNull z Jakarta EE namísto JetBrains (pro standardizaci)
// Pokud používáš Spring Boot 3+, preferuj jakarta.validation.constraints.NotNull
// import org.jetbrains.annotations.NotNull;
import jakarta.validation.constraints.NotNull; // <-- Použijeme Jakarta EE

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser; // Potřeba pro @WithMockUser
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@WithMockUser // Přidáno pro konzistenci, i když zde není striktně vyžadováno
@Import(SecurityTestConfig.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private ProductService productService;
    @MockBean private AddonsService addonsService; // Předpokládáme, že controller ho používá
    @Autowired private ObjectMapper objectMapper;
    @MockBean private CurrencyService currencyService;

    private Product standardProduct;
    private Product customProduct;
    private Product inactiveProduct;
    private TaxRate standardTaxRate;

    @BeforeEach
    void setUp() {
        // OPRAVA: Použití konstruktoru bez argumentů a setterů dle TaxRate.java
        standardTaxRate = new TaxRate();
        standardTaxRate.setId(1L);
        standardTaxRate.setName("Standard 21%");
        standardTaxRate.setRate(new BigDecimal("0.21"));
        standardTaxRate.setReverseCharge(false);


        standardProduct = new Product();
        standardProduct.setId(1L); standardProduct.setName("Standard Dřevník"); standardProduct.setSlug("standard-drevnik");
        standardProduct.setActive(true); standardProduct.setCustomisable(false);
        // OPRAVA: Používáme setAvailableTaxRates
        standardProduct.setAvailableTaxRates(new HashSet<>(Collections.singletonList(standardTaxRate)));
        standardProduct.setBasePriceCZK(new BigDecimal("1000.00")); standardProduct.setBasePriceEUR(new BigDecimal("40.00"));
        Image stdImg = new Image(); stdImg.setUrl("/std.jpg");
        standardProduct.setImages(Set.of(stdImg)); // Set s obrázkem
        Design stdDesign = new Design(); stdDesign.setId(1L);
        Glaze stdGlaze = new Glaze(); stdGlaze.setId(1L);
        RoofColor stdRoofColor = new RoofColor(); stdRoofColor.setId(1L);
        standardProduct.setAvailableDesigns(Set.of(stdDesign));
        standardProduct.setAvailableGlazes(Set.of(stdGlaze));
        standardProduct.setAvailableRoofColors(Set.of(stdRoofColor));

        customProduct = new Product();
        customProduct.setId(2L); customProduct.setName("Dřevník na míru"); customProduct.setSlug("drevnik-na-miru");
        customProduct.setActive(true); customProduct.setCustomisable(true);
        // OPRAVA: Používáme setAvailableTaxRates
        customProduct.setAvailableTaxRates(new HashSet<>(Collections.singletonList(standardTaxRate)));
        ProductConfigurator configurator = getProductConfigurator();
        configurator.setProduct(customProduct); // Důležité pro vazbu
        customProduct.setConfigurator(configurator);
        Addon cAddon = new Addon(); cAddon.setId(1L); cAddon.setActive(true); // Aktivní addon
        customProduct.setAvailableAddons(Set.of(cAddon));

        inactiveProduct = new Product();
        inactiveProduct.setId(3L); inactiveProduct.setName("Neaktivní Produkt"); inactiveProduct.setSlug("neaktivni-produkt");
        inactiveProduct.setActive(false);
        // OPRAVA: Používáme setAvailableTaxRates
        inactiveProduct.setAvailableTaxRates(new HashSet<>(Collections.singletonList(standardTaxRate)));

        lenient().when(currencyService.getSelectedCurrency()).thenReturn("CZK");

    }

    // OPRAVA: Návratový typ getProductConfigurator musí být @NotNull z Jakarta EE
    @NotNull
    private static ProductConfigurator getProductConfigurator() {
        ProductConfigurator configurator = new ProductConfigurator();
        configurator.setMinLength(new BigDecimal("100")); configurator.setMaxLength(new BigDecimal("500"));
        configurator.setMinWidth(new BigDecimal("50")); configurator.setMaxWidth(new BigDecimal("200"));
        configurator.setMinHeight(new BigDecimal("150")); configurator.setMaxHeight(new BigDecimal("300"));
        configurator.setPricePerCmHeightCZK(BigDecimal.TEN); configurator.setPricePerCmLengthCZK(BigDecimal.ONE); configurator.setPricePerCmDepthCZK(BigDecimal.ONE);
        configurator.setPricePerCmHeightEUR(BigDecimal.ONE); configurator.setPricePerCmLengthEUR(BigDecimal.ONE); configurator.setPricePerCmDepthEUR(BigDecimal.ONE);
        configurator.setDesignPriceCZK(BigDecimal.ZERO); configurator.setDesignPriceEUR(BigDecimal.ZERO);
        configurator.setDividerPricePerCmDepthCZK(BigDecimal.ZERO); configurator.setDividerPricePerCmDepthEUR(BigDecimal.ZERO);
        configurator.setGutterPriceCZK(BigDecimal.ZERO); configurator.setGutterPriceEUR(BigDecimal.ZERO);
        configurator.setShedPriceCZK(BigDecimal.ZERO); configurator.setShedPriceEUR(BigDecimal.ZERO);
        return configurator;
    }

    @Test
    @DisplayName("GET /produkty - Zobrazí stránku se seznamem aktivních produktů")
    void listProducts_ShouldReturnProductsView() throws Exception {
        Page<Product> productPage = new PageImpl<>(List.of(standardProduct), PageRequest.of(0, 9), 1);
        when(productService.getActiveProducts(any(Pageable.class))).thenReturn(productPage);
        // Mock pro výpočet ceny standardního produktu
        when(productService.calculateFinalProductPrice(eq(standardProduct), eq("CZK")))
                .thenReturn(Map.of("originalPrice", standardProduct.getBasePriceCZK(), "discountedPrice", null, "discountApplied", null));


        mockMvc.perform(MockMvcRequestBuilders.get("/produkty"))
                .andExpect(status().isOk())
                .andExpect(view().name("produkty"))
                .andExpect(model().attributeExists("productPage", "productPrices", "currentCurrency"))
                .andExpect(model().attribute("productPage", hasProperty("content", hasSize(1))))
                .andExpect(model().attribute("productPage", hasProperty("content", hasItem(
                        hasProperty("slug", is("standard-drevnik"))
                ))))
                .andExpect(model().attribute("productPrices", hasKey(standardProduct.getId()))); // Ověříme, že cena byla vypočtena

        verify(productService).getActiveProducts(any(Pageable.class));
        verify(productService).calculateFinalProductPrice(eq(standardProduct), eq("CZK")); // Ověříme výpočet ceny
    }

    @Test
    @DisplayName("GET /produkt/{slug} - Zobrazí detail standardního produktu")
    void productDetail_StandardProduct_ShouldReturnStandardDetailView() throws Exception {
        String slug = "standard-drevnik";
        when(productService.getActiveProductBySlug(slug)).thenReturn(Optional.of(standardProduct));
        // Mock pro výpočet ceny standardního produktu
        when(productService.calculateFinalProductPrice(eq(standardProduct), eq("CZK")))
                .thenReturn(Map.of("originalPrice", standardProduct.getBasePriceCZK(), "discountedPrice", null, "discountApplied", null));


        mockMvc.perform(MockMvcRequestBuilders.get("/produkt/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(view().name("produkt-detail-standard"))
                .andExpect(model().attributeExists("product", "cartItemDto", "availableDesigns", "availableGlazes", "availableRoofColors", "priceInfo", "availableTaxRates")) // Přidáno availableTaxRates
                .andExpect(model().attribute("product", hasProperty("slug", is(slug))))
                .andExpect(model().attribute("cartItemDto", hasProperty("productId", is(standardProduct.getId()))))
                .andExpect(model().attribute("cartItemDto", hasProperty("custom", is(false))))
                .andExpect(model().attribute("priceInfo", hasKey("originalPrice")))
                .andExpect(model().attribute("availableTaxRates", hasSize(1))) // Ověříme, že sazby jsou v modelu
                .andExpect(model().attribute("availableTaxRates", hasItem(hasProperty("id", is(standardTaxRate.getId())))));

        verify(productService).getActiveProductBySlug(slug);
        verify(productService).calculateFinalProductPrice(eq(standardProduct), eq("CZK")); // Ověříme výpočet ceny
    }

    @Test
    @DisplayName("GET /produkt/{slug} - Zobrazí detail custom produktu")
    void productDetail_CustomProduct_ShouldReturnCustomDetailView() throws Exception {
        String slug = "drevnik-na-miru";
        when(productService.getActiveProductBySlug(slug)).thenReturn(Optional.of(customProduct));
        // Mockování výpočtu počáteční ceny (bez design stringu)
        when(productService.calculateDynamicProductPrice(eq(customProduct), anyMap(), isNull(), eq(false), eq(false), eq(false), eq("CZK")))
                .thenReturn(new BigDecimal("5000.00"));
        when(productService.calculateDynamicProductPrice(eq(customProduct), anyMap(), isNull(), eq(false), eq(false), eq(false), eq("EUR")))
                .thenReturn(new BigDecimal("200.00"));

        mockMvc.perform(MockMvcRequestBuilders.get("/produkt/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(view().name("produkt-detail-custom"))
                // Zkontroluj klíče modelu
                .andExpect(model().attributeExists("product", "cartItemDto", "configurator", "availableAddons", "initialCustomPriceCZK", "initialCustomPriceEUR", "availableDesigns", "availableGlazes", "availableRoofColors", "availableTaxRates")) // Přidáno availableTaxRates
                .andExpect(model().attribute("product", hasProperty("slug", is(slug))))
                .andExpect(model().attribute("cartItemDto", hasProperty("productId", is(customProduct.getId()))))
                .andExpect(model().attribute("cartItemDto", hasProperty("custom", is(true))))
                .andExpect(model().attribute("configurator", notNullValue()))
                .andExpect(model().attribute("initialCustomPriceCZK", comparesEqualTo(new BigDecimal("5000.00"))))
                .andExpect(model().attribute("availableTaxRates", hasSize(1))) // Ověříme, že sazby jsou v modelu
                .andExpect(model().attribute("availableTaxRates", hasItem(hasProperty("id", is(standardTaxRate.getId())))))
                .andExpect(model().attribute("availableAddons", hasSize(1))); // Ověření addonů

        verify(productService).getActiveProductBySlug(slug);
        verify(productService).calculateDynamicProductPrice(eq(customProduct), anyMap(), isNull(), eq(false), eq(false), eq(false), eq("CZK"));
        verify(productService).calculateDynamicProductPrice(eq(customProduct), anyMap(), isNull(), eq(false), eq(false), eq(false), eq("EUR"));
    }

    @Test
    @DisplayName("GET /produkt/{slug} - Nenalezeno (404)")
    void productDetail_NotFound_ShouldReturn404() throws Exception {
        String slug = "neexistujici-produkt";
        when(productService.getActiveProductBySlug(slug)).thenReturn(Optional.empty());
        mockMvc.perform(MockMvcRequestBuilders.get("/produkt/{slug}", slug))
                .andExpect(status().isNotFound());
        verify(productService).getActiveProductBySlug(slug);
    }

    @Test
    @DisplayName("GET /produkt/{slug} - Neaktivní produkt (404)")
    void productDetail_Inactive_ShouldReturn404() throws Exception {
        String slug = inactiveProduct.getSlug();
        // getActiveProductBySlug by měl vrátit empty pro neaktivní produkt
        when(productService.getActiveProductBySlug(slug)).thenReturn(Optional.empty());
        mockMvc.perform(MockMvcRequestBuilders.get("/produkt/{slug}", slug))
                .andExpect(status().isNotFound());
        verify(productService).getActiveProductBySlug(slug);
    }

    @Test
    @DisplayName("POST /api/product/calculate-price - Úspěšný výpočet (bez custom design stringu)")
    void calculateCustomPrice_Success() throws Exception {
        Long productId = customProduct.getId();
        BigDecimal calculatedCZK = new BigDecimal("6543.21");
        BigDecimal calculatedEUR = new BigDecimal("261.73");

        // Vstupní DTO už neobsahuje customDesign
        CustomPriceRequestDto requestDto = new CustomPriceRequestDto();
        requestDto.setProductId(productId);
        requestDto.setCustomDimensions(Map.of("length", new BigDecimal("250"), "width", new BigDecimal("150"), "height", new BigDecimal("200")));
        requestDto.setCustomHasGutter(true);
        requestDto.setCustomHasDivider(false);
        requestDto.setCustomHasGardenShed(false);

        when(productService.getProductById(productId)).thenReturn(Optional.of(customProduct));
        // Volání service metody už je bez customDesign argumentu
        when(productService.calculateDynamicProductPrice(eq(customProduct), anyMap(), isNull(), eq(false), eq(true), eq(false), eq("CZK")))
                .thenReturn(calculatedCZK);
        when(productService.calculateDynamicProductPrice(eq(customProduct), anyMap(), isNull(), eq(false), eq(true), eq(false), eq("EUR")))
                .thenReturn(calculatedEUR);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/product/calculate-price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.priceCZK", comparesEqualTo(calculatedCZK))) // Použij comparesEqualTo pro BigDecimal
                .andExpect(jsonPath("$.priceEUR", comparesEqualTo(calculatedEUR)))
                .andExpect(jsonPath("$.errorMessage", is(nullValue())));

        verify(productService).getProductById(productId);
        verify(productService).calculateDynamicProductPrice(eq(customProduct), eq(requestDto.getCustomDimensions()), isNull(), eq(false), eq(true), eq(false), eq("CZK"));
        verify(productService).calculateDynamicProductPrice(eq(customProduct), eq(requestDto.getCustomDimensions()), isNull(), eq(false), eq(true), eq(false), eq("EUR"));
    }

    @Test
    @DisplayName("POST /api/product/calculate-price - Produkt nenalezen (404)")
    void calculateCustomPrice_ProductNotFound() throws Exception {
        Long nonExistentId = 999L;
        CustomPriceRequestDto requestDto = new CustomPriceRequestDto();
        requestDto.setProductId(nonExistentId);
        requestDto.setCustomDimensions(Map.of("length", new BigDecimal("200"), "width", new BigDecimal("100"), "height", new BigDecimal("180")));

        when(productService.getProductById(nonExistentId)).thenReturn(Optional.empty());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/product/calculate-price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto))
                )
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorMessage", containsString("Produkt nenalezen")));

        verify(productService).getProductById(nonExistentId);
        verify(productService, never()).calculateDynamicProductPrice(any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyString());
    }

    @Test
    @DisplayName("POST /api/product/calculate-price - Neplatná data (chybějící rozměry - 400 Bad Request)")
    void calculateCustomPrice_InvalidData() throws Exception {
        Long productId = customProduct.getId();
        CustomPriceRequestDto requestDto = new CustomPriceRequestDto();
        requestDto.setProductId(productId);
        requestDto.setCustomDimensions(null); // Neplatná data

        mockMvc.perform(MockMvcRequestBuilders.post("/api/product/calculate-price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto))
                )
                .andExpect(status().isBadRequest()); // Očekáváme chybu validace vstupu
    }


    @Test
    @DisplayName("POST /api/product/calculate-price - Neočekávaná chyba serveru (500)")
    void calculateCustomPrice_ServerError() throws Exception {
        Long productId = customProduct.getId();
        CustomPriceRequestDto requestDto = new CustomPriceRequestDto();
        requestDto.setProductId(productId);
        requestDto.setCustomDimensions(Map.of("length", new BigDecimal("200"), "width", new BigDecimal("100"), "height", new BigDecimal("180")));

        when(productService.getProductById(productId)).thenReturn(Optional.of(customProduct));
        when(productService.calculateDynamicProductPrice(any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyString()))
                .thenThrow(new RuntimeException("Unexpected database error"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/product/calculate-price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto))
                )
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorMessage", containsString("neočekávané chybě")));

        verify(productService).getProductById(productId);
        verify(productService).calculateDynamicProductPrice(any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyString());
    }
}