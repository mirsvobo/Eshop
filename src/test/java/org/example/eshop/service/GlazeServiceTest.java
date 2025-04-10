package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.admin.service.GlazeService;
import org.example.eshop.model.Glaze;
import org.example.eshop.model.Product;
import org.example.eshop.repository.GlazeRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlazeServiceTest {

    @Mock private GlazeRepository glazeRepository;
    @Mock private ProductRepository productRepository; // Pro kontrolu delete

    @InjectMocks private GlazeService glazeService;

    private Glaze glaze1;
    private Glaze glaze2;

    @BeforeEach
    void setUp() {
        glaze1 = new Glaze(); glaze1.setId(1L); glaze1.setName("Ořech"); glaze1.setActive(true); glaze1.setPriceSurchargeCZK(null); glaze1.setPriceSurchargeEUR(null);
        glaze2 = new Glaze(); glaze2.setId(2L); glaze2.setName("Teak Premium"); glaze2.setActive(true); glaze2.setPriceSurchargeCZK(new BigDecimal("200.00")); glaze2.setPriceSurchargeEUR(new BigDecimal("8.00"));
    }

    @Test
    @DisplayName("getAllGlazesSortedByName vrátí seřazený seznam")
    void getAllGlazesSortedByName_ReturnsSortedList() {
        when(glazeRepository.findAll(Sort.by("name"))).thenReturn(Arrays.asList(glaze1, glaze2)); // Předpokládáme toto pořadí
        List<Glaze> result = glazeService.getAllGlazesSortedByName();
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Ořech", result.get(0).getName()); // Ověření pořadí
        assertEquals("Teak Premium", result.get(1).getName());
        verify(glazeRepository).findAll(Sort.by("name"));
    }

    @Test
    @DisplayName("createGlaze úspěšně vytvoří novou lazuru")
    void createGlaze_Success() {
        Glaze newGlaze = new Glaze(); newGlaze.setName("  Dub  "); newGlaze.setPriceSurchargeCZK(new BigDecimal("100"));
        when(glazeRepository.findByNameIgnoreCase("Dub")).thenReturn(Optional.empty());
        when(glazeRepository.save(any(Glaze.class))).thenAnswer(i -> {
            Glaze saved = i.getArgument(0);
            saved.setId(3L);
            assertEquals("Dub", saved.getName());
            assertTrue(saved.isActive());
            assertNotNull(saved.getPriceSurchargeCZK());
            assertNull(saved.getPriceSurchargeEUR()); // Normalizace ceny
            return saved;
        });

        Glaze created = glazeService.createGlaze(newGlaze);

        assertNotNull(created);
        assertEquals(3L, created.getId());
        assertEquals("Dub", created.getName());
        assertEquals(0, new BigDecimal("100").compareTo(created.getPriceSurchargeCZK()));
        assertNull(created.getPriceSurchargeEUR());
        verify(glazeRepository).findByNameIgnoreCase("Dub");
        verify(glazeRepository).save(any(Glaze.class));
    }

    @Test
    @DisplayName("createGlaze normalizuje nulové/záporné ceny na null")
    void createGlaze_NormalizesPrices() {
        Glaze newGlaze = new Glaze(); newGlaze.setName("Normalizovaná");
        newGlaze.setPriceSurchargeCZK(BigDecimal.ZERO); // Nula
        newGlaze.setPriceSurchargeEUR(new BigDecimal("-10.00")); // Záporná

        when(glazeRepository.findByNameIgnoreCase("Normalizovaná")).thenReturn(Optional.empty());
        when(glazeRepository.save(any(Glaze.class))).thenAnswer(i -> i.getArgument(0));

        Glaze created = glazeService.createGlaze(newGlaze);

        assertNull(created.getPriceSurchargeCZK(), "Nulová cena CZK by měla být null");
        assertNull(created.getPriceSurchargeEUR(), "Záporná cena EUR by měla být null");
        verify(glazeRepository).save(created);
    }

    @Test
    @DisplayName("createGlaze vyhodí výjimku pro duplicitní název")
    void createGlaze_ThrowsForDuplicateName() {
        Glaze duplicateGlaze = new Glaze(); duplicateGlaze.setName("Ořech");
        when(glazeRepository.findByNameIgnoreCase("Ořech")).thenReturn(Optional.of(glaze1));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            glazeService.createGlaze(duplicateGlaze);
        });
        assertTrue(ex.getMessage().contains("již existuje"));
        verify(glazeRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateGlaze úspěšně aktualizuje lazuru")
    void updateGlaze_Success() {
        Long id = 1L;
        Glaze updateData = new Glaze();
        updateData.setName("Ořech Světlý");
        updateData.setDescription("Světlý odstín");
        updateData.setActive(true);
        updateData.setPriceSurchargeCZK(new BigDecimal("50.00"));
        updateData.setPriceSurchargeEUR(null); // EUR cena bude null

        when(glazeRepository.findById(id)).thenReturn(Optional.of(glaze1));
        when(glazeRepository.findByNameIgnoreCase("Ořech Světlý")).thenReturn(Optional.empty());
        when(glazeRepository.save(any(Glaze.class))).thenAnswer(i -> i.getArgument(0));

        Glaze updated = glazeService.updateGlaze(id, updateData);

        assertNotNull(updated);
        assertEquals("Ořech Světlý", updated.getName());
        assertEquals("Světlý odstín", updated.getDescription());
        assertEquals(0, new BigDecimal("50.00").compareTo(updated.getPriceSurchargeCZK()));
        assertNull(updated.getPriceSurchargeEUR());
        assertTrue(updated.isActive());
        verify(glazeRepository).findById(id);
        verify(glazeRepository).findByNameIgnoreCase("Ořech Světlý");
        verify(glazeRepository).save(updated);
    }

    @Test
    @DisplayName("deleteGlaze deaktivuje nepoužitou lazuru")
    void deleteGlaze_NotInUse_Deactivates() {
        Long id = 1L;
        glaze1.setProducts(Collections.emptySet()); // Nepoužitý
        when(glazeRepository.findById(id)).thenReturn(Optional.of(glaze1));
        when(glazeRepository.save(any(Glaze.class))).thenReturn(glaze1);

        assertTrue(glaze1.isActive());
        glazeService.deleteGlaze(id);

        assertFalse(glaze1.isActive());
        verify(glazeRepository).findById(id);
        verify(glazeRepository).save(glaze1);
    }

    @Test
    @DisplayName("deleteGlaze vyhodí výjimku pro použitou lazuru")
    void deleteGlaze_InUse_ThrowsException() {
        Long id = 1L;
        Set<Product> products = new HashSet<>();
        products.add(new Product());
        glaze1.setProducts(products); // Použitý
        when(glazeRepository.findById(id)).thenReturn(Optional.of(glaze1));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            glazeService.deleteGlaze(id);
        });
        assertTrue(ex.getMessage().contains("nelze deaktivovat, je přiřazena k produktům"));
        assertTrue(glaze1.isActive());
        verify(glazeRepository).findById(id);
        verify(glazeRepository, never()).save(any());
    }
}