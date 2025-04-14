package org.example.eshop.admin.controller; // Balíček musí být admin.controller

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.config.SecurityTestConfig; // Import SecurityTestConfig
import org.example.eshop.model.*;
import org.example.eshop.repository.*;
import org.example.eshop.service.*; // Import service tříd
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
import org.springframework.mock.web.MockMultipartFile; // <-- Přidáno pro test uploadu
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.multipart.MultipartFile; // <-- Přidáno pro test uploadu

import java.io.IOException; // <-- Přidáno pro IOException
import java.math.BigDecimal;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
    @MockBean private ImageRepository imageRepository; // <-- Přidáno
    @MockBean private FileStorageService fileStorageService; // <-- Přidáno

    private Product product1, product2;
    private TaxRate taxRate21;
    private Design design1;
    private Glaze glaze1;
    private RoofColor roofColor1;
    private Addon addon1;
    private ProductConfigurator testConfigurator;
    private Image image1; // <-- Přidáno

    @BeforeEach
    void setUp() {
        taxRate21 = new TaxRate(1L, "21%", new BigDecimal("0.21"), false, null);
        design1 = new Design(); design1.setId(10L); design1.setName("Klasik");
        glaze1 = new Glaze(); glaze1.setId(20L); glaze1.setName("Ořech");
        roofColor1 = new RoofColor(); roofColor1.setId(30L); roofColor1.setName("Antracit");
        addon1 = new Addon(); addon1.setId(40L); addon1.setName("Polička");
        image1 = new Image(); image1.setId(50L); image1.setUrl("/uploads/products/img1.jpg"); image1.setAltText("Obrázek 1"); image1.setDisplayOrder(0); // <-- Přidáno

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
        product1.setImages(new ArrayList<>(List.of(image1))); // <-- Přiřazení obrázku
        image1.setProduct(product1); // <-- Obousměrná vazba

        testConfigurator = new ProductConfigurator(); // Vytvoříme konfigurátor
        testConfigurator.setId(2L); // ID musí odpovídat produktu
        testConfigurator.setMinLength(new BigDecimal("100.00"));
        testConfigurator.setMaxLength(new BigDecimal("500.00"));
        testConfigurator.setMinWidth(new BigDecimal("50.00"));
        testConfigurator.setMaxWidth(new BigDecimal("200.00"));
        testConfigurator.setMinHeight(new BigDecimal("150.00"));
        testConfigurator.setMaxHeight(new BigDecimal("300.00"));
        // ... (další nastavení konfigurátoru, pokud je potřeba) ...

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
        product2.setImages(new ArrayList<>()); // Prázdný seznam obrázků

        // Lenient mockování (zůstává a přidáváme pro imageRepository)
        lenient().when(currencyService.getSelectedCurrency()).thenReturn("CZK");
        lenient().when(designRepository.findAllById(argThat(iterable -> iterable != null && !iterable.iterator().hasNext()))).thenReturn(Collections.emptyList());
        lenient().when(glazeRepository.findAllById(argThat(iterable -> iterable != null && !iterable.iterator().hasNext()))).thenReturn(Collections.emptyList());
        lenient().when(roofColorRepository.findAllById(argThat(iterable -> iterable != null && !iterable.iterator().hasNext()))).thenReturn(Collections.emptyList());
        lenient().when(addonsRepository.findAllById(argThat(iterable -> iterable != null && !iterable.iterator().hasNext()))).thenReturn(Collections.emptyList());
        lenient().when(imageRepository.findById(50L)).thenReturn(Optional.of(image1)); // <-- Přidáno
        lenient().when(imageRepository.findById(999L)).thenReturn(Optional.empty()); // <-- Přidáno

        // Mock pro načítání atributů ve formuláři (zůstává)
        lenient().when(taxRateService.getAllTaxRates()).thenReturn(Collections.singletonList(taxRate21));
        lenient().when(designRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(design1));
        lenient().when(glazeRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(glaze1));
        lenient().when(roofColorRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(roofColor1));
        lenient().when(addonsRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(addon1));

        // Lenient mockování pro getProductById (zůstává)
        lenient().when(productService.getProductById(1L)).thenReturn(Optional.of(product1));
        lenient().when(productService.getProductById(2L)).thenReturn(Optional.of(product2));
        lenient().when(productService.getProductById(99L)).thenReturn(Optional.empty());
    }

    // --- Testy GET (seznam, nový produkt) - beze změny ---
    @Test
    @DisplayName("GET /admin/products - Zobrazí seznam produktů")
    void listProducts_ShouldReturnListView() throws Exception {
        Page<Product> productPage = new PageImpl<>(Arrays.asList(product1, product2), PageRequest.of(0, 15), 2);
        when(productService.getAllProducts(any(Pageable.class))).thenReturn(productPage);

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/products"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/products-list"))
                .andExpect(model().attributeExists("productPage"));
    }

    @Test
    @DisplayName("GET /admin/products/new - Zobrazí formulář pro nový produkt")
    void showCreateProductForm_ShouldReturnFormView() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/products/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/product-form"))
                .andExpect(model().attributeExists("product", "allTaxRates", "allDesigns", "allGlazes", "allRoofColors", "allAddons"));
    }

    // --- Testy GET /edit (aktualizované) ---
    @Test
    @DisplayName("GET /admin/products/{id}/edit - Zobrazí formulář pro úpravu STANDARD produktu (včetně obrázků)")
    void showEditProductForm_Standard_ShouldReturnFormViewWithImages() throws Exception {
        // getProductById mockováno v setUp
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/products/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/product-form"))
                .andExpect(model().attributeExists("product", "allTaxRates", "allDesigns", "allGlazes", "allRoofColors", "allAddons", "newImage")) // Ověření existence newImage
                .andExpect(model().attribute("product", hasProperty("id", is(1L))))
                .andExpect(model().attribute("product", hasProperty("customisable", is(false))))
                .andExpect(model().attribute("product", hasProperty("images", hasSize(1)))) // Ověření, že obrázky jsou v modelu
                .andExpect(model().attribute("product", hasProperty("images", hasItem(hasProperty("id", is(image1.getId())))))) // Ověření konkrétního obrázku
                .andExpect(model().attribute("pageTitle", containsString("Upravit")));

        verify(productService).getProductById(1L);
    }

    @Test
    @DisplayName("GET /admin/products/{id}/edit - Zobrazí formulář pro úpravu CUSTOM produktu")
    void showEditProductForm_Custom_ShouldReturnFormViewWithConfig() throws Exception {
        // getProductById mockováno v setUp
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/products/2/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/product-form"))
                .andExpect(model().attributeExists("product", "allTaxRates", "allDesigns", "allGlazes", "allRoofColors", "allAddons", "newImage"))
                .andExpect(model().attribute("product", hasProperty("id", is(2L))))
                .andExpect(model().attribute("product", hasProperty("customisable", is(true))))
                .andExpect(model().attribute("product", hasProperty("configurator", notNullValue())))
                .andExpect(model().attribute("product", hasProperty("images", empty()))); // Custom produkt nemá obrázky v testu
        verify(productService).getProductById(2L);
    }

    @Test
    @DisplayName("GET /admin/products/{id}/edit - Nenalezeno - Přesměruje na seznam")
    void showEditProductForm_NotFound_ShouldRedirect() throws Exception {
        // getProductById mockováno v setUp
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/products/99/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"));
        verify(productService).getProductById(99L);
    }

    // --- Testy POST (create, update) - beze změny ---
    // ... (stávající testy pro create a update produktu) ...

    // --- Testy POST Delete - beze změny ---
    @Test
    @DisplayName("POST /admin/products/{id}/delete - Úspěšně deaktivuje produkt")
    void deleteProduct_Success() throws Exception {
        long productId = 1L;
        doNothing().when(productService).deleteProduct(productId);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/products/{id}/delete", productId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"));

        verify(productService).deleteProduct(productId);
    }

    // --- NOVÉ TESTY pro Upload/Delete obrázků ---

    @Test
    @DisplayName("POST /admin/products/{productId}/images/upload - Úspěšný upload")
    void uploadProductImage_Success() throws Exception {
        long productId = 1L;
        MockMultipartFile mockFile = new MockMultipartFile(
                "imageFile", // Název parametru v controlleru
                "hello.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "Hello, World!".getBytes()
        );
        String altText = "Alt Popis";
        String titleText = "Title Popis";
        Integer displayOrder = 1;

        Image savedImage = new Image(); savedImage.setId(101L); savedImage.setUrl("/uploads/products/new.jpg");
        when(productService.addImageToProduct(eq(productId), any(MultipartFile.class), eq(altText), eq(titleText), eq(displayOrder)))
                .thenReturn(savedImage);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/admin/products/{productId}/images/upload", productId) // Použijeme multipart
                                .file(mockFile)
                                .param("altText", altText)
                                .param("titleText", titleText)
                                .param("displayOrder", String.valueOf(displayOrder))
                        // Bez .with(csrf()) protože je vypnuté v SecurityTestConfig
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products/" + productId + "/edit"))
                .andExpect(flash().attributeExists("imageSuccess"));

        verify(productService).addImageToProduct(eq(productId), any(MultipartFile.class), eq(altText), eq(titleText), eq(displayOrder));
    }

    @Test
    @DisplayName("POST /admin/products/{productId}/images/upload - Chyba (prázdný soubor)")
    void uploadProductImage_EmptyFile() throws Exception {
        long productId = 1L;
        MockMultipartFile mockFile = new MockMultipartFile(
                "imageFile", // Název parametru
                "empty.txt",
                MediaType.TEXT_PLAIN_VALUE,
                new byte[0] // Prázdný obsah
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart("/admin/products/{productId}/images/upload", productId)
                        .file(mockFile)
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products/" + productId + "/edit"))
                .andExpect(flash().attributeExists("imageError"))
                .andExpect(flash().attribute("imageError", containsString("Nebyl vybrán žádný soubor")));

        verify(productService, never()).addImageToProduct(anyLong(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("POST /admin/products/{productId}/images/upload - Chyba (produkt nenalezen)")
    void uploadProductImage_ProductNotFound() throws Exception {
        long nonExistentProductId = 99L;
        MockMultipartFile mockFile = new MockMultipartFile("imageFile", "test.jpg", MediaType.IMAGE_JPEG_VALUE, "test".getBytes());
        when(productService.addImageToProduct(eq(nonExistentProductId), any(), any(), any(), any()))
                .thenThrow(new EntityNotFoundException("Produkt nenalezen."));

        mockMvc.perform(MockMvcRequestBuilders.multipart("/admin/products/{productId}/images/upload", nonExistentProductId)
                        .file(mockFile)
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products")) // Přesměrování na seznam
                .andExpect(flash().attributeExists("errorMessage"))
                .andExpect(flash().attribute("errorMessage", containsString("Produkt nenalezen")));

        verify(productService).addImageToProduct(eq(nonExistentProductId), any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /admin/products/{productId}/images/upload - Chyba (IO chyba při ukládání)")
    void uploadProductImage_StorageError() throws Exception {
        long productId = 1L;
        MockMultipartFile mockFile = new MockMultipartFile("imageFile", "test.jpg", MediaType.IMAGE_JPEG_VALUE, "test".getBytes());
        when(productService.addImageToProduct(eq(productId), any(), any(), any(), any()))
                .thenThrow(new IOException("Disk full"));

        mockMvc.perform(MockMvcRequestBuilders.multipart("/admin/products/{productId}/images/upload", productId)
                        .file(mockFile)
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products/" + productId + "/edit"))
                .andExpect(flash().attributeExists("imageError"))
                .andExpect(flash().attribute("imageError", containsString("Chyba při ukládání souboru: Disk full")));

        verify(productService).addImageToProduct(eq(productId), any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /admin/products/images/{imageId}/delete - Úspěšné smazání")
    void deleteProductImage_Success() throws Exception {
        long imageId = 50L;
        long productId = 1L;
        // Mock imageRepository.findById v setUp()
        doNothing().when(productService).deleteImage(imageId);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/products/images/{imageId}/delete", imageId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products/" + productId + "/edit")) // Očekáváme přesměrování zpět na editaci produktu
                .andExpect(flash().attributeExists("imageSuccess"));

        verify(productService).deleteImage(imageId);
        verify(imageRepository).findById(imageId); // Ověříme, že se ID produktu hledalo přes repo
    }

    @Test
    @DisplayName("POST /admin/products/images/{imageId}/delete - Chyba (obrázek nenalezen)")
    void deleteProductImage_NotFound() throws Exception {
        long nonExistentImageId = 999L;
        String errorMessage = "Image not found: " + nonExistentImageId;
        // Mock imageRepository.findById v setUp()
        doThrow(new EntityNotFoundException(errorMessage)).when(productService).deleteImage(nonExistentImageId);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/products/images/{imageId}/delete", nonExistentImageId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products")) // Přesměrování na seznam, protože ID produktu neznáme
                .andExpect(flash().attribute("imageError", is(errorMessage)));

        verify(productService).deleteImage(nonExistentImageId);
        verify(imageRepository).findById(nonExistentImageId); // Zkusí najít ID pro productId
    }

    @Test
    @DisplayName("POST /admin/products/images/update-order - Úspěšná aktualizace pořadí")
    void updateImageOrder_Success() throws Exception {
        long imageId1 = 50L; // image1 je v setUp mockován pro findById
        int newOrder = 5;

        // Mock save, aby vrátil upravený obrázek
        when(imageRepository.save(any(Image.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/products/images/update-order")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("displayOrder_" + imageId1, String.valueOf(newOrder))
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products/1/edit")) // Přesměrování na editaci produktu 1
                .andExpect(flash().attributeExists("imageSuccess"));

        verify(imageRepository).findById(imageId1);
        verify(imageRepository).save(argThat(img -> img.getId().equals(imageId1) && Objects.equals(img.getDisplayOrder(), newOrder)));
    }

}