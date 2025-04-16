package org.example.eshop.controller;

import org.example.eshop.admin.controller.AdminProductController; // Správný import controlleru
import org.example.eshop.config.SecurityTestConfig;
import org.example.eshop.model.*;
import org.example.eshop.service.FileStorageService;
import org.example.eshop.service.ProductService;
// OPRAVA: Import správné třídy AddonsService
import org.example.eshop.admin.service.AddonsService;
import org.example.eshop.admin.service.DesignService;
import org.example.eshop.admin.service.GlazeService;
import org.example.eshop.admin.service.RoofColorService;
import org.example.eshop.service.TaxRateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean; // Anotace je v pořádku
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;


import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*; // Pro detailnější asserce modelu
import static org.junit.jupiter.api.Assertions.*; // Pro ověření captoru

// Cílíme na správný controller
@WebMvcTest(AdminProductController.class)
@Import(SecurityTestConfig.class) // Importujeme testovací SecurityConfig
class AdminProductControllerTest {

    @Autowired
    private MockMvc mvc;

    // Mockujeme všechny PŘÍMÉ závislosti AdminProductController
    @MockBean private ProductService productService;
    @MockBean private DesignService designService;
    @MockBean private GlazeService glazeService;
    @MockBean private RoofColorService roofColorService;
    // OPRAVA: Mock správné třídy AddonsService
    @MockBean private AddonsService addonsService;
    @MockBean private FileStorageService fileStorageService;
    @MockBean private TaxRateService taxRateService;

    // Testovací data
    private Product product1;
    private Product product2;
    private TaxRate taxRate1;
    private TaxRate taxRate2;
    private Design design1;
    private Glaze glaze1;
    private RoofColor roofColor1;
    private Addon addon1;
    private List<TaxRate> allTaxRates;
    private List<Design> allDesigns;
    private List<Glaze> allGlazes;
    private List<RoofColor> allRoofColors;
    private List<Addon> allAddons;
    private Image image1;


    @BeforeEach
    void setUp() {
        // Inicializace testovacích dat s Long ID a BigDecimal
        taxRate1 = new TaxRate();
        taxRate1.setId(1L);
        taxRate1.setName("Standard 21%");
        taxRate1.setRate(new BigDecimal("0.21"));
        taxRate1.setReverseCharge(false);
        allTaxRates = Collections.singletonList(taxRate1);

        design1 = new Design();
        design1.setId(1L);
        design1.setName("Standard Design");
        design1.setImageUrl("path/design1.jpg");
        design1.setActive(true);
        allDesigns = Collections.singletonList(design1);

        glaze1 = new Glaze();
        glaze1.setId(1L);
        glaze1.setName("Clear Glaze");
        glaze1.setImageUrl("path/glaze1.png");
        glaze1.setActive(true);
        allGlazes = Collections.singletonList(glaze1);

        roofColor1 = new RoofColor();
        roofColor1.setId(1L);
        roofColor1.setName("Red Tile");
        roofColor1.setImageUrl("path/roof1.jpg");
        roofColor1.setActive(true);
        allRoofColors = Collections.singletonList(roofColor1);

        addon1 = new Addon();
        addon1.setId(1L);
        addon1.setName("Extra Shelf");
        addon1.setActive(true);
        addon1.setPriceCZK(new BigDecimal("500.00"));
        addon1.setPriceEUR(new BigDecimal("20.00"));
        allAddons = Collections.singletonList(addon1);

        image1 = new Image();
        image1.setId(1L);
        image1.setUrl("/uploads/products/image1.jpg");
        image1.setAltText("Alt text 1");
        image1.setDisplayOrder(0);


        product1 = new Product();
        product1.setId(1L);
        product1.setName("Standard Drevnik");
        product1.setSlug("standard-drevnik");
        product1.setBasePriceCZK(new BigDecimal("10000.00"));
        product1.setBasePriceEUR(new BigDecimal("400.00"));
        product1.setDescription("Description 1");
        product1.setActive(true);
        product1.setCustomisable(false);
        product1.setAvailableTaxRates(new HashSet<>(Collections.singletonList(taxRate1)));
        product1.setImages(new HashSet<>(Collections.singletonList(image1)));
        image1.setProduct(product1);
        product1.setAvailableDesigns(new HashSet<>(allDesigns));
        product1.setAvailableGlazes(new HashSet<>(allGlazes));
        product1.setAvailableRoofColors(new HashSet<>(allRoofColors));


        product2 = new Product();
        product2.setId(2L);
        product2.setName("Custom Drevnik");
        product2.setSlug("custom-drevnik");
        product2.setBasePriceCZK(new BigDecimal("12000.00"));
        product2.setDescription("Description 2");
        product2.setActive(true);
        product2.setCustomisable(true);
        product2.setAvailableTaxRates(new HashSet<>(Collections.singletonList(taxRate1)));
        product2.setImages(new HashSet<>());
        product2.setAvailableDesigns(new HashSet<>());
        product2.setAvailableGlazes(new HashSet<>());
        product2.setAvailableRoofColors(new HashSet<>());
        product2.setAvailableAddons(new HashSet<>(allAddons));


        // Mockování služeb - POUŽÍVÁME SPRÁVNÉ NÁZVY METOD
        // OPRAVA: Používáme addonsService (s 's')
        // Předpokládáme, že AddonsService má metodu getAllActiveAddons()
        lenient().when(addonsService.getAllActiveAddons()).thenReturn(allAddons);
        lenient().when(designService.getAllDesignsSortedByName()).thenReturn(allDesigns);
        lenient().when(glazeService.getAllGlazesSortedByName()).thenReturn(allGlazes);
        lenient().when(roofColorService.getAllRoofColorsSortedByName()).thenReturn(allRoofColors);
        lenient().when(taxRateService.getAllTaxRates()).thenReturn(allTaxRates);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listProducts_ShouldReturnProductsListView() throws Exception {
        Page<Product> productPage = new PageImpl<>(Arrays.asList(product1, product2), PageRequest.of(0, 10), 2);
        when(productService.getAllProducts(any(Pageable.class))).thenReturn(productPage);

        mvc.perform(get("/admin/products"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/products-list"))
                .andExpect(model().attributeExists("productsPage"))
                .andExpect(model().attribute("productsPage", productPage))
                .andExpect(model().attributeExists("pageTitle"));

        verify(productService).getAllProducts(any(Pageable.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void showAddProductForm_ShouldReturnProductFormViewWithSupportData() throws Exception {
        // Mockování služeb pro tento test
        when(designService.getAllDesignsSortedByName()).thenReturn(allDesigns);
        when(glazeService.getAllGlazesSortedByName()).thenReturn(allGlazes);
        when(roofColorService.getAllRoofColorsSortedByName()).thenReturn(allRoofColors);
        // OPRAVA: Používáme addonsService (s 's')
        when(addonsService.getAllActiveAddons()).thenReturn(allAddons);
        when(taxRateService.getAllTaxRates()).thenReturn(allTaxRates);

        mvc.perform(get("/admin/products/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/product-form"))
                .andExpect(model().attributeExists("product"))
                .andExpect(model().attribute("product", instanceOf(Product.class)))
                .andExpect(model().attribute("allDesigns", allDesigns))
                .andExpect(model().attribute("allGlazes", allGlazes))
                .andExpect(model().attribute("allRoofColors", allRoofColors))
                .andExpect(model().attribute("allAddons", allAddons)) // Očekávaný atribut
                .andExpect(model().attribute("allTaxRates", allTaxRates))
                .andExpect(model().attribute("pageTitle", "Přidat nový produkt"));

        // Verify services were called
        verify(designService).getAllDesignsSortedByName();
        verify(glazeService).getAllGlazesSortedByName();
        verify(roofColorService).getAllRoofColorsSortedByName();
        // OPRAVA: Ověřujeme addonsService (s 's')
        verify(addonsService).getAllActiveAddons();
        verify(taxRateService).getAllTaxRates();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void saveProduct_NewProduct_ShouldCreateProductAndRedirect() throws Exception {
        MockMultipartFile imageFile = new MockMultipartFile("imageFiles", "test.jpg", "image/jpeg", "test image content".getBytes());
        String storedFileUrl = "/uploads/products/unique-filename.jpg";
        Long selectedTaxRateId = taxRate1.getId();

        when(fileStorageService.storeFile(any(MultipartFile.class), eq("products"))).thenReturn(storedFileUrl);
        when(productService.createProduct(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(3L);
            assertNotNull(p.getAvailableTaxRates());
            assertTrue(p.getAvailableTaxRates().stream().anyMatch(tr -> tr.getId().equals(selectedTaxRateId)));
            Image savedImage = new Image(); savedImage.setId(10L); savedImage.setUrl(storedFileUrl); savedImage.setProduct(p);
            p.setImages(new HashSet<>(Collections.singletonList(savedImage)));
            // Simulace načtení a přiřazení asociací controllerem (pro kontrolu IDček)
            p.setAvailableDesigns(new HashSet<>(allDesigns));
            p.setAvailableGlazes(new HashSet<>(allGlazes));
            p.setAvailableRoofColors(new HashSet<>(allRoofColors));
            p.setAvailableAddons(new HashSet<>(allAddons));
            return p;
        });
        when(productService.addImageToProduct(anyLong(), any(MultipartFile.class), any(), any(), any())).thenReturn(new Image()); // Mock pro případné volání

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("name", "Nový Produkt");
        params.add("basePriceCZK", "15000.00");
        params.add("basePriceEUR", "600.00");
        params.add("description", "Nový popis");
        params.add("active", "true");
        params.add("customisable", "false");
        params.add("availableDesignIds", design1.getId().toString());
        params.add("availableGlazeIds", glaze1.getId().toString());
        params.add("availableRoofColorIds", roofColor1.getId().toString());
        params.add("availableAddonIds", addon1.getId().toString()); // ID doplňku
        params.add("availableTaxRateIds", selectedTaxRateId.toString());

        mvc.perform(multipart("/admin/products/save")
                        .file(imageFile)
                        .params(params)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(fileStorageService).storeFile(eq(imageFile), eq("products"));

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productService).createProduct(productCaptor.capture());

        Product capturedProduct = productCaptor.getValue();
        assertEquals("Nový Produkt", capturedProduct.getName());
        assertEquals(new BigDecimal("15000.00"), capturedProduct.getBasePriceCZK());
        // Ověření IDček (předpokládáme, že gettery vracejí Set<Long>)
        // Pokud gettery nevracejí Set<Long>, museli bychom iterovat Set<Entity> a získat ID
        assertTrue(capturedProduct.getAvailableTaxRates().stream().anyMatch(tr -> tr.getId().equals(selectedTaxRateId)));
        assertTrue(capturedProduct.getAvailableDesigns().stream().anyMatch(d -> d.getId().equals(design1.getId())));
        assertTrue(capturedProduct.getAvailableGlazes().stream().anyMatch(g -> g.getId().equals(glaze1.getId())));
        assertTrue(capturedProduct.getAvailableRoofColors().stream().anyMatch(rc -> rc.getId().equals(roofColor1.getId())));
        assertTrue(capturedProduct.getAvailableAddons().stream().anyMatch(a -> a.getId().equals(addon1.getId()))); // Ověření doplňku

        verify(productService, atMost(1)).addImageToProduct(anyLong(), any(MultipartFile.class), any(), any(), any());
    }


    @Test
    @WithMockUser(roles = "ADMIN")
    void showEditProductForm_ShouldReturnProductFormViewWithProductData() throws Exception {
        Long productId = product1.getId();
        when(productService.getProductById(productId)).thenReturn(Optional.of(product1));
        when(designService.getAllDesignsSortedByName()).thenReturn(allDesigns);
        when(glazeService.getAllGlazesSortedByName()).thenReturn(allGlazes);
        when(roofColorService.getAllRoofColorsSortedByName()).thenReturn(allRoofColors);
        // OPRAVA: Používáme addonsService (s 's')
        when(addonsService.getAllActiveAddons()).thenReturn(allAddons);
        when(taxRateService.getAllTaxRates()).thenReturn(allTaxRates);

        mvc.perform(get("/admin/products/edit/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/product-form"))
                .andExpect(model().attributeExists("product"))
                .andExpect(model().attribute("product", hasProperty("id", is(productId))))
                .andExpect(model().attribute("allDesigns", allDesigns))
                .andExpect(model().attribute("allGlazes", allGlazes))
                .andExpect(model().attribute("allRoofColors", allRoofColors))
                .andExpect(model().attribute("allAddons", allAddons)) // Očekávaný atribut
                .andExpect(model().attribute("allTaxRates", allTaxRates))
                .andExpect(model().attribute("pageTitle", containsString("Upravit produkt")));

        verify(productService).getProductById(productId);
        verify(designService).getAllDesignsSortedByName();
        verify(glazeService).getAllGlazesSortedByName();
        verify(roofColorService).getAllRoofColorsSortedByName();
        // OPRAVA: Ověřujeme addonsService (s 's')
        verify(addonsService).getAllActiveAddons();
        verify(taxRateService).getAllTaxRates();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void saveProduct_UpdateProduct_ShouldUpdateProductAndRedirect() throws Exception {
        Long productId = product1.getId();
        Long newTaxRateId = 2L;
        taxRate2 = new TaxRate(); taxRate2.setId(newTaxRateId); taxRate2.setName("Reduced 15%"); taxRate2.setRate(new BigDecimal("0.15"));
        allTaxRates = Arrays.asList(taxRate1, taxRate2); // Potřebujeme obě sazby

        MockMultipartFile newImageFile = new MockMultipartFile("imageFiles", "update.jpg", "image/jpeg", "update image content".getBytes());
        String storedFileUrl = "/uploads/products/updated-image.jpg";
        String existingImageIdToDelete = image1.getId().toString();

        when(productService.getProductById(productId)).thenReturn(Optional.of(product1));
        when(fileStorageService.storeFile(any(MultipartFile.class), eq("products"))).thenReturn(storedFileUrl);
        when(productService.updateProduct(eq(productId), any(Product.class), any(Product.class))).thenReturn(Optional.of(product1));
        when(productService.addImageToProduct(eq(productId), any(MultipartFile.class), any(), any(), any())).thenReturn(new Image());
        doNothing().when(productService).deleteImage(eq(image1.getId()));
        when(taxRateService.getTaxRateById(taxRate1.getId())).thenReturn(Optional.of(taxRate1));
        when(taxRateService.getTaxRateById(newTaxRateId)).thenReturn(Optional.of(taxRate2));


        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("id", productId.toString());
        params.add("name", "Upravený Produkt");
        params.add("basePriceCZK", "11000.00");
        params.add("description", "Upravený popis");
        params.add("active", "false");
        params.add("customisable", "false");
        params.add("availableTaxRateIds", newTaxRateId.toString());
        params.add("imagesToDelete", existingImageIdToDelete);
        params.add("availableDesignIds", design1.getId().toString());
        params.add("availableGlazeIds", glaze1.getId().toString());
        params.add("availableRoofColorIds", roofColor1.getId().toString());
        params.add("availableAddonIds", addon1.getId().toString()); // ID doplňku


        mvc.perform(multipart("/admin/products/save")
                        .file(newImageFile)
                        .params(params)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(fileStorageService).storeFile(eq(newImageFile), eq("products"));

        ArgumentCaptor<Product> productDataCaptor = ArgumentCaptor.forClass(Product.class);
        ArgumentCaptor<Product> productToUpdateCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productService).updateProduct(eq(productId), productDataCaptor.capture(), productToUpdateCaptor.capture());

        Product capturedData = productDataCaptor.getValue();
        assertEquals("Upravený Produkt", capturedData.getName());
        // Předpokládáme, že controller naplní Set IDček do data objektu pro updateProduct
        // Zkontrolujeme, že ID nové sazby je v datech z formuláře
        assertNotNull(capturedData.getAvailableTaxRates()); // Pokud controller používá entity
        assertTrue(capturedData.getAvailableTaxRates().stream().anyMatch(tr -> tr.getId().equals(newTaxRateId))); // Pokud controller používá entity


        assertEquals(productId, productToUpdateCaptor.getValue().getId());


        verify(productService, atMost(1)).deleteImage(eq(image1.getId()));
        verify(productService, atMost(1)).addImageToProduct(eq(productId), eq(newImageFile), any(), any(), any());
    }


    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteProduct_ShouldDeleteProductAndRedirect() throws Exception {
        Long productId = product1.getId();
        doNothing().when(productService).deleteProduct(productId);

        mvc.perform(post("/admin/products/delete/{id}", productId)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(productService).deleteProduct(productId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteProductImage_Ajax_ShouldDeleteImageAndReturnOk() throws Exception {
        Long imageId = image1.getId();

        doNothing().when(productService).deleteImage(imageId);

        mvc.perform(delete("/admin/products/images/delete/{imageId}", imageId)
                        .with(csrf())
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk());

        verify(productService).deleteImage(imageId);
    }


    // --- Testy pro chybové stavy ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void showEditProductForm_ShouldReturnNotFound_WhenProductDoesNotExist() throws Exception {
        Long nonExistentId = 999L;
        when(productService.getProductById(nonExistentId)).thenReturn(Optional.empty());

        mvc.perform(get("/admin/products/edit/{id}", nonExistentId))
                .andExpect(status().isNotFound());

        verify(productService).getProductById(nonExistentId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void saveProduct_NewProduct_ShouldReturnFormWithError_WhenValidationFails() throws Exception {
        // Mock služby potřebné pro znovuzobrazení formuláře
        when(designService.getAllDesignsSortedByName()).thenReturn(allDesigns);
        when(glazeService.getAllGlazesSortedByName()).thenReturn(allGlazes);
        when(roofColorService.getAllRoofColorsSortedByName()).thenReturn(allRoofColors);
        // OPRAVA: Používáme addonsService (s 's')
        when(addonsService.getAllActiveAddons()).thenReturn(allAddons);
        when(taxRateService.getAllTaxRates()).thenReturn(allTaxRates);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        // Chybí jméno
        params.add("basePriceCZK", "15000.00");
        params.add("availableTaxRateIds", taxRate1.getId().toString());

        mvc.perform(post("/admin/products/save")
                        .params(params)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/product-form"))
                .andExpect(model().attributeHasFieldErrors("product", "name"))
                .andExpect(model().attributeExists("allDesigns", "allGlazes", "allRoofColors", "allAddons", "allTaxRates")); // Ověření atributů

        verify(productService, never()).createProduct(any(Product.class));
        verify(fileStorageService, never()).storeFile(any(), any());
    }
}