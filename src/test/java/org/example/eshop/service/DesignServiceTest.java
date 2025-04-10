package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.admin.service.DesignService;
import org.example.eshop.model.Design;
import org.example.eshop.model.Product;
import org.example.eshop.repository.DesignRepository;
import org.example.eshop.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DesignServiceTest {

    @Mock private DesignRepository designRepository;
    @Mock private ProductRepository productRepository; // Pro kontrolu delete

    @InjectMocks private DesignService designService;

    private Design design1;
    private Design design2_withPrice;

    @BeforeEach
    void setUp() {
        design1 = new Design();
        design1.setId(1L);
        design1.setName("Klasik");
        design1.setActive(true);
        design1.setImageUrl(null); // Bez obrázku a ceny
        design1.setPriceSurchargeCZK(null);
        design1.setPriceSurchargeEUR(null);

        design2_withPrice = new Design();
        design2_withPrice.setId(2L);
        design2_withPrice.setName("Modern Premium");
        design2_withPrice.setActive(true);
        design2_withPrice.setImageUrl("/img/modern.jpg"); // S obrázkem a cenou
        design2_withPrice.setPriceSurchargeCZK(new BigDecimal("500.00"));
        design2_withPrice.setPriceSurchargeEUR(new BigDecimal("20.00"));
    }

    @Test
    @DisplayName("getAllDesignsSortedByName vrátí seřazený seznam")
    void getAllDesignsSortedByName_ReturnsSortedList() {
        // Pořadí se může změnit kvůli názvům
        when(designRepository.findAll(Sort.by("name"))).thenReturn(Arrays.asList(design1, design2_withPrice));
        List<Design> result = designService.getAllDesignsSortedByName();
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Klasik", result.get(0).getName());
        assertEquals("Modern Premium", result.get(1).getName());
        verify(designRepository).findAll(Sort.by("name"));
    }

    @Test
    @DisplayName("createDesign úspěšně vytvoří nový design s cenou a obrázkem")
    void createDesign_Success_WithExtras() {
        Design newDesign = new Design();
        newDesign.setName("   Nový Vzor   ");
        newDesign.setPriceSurchargeCZK(new BigDecimal("150.00"));
        newDesign.setPriceSurchargeEUR(new BigDecimal("6.00"));
        newDesign.setImageUrl("/img/novy.jpg");

        when(designRepository.findByNameIgnoreCase("Nový Vzor")).thenReturn(Optional.empty());
        when(designRepository.save(any(Design.class))).thenAnswer(i -> {
            Design saved = i.getArgument(0);
            saved.setId(3L);
            assertEquals("Nový Vzor", saved.getName());
            assertTrue(saved.isActive());
            assertEquals("/img/novy.jpg", saved.getImageUrl());
            assertEquals(0, new BigDecimal("150.00").compareTo(saved.getPriceSurchargeCZK()));
            assertEquals(0, new BigDecimal("6.00").compareTo(saved.getPriceSurchargeEUR()));
            return saved;
        });

        Design created = designService.createDesign(newDesign);

        assertNotNull(created);
        assertEquals(3L, created.getId());
        assertEquals("Nový Vzor", created.getName());
        verify(designRepository).findByNameIgnoreCase("Nový Vzor");
        verify(designRepository).save(any(Design.class));
    }

    @Test
    @DisplayName("createDesign normalizuje nulové/záporné ceny na null")
    void createDesign_NormalizesPrices() {
        Design newDesign = new Design(); newDesign.setName("Normalizovaný Design");
        newDesign.setPriceSurchargeCZK(BigDecimal.ZERO); // Nula
        newDesign.setPriceSurchargeEUR(new BigDecimal("-10.00")); // Záporná

        when(designRepository.findByNameIgnoreCase("Normalizovaný Design")).thenReturn(Optional.empty());
        when(designRepository.save(any(Design.class))).thenAnswer(i -> i.getArgument(0));

        Design created = designService.createDesign(newDesign);

        assertNull(created.getPriceSurchargeCZK(), "Nulová cena CZK by měla být null");
        assertNull(created.getPriceSurchargeEUR(), "Záporná cena EUR by měla být null");
        verify(designRepository).save(created);
    }


    @Test
    @DisplayName("createDesign vyhodí výjimku pro duplicitní název")
    void createDesign_ThrowsForDuplicateName() {
        Design duplicateDesign = new Design(); duplicateDesign.setName("Klasik");
        when(designRepository.findByNameIgnoreCase("Klasik")).thenReturn(Optional.of(design1));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            designService.createDesign(duplicateDesign);
        });
        assertTrue(ex.getMessage().contains("již existuje"));
        verify(designRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateDesign úspěšně aktualizuje design včetně ceny a obrázku")
    void updateDesign_Success_WithExtras() {
        Long id = 1L; // Aktualizujeme design1 (Klasik)
        Design updateData = new Design();
        updateData.setName("Klasik Plus");
        updateData.setDescription("Vylepšený popis");
        updateData.setActive(true);
        updateData.setImageUrl("/img/klasik-plus.jpg");
        updateData.setPriceSurchargeCZK(new BigDecimal("250.00"));
        updateData.setPriceSurchargeEUR(new BigDecimal("10.00"));

        when(designRepository.findById(id)).thenReturn(Optional.of(design1));
        when(designRepository.findByNameIgnoreCase("Klasik Plus")).thenReturn(Optional.empty());
        when(designRepository.save(any(Design.class))).thenAnswer(i -> i.getArgument(0));

        Design updated = designService.updateDesign(id, updateData);

        assertNotNull(updated);
        assertEquals("Klasik Plus", updated.getName());
        assertEquals("Vylepšený popis", updated.getDescription());
        assertEquals("/img/klasik-plus.jpg", updated.getImageUrl());
        assertTrue(updated.isActive());
        assertEquals(0, new BigDecimal("250.00").compareTo(updated.getPriceSurchargeCZK()));
        assertEquals(0, new BigDecimal("10.00").compareTo(updated.getPriceSurchargeEUR()));
        verify(designRepository).findById(id);
        verify(designRepository).findByNameIgnoreCase("Klasik Plus");
        verify(designRepository).save(updated);
    }

    @Test
    @DisplayName("updateDesign normalizuje nulové/záporné ceny na null")
    void updateDesign_NormalizesPrices() {
        Long id = 2L; // Aktualizujeme design2_withPrice
        Design updateData = new Design();
        updateData.setName("Modern Premium"); // Název neměníme
        updateData.setPriceSurchargeCZK(BigDecimal.ZERO); // Nastavíme na 0
        updateData.setPriceSurchargeEUR(new BigDecimal("-5.00")); // Nastavíme záporně
        updateData.setActive(true);

        when(designRepository.findById(id)).thenReturn(Optional.of(design2_withPrice));
        // Protože neměníme název, findByNameIgnoreCase se nevolá pro kontrolu duplicity
        when(designRepository.save(any(Design.class))).thenAnswer(i -> i.getArgument(0));

        Design updated = designService.updateDesign(id, updateData);

        assertNotNull(updated);
        assertNull(updated.getPriceSurchargeCZK(), "Nulová cena CZK by měla být null po update");
        assertNull(updated.getPriceSurchargeEUR(), "Záporná cena EUR by měla být null po update");
        verify(designRepository).save(updated);
    }

    @Test
    @DisplayName("deleteDesign deaktivuje nepoužitý design")
    void deleteDesign_NotInUse_Deactivates() {
        Long id = 1L;
        design1.setProducts(Collections.emptySet());
        when(designRepository.findById(id)).thenReturn(Optional.of(design1));
        when(designRepository.save(any(Design.class))).thenReturn(design1);

        assertTrue(design1.isActive());
        designService.deleteDesign(id);

        assertFalse(design1.isActive());
        verify(designRepository).findById(id);
        verify(designRepository).save(design1);
    }

    @Test
    @DisplayName("deleteDesign vyhodí výjimku pro použitý design")
    void deleteDesign_InUse_ThrowsException() {
        Long id = 1L;
        Set<Product> products = new HashSet<>(); products.add(new Product());
        design1.setProducts(products);
        when(designRepository.findById(id)).thenReturn(Optional.of(design1));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            designService.deleteDesign(id);
        });
        assertTrue(ex.getMessage().contains("nelze deaktivovat, je přiřazen k produktům"));
        assertTrue(design1.isActive());
        verify(designRepository).findById(id);
        verify(designRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateDesign vyhodí chybu pro zápornou CZK cenu")
    void validateDesign_ThrowsForNegativeCZKPrice() {
        Design invalidDesign = new Design();
        invalidDesign.setName("Záporná CZK");
        invalidDesign.setPriceSurchargeCZK(new BigDecimal("-100"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            // Voláme přímo validační metodu (i když je private, můžeme ji testovat takto nepřímo)
            designService.createDesign(invalidDesign); // Nebo updateDesign
        });
        assertTrue(ex.getMessage().contains("Price surcharge CZK cannot be negative"));
    }

    @Test
    @DisplayName("validateDesign vyhodí chybu pro zápornou EUR cenu")
    void validateDesign_ThrowsForNegativeEURPrice() {
        Design invalidDesign = new Design();
        invalidDesign.setName("Záporná EUR");
        invalidDesign.setPriceSurchargeEUR(new BigDecimal("-5"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            designService.createDesign(invalidDesign);
        });
        assertTrue(ex.getMessage().contains("Price surcharge EUR cannot be negative"));
    }
}