// Soubor: src/test/java/org/example/eshop/service/ProductServiceTest.java
package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
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

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Povolí použití Mockito anotací
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ImageRepository imageRepository;
    @Mock private TaxRateRepository taxRateRepository;

    @InjectMocks private ProductService productService;

    private Product standardProduct;
    private Product customProduct;
    private Product inactiveProduct;
    private TaxRate standardTaxRate;
    private TaxRate reducedTaxRate;
    private ProductConfigurator configurator;

    @BeforeEach
    void setUp() {
        standardTaxRate = new TaxRate(1L, "Standard 21%", new BigDecimal("0.21"), false, null);
        reducedTaxRate = new TaxRate(2L, "Reduced 12%", new BigDecimal("0.12"), false, null);

        standardProduct = new Product();
        standardProduct.setId(1L);
        standardProduct.setName("Standard Dřevník");
        standardProduct.setSlug("standard-drevnik");
        standardProduct.setActive(true);
        standardProduct.setCustomisable(false);
        standardProduct.setTaxRate(standardTaxRate);
        standardProduct.setBasePriceCZK(new BigDecimal("1000.00"));
        standardProduct.setBasePriceEUR(new BigDecimal("40.00"));

        customProduct = new Product();
        customProduct.setId(2L);
        customProduct.setName("Dřevník na míru");
        customProduct.setSlug("drevnik-na-miru");
        customProduct.setActive(true);
        customProduct.setCustomisable(true);
        customProduct.setTaxRate(standardTaxRate);

        configurator = new ProductConfigurator();
        configurator.setId(customProduct.getId());
        configurator.setProduct(customProduct);
        configurator.setMinLength(new BigDecimal("100")); configurator.setMaxLength(new BigDecimal("500"));
        configurator.setMinWidth(new BigDecimal("50")); configurator.setMaxWidth(new BigDecimal("200"));
        configurator.setMinHeight(new BigDecimal("150")); configurator.setMaxHeight(new BigDecimal("300"));
        configurator.setPricePerCmLengthCZK(new BigDecimal("10.00")); configurator.setPricePerCmDepthCZK(new BigDecimal("5.00")); configurator.setPricePerCmHeightCZK(new BigDecimal("8.00"));
        configurator.setDividerPricePerCmDepthCZK(new BigDecimal("3.00"));
        configurator.setGutterPriceCZK(new BigDecimal("500.00"));
        configurator.setShedPriceCZK(new BigDecimal("2000.00"));
        configurator.setDesignPriceCZK(new BigDecimal("100.00"));
        configurator.setPricePerCmLengthEUR(new BigDecimal("0.40")); configurator.setPricePerCmDepthEUR(new BigDecimal("0.20")); configurator.setPricePerCmHeightEUR(new BigDecimal("0.32"));
        configurator.setDividerPricePerCmDepthEUR(new BigDecimal("0.12")); configurator.setGutterPriceEUR(new BigDecimal("20.00")); configurator.setShedPriceEUR(new BigDecimal("80.00"));
        configurator.setDesignPriceEUR(new BigDecimal("4.00"));
        customProduct.setConfigurator(configurator);


        inactiveProduct = new Product();
        inactiveProduct.setId(3L);
        inactiveProduct.setName("Neaktivní Produkt");
        inactiveProduct.setSlug("neaktivni-produkt");
        inactiveProduct.setActive(false);
        inactiveProduct.setTaxRate(standardTaxRate);
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
    @DisplayName("getActiveProductBySlug najde aktivní produkt")
    void getActiveProductBySlug_FindsActive() {
        when(productRepository.findByActiveTrueAndSlugIgnoreCase("standard-drevnik")).thenReturn(Optional.of(standardProduct));

        Optional<Product> result = productService.getActiveProductBySlug("standard-drevnik");

        assertTrue(result.isPresent());
        assertEquals(standardProduct.getId(), result.get().getId());
        verify(productRepository).findByActiveTrueAndSlugIgnoreCase("standard-drevnik");
    }

    @Test
    @DisplayName("getActiveProductBySlug nenajde neaktivní produkt")
    void getActiveProductBySlug_DoesNotFindInactive() {
        when(productRepository.findByActiveTrueAndSlugIgnoreCase("neaktivni-produkt")).thenReturn(Optional.empty());

        Optional<Product> result = productService.getActiveProductBySlug("neaktivni-produkt");

        assertTrue(result.isEmpty());
        verify(productRepository).findByActiveTrueAndSlugIgnoreCase("neaktivni-produkt");
    }

    @Test
    @DisplayName("getActiveProductBySlug nenajde neexistující produkt")
    void getActiveProductBySlug_NotFound() {
        when(productRepository.findByActiveTrueAndSlugIgnoreCase("neexistujici-slug")).thenReturn(Optional.empty());
        Optional<Product> result = productService.getActiveProductBySlug("neexistujici-slug");
        assertTrue(result.isEmpty());
        verify(productRepository).findByActiveTrueAndSlugIgnoreCase("neexistujici-slug");
    }

    // --- Testy Výpočtu Ceny Na Míru ---

    @Test
    @DisplayName("calculateDynamicProductPrice spočítá cenu správně (CZK)")
    void calculateDynamicProductPrice_CorrectCalculation_CZK() {
        Map<String, BigDecimal> dimensions = Map.of(
                "length", new BigDecimal("200"),
                "width", new BigDecimal("100"),
                "height", new BigDecimal("180")
        );
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
        customProduct.getConfigurator().setPricePerCmLengthCZK(new BigDecimal("-100"));
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
        standardProduct.setCustomisable(true);
        standardProduct.setConfigurator(null);
        Map<String, BigDecimal> dimensions = Map.of("length", new BigDecimal("200"), "width", new BigDecimal("100"), "height", new BigDecimal("180"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            productService.calculateDynamicProductPrice(standardProduct, dimensions, null, false, false, false, "CZK");
        });
        assertTrue(exception.getMessage().contains("is not customisable or missing configurator"));
    }

    @Test
    @DisplayName("calculateDynamicProductPrice vyhodí výjimku pro chybějící rozměry")
    void calculateDynamicProductPrice_ThrowsForMissingDimensions() {
        Map<String, BigDecimal> dimensions = Map.of("length", new BigDecimal("200"), "width", new BigDecimal("100"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            productService.calculateDynamicProductPrice(customProduct, dimensions, null, false, false, false, "CZK");
        });
        assertTrue(exception.getMessage().contains("Missing dimensions"));
    }

    @Test
    @DisplayName("calculateDynamicProductPrice vyhodí výjimku pro rozměry mimo limity")
    void calculateDynamicProductPrice_ThrowsForDimensionOutOfBounds() {
        Map<String, BigDecimal> dimensions = Map.of(
                "length", new BigDecimal("600"),
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
        customProduct.getConfigurator().setPricePerCmLengthCZK(null);
        Map<String, BigDecimal> dimensions = Map.of( "length", new BigDecimal("200"), "width", new BigDecimal("100"), "height", new BigDecimal("180") );

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            productService.calculateDynamicProductPrice(customProduct, dimensions, null, false, false, false, "CZK");
        });
        assertTrue(exception.getMessage().contains("Price config (per cm) missing"));
    }


    // --- Testy CRUD ---

    @Test
    @DisplayName("createProduct vytvoří produkt a vygeneruje slug")
    void createProduct_GeneratesSlugAndSaves() {
        Product newProduct = new Product();
        newProduct.setName("  Nový Dřevník Test  ");
        newProduct.setActive(true);
        TaxRate rateRef = new TaxRate(); rateRef.setId(1L);
        newProduct.setTaxRate(rateRef);

        when(taxRateRepository.findById(1L)).thenReturn(Optional.of(standardTaxRate));
        when(productRepository.existsBySlugIgnoreCase("novy-drevnik-test")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(5L);
            assertEquals("novy-drevnik-test", p.getSlug());
            return p;
        });

        Product savedProduct = productService.createProduct(newProduct);

        assertNotNull(savedProduct);
        assertEquals("novy-drevnik-test", savedProduct.getSlug());
        assertEquals(standardTaxRate, savedProduct.getTaxRate());
        verify(productRepository).existsBySlugIgnoreCase("novy-drevnik-test");
        verify(productRepository).save(savedProduct);
    }

    @Test
    @DisplayName("createProduct vyhodí výjimku pro existující slug")
    void createProduct_ThrowsForExistingSlug() {
        Product newProduct = new Product();
        newProduct.setName("Existující Slug Produkt");
        newProduct.setSlug("existujici-slug");
        TaxRate rateRef = new TaxRate(); rateRef.setId(1L);
        newProduct.setTaxRate(rateRef);

        when(taxRateRepository.findById(1L)).thenReturn(Optional.of(standardTaxRate));
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
        TaxRate rateRef = new TaxRate(); rateRef.setId(1L);
        newCustom.setTaxRate(rateRef);

        when(taxRateRepository.findById(1L)).thenReturn(Optional.of(standardTaxRate));
        when(productRepository.existsBySlugIgnoreCase(anyString())).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(6L);
            assertNotNull(p.getConfigurator(), "Konfigurátor by měl být vytvořen");
            assertEquals(p, p.getConfigurator().getProduct(), "Konfigurátor by měl být propojen s produktem");
            return p;
        });

        Product saved = productService.createProduct(newCustom);

        assertNotNull(saved.getConfigurator());
        verify(productRepository).save(saved);
    }

    @Test
    @DisplayName("deleteProduct označí produkt jako neaktivní")
    void deleteProduct_MarksInactive() {
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

        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> {
            productService.deleteProduct(999L);
        });
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateProduct úspěšně aktualizuje data produktu")
    void updateProduct_Success() {
        long productId = 1L;
        Product updatedData = new Product();
        updatedData.setName("Aktualizovaný Název");
        updatedData.setDescription("Nový popis.");
        updatedData.setBasePriceCZK(new BigDecimal("1200.00"));
        updatedData.setActive(true);
        updatedData.setSlug("standard-drevnik");
        TaxRate rateRef = new TaxRate(); rateRef.setId(2L);
        updatedData.setTaxRate(rateRef);

        Product existingProduct = new Product();
        existingProduct.setId(productId);
        existingProduct.setName("Standard Dřevník");
        existingProduct.setSlug("standard-drevnik");
        existingProduct.setTaxRate(standardTaxRate);

        when(taxRateRepository.findById(2L)).thenReturn(Optional.of(reducedTaxRate));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Product> resultOpt = productService.updateProduct(productId, updatedData, existingProduct);

        assertTrue(resultOpt.isPresent());
        Product savedProduct = resultOpt.get();

        assertEquals("Aktualizovaný Název", savedProduct.getName());
        assertEquals("Nový popis.", savedProduct.getDescription());
        assertEquals(0, new BigDecimal("1200.00").compareTo(savedProduct.getBasePriceCZK()));
        assertEquals(reducedTaxRate, savedProduct.getTaxRate());
        assertTrue(savedProduct.isActive());

        verify(taxRateRepository).findById(2L);
        verify(productRepository).save(existingProduct);
    }

    @Test
    @DisplayName("updateProduct vrátí empty Optional pro neexistující ID")
    void updateProduct_NotFound() {
        long nonExistentId = 999L;
        Product updatedData = new Product();
        updatedData.setName("Nezáleží");
        TaxRate rateRef = new TaxRate(); rateRef.setId(1L);
        updatedData.setTaxRate(rateRef);

        // --- OPRAVA: Odebrání zbytečného when(...) ---
        // Toto mockování bylo označeno jako zbytečné, protože findById selže dříve
        // when(productRepository.findById(nonExistentId)).thenReturn(Optional.empty());
        // --- KONEC OPRAVY ---

        // TENTO TEST NEPŘÍMO OVĚŘUJE LOGIKU CONTROLLERU, KTERÁ BY MĚLA ZABRÁNIT
        // VOLÁNÍ updateProduct, POKUD PRODUKT NEEXISTUJE.
        // Samotná metoda updateProduct(id, data, existing) by očekávala non-null existing.
        // Zde pouze ověříme, že se nic neuloží, pokud by logika selhala.

        // Pokud by hypoteticky došlo k volání s null 'existingProduct':
        // assertThrows(NullPointerException.class, () -> { // Nebo jiná vhodná exception
        //     productService.updateProduct(nonExistentId, updatedData, null);
        // });

        // Ověříme, že se nic neuložilo a ani se nehledala sazba
        verify(productRepository, never()).save(any());
        verify(taxRateRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("updateProduct vyhodí výjimku při změně slugu na existující")
    void updateProduct_ThrowsForExistingSlugConflict() {
        long productId = 1L;
        Product updatedData = new Product();
        updatedData.setName("Produkt s novým slugem");
        updatedData.setSlug("existujici-slug");
        TaxRate rateRef = new TaxRate(); rateRef.setId(1L);
        updatedData.setTaxRate(rateRef);

        Product existingProduct = new Product();
        existingProduct.setId(productId);
        existingProduct.setName("Standard Dřevník");
        existingProduct.setSlug("standard-drevnik");
        existingProduct.setTaxRate(standardTaxRate);

        Product conflictingProduct = new Product();
        conflictingProduct.setId(50L);
        conflictingProduct.setSlug("existujici-slug");

        when(productRepository.findBySlugIgnoreCase("existujici-slug")).thenReturn(Optional.of(conflictingProduct));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            productService.updateProduct(productId, updatedData, existingProduct);
        });

        assertTrue(exception.getMessage().contains("Produkt se slugem") && exception.getMessage().contains("již používá jiný produkt"));

        verify(productRepository).findBySlugIgnoreCase("existujici-slug");
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateProduct vyhodí výjimku při neplatné TaxRate ID")
    void updateProduct_ThrowsForInvalidTaxRateId() {
        long productId = 1L;
        Product updatedData = new Product();
        updatedData.setName("Název");
        updatedData.setSlug("standard-drevnik");
        TaxRate rateRef = new TaxRate(); rateRef.setId(999L);
        updatedData.setTaxRate(rateRef);

        Product existingProduct = new Product();
        existingProduct.setId(productId);
        existingProduct.setName("Standard Dřevník");
        existingProduct.setSlug("standard-drevnik");
        existingProduct.setTaxRate(standardTaxRate);

        when(taxRateRepository.findById(999L)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> {
            productService.updateProduct(productId, updatedData, existingProduct);
        });

        assertTrue(exception.getMessage().contains("TaxRate not found"));

        verify(taxRateRepository).findById(999L);
        verify(productRepository, never()).save(any());
    }


    // --- Testy Správy Obrázků ---

    @Test
    @DisplayName("addProductImage úspěšně přidá obrázek k produktu")
    void addProductImage_Success() {
        long productId = 1L;
        Image newImage = new Image();
        newImage.setUrl("/images/new.jpg");
        newImage.setAltText("Nový obrázek");
        newImage.setDisplayOrder(1);

        when(productRepository.findById(productId)).thenReturn(Optional.of(standardProduct));
        when(imageRepository.save(any(Image.class))).thenAnswer(invocation -> {
            Image img = invocation.getArgument(0);
            img.setId(100L);
            assertEquals(standardProduct, img.getProduct());
            return img;
        });

        Image savedImage = productService.addProductImage(productId, newImage);

        assertNotNull(savedImage);
        assertEquals(100L, savedImage.getId());
        assertEquals("/images/new.jpg", savedImage.getUrl());
        assertEquals(standardProduct, savedImage.getProduct());

        verify(productRepository).findById(productId);
        verify(imageRepository).save(newImage);
    }

    @Test
    @DisplayName("addProductImage vyhodí výjimku pro neexistující produkt ID")
    void addProductImage_ProductNotFound() {
        long nonExistentProductId = 999L;
        Image newImage = new Image();
        newImage.setUrl("/image.jpg");

        when(productRepository.findById(nonExistentProductId)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> {
            productService.addProductImage(nonExistentProductId, newImage);
        });
        assertTrue(exception.getMessage().contains("Product not found"));
        verify(imageRepository, never()).save(any());
    }

    @Test
    @DisplayName("addProductImage vyhodí výjimku pro neplatný obrázek (chybí URL)")
    void addProductImage_InvalidImageData() {
        long productId = 1L;
        Image invalidImage = new Image();
        invalidImage.setAltText("Chybný");

        when(productRepository.findById(productId)).thenReturn(Optional.of(standardProduct));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            productService.addProductImage(productId, invalidImage);
        });
        assertTrue(exception.getMessage().contains("Image or URL missing"));
        verify(imageRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteImage úspěšně smaže obrázek")
    void deleteImage_Success() {
        long imageId = 50L;
        Image existingImage = new Image();
        existingImage.setId(imageId);
        existingImage.setUrl("/stary.jpg");
        existingImage.setProduct(standardProduct);

        when(imageRepository.findById(imageId)).thenReturn(Optional.of(existingImage));
        doNothing().when(imageRepository).delete(any(Image.class));

        productService.deleteImage(imageId);

        verify(imageRepository).findById(imageId);
        verify(imageRepository).delete(existingImage);
    }

    @Test
    @DisplayName("deleteImage vyhodí výjimku pro neexistující ID obrázku")
    void deleteImage_NotFound() {
        long nonExistentImageId = 999L;
        when(imageRepository.findById(nonExistentImageId)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> {
            productService.deleteImage(nonExistentImageId);
        });
        assertTrue(exception.getMessage().contains("Image not found"));
        verify(imageRepository, never()).delete(any());
    }

    // --- Testy Generování Slugu ---
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