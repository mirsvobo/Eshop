// Soubor: src/test/java/org/example/eshop/service/ProductServiceTest.java
package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.config.PriceConstants;
import org.example.eshop.model.*;
import org.example.eshop.repository.ImageRepository;
import org.example.eshop.repository.ProductRepository;
import org.example.eshop.repository.TaxRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Povolí použití Mockito anotací
class ProductServiceTest implements PriceConstants { // Přidáno implementace PriceConstants

    @Mock private ProductRepository productRepository;
    @Mock private ImageRepository imageRepository;
    @Mock private TaxRateRepository taxRateRepository; // Stále potřeba pro jiné metody, pokud existují
    @Mock private DiscountService discountService;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks private ProductService productService;

    private Product standardProduct;
    private Product customProduct;
    private Product inactiveProduct;
    private TaxRate standardTaxRate;
    private TaxRate reducedTaxRate;
    private ProductConfigurator configurator;
    private Image image1;

    @BeforeEach
    void setUp() {
        // TaxRate konstruktor dle TaxRate.java
        standardTaxRate = new TaxRate();
        standardTaxRate.setId(1L);
        standardTaxRate.setName("Standard 21%");
        standardTaxRate.setRate(new BigDecimal("0.21"));
        standardTaxRate.setReverseCharge(false);

        reducedTaxRate = new TaxRate();
        reducedTaxRate.setId(2L);
        reducedTaxRate.setName("Reduced 12%");
        reducedTaxRate.setRate(new BigDecimal("0.12"));
        reducedTaxRate.setReverseCharge(false);

        image1 = new Image();
        image1.setId(50L);
        image1.setUrl("/uploads/products/img1.jpg");
        image1.setAltText("Obrázek 1");
        image1.setDisplayOrder(0);

        standardProduct = new Product();
        standardProduct.setId(1L);
        standardProduct.setName("Standard Dřevník");
        standardProduct.setSlug("standard-drevnik");
        standardProduct.setActive(true);
        standardProduct.setCustomisable(false);
        // OPRAVA: Používáme setAvailableTaxRates
        standardProduct.setAvailableTaxRates(new HashSet<>(Collections.singletonList(standardTaxRate)));
        standardProduct.setBasePriceCZK(new BigDecimal("1000.00"));
        standardProduct.setBasePriceEUR(new BigDecimal("40.00"));
        standardProduct.setImages(new HashSet<>(List.of(image1)));
        standardProduct.setDiscounts(new HashSet<>()); // Inicializace pro testy cen
        image1.setProduct(standardProduct);

        customProduct = new Product();
        customProduct.setId(2L);
        customProduct.setName("Dřevník na míru");
        customProduct.setSlug("drevnik-na-miru");
        customProduct.setActive(true);
        customProduct.setCustomisable(true);
        // OPRAVA: Používáme setAvailableTaxRates
        customProduct.setAvailableTaxRates(new HashSet<>(Collections.singletonList(standardTaxRate)));
        customProduct.setImages(new HashSet<>());
        customProduct.setDiscounts(new HashSet<>()); // Inicializace

        // ProductConfigurator konstruktor dle ProductConfigurator.java - nemá vlastní ID
        configurator = new ProductConfigurator();
        //configurator.setId(customProduct.getId()); // ID se dědí z Product nebo je vlastní
        configurator.setProduct(customProduct);
        configurator.setMinLength(new BigDecimal("100")); configurator.setMaxLength(new BigDecimal("500"));
        configurator.setMinWidth(new BigDecimal("50")); configurator.setMaxWidth(new BigDecimal("200"));
        configurator.setMinHeight(new BigDecimal("150")); configurator.setMaxHeight(new BigDecimal("300"));
        configurator.setPricePerCmLengthCZK(new BigDecimal("10.00")); configurator.setPricePerCmWidthCZK((new BigDecimal("5.00"))); configurator.setPricePerCmHeightCZK(new BigDecimal("8.00"));
        configurator.setDividerPricePerCmDepthCZK(new BigDecimal("3.00"));
        configurator.setGutterPriceCZK(new BigDecimal("500.00"));
        configurator.setShedPriceCZK(new BigDecimal("2000.00"));
        configurator.setDesignPriceCZK(new BigDecimal("100.00"));
        configurator.setPricePerCmLengthEUR(new BigDecimal("0.40")); configurator.setPricePerCmWidthEUR(new BigDecimal("0.20")); configurator.setPricePerCmHeightEUR(new BigDecimal("0.32"));
        configurator.setDividerPricePerCmDepthEUR(new BigDecimal("0.12")); configurator.setGutterPriceEUR(new BigDecimal("20.00")); configurator.setShedPriceEUR(new BigDecimal("80.00"));
        configurator.setDesignPriceEUR(new BigDecimal("4.00"));
        customProduct.setConfigurator(configurator);

        inactiveProduct = new Product();
        inactiveProduct.setId(3L);
        inactiveProduct.setName("Neaktivní Produkt");
        inactiveProduct.setSlug("neaktivni-produkt");
        inactiveProduct.setActive(false);
        // OPRAVA: Používáme setAvailableTaxRates
        inactiveProduct.setAvailableTaxRates(new HashSet<>(Collections.singletonList(standardTaxRate)));
        inactiveProduct.setImages(new HashSet<>());

        // Lenient mockování
        lenient().when(productRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(standardProduct));
        lenient().when(productRepository.findByIdWithDetails(2L)).thenReturn(Optional.of(customProduct));
        lenient().when(productRepository.findByIdWithDetails(3L)).thenReturn(Optional.of(inactiveProduct));
        lenient().when(productRepository.findByIdWithDetails(999L)).thenReturn(Optional.empty());
        lenient().when(imageRepository.findById(50L)).thenReturn(Optional.of(image1));
        lenient().when(imageRepository.findById(999L)).thenReturn(Optional.empty());
        // Mock taxRateRepository zde není nutný pro základní findById, protože ProductService je nevolá přímo při loadu produktu
    }

    // --- Testy Načítání ---

    @Test
    @DisplayName("getActiveProducts vrátí stránku aktivních produktů")
    void getActiveProducts_ReturnsActiveOnly() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> mockPage = new PageImpl<>(List.of(standardProduct, customProduct), pageable, 2);
        when(productRepository.findByActiveTrue(pageable)).thenReturn(mockPage);

        Page<Product> result = productService.getActiveProducts(pageable);

        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertTrue(result.getContent().stream().allMatch(Product::isActive));
        verify(productRepository).findByActiveTrue(pageable);
    }

    @Test
    @DisplayName("getAllActiveProducts vrátí seznam aktivních produktů")
    void getAllActiveProducts_ReturnsActiveList() {
        List<Product> mockList = List.of(standardProduct, customProduct);
        when(productRepository.findAllByActiveTrue()).thenReturn(mockList);

        List<Product> result = productService.getAllActiveProducts();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(Product::isActive));
        verify(productRepository).findAllByActiveTrue();
    }


    @Test
    @DisplayName("getActiveProductBySlug najde aktivní produkt (s detaily)")
    void getActiveProductBySlug_FindsActiveWithDetails() {
        when(productRepository.findActiveBySlugWithDetails("standard-drevnik")).thenReturn(Optional.of(standardProduct));

        Optional<Product> result = productService.getActiveProductBySlug("standard-drevnik");

        assertTrue(result.isPresent());
        assertEquals(standardProduct.getId(), result.get().getId());
        // OPRAVA: Ověření obsahu kolekce
        assertNotNull(result.get().getAvailableTaxRates());
        assertFalse(result.get().getAvailableTaxRates().isEmpty());
        assertTrue(result.get().getAvailableTaxRates().contains(standardTaxRate));
        verify(productRepository).findActiveBySlugWithDetails("standard-drevnik");
    }

    @Test
    @DisplayName("getProductById najde produkt (s detaily)")
    void getProductById_FindsWithDetails() {
        Optional<Product> result = productService.getProductById(1L);

        assertTrue(result.isPresent());
        assertEquals(standardProduct.getId(), result.get().getId());
        // OPRAVA: Ověření obsahu kolekce
        assertNotNull(result.get().getAvailableTaxRates());
        assertFalse(result.get().getAvailableTaxRates().isEmpty());
        assertTrue(result.get().getAvailableTaxRates().contains(standardTaxRate));
        verify(productRepository).findByIdWithDetails(1L);
    }


    // --- Testy Výpočtu Ceny Na Míru (zůstávají stejné, nezávisí na get/set TaxRate) ---
    @Test
    @DisplayName("calculateDynamicProductPrice spočítá cenu správně (CZK)")
    void calculateDynamicProductPrice_CorrectCalculation_CZK() {
        Map<String, BigDecimal> dimensions = Map.of(
                "length", new BigDecimal("200"),
                "width", new BigDecimal("100"), // Depth
                "height", new BigDecimal("180")
        );
        // Výpočet: (200*10) + (100*5) + (180*8) + (100*3)[přepážka] + 500[okapy] + 2000[kůlna] + 100[design]
        // = 2000 + 500 + 1440 + 300 + 500 + 2000 + 100 = 6840
        BigDecimal expectedPrice = new BigDecimal("6840.00");
        BigDecimal calculatedPrice = productService.calculateDynamicProductPrice(
                customProduct, dimensions, "Nějaký Design", true, true, true, "CZK"
        );
        assertNotNull(calculatedPrice);
        assertEquals(0, expectedPrice.compareTo(calculatedPrice), "Vypočtená dynamická cena nesouhlasí");
    }
    @Test
    @DisplayName("calculateDynamicProductPrice vrátí 0 pro záporný výsledek (CZK)")
    void calculateDynamicProductPrice_NegativeResultBecomesZero_CZK() {
        customProduct.getConfigurator().setPricePerCmLengthCZK(new BigDecimal("-100")); // Make price negative
        Map<String, BigDecimal> dimensions = Map.of(
                "length", new BigDecimal("200"),
                "width", new BigDecimal("100"),
                "height", new BigDecimal("180")
        );

        BigDecimal calculatedPrice = productService.calculateDynamicProductPrice(
                customProduct, dimensions, null, false, false, false, "CZK"
        );

        assertNotNull(calculatedPrice);
        assertEquals(0, BigDecimal.ZERO.compareTo(calculatedPrice), "Záporná vypočtená cena by měla být 0");
    }

    @Test
    @DisplayName("calculateDynamicProductPrice vyhodí výjimku pro neaktivní produkt")
    void calculateDynamicProductPrice_ThrowsForInactiveProduct() {
        Map<String, BigDecimal> dimensions = Map.of("length", new BigDecimal("200"), "width", new BigDecimal("100"), "height", new BigDecimal("180"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            productService.calculateDynamicProductPrice(inactiveProduct, dimensions, null, false, false, false, "CZK");
        });
        assertTrue(exception.getMessage().contains("Cannot calculate price for inactive product"));
    }

    @Test
    @DisplayName("calculateDynamicProductPrice vyhodí výjimku pro produkt bez konfigurátoru")
    void calculateDynamicProductPrice_ThrowsForMissingConfigurator() {
        standardProduct.setCustomisable(true); // Make it customisable
        standardProduct.setConfigurator(null); // Ensure no configurator
        Map<String, BigDecimal> dimensions = Map.of("length", new BigDecimal("200"), "width", new BigDecimal("100"), "height", new BigDecimal("180"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            productService.calculateDynamicProductPrice(standardProduct, dimensions, null, false, false, false, "CZK");
        });
        assertTrue(exception.getMessage().contains("is not customisable or missing configurator"));
    }

    @Test
    @DisplayName("calculateDynamicProductPrice vyhodí výjimku pro chybějící rozměry")
    void calculateDynamicProductPrice_ThrowsForMissingDimensions() {
        Map<String, BigDecimal> dimensions = Map.of("length", new BigDecimal("200"), "width", new BigDecimal("100")); // Height is missing

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            productService.calculateDynamicProductPrice(customProduct, dimensions, null, false, false, false, "CZK");
        });
        assertTrue(exception.getMessage().contains("Missing one or more dimensions"));
    }

    @Test
    @DisplayName("calculateDynamicProductPrice vyhodí výjimku pro rozměry mimo limity")
    void calculateDynamicProductPrice_ThrowsForDimensionOutOfBounds() {
        Map<String, BigDecimal> dimensions = Map.of(
                "length", new BigDecimal("600"), // Too long
                "width", new BigDecimal("100"),
                "height", new BigDecimal("180")
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            productService.calculateDynamicProductPrice(customProduct, dimensions, null, false, false, false, "CZK");
        });
        assertTrue(exception.getMessage().contains("dimension (600 cm) is outside allowed range"));
    }

    @Test
    @DisplayName("calculateDynamicProductPrice vyhodí výjimku pro chybějící cenu v konfigurátoru")
    void calculateDynamicProductPrice_ThrowsForMissingConfigPrice() {
        customProduct.getConfigurator().setPricePerCmLengthCZK(null); // Remove a required price
        Map<String, BigDecimal> dimensions = Map.of( "length", new BigDecimal("200"), "width", new BigDecimal("100"), "height", new BigDecimal("180") );

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            productService.calculateDynamicProductPrice(customProduct, dimensions, null, false, false, false, "CZK");
        });
        assertTrue(exception.getMessage().contains("price configuration 'Length Price/cm' missing"));
    }

    // --- Testy CRUD ---

    @Test
    @DisplayName("createProduct vytvoří produkt a vygeneruje slug")
    void createProduct_GeneratesSlugAndSaves() {
        Product newProduct = new Product();
        newProduct.setName("  Nový Dřevník Test  ");
        newProduct.setActive(true);
        // OPRAVA: Nastavíme Set sazeb
        newProduct.setAvailableTaxRates(new HashSet<>(Collections.singletonList(standardTaxRate))); // Předpokládáme, že controller nastaví entity

        when(productRepository.existsBySlugIgnoreCase("novy-drevnik-test")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(5L); // Simulace ID z DB
            assertEquals("novy-drevnik-test", p.getSlug());
            // Ponecháme kolekce, jak přišly (už by měly být nastavené)
            return p;
        });

        // Služba createProduct nevolá taxRateRepository.findById
        Product savedProduct = productService.createProduct(newProduct);

        assertNotNull(savedProduct);
        assertEquals("novy-drevnik-test", savedProduct.getSlug());
        // OPRAVA: Ověření obsahu kolekce
        assertNotNull(savedProduct.getAvailableTaxRates());
        assertTrue(savedProduct.getAvailableTaxRates().contains(standardTaxRate));
        verify(productRepository).existsBySlugIgnoreCase("novy-drevnik-test");
        verify(productRepository).save(savedProduct);
        // verify(taxRateRepository, never()).findById(anyLong()); // Ověření, že se nevolá findById
    }

    @Test
    @DisplayName("createProduct vyhodí výjimku pro existující slug")
    void createProduct_ThrowsForExistingSlug() {
        Product newProduct = new Product();
        newProduct.setName("Existující Slug Produkt");
        newProduct.setSlug("existujici-slug");
        // OPRAVA: Nastavíme Set sazeb
        newProduct.setAvailableTaxRates(new HashSet<>(Collections.singletonList(standardTaxRate)));

        when(productRepository.existsBySlugIgnoreCase("existujici-slug")).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            productService.createProduct(newProduct);
        });

        assertTrue(exception.getMessage().contains("již existuje"));
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("createProduct vytvoří konfigurátor pro customisable produkt")
    void createProduct_CreatesConfiguratorForCustomisable() {
        Product newCustom = new Product();
        newCustom.setName("Nový na míru");
        newCustom.setCustomisable(true);
        // OPRAVA: Nastavíme Set sazeb
        newCustom.setAvailableTaxRates(new HashSet<>(Collections.singletonList(standardTaxRate)));

        when(productRepository.existsBySlugIgnoreCase(anyString())).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(6L);
            assertNotNull(p.getConfigurator(), "Konfigurátor by měl být vytvořen");
            assertEquals(p, p.getConfigurator().getProduct(), "Konfigurátor by měl být propojen s produktem");
            // Ponecháme kolekce, jak přišly
            return p;
        });

        Product saved = productService.createProduct(newCustom);

        assertNotNull(saved.getConfigurator());
        verify(productRepository).save(saved);
    }
    @Test
    @DisplayName("createProduct vyhodí výjimku, pokud nejsou nastaveny TaxRates")
    void createProduct_ThrowsIfTaxRatesNotSet() {
        Product newProduct = new Product();
        newProduct.setName("Produkt bez sazby");
        // newProduct.setAvailableTaxRates(null); // Nebo prázdný set
        newProduct.setAvailableTaxRates(new HashSet<>());

        when(productRepository.existsBySlugIgnoreCase(anyString())).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            productService.createProduct(newProduct);
        });

        assertTrue(exception.getMessage().contains("Produkt musí mít přiřazenu alespoň jednu daňovou sazbu"));
        verify(productRepository, never()).save(any());
    }


    @Test
    @DisplayName("deleteProduct označí produkt jako neaktivní")
    void deleteProduct_MarksInactive() {
        // Použijeme standardní findById zde, protože delete nemusí načítat detaily
        when(productRepository.findById(1L)).thenReturn(Optional.of(standardProduct));
        when(productRepository.save(any(Product.class))).thenReturn(standardProduct);

        assertTrue(standardProduct.isActive(), "Produkt by měl být aktivní před smazáním");
        productService.deleteProduct(1L);

        assertFalse(standardProduct.isActive(), "Produkt by měl být neaktivní po smazání");
        verify(productRepository).findById(1L);
        verify(productRepository).save(standardProduct);
    }

    @Test
    @DisplayName("deleteProduct vyhodí výjimku pro neexistující ID")
    void deleteProduct_ThrowsForNonExistingId() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> {
            productService.deleteProduct(999L);
        });
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateProduct úspěšně aktualizuje data produktu")
    void updateProduct_Success() {
        long productId = 1L;
        // Data z formuláře
        Product updatedData = new Product();
        updatedData.setName("Aktualizovaný Název");
        updatedData.setDescription("Nový popis.");
        updatedData.setBasePriceCZK(new BigDecimal("1200.00"));
        updatedData.setActive(true);
        updatedData.setSlug("standard-drevnik"); // Slug neměníme
        // OPRAVA: Controller by měl nastavit Set entit na základě ID z formuláře
        updatedData.setAvailableTaxRates(new HashSet<>(Collections.singletonList(reducedTaxRate)));

        // Klon existující entity (načtený v controlleru)
        Product existingProductClone = new Product();
        existingProductClone.setId(productId);
        existingProductClone.setName("Standard Dřevník");
        existingProductClone.setSlug("standard-drevnik");
        // OPRAVA: Původní sazby
        existingProductClone.setAvailableTaxRates(new HashSet<>(Collections.singletonList(standardTaxRate)));

        // ProductService.updateProduct nevolá taxRateRepository.findById
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Volání metody služby
        Optional<Product> resultOpt = productService.updateProduct(productId, updatedData, existingProductClone);

        assertTrue(resultOpt.isPresent());
        Product savedProduct = resultOpt.get();

        // Ověření aktualizovaných dat
        assertEquals("Aktualizovaný Název", savedProduct.getName());
        assertEquals("Nový popis.", savedProduct.getDescription());
        assertEquals(0, new BigDecimal("1200.00").compareTo(savedProduct.getBasePriceCZK()));
        // OPRAVA: Ověření nové sazby v kolekci
        assertNotNull(savedProduct.getAvailableTaxRates());
        assertTrue(savedProduct.getAvailableTaxRates().contains(reducedTaxRate));
        assertFalse(savedProduct.getAvailableTaxRates().contains(standardTaxRate)); // Stará by tam neměla být
        assertTrue(savedProduct.isActive());

        verify(productRepository).save(existingProductClone); // Ukládá se upravená existující entita
        verify(productRepository, never()).findBySlugIgnoreCase(anyString()); // Slug se neměnil
        // verify(taxRateRepository, never()).findById(anyLong()); // Ověření, že se nevolá findById
    }

    @Test
    @DisplayName("updateProduct vyhodí výjimku při změně slugu na existující")
    void updateProduct_ThrowsForExistingSlugConflict() {
        long productId = 1L;
        // Data z formuláře
        Product updatedData = new Product();
        updatedData.setName("Produkt s novým slugem");
        updatedData.setSlug("existujici-slug"); // Nový konfliktní slug
        // OPRAVA: Nastavíme Set sazeb
        updatedData.setAvailableTaxRates(new HashSet<>(Collections.singletonList(standardTaxRate)));

        // Klon existující entity
        Product existingProductClone = new Product();
        existingProductClone.setId(productId);
        existingProductClone.setName("Standard Dřevník");
        existingProductClone.setSlug("standard-drevnik"); // Původní slug
        // OPRAVA: Původní sazby
        existingProductClone.setAvailableTaxRates(new HashSet<>(Collections.singletonList(standardTaxRate)));

        // Konfliktní produkt vrácený repository
        Product conflictingProduct = new Product();
        conflictingProduct.setId(50L);
        conflictingProduct.setSlug("existujici-slug");

        // Mock repository pro kontrolu slugu
        // findBySlugIgnoreCaseAndIdNot se volá PŘED save
        when(productRepository.existsBySlugIgnoreCaseAndIdNot("existujici-slug", productId)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            // Služba by měla vyhodit výjimku PŘED voláním save
            productService.updateProduct(productId, updatedData, existingProductClone);
        });

        assertTrue(exception.getMessage().contains("Produkt se slugem 'existujici-slug' již existuje."));

        verify(productRepository).existsBySlugIgnoreCaseAndIdNot("existujici-slug", productId);
        verify(productRepository, never()).save(any());
    }

    // --- Testy Správy Obrázků (zůstávají stejné, nezávisí na TaxRate) ---
    @Test
    @DisplayName("[addImageToProduct] Úspěšně přidá obrázek pomocí MultipartFile")
    void addImageToProduct_MultipartFile_Success() throws IOException {
        long productId = 1L;
        String originalFileName = "test.jpg";
        String storedFileName = "unique-id.jpg";
        String storedFileUrl = "/uploads/products/" + storedFileName;
        String altText = "Test Alt";
        String titleText = "Test Title";
        Integer displayOrder = 1;
        MockMultipartFile mockFile = new MockMultipartFile("file", originalFileName, MediaType.IMAGE_JPEG_VALUE, "content".getBytes());

        // Použijeme standardní findById pro načtení produktu zde
        when(productRepository.findById(productId)).thenReturn(Optional.of(standardProduct));
        when(fileStorageService.storeFile(any(MultipartFile.class), eq("products"))).thenReturn(storedFileUrl);
        when(imageRepository.save(any(Image.class))).thenAnswer(invocation -> {
            Image img = invocation.getArgument(0);
            img.setId(101L);
            assertEquals(standardProduct, img.getProduct());
            assertEquals(storedFileUrl, img.getUrl());
            assertEquals(altText, img.getAltText());
            assertEquals(titleText, img.getTitleText());
            assertEquals(displayOrder, img.getDisplayOrder());
            return img;
        });

        Image savedImage = productService.addImageToProduct(productId, mockFile, altText, titleText, displayOrder);

        assertNotNull(savedImage);
        assertEquals(101L, savedImage.getId());
        assertEquals(storedFileUrl, savedImage.getUrl());

        verify(productRepository).findById(productId); // Ověření volání standardní metody
        verify(fileStorageService).storeFile(mockFile, "products");
        verify(imageRepository).save(any(Image.class));
    }
    @Test
    @DisplayName("[addImageToProduct] Vyhodí chybu, pokud selže uložení souboru")
    void addImageToProduct_MultipartFile_StorageError() throws IOException {
        long productId = 1L;
        MockMultipartFile mockFile = new MockMultipartFile("file", "error.jpg", MediaType.IMAGE_JPEG_VALUE, "content".getBytes());
        String exceptionMessage = "Disk is full";

        when(fileStorageService.storeFile(any(MultipartFile.class), eq("products"))).thenThrow(new IOException(exceptionMessage));

        IOException thrownException = assertThrows(IOException.class, () -> {
            productService.addImageToProduct(productId, mockFile, "Alt", "Title", 0);
        });

        assertTrue(thrownException.getMessage().contains(exceptionMessage));
        verify(imageRepository, never()).save(any(Image.class));
        verify(productRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("[addImageToProduct] Vyhodí chybu pro neexistující produkt ID (MultipartFile)")
    void addImageToProduct_MultipartFile_ProductNotFound() throws IOException {
        long nonExistentProductId = 999L;
        MockMultipartFile mockFile = new MockMultipartFile("file", "test.jpg", MediaType.IMAGE_JPEG_VALUE, "test".getBytes());
        String storedFileUrl = "/uploads/products/some.jpg";

        when(fileStorageService.storeFile(any(MultipartFile.class), eq("products"))).thenReturn(storedFileUrl);
        // findById vrátí empty
        when(productRepository.findById(nonExistentProductId)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> {
            productService.addImageToProduct(nonExistentProductId, mockFile, "Alt", "Title", 0);
        });

        assertTrue(exception.getMessage().contains("Product not found"));
        verify(fileStorageService).storeFile(mockFile, "products");
        verify(productRepository).findById(nonExistentProductId); // Ověříme volání standardní metody
        verify(imageRepository, never()).save(any());
    }

    @Test
    @DisplayName("[addImageToProduct] Vyhodí chybu pro prázdný soubor (MultipartFile)")
    void addImageToProduct_MultipartFile_EmptyFile() throws IOException {
        long productId = 1L;
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", MediaType.TEXT_PLAIN_VALUE, new byte[0]);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            productService.addImageToProduct(productId, emptyFile, "Alt", "Title", 0);
        });

        assertTrue(exception.getMessage().contains("empty image file"));
        verify(fileStorageService, never()).storeFile(any(), anyString());
        verify(imageRepository, never()).save(any());
        verify(productRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("[deleteImage] Úspěšně smaže obrázek (entitu i soubor)")
    void deleteImage_Success_DeletesEntityAndFile() throws IOException {
        long imageId = 50L;
        String fileUrl = image1.getUrl();
        when(imageRepository.findById(imageId)).thenReturn(Optional.of(image1));
        doNothing().when(imageRepository).delete(image1);
        doNothing().when(fileStorageService).deleteFile(fileUrl);

        productService.deleteImage(imageId);

        verify(imageRepository).findById(imageId);
        verify(imageRepository).delete(image1);
        verify(fileStorageService).deleteFile(fileUrl);
    }

    @Test
    @DisplayName("[deleteImage] Smaže entitu, ale nevolá deleteFile, pokud URL chybí")
    void deleteImage_Success_NoUrlSkipFileDelete() throws IOException {
        long imageId = 50L;
        image1.setUrl(null);
        when(imageRepository.findById(imageId)).thenReturn(Optional.of(image1));
        doNothing().when(imageRepository).delete(image1);

        productService.deleteImage(imageId);

        verify(imageRepository).findById(imageId);
        verify(imageRepository).delete(image1);
        verify(fileStorageService, never()).deleteFile(anyString());
    }

    @Test
    @DisplayName("[deleteImage] Vyhodí výjimku pro neexistující ID obrázku")
    void deleteImage_NotFound() throws IOException {
        long nonExistentImageId = 999L;
        when(imageRepository.findById(nonExistentImageId)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> {
            productService.deleteImage(nonExistentImageId);
        });
        assertTrue(exception.getMessage().contains("Image not found"));
        verify(imageRepository, never()).delete(any());
        verify(fileStorageService, never()).deleteFile(anyString());
    }

    @Test
    @DisplayName("[deleteImage] Zpracuje chybu při mazání souboru, ale entitu smaže")
    void deleteImage_FileDeleteError_EntityStillDeleted() throws IOException {
        long imageId = 50L;
        String fileUrl = image1.getUrl();
        when(imageRepository.findById(imageId)).thenReturn(Optional.of(image1));
        doNothing().when(imageRepository).delete(image1);
        doThrow(new IOException("Permission denied")).when(fileStorageService).deleteFile(fileUrl);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            productService.deleteImage(imageId);
        });
        assertTrue(exception.getMessage().contains("Failed to delete image"));
        assertTrue(exception.getCause() instanceof IOException);

        verify(imageRepository).findById(imageId);
        verify(imageRepository).delete(image1);
        verify(fileStorageService).deleteFile(fileUrl);
    }


    // --- Testy Generování Slugu (zůstávají stejné) ---
    @Test
    @DisplayName("generateSlug správně generuje slugy")
    void generateSlug_GeneratesCorrectly() {
        assertEquals("ahoj-svete", ProductService.generateSlug(" Ahoj Světe! "));
        assertEquals("produkt-s-cisly-123", ProductService.generateSlug("Produkt s čísly 123"));
        assertEquals("specialni-znakyescrzyaien", ProductService.generateSlug("Speciální znakyěščřžýáíéň"));
        assertEquals("vic-pomlcek", ProductService.generateSlug("  Víc---pomlček--  "));
        assertEquals("pomlcka-na-konci", ProductService.generateSlug("-Pomlčka na konci-"));
        assertEquals("", ProductService.generateSlug(""));
        assertEquals("", ProductService.generateSlug(null));
    }

}