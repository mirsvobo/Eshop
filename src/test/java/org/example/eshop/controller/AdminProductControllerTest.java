package org.example.eshop.controller; // Balíček může být jiný

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.admin.controller.AdminProductController;
import org.example.eshop.config.SecurityTestConfig; // Import SecurityTestConfig
import org.example.eshop.model.*;
import org.example.eshop.repository.*;
import org.example.eshop.service.CurrencyService; // Ponecháno pro @ControllerAdvice
import org.example.eshop.service.ProductService;
import org.example.eshop.service.TaxRateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import; // Import pro @Import
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.math.RoundingMode; // Přidáno pro BigDecimal
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat; // Ponecháno pro případné budoucí použití
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminProductController.class)
@WithMockUser(roles = "ADMIN")
@Import(SecurityTestConfig.class) // Použití sdílené testovací konfigurace
class AdminProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private CurrencyService currencyService;
    @MockBean private ProductService productService;
    @MockBean private TaxRateService taxRateService;
    @MockBean private DesignRepository designRepository;
    @MockBean private GlazeRepository glazeRepository;
    @MockBean private RoofColorRepository roofColorRepository;
    @MockBean private AddonsRepository addonsRepository;

    private Product product1, product2;
    private TaxRate taxRate21;
    private Design design1;
    private Glaze glaze1;
    private RoofColor roofColor1;
    private Addon addon1;
    private ProductConfigurator testConfigurator;

    @BeforeEach
    void setUp() {
        taxRate21 = new TaxRate(1L, "21%", new BigDecimal("0.21"), false, null);
        design1 = new Design(); design1.setId(10L); design1.setName("Klasik");
        glaze1 = new Glaze(); glaze1.setId(20L); glaze1.setName("Ořech");
        roofColor1 = new RoofColor(); roofColor1.setId(30L); roofColor1.setName("Antracit");
        addon1 = new Addon(); addon1.setId(40L); addon1.setName("Polička");

        product1 = new Product();
        product1.setId(1L);
        product1.setName("Produkt 1 Standard");
        product1.setSlug("produkt-1-standard");
        product1.setActive(true);
        product1.setCustomisable(false);
        product1.setTaxRate(taxRate21);
        product1.setBasePriceCZK(new BigDecimal("1000"));
        product1.setAvailableDesigns(new HashSet<>(Set.of(design1)));
        product1.setAvailableGlazes(new HashSet<>(Set.of(glaze1)));
        product1.setAvailableRoofColors(new HashSet<>(Set.of(roofColor1)));
        product1.setAvailableAddons(new HashSet<>());

        testConfigurator = new ProductConfigurator(); // Vytvoříme konfigurátor
        testConfigurator.setId(2L); // ID musí odpovídat produktu
        testConfigurator.setMinLength(new BigDecimal("100.00"));
        testConfigurator.setMaxLength(new BigDecimal("500.00"));
        testConfigurator.setMinWidth(new BigDecimal("50.00"));
        testConfigurator.setMaxWidth(new BigDecimal("200.00"));
        testConfigurator.setMinHeight(new BigDecimal("150.00"));
        testConfigurator.setMaxHeight(new BigDecimal("300.00"));
        testConfigurator.setPricePerCmHeightCZK(new BigDecimal("14.00"));
        testConfigurator.setPricePerCmLengthCZK(new BigDecimal("99.00"));
        testConfigurator.setPricePerCmDepthCZK(new BigDecimal("25.00"));
        testConfigurator.setDividerPricePerCmDepthCZK(new BigDecimal("13.00"));
        testConfigurator.setDesignPriceCZK(BigDecimal.ZERO);
        testConfigurator.setGutterPriceCZK(new BigDecimal("1000.00"));
        testConfigurator.setShedPriceCZK(new BigDecimal("5000.00"));
        testConfigurator.setPricePerCmHeightEUR(new BigDecimal("0.56"));
        testConfigurator.setPricePerCmLengthEUR(new BigDecimal("3.96"));
        testConfigurator.setPricePerCmDepthEUR(new BigDecimal("1.00"));
        testConfigurator.setDividerPricePerCmDepthEUR(new BigDecimal("0.52"));
        testConfigurator.setDesignPriceEUR(BigDecimal.ZERO);
        testConfigurator.setGutterPriceEUR(new BigDecimal("40.00"));
        testConfigurator.setShedPriceEUR(new BigDecimal("200.00"));

        product2 = new Product();
        product2.setId(2L);
        product2.setName("Produkt 2 Custom");
        product2.setSlug("produkt-2-custom");
        product2.setActive(true);
        product2.setCustomisable(true); // Je customisable
        product2.setTaxRate(taxRate21);
        product2.setBasePriceCZK(null);
        product2.setAvailableDesigns(new HashSet<>());
        product2.setAvailableGlazes(new HashSet<>());
        product2.setAvailableRoofColors(new HashSet<>());
        product2.setAvailableAddons(new HashSet<>(Set.of(addon1)));
        product2.setConfigurator(testConfigurator); // Přiřadíme konfigurátor
        testConfigurator.setProduct(product2); // Obousměrná vazba

        // Lenient mockování
        lenient().when(currencyService.getSelectedCurrency()).thenReturn("CZK");
        lenient().when(designRepository.findAllById(argThat(iterable -> iterable != null && !iterable.iterator().hasNext()))).thenReturn(Collections.emptyList());
        lenient().when(glazeRepository.findAllById(argThat(iterable -> iterable != null && !iterable.iterator().hasNext()))).thenReturn(Collections.emptyList());
        lenient().when(roofColorRepository.findAllById(argThat(iterable -> iterable != null && !iterable.iterator().hasNext()))).thenReturn(Collections.emptyList());
        lenient().when(addonsRepository.findAllById(argThat(iterable -> iterable != null && !iterable.iterator().hasNext()))).thenReturn(Collections.emptyList());

        // Mock pro načítání atributů ve formuláři
        lenient().when(taxRateService.getAllTaxRates()).thenReturn(Collections.singletonList(taxRate21));
        lenient().when(designRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(design1));
        lenient().when(glazeRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(glaze1));
        lenient().when(roofColorRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(roofColor1));
        lenient().when(addonsRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(addon1));

        // Lenient mockování pro getProductById, protože se volá v různých scénářích
        lenient().when(productService.getProductById(1L)).thenReturn(Optional.of(product1));
        lenient().when(productService.getProductById(2L)).thenReturn(Optional.of(product2));
        lenient().when(productService.getProductById(99L)).thenReturn(Optional.empty());
    }

    // --- Testy GET (zůstávají stejné) ---
    @Test
    @DisplayName("GET /admin/products - Zobrazí seznam produktů")
    void listProducts_ShouldReturnListView() throws Exception {
        Page<Product> productPage = new PageImpl<>(Arrays.asList(product1, product2), PageRequest.of(0, 15), 2);
        when(productService.getAllProducts(any(Pageable.class))).thenReturn(productPage);

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/products"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/products-list"))
                .andExpect(model().attributeExists("productPage"))
                .andExpect(model().attribute("productPage", hasProperty("content", hasSize(2))))
                .andExpect(model().attribute("currentSort", not(emptyOrNullString())));

        verify(productService).getAllProducts(any(Pageable.class));
    }

    @Test
    @DisplayName("GET /admin/products/new - Zobrazí formulář pro nový produkt")
    void showCreateProductForm_ShouldReturnFormView() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/products/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/product-form"))
                .andExpect(model().attributeExists("product", "allTaxRates", "allDesigns", "allGlazes", "allRoofColors", "allAddons"))
                .andExpect(model().attribute("product", hasProperty("id", nullValue())))
                .andExpect(model().attribute("pageTitle", containsString("Vytvořit")));

        verify(taxRateService).getAllTaxRates();
    }

    @Test
    @DisplayName("GET /admin/products/{id}/edit - Zobrazí formulář pro úpravu STANDARD produktu")
    void showEditProductForm_Standard_ShouldReturnFormView() throws Exception {
        // getProductById je mockováno v setUp

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/products/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/product-form"))
                .andExpect(model().attributeExists("product", "allTaxRates", "allDesigns", "allGlazes", "allRoofColors", "allAddons"))
                .andExpect(model().attribute("product", hasProperty("id", is(1L))))
                .andExpect(model().attribute("product", hasProperty("customisable", is(false))))
                .andExpect(model().attribute("pageTitle", containsString("Upravit")));

        verify(productService).getProductById(1L);
    }

    @Test
    @DisplayName("GET /admin/products/{id}/edit - Zobrazí formulář pro úpravu CUSTOM produktu")
    void showEditProductForm_Custom_ShouldReturnFormViewWithConfig() throws Exception {
        // getProductById je mockováno v setUp

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/products/2/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/product-form"))
                .andExpect(model().attributeExists("product", "allTaxRates", "allDesigns", "allGlazes", "allRoofColors", "allAddons"))
                .andExpect(model().attribute("product", hasProperty("id", is(2L))))
                .andExpect(model().attribute("product", hasProperty("customisable", is(true))))
                .andExpect(model().attribute("product", hasProperty("configurator", notNullValue())))
                .andExpect(model().attribute("product", hasProperty("configurator", hasProperty("minLength", comparesEqualTo(new BigDecimal("100.00"))))))
                .andExpect(model().attribute("pageTitle", containsString("Upravit")));

        verify(productService).getProductById(2L);
    }


    @Test
    @DisplayName("GET /admin/products/{id}/edit - Nenalezeno - Přesměruje na seznam")
    void showEditProductForm_NotFound_ShouldRedirect() throws Exception {
        // getProductById je mockováno v setUp

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/products/99/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(productService).getProductById(99L);
    }


    // --- Testy POST Vytvoření ---

    @Test
    @DisplayName("POST /admin/products - Úspěšně vytvoří STANDARD produkt")
    void createProduct_Standard_Success() throws Exception {
        Product createdProduct = new Product(); createdProduct.setId(3L); createdProduct.setName("Nový Standard"); createdProduct.setSlug("novy-standard");
        when(productService.createProduct(any(Product.class))).thenReturn(createdProduct);
        when(designRepository.findAllById(eq(List.of(10L)))).thenReturn(List.of(design1));

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/products")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Nový Standard")
                        .param("basePriceCZK", "1500")
                        .param("active", "true")
                        .param("customisable", "false") // Standardní
                        .param("taxRate.id", "1")
                        .param("designIds", "10") // Asociace pro standardní
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"))
                .andExpect(flash().attributeExists("successMessage"));

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productService).createProduct(productCaptor.capture());
        Product productPassed = productCaptor.getValue();
        assertEquals("Nový Standard", productPassed.getName());
        assertFalse(productPassed.isCustomisable());
        assertNull(productPassed.getConfigurator()); // Standardní nemá mít konfigurátor
        assertNotNull(productPassed.getAvailableDesigns());
        assertFalse(productPassed.getAvailableDesigns().isEmpty());
        assertTrue(productPassed.getAvailableAddons() == null || productPassed.getAvailableAddons().isEmpty()); // Standardní nemá mít addony

        verify(designRepository).findAllById(List.of(10L)); // Ověříme načtení asociace
        verify(addonsRepository, never()).findAllById(any()); // Addony se nenačítají
    }

    @Test
    @DisplayName("POST /admin/products - Úspěšně vytvoří CUSTOM produkt")
    void createProduct_Custom_Success() throws Exception {
        Product createdProduct = new Product(); createdProduct.setId(4L); createdProduct.setName("Nový Custom"); createdProduct.setSlug("novy-custom");
        createdProduct.setCustomisable(true);
        // Mock service tak, aby vrátil produkt s konfigurátorem
        when(productService.createProduct(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(4L); // Simulace ID
            if (p.isCustomisable()) { // Service by měl vytvořit configurator
                ProductConfigurator cfg = new ProductConfigurator();
                cfg.setProduct(p);
                p.setConfigurator(cfg);
            }
            return p;
        });
        when(addonsRepository.findAllById(eq(List.of(40L)))).thenReturn(List.of(addon1));

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/products")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Nový Custom")
                        .param("active", "true")
                        .param("customisable", "true") // Custom
                        .param("taxRate.id", "1")
                        .param("addonIds", "40") // Asociace pro custom
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"))
                .andExpect(flash().attributeExists("successMessage"));

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productService).createProduct(productCaptor.capture());
        Product productPassed = productCaptor.getValue();
        assertEquals("Nový Custom", productPassed.getName());
        assertTrue(productPassed.isCustomisable());
        assertNotNull(productPassed.getConfigurator()); // Konfigurátor by měl být vytvořen
        assertTrue(productPassed.getAvailableDesigns() == null || productPassed.getAvailableDesigns().isEmpty()); // Custom nemá mít std atributy
        assertNotNull(productPassed.getAvailableAddons());
        assertFalse(productPassed.getAvailableAddons().isEmpty());

        verify(addonsRepository).findAllById(List.of(40L)); // Ověříme načtení asociace
        verify(designRepository, never()).findAllById(any()); // Standardní se nenačítají
    }

    @Test
    @DisplayName("POST /admin/products - Chyba validace vrátí formulář s daty")
    void createProduct_ValidationError_ShouldReturnForm() throws Exception {
        // Mock pro zobrazení formuláře (atributy) je v setUp

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/products")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "") // Nevalidní jméno
                        .param("taxRate.id", "1") // Platná sazba
                )
                .andExpect(status().isOk()) // Vrací formulář
                .andExpect(view().name("admin/product-form"))
                .andExpect(model().attributeExists("product", "allTaxRates", "allDesigns", "allGlazes", "allRoofColors", "allAddons"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("product", "name")); // Chyba u jména

        verify(productService, never()).createProduct(any()); // Create se nevolá
        verify(taxRateService).getAllTaxRates(); // Ověření načtení dat pro formulář
    }

    // --- Testy POST Aktualizace ---

    @Test
    @DisplayName("POST /admin/products/{id} - Úspěšně aktualizuje STANDARD produkt")
    void updateProduct_Standard_Success() throws Exception {
        long productId = 1L;
        Product updatedProduct = new Product(); /* ... data ... */
        updatedProduct.setId(productId); updatedProduct.setName("Aktualizovaný Standard");

        // Mock productService.getProductById je v setUp
        when(productService.updateProduct(eq(productId), any(Product.class), any(Product.class)))
                .thenReturn(Optional.of(updatedProduct));
        when(designRepository.findAllById(eq(List.of(10L)))).thenReturn(List.of(design1)); // Ponecháváme design

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/products/{id}", productId)
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "Aktualizovaný Standard")
                                .param("basePriceCZK", "1100")
                                .param("active", "true")
                                .param("customisable", "false")
                                .param("taxRate.id", "1")
                                .param("designIds", "10") // Ponecháme design
                        // Odebereme glaze a roofColor (neposíláme parametry)
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"))
                .andExpect(flash().attributeExists("successMessage"));

        ArgumentCaptor<Product> dataCaptor = ArgumentCaptor.forClass(Product.class);
        ArgumentCaptor<Product> existingCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productService).updateProduct(eq(productId), dataCaptor.capture(), existingCaptor.capture());

        Product dataPassed = dataCaptor.getValue();
        assertEquals("Aktualizovaný Standard", dataPassed.getName());

        Product existingPassed = existingCaptor.getValue(); // Entita PŘEDANÁ do updateProduct
        // Controller volá updateAssociationsFromIds na existingPassed PŘED voláním service.updateProduct
        assertNotNull(existingPassed.getAvailableDesigns());
        assertEquals(1, existingPassed.getAvailableDesigns().size()); // Design zůstal
        assertTrue(existingPassed.getAvailableGlazes().isEmpty()); // Glaze byla odebrána
        assertTrue(existingPassed.getAvailableRoofColors().isEmpty()); // RoofColor byla odebrána

        verify(designRepository).findAllById(List.of(10L)); // Voláno controllerem
        // Ověření, že pro prázdné seznamy se findAllById také volá (podle logiky controlleru)
        // Používáme eq(Collections.emptyList()) nebo argThat pro kontrolu prázdného Iterable
        verify(glazeRepository).findAllById(eq(Collections.emptyList()));
        verify(roofColorRepository).findAllById(eq(Collections.emptyList()));
        verify(addonsRepository).findAllById(eq(Collections.emptyList()));
    }

    @Test
    @DisplayName("POST /admin/products/{id} - Úspěšně aktualizuje CUSTOM produkt (vč. konfigurátoru)")
    void updateProduct_Custom_SuccessWithConfig() throws Exception {
        long productId = 2L;
        Product updatedProduct = new Product(); /* ... data ... */
        updatedProduct.setId(productId); updatedProduct.setName("Aktualizovaný Custom");
        updatedProduct.setCustomisable(true); // Zůstává custom

        // Mock productService.getProductById je v setUp
        when(productService.updateProduct(eq(productId), any(Product.class), any(Product.class)))
                .thenReturn(Optional.of(updatedProduct));
        when(addonsRepository.findAllById(eq(List.of(40L)))).thenReturn(List.of(addon1)); // Ponecháváme addon

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Aktualizovaný Custom")
                        .param("active", "true")
                        .param("customisable", "true") // Je custom
                        .param("taxRate.id", "1")
                        .param("addonIds", "40") // Ponecháme addon
                        // --- Data konfigurátoru ---
                        .param("configurator.minLength", "120")
                        .param("configurator.maxLength", "550")
                        .param("configurator.minWidth", "60")
                        .param("configurator.maxWidth", "210")
                        .param("configurator.minHeight", "160")
                        .param("configurator.maxHeight", "310")
                        .param("configurator.pricePerCmLengthCZK", "105.50")
                        .param("configurator.pricePerCmLengthEUR", "4.10")
                        .param("configurator.pricePerCmDepthCZK", "26.00")
                        .param("configurator.pricePerCmDepthEUR", "1.10")
                        .param("configurator.pricePerCmHeightCZK", "15.00")
                        .param("configurator.pricePerCmHeightEUR", "0.60")
                        .param("configurator.designPriceCZK", "50.00")
                        .param("configurator.designPriceEUR", "2.00")
                        .param("configurator.dividerPricePerCmDepthCZK", "14.00")
                        .param("configurator.dividerPricePerCmDepthEUR", "0.55")
                        .param("configurator.gutterPriceCZK", "1100.00")
                        .param("configurator.gutterPriceEUR", "44.00")
                        .param("configurator.shedPriceCZK", "5500.00")
                        .param("configurator.shedPriceEUR", "220.00")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"))
                .andExpect(flash().attributeExists("successMessage"));

        ArgumentCaptor<Product> dataCaptor = ArgumentCaptor.forClass(Product.class);
        ArgumentCaptor<Product> existingCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productService).updateProduct(eq(productId), dataCaptor.capture(), existingCaptor.capture());

        Product dataPassed = dataCaptor.getValue(); // Data z formuláře (@ModelAttribute)
        assertEquals("Aktualizovaný Custom", dataPassed.getName());
        assertTrue(dataPassed.isCustomisable());
        assertNotNull(dataPassed.getConfigurator()); // Ověříme, že DTO obsahuje data konfigurátoru
        assertEquals(0, new BigDecimal("120").compareTo(dataPassed.getConfigurator().getMinLength()));
        assertEquals(0, new BigDecimal("105.50").compareTo(dataPassed.getConfigurator().getPricePerCmLengthCZK()));

        Product existingPassed = existingCaptor.getValue(); // Načtená entita předaná do service
        assertNotNull(existingPassed.getAvailableAddons());
        assertFalse(existingPassed.getAvailableAddons().isEmpty()); // Addon zůstal
        assertTrue(existingPassed.getAvailableDesigns().isEmpty()); // Standardní atributy jsou prázdné

        verify(addonsRepository).findAllById(List.of(40L)); // Voláno controllerem
        // Ověření volání pro prázdné seznamy standardních atributů
        verify(designRepository).findAllById(eq(Collections.emptyList()));
        verify(glazeRepository).findAllById(eq(Collections.emptyList()));
        verify(roofColorRepository).findAllById(eq(Collections.emptyList()));
    }


    @Test
    @DisplayName("POST /admin/products/{id} - Chyba validace vrátí formulář s daty")
    void updateProduct_ValidationError_ShouldReturnForm() throws Exception {
        long productId = 1L;
        // Mock pro načtení produktu ID=1 pro zobrazení chyby je v setUp

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "") // Nevalidní jméno
                        .param("slug", "produkt-1-standard")
                        .param("taxRate.id", "1")
                )
                .andExpect(status().isOk()) // Vrací formulář
                .andExpect(view().name("admin/product-form"))
                .andExpect(model().attributeExists("product", "allTaxRates", "allDesigns", "allGlazes", "allRoofColors", "allAddons", "pageTitle"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("product", "name"))
                // Ověření, že se DTO pro asociace správně načetly i při chybě
                .andExpect(model().attribute("allDesigns", not(empty())));

        verify(productService, never()).updateProduct(anyLong(), any(), any()); // Update se nevolá
        // Není třeba ověřovat getProductById zde, protože se volá v handleru exception/validace
    }

    // --- Testy DELETE ---

    @Test
    @DisplayName("POST /admin/products/{id}/delete - Úspěšně deaktivuje produkt")
    void deleteProduct_Success() throws Exception {
        long productId = 1L;
        doNothing().when(productService).deleteProduct(productId);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/products/{id}/delete", productId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(productService).deleteProduct(productId);
    }

    @Test
    @DisplayName("POST /admin/products/{id}/delete - Chyba (produkt nenalezen)")
    void deleteProduct_NotFound() throws Exception {
        long nonExistentId = 99L;
        String errorMessage = "Produkt s ID " + nonExistentId + " nenalezen.";
        doThrow(new EntityNotFoundException(errorMessage)).when(productService).deleteProduct(nonExistentId);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/products/{id}/delete", nonExistentId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"))
                .andExpect(flash().attribute("errorMessage", is(errorMessage)));

        verify(productService).deleteProduct(nonExistentId);
    }
}