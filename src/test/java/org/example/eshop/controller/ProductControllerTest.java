// Soubor: src/test/java/org/example/eshop/controller/ProductControllerTest.java
package org.example.eshop.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.eshop.config.SecurityTestConfig;
import org.example.eshop.dto.CustomPriceRequestDto;
import org.example.eshop.dto.CustomPriceResponseDto;
import org.example.eshop.model.*;
import org.example.eshop.service.AddonsService;
import org.example.eshop.service.CurrencyService;
import org.example.eshop.service.ProductService;
import org.jetbrains.annotations.NotNull;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.server.ResponseStatusException;

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
@Import(SecurityTestConfig.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private ProductService productService;
    @MockBean private AddonsService addonsService;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private CurrencyService currencyService;

    private Product standardProduct;
    private Product customProduct;
    private Product inactiveProduct;
    private TaxRate standardTaxRate;

    @BeforeEach
    void setUp() {
        // ... (setUp zůstává stejný) ...
        standardTaxRate = new TaxRate(1L, "Standard 21%", new BigDecimal("0.21"), false, null);

        standardProduct = new Product();
        standardProduct.setId(1L);
        standardProduct.setName("Standard Dřevník");
        standardProduct.setSlug("standard-drevnik");
        standardProduct.setActive(true);
        standardProduct.setCustomisable(false);
        standardProduct.setTaxRate(standardTaxRate);
        standardProduct.setBasePriceCZK(new BigDecimal("1000.00"));
        standardProduct.setBasePriceEUR(new BigDecimal("40.00"));
        standardProduct.setImages(List.of(new Image()));
        standardProduct.setAvailableDesigns(Set.of(new Design()));
        standardProduct.setAvailableGlazes(Set.of(new Glaze()));
        standardProduct.setAvailableRoofColors(Set.of(new RoofColor()));

        customProduct = new Product();
        customProduct.setId(2L);
        customProduct.setName("Dřevník na míru");
        customProduct.setSlug("drevnik-na-miru");
        customProduct.setActive(true);
        customProduct.setCustomisable(true);
        customProduct.setTaxRate(standardTaxRate);
        ProductConfigurator configurator = getProductConfigurator();
        customProduct.setConfigurator(configurator);
        customProduct.setAvailableAddons(Set.of(new Addon()));

        inactiveProduct = new Product();
        inactiveProduct.setId(3L);
        inactiveProduct.setName("Neaktivní Produkt");
        inactiveProduct.setSlug("neaktivni-produkt");
        inactiveProduct.setActive(false);
        inactiveProduct.setTaxRate(standardTaxRate);

        lenient().when(currencyService.getSelectedCurrency()).thenReturn("CZK");
    }

    @NotNull
    private static ProductConfigurator getProductConfigurator() {
        ProductConfigurator configurator = new ProductConfigurator();
        configurator.setMinLength(new BigDecimal("100"));
        configurator.setMaxLength(new BigDecimal("500"));
        configurator.setMinWidth(new BigDecimal("50"));
        configurator.setMaxWidth(new BigDecimal("200"));
        configurator.setMinHeight(new BigDecimal("150"));
        configurator.setMaxHeight(new BigDecimal("300"));
        configurator.setPricePerCmHeightCZK(BigDecimal.TEN);
        configurator.setPricePerCmLengthCZK(BigDecimal.ONE);
        configurator.setPricePerCmDepthCZK(BigDecimal.ONE);
        configurator.setPricePerCmHeightEUR(BigDecimal.ONE);
        configurator.setPricePerCmLengthEUR(BigDecimal.ONE);
        configurator.setPricePerCmDepthEUR(BigDecimal.ONE);
        configurator.setDesignPriceCZK(BigDecimal.ZERO);
        configurator.setDesignPriceEUR(BigDecimal.ZERO);
        configurator.setDividerPricePerCmDepthCZK(BigDecimal.ZERO);
        configurator.setDividerPricePerCmDepthEUR(BigDecimal.ZERO);
        configurator.setGutterPriceCZK(BigDecimal.ZERO);
        configurator.setGutterPriceEUR(BigDecimal.ZERO);
        configurator.setShedPriceCZK(BigDecimal.ZERO);
        configurator.setShedPriceEUR(BigDecimal.ZERO);
        return configurator;
    }

    // ... ostatní testy (listProducts, productDetail) ...
    @Test
    @DisplayName("GET /produkty - Zobrazí stránku se seznamem aktivních produktů")
    void listProducts_ShouldReturnProductsView() throws Exception {
        Page<Product> productPage = new PageImpl<>(List.of(standardProduct), PageRequest.of(0, 9), 1);
        when(productService.getActiveProducts(any(Pageable.class))).thenReturn(productPage);

        mockMvc.perform(MockMvcRequestBuilders.get("/produkty"))
                .andExpect(status().isOk())
                .andExpect(view().name("produkty"))
                .andExpect(model().attributeExists("productPage"))
                .andExpect(model().attribute("productPage", hasProperty("content", hasSize(1))))
                .andExpect(model().attribute("productPage", hasProperty("content", hasItem(
                        hasProperty("slug", is("standard-drevnik"))
                ))));

        verify(productService).getActiveProducts(any(Pageable.class));
    }

    @Test
    @DisplayName("GET /produkty - Zobrazí informaci, pokud nejsou žádné aktivní produkty")
    void listProducts_NoActiveProducts_ShouldShowInfoMessage() throws Exception {
        Page<Product> emptyPage = Page.empty(PageRequest.of(0, 9));
        when(productService.getActiveProducts(any(Pageable.class))).thenReturn(emptyPage);

        mockMvc.perform(MockMvcRequestBuilders.get("/produkty"))
                .andExpect(status().isOk())
                .andExpect(view().name("produkty"))
                .andExpect(model().attributeExists("productPage"))
                .andExpect(model().attribute("productPage", hasProperty("empty", is(true))));

        verify(productService).getActiveProducts(any(Pageable.class));
    }

    @Test
    @DisplayName("GET /produkt/{slug} - Zobrazí detail standardního produktu")
    void productDetail_StandardProduct_ShouldReturnStandardDetailView() throws Exception {
        String slug = "standard-drevnik";
        when(productService.getActiveProductBySlug(slug)).thenReturn(Optional.of(standardProduct));

        mockMvc.perform(MockMvcRequestBuilders.get("/produkt/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(view().name("produkt-detail-standard"))
                .andExpect(model().attributeExists("product", "cartItemDto", "availableDesigns", "availableGlazes", "availableRoofColors"))
                .andExpect(model().attribute("product", hasProperty("slug", is(slug))))
                .andExpect(model().attribute("cartItemDto", hasProperty("productId", is(standardProduct.getId()))))
                .andExpect(model().attribute("cartItemDto", hasProperty("custom", is(false))));

        verify(productService).getActiveProductBySlug(slug);
    }

    @Test
    @DisplayName("GET /produkt/{slug} - Zobrazí detail custom produktu")
    void productDetail_CustomProduct_ShouldReturnCustomDetailView() throws Exception {
        String slug = "drevnik-na-miru";
        when(productService.getActiveProductBySlug(slug)).thenReturn(Optional.of(customProduct));
        when(productService.calculateDynamicProductPrice(eq(customProduct), anyMap(), isNull(), eq(false), eq(false), eq(false), eq("CZK")))
                .thenReturn(new BigDecimal("5000.00"));
        when(productService.calculateDynamicProductPrice(eq(customProduct), anyMap(), isNull(), eq(false), eq(false), eq(false), eq("EUR")))
                .thenReturn(new BigDecimal("200.00"));

        mockMvc.perform(MockMvcRequestBuilders.get("/produkt/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(view().name("produkt-detail-custom"))
                .andExpect(model().attributeExists("product", "cartItemDto", "configurator", "availableAddons", "initialCustomPriceCZK", "initialCustomPriceEUR"))
                .andExpect(model().attribute("product", hasProperty("slug", is(slug))))
                .andExpect(model().attribute("cartItemDto", hasProperty("productId", is(customProduct.getId()))))
                .andExpect(model().attribute("cartItemDto", hasProperty("custom", is(true))))
                .andExpect(model().attribute("configurator", notNullValue()))
                .andExpect(model().attribute("initialCustomPriceCZK", is(new BigDecimal("5000.00"))));

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
        when(productService.getActiveProductBySlug(slug)).thenReturn(Optional.empty());

        mockMvc.perform(MockMvcRequestBuilders.get("/produkt/{slug}", slug))
                .andExpect(status().isNotFound());

        verify(productService).getActiveProductBySlug(slug);
    }

    @Test
    @DisplayName("POST /api/product/calculate-price - Úspěšný výpočet")
    void calculateCustomPrice_Success() throws Exception {
        Long productId = customProduct.getId();
        BigDecimal calculatedCZK = new BigDecimal("6543.21");
        BigDecimal calculatedEUR = new BigDecimal("261.73");

        CustomPriceRequestDto requestDto = new CustomPriceRequestDto();
        requestDto.setProductId(productId);
        requestDto.setCustomDimensions(Map.of("length", new BigDecimal("250"), "width", new BigDecimal("150"), "height", new BigDecimal("200")));
        requestDto.setCustomDesign("Test Design");
        requestDto.setCustomHasGutter(true);

        when(productService.getProductById(productId)).thenReturn(Optional.of(customProduct));
        when(productService.calculateDynamicProductPrice(eq(customProduct), anyMap(), eq("Test Design"), eq(false), eq(true), eq(false), eq("CZK")))
                .thenReturn(calculatedCZK);
        when(productService.calculateDynamicProductPrice(eq(customProduct), anyMap(), eq("Test Design"), eq(false), eq(true), eq(false), eq("EUR")))
                .thenReturn(calculatedEUR);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/product/calculate-price")
                                .contentType(MediaType.APPLICATION_JSON) // Zajištěno
                                .content(objectMapper.writeValueAsString(requestDto))
                        // CSRF vypnuto
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.priceCZK", is(6543.21)))
                .andExpect(jsonPath("$.priceEUR", is(261.73)))
                .andExpect(jsonPath("$.errorMessage", nullValue()));

        verify(productService).getProductById(productId);
        verify(productService).calculateDynamicProductPrice(eq(customProduct), eq(requestDto.getCustomDimensions()), eq("Test Design"), eq(false), eq(true), eq(false), eq("CZK"));
        verify(productService).calculateDynamicProductPrice(eq(customProduct), eq(requestDto.getCustomDimensions()), eq("Test Design"), eq(false), eq(true), eq(false), eq("EUR"));
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
                                .contentType(MediaType.APPLICATION_JSON) // Zajištěno
                                .content(objectMapper.writeValueAsString(requestDto))
                        // CSRF vypnuto
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
        requestDto.setCustomDimensions(null);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/product/calculate-price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto))
                )
                .andExpect(status().isBadRequest());

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
                                .contentType(MediaType.APPLICATION_JSON) // Zajištěno
                                .content(objectMapper.writeValueAsString(requestDto))
                        // CSRF vypnuto
                )
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorMessage", containsString("neočekávané chybě")));

        verify(productService).getProductById(productId);
        verify(productService).calculateDynamicProductPrice(any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyString());
    }
}