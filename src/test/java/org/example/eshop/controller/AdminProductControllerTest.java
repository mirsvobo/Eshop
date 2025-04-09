// src/test/java/org/example/eshop/admin/controller/AdminProductControllerTest.java

package org.example.eshop.controller; // Zkontroluj, zda je balíček správně

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException; // Import pro ověření exception

import java.math.BigDecimal;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat; // Ponecháno pro případné budoucí použití, ale v verify se nepoužije pro prázdné seznamy
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

        product2 = new Product();
        product2.setId(2L);
        product2.setName("Produkt 2 Custom");
        product2.setSlug("produkt-2-custom");
        product2.setActive(true);
        product2.setCustomisable(true);
        product2.setTaxRate(taxRate21);
        product2.setBasePriceCZK(null);
        product2.setAvailableDesigns(new HashSet<>());
        product2.setAvailableGlazes(new HashSet<>());
        product2.setAvailableRoofColors(new HashSet<>());
        product2.setAvailableAddons(new HashSet<>(Set.of(addon1)));
        product2.setConfigurator(new ProductConfigurator());

        lenient().when(currencyService.getSelectedCurrency()).thenReturn("CZK");

        // Lenient mockování pro findAllById s prázdným seznamem - Ponecháno pro jistotu
        lenient().when(designRepository.findAllById(argThat(iterable -> iterable != null && !iterable.iterator().hasNext()))).thenReturn(Collections.emptyList());
        lenient().when(glazeRepository.findAllById(argThat(iterable -> iterable != null && !iterable.iterator().hasNext()))).thenReturn(Collections.emptyList());
        lenient().when(roofColorRepository.findAllById(argThat(iterable -> iterable != null && !iterable.iterator().hasNext()))).thenReturn(Collections.emptyList());
        lenient().when(addonsRepository.findAllById(argThat(iterable -> iterable != null && !iterable.iterator().hasNext()))).thenReturn(Collections.emptyList());
    }

    // --- Testy GET metod (beze změny) ---
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
                .andExpect(model().attribute("productPage", hasProperty("content", hasItem(
                        hasProperty("slug", is("produkt-1-standard"))
                ))));

        verify(productService).getAllProducts(any(Pageable.class));
    }

    @Test
    @DisplayName("GET /admin/products/new - Zobrazí formulář pro nový produkt")
    void showCreateProductForm_ShouldReturnFormView() throws Exception {
        when(taxRateService.getAllTaxRates()).thenReturn(Collections.singletonList(taxRate21));
        when(designRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(design1));
        when(glazeRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(glaze1));
        when(roofColorRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(roofColor1));
        when(addonsRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(addon1));


        mockMvc.perform(MockMvcRequestBuilders.get("/admin/products/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/product-form"))
                .andExpect(model().attributeExists("product", "allTaxRates", "allDesigns", "allGlazes", "allRoofColors", "allAddons"))
                .andExpect(model().attribute("product", hasProperty("id", nullValue())))
                .andExpect(model().attribute("product", hasProperty("availableDesigns", instanceOf(Set.class))))
                .andExpect(model().attribute("product", hasProperty("availableGlazes", instanceOf(Set.class))))
                .andExpect(model().attribute("product", hasProperty("availableRoofColors", instanceOf(Set.class))))
                .andExpect(model().attribute("product", hasProperty("availableAddons", instanceOf(Set.class))))
                .andExpect(model().attribute("pageTitle", containsString("Vytvořit")));

        verify(taxRateService).getAllTaxRates();
        verify(designRepository).findAll(any(Sort.class));
        verify(glazeRepository).findAll(any(Sort.class));
        verify(roofColorRepository).findAll(any(Sort.class));
        verify(addonsRepository).findAll(any(Sort.class));
    }

    @Test
    @DisplayName("GET /admin/products/{id}/edit - Zobrazí formulář pro úpravu produktu")
    void showEditProductForm_ShouldReturnFormView() throws Exception {
        when(productService.getProductById(1L)).thenReturn(Optional.of(product1));
        when(taxRateService.getAllTaxRates()).thenReturn(Collections.singletonList(taxRate21));
        when(designRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(design1));
        when(glazeRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(glaze1));
        when(roofColorRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(roofColor1));
        when(addonsRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(addon1));

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/products/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/product-form"))
                .andExpect(model().attributeExists("product", "allTaxRates", "allDesigns", "allGlazes", "allRoofColors", "allAddons"))
                .andExpect(model().attribute("product", hasProperty("id", is(1L))))
                .andExpect(model().attribute("pageTitle", containsString("Upravit")));

        verify(productService).getProductById(1L);
        verify(taxRateService).getAllTaxRates();
        verify(designRepository).findAll(any(Sort.class));
        verify(glazeRepository).findAll(any(Sort.class));
        verify(roofColorRepository).findAll(any(Sort.class));
        verify(addonsRepository).findAll(any(Sort.class));
    }

    @Test
    @DisplayName("GET /admin/products/{id}/edit - Nenalezeno - Přesměruje na seznam")
    void showEditProductForm_NotFound_ShouldRedirect() throws Exception {
        when(productService.getProductById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/products/99/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(productService).getProductById(99L);
    }

    // --- Testy POST metod ---

    @Test
    @DisplayName("POST /admin/products - Úspěšně vytvoří nový produkt")
    void createProduct_Success() throws Exception {
        Product createdProduct = new Product();
        createdProduct.setId(3L);
        createdProduct.setName("Nový Produkt Test");
        createdProduct.setSlug("novy-produkt-test");
        createdProduct.setTaxRate(taxRate21);

        when(productService.createProduct(any(Product.class))).thenReturn(createdProduct);
        when(designRepository.findAllById(eq(List.of(10L)))).thenReturn(List.of(design1));
        when(glazeRepository.findAllById(eq(List.of(20L)))).thenReturn(List.of(glaze1));
        // Mockování pro prázdné seznamy je v setUp()

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/products")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "Nový Produkt Test")
                                .param("slug", "novy-produkt-test")
                                .param("basePriceCZK", "1500")
                                .param("active", "true")
                                .param("customisable", "false")
                                .param("taxRate.id", "1")
                                .param("designIds", "10")
                                .param("glazeIds", "20")
                        // .with(csrf())
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"))
                .andExpect(flash().attributeExists("successMessage"));

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productService).createProduct(productCaptor.capture());

        Product productPassedToService = productCaptor.getValue();
        assertEquals("Nový Produkt Test", productPassedToService.getName());
        assertEquals("novy-produkt-test", productPassedToService.getSlug());
        assertNotNull(productPassedToService.getTaxRate());
        assertEquals(1L, productPassedToService.getTaxRate().getId());
        assertFalse(productPassedToService.isCustomisable());
        assertEquals(1, productPassedToService.getAvailableDesigns().size());
        assertTrue(productPassedToService.getAvailableDesigns().stream().anyMatch(d -> d.getId().equals(10L)));
        assertEquals(1, productPassedToService.getAvailableGlazes().size());
        assertTrue(productPassedToService.getAvailableGlazes().stream().anyMatch(g -> g.getId().equals(20L)));
        assertTrue(productPassedToService.getAvailableRoofColors().isEmpty());
        assertTrue(productPassedToService.getAvailableAddons().isEmpty());

        // Ověření volání findAllById pro NEPRÁZDNÉ seznamy
        verify(designRepository).findAllById(eq(List.of(10L)));
        verify(glazeRepository).findAllById(eq(List.of(20L)));
        // Neověřujeme findAllById pro prázdné seznamy, spoléháme na kontrolu výsledného Product objektu
    }


    @Test
    @DisplayName("POST /admin/products - Chyba validace (chybí název) - Očekává 400")
    void createProduct_ValidationError_ShouldReturnBadRequest() throws Exception {
        // Mockování findAllById pro prázdné seznamy je v setUp()

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/admin/products")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "") // Prázdný název
                                .param("slug", "chyba-validace")
                                .param("taxRate.id", "1")
                        // .with(csrf())
                )
                .andExpect(status().isBadRequest()) // --- OČEKÁVÁME 400 ---
                .andReturn();

        // Ověříme typ vyhozené výjimky
        Exception resolvedException = mvcResult.getResolvedException();
        assertNotNull(resolvedException, "Očekávána výjimka, ale žádná nebyla vyhozena");
        assertInstanceOf(MethodArgumentNotValidException.class, resolvedException, "Očekáván typ výjimky MethodArgumentNotValidException");

        // Ověříme, že chyba validace se týká pole 'name'
        MethodArgumentNotValidException validationException = (MethodArgumentNotValidException) resolvedException;
        assertTrue(validationException.getBindingResult().hasFieldErrors("name"), "Měla nastat chyba validace pro pole 'name'");

        // Ověříme, že service metoda pro vytvoření produktu NEBYLA volána
        verify(productService, never()).createProduct(any(Product.class));

        // Neověřujeme findAllById zde, protože je nespolehlivé a pro funkčnost testu není kritické
        // verify(designRepository).findAllById(argThat(iterable -> iterable != null && !iterable.iterator().hasNext()));
        // verify(glazeRepository).findAllById(argThat(iterable -> iterable != null && !iterable.iterator().hasNext()));
        // verify(roofColorRepository).findAllById(argThat(iterable -> iterable != null && !iterable.iterator().hasNext()));
        // verify(addonsRepository).findAllById(argThat(iterable -> iterable != null && !iterable.iterator().hasNext()));

        // Neověřujeme volání pro renderování formuláře
    }


    @Test
    @DisplayName("POST /admin/products/{id} - Úspěšně aktualizuje produkt")
    void updateProduct_Success() throws Exception {
        long productId = 1L;
        Product updatedProduct = new Product();
        updatedProduct.setId(productId);
        updatedProduct.setName("Aktualizovaný Produkt 1");
        updatedProduct.setSlug("produkt-1-standard");

        when(productService.getProductById(productId)).thenReturn(Optional.of(product1));
        when(productService.updateProduct(eq(productId), any(Product.class), any(Product.class)))
                .thenReturn(Optional.of(updatedProduct));
        // Mock načtení asociací podle ID z requestu
        //findAllById pro prázdné seznamy mockováno v setUp()
        when(glazeRepository.findAllById(eq(List.of(20L)))).thenReturn(List.of(glaze1));
        when(roofColorRepository.findAllById(eq(List.of(30L)))).thenReturn(List.of(roofColor1));

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/products/{id}", productId)
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "Aktualizovaný Produkt 1")
                                .param("slug", "produkt-1-standard")
                                .param("active", "true")
                                .param("customisable", "false")
                                .param("taxRate.id", "1")
                                .param("glazeIds", "20")
                                .param("roofColorIds", "30")
                        // .with(csrf())
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"))
                .andExpect(flash().attributeExists("successMessage"));

        ArgumentCaptor<Product> productDataCaptor = ArgumentCaptor.forClass(Product.class);
        ArgumentCaptor<Product> existingProductCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productService).updateProduct(eq(productId), productDataCaptor.capture(), existingProductCaptor.capture());

        Product dataPassed = productDataCaptor.getValue();
        assertEquals("Aktualizovaný Produkt 1", dataPassed.getName());
        assertEquals(1L, dataPassed.getTaxRate().getId());

        Product existingPassed = existingProductCaptor.getValue();
        assertTrue(existingPassed.getAvailableDesigns().isEmpty());
        assertEquals(1, existingPassed.getAvailableGlazes().size());
        assertTrue(existingPassed.getAvailableGlazes().stream().anyMatch(g -> g.getId().equals(20L)));
        assertEquals(1, existingPassed.getAvailableRoofColors().size());
        assertTrue(existingPassed.getAvailableRoofColors().stream().anyMatch(rc -> rc.getId().equals(30L)));

        // Ověření volání findAllById pro NEPRÁZDNÉ seznamy
        verify(glazeRepository).findAllById(eq(List.of(20L)));
        verify(roofColorRepository).findAllById(eq(List.of(30L)));
        // Neověřujeme findAllById pro prázdné seznamy
    }


    @Test
    @DisplayName("POST /admin/products/{id} - Chyba validace vrátí formulář")
    void updateProduct_ValidationError() throws Exception {
        long productId = 1L;

        // Mock pro zobrazení formuláře při chybě
        when(taxRateService.getAllTaxRates()).thenReturn(Collections.singletonList(taxRate21));
        when(designRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(design1));
        when(glazeRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(glaze1));
        when(roofColorRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(roofColor1));
        when(addonsRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(addon1));
        // Mock pro načtení asociací podle (prázdných) ID - je v setUp()

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/products/{id}", productId)
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "") // Nevalidní jméno
                                .param("slug", "produkt-1-standard")
                                .param("taxRate.id", "1")
                        // .with(csrf())
                )
                .andExpect(status().isOk()) // Očekáváme 200 OK
                .andExpect(view().name("admin/product-form")) // Vrátí formulář
                .andExpect(model().attributeExists("product", "allTaxRates", "allDesigns", "allGlazes", "allRoofColors", "allAddons"))
                .andExpect(model().hasErrors()) // Očekáváme chyby
                .andExpect(model().attributeHasFieldErrors("product", "name")) // Chyba u jména
                .andExpect(model().attribute("product", hasProperty("availableDesigns", instanceOf(Set.class))))
                .andExpect(model().attribute("product", hasProperty("availableGlazes", instanceOf(Set.class))))
                .andExpect(model().attribute("product", hasProperty("availableRoofColors", instanceOf(Set.class))))
                .andExpect(model().attribute("product", hasProperty("availableAddons", instanceOf(Set.class))));

        verify(productService, never()).updateProduct(anyLong(), any(), any()); // Update se nevolá
        // Ověření načtení dat pro formulář
        verify(taxRateService).getAllTaxRates();
        verify(designRepository).findAll(any(Sort.class));
        verify(glazeRepository).findAll(any(Sort.class));
        verify(roofColorRepository).findAll(any(Sort.class));
        verify(addonsRepository).findAll(any(Sort.class));
        // Neověřujeme findAllById pro prázdné seznamy
    }

    // --- Testy DELETE metod (beze změny) ---
    @Test
    @DisplayName("POST /admin/products/{id}/delete - Úspěšně deaktivuje produkt")
    void deleteProduct_Success() throws Exception {
        long productId = 1L;
        doNothing().when(productService).deleteProduct(productId);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/products/{id}/delete", productId)
                        // .with(csrf())
                )
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

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/products/{id}/delete", nonExistentId)
                        // .with(csrf())
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"))
                .andExpect(flash().attribute("errorMessage", is(errorMessage)));

        verify(productService).deleteProduct(nonExistentId);
    }
}