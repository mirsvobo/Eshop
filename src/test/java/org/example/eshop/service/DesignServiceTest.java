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
    private Design design2;

    @BeforeEach
    void setUp() {
        design1 = new Design(); design1.setId(1L); design1.setName("Klasik"); design1.setActive(true);
        design2 = new Design(); design2.setId(2L); design2.setName("Modern"); design2.setActive(true);
    }

    @Test
    @DisplayName("getAllDesignsSortedByName vrátí seřazený seznam")
    void getAllDesignsSortedByName_ReturnsSortedList() {
        when(designRepository.findAll(Sort.by("name"))).thenReturn(Arrays.asList(design1, design2));
        List<Design> result = designService.getAllDesignsSortedByName();
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Klasik", result.get(0).getName());
        verify(designRepository).findAll(Sort.by("name"));
    }

    @Test
    @DisplayName("createDesign úspěšně vytvoří nový design")
    void createDesign_Success() {
        Design newDesign = new Design(); newDesign.setName("   Nový   ");
        when(designRepository.findByNameIgnoreCase("Nový")).thenReturn(Optional.empty());
        when(designRepository.save(any(Design.class))).thenAnswer(i -> {
            Design saved = i.getArgument(0);
            saved.setId(3L); // Simulace ID
            assertEquals("Nový", saved.getName()); // Ověření oříznutí
            assertTrue(saved.isActive()); // Ověření defaultní aktivity
            return saved;
        });

        Design created = designService.createDesign(newDesign);

        assertNotNull(created);
        assertEquals(3L, created.getId());
        assertEquals("Nový", created.getName());
        verify(designRepository).findByNameIgnoreCase("Nový");
        verify(designRepository).save(any(Design.class));
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
    @DisplayName("updateDesign úspěšně aktualizuje design")
    void updateDesign_Success() {
        Long id = 1L;
        Design updateData = new Design();
        updateData.setName("Klasik Upravený");
        updateData.setDescription("Nový popis");
        updateData.setActive(false);

        when(designRepository.findById(id)).thenReturn(Optional.of(design1));
        // Mock pro kontrolu unikátnosti nového názvu
        when(designRepository.findByNameIgnoreCase("Klasik Upravený")).thenReturn(Optional.empty());
        when(designRepository.save(any(Design.class))).thenAnswer(i -> i.getArgument(0));

        Design updated = designService.updateDesign(id, updateData);

        assertNotNull(updated);
        assertEquals("Klasik Upravený", updated.getName());
        assertEquals("Nový popis", updated.getDescription());
        assertFalse(updated.isActive());
        verify(designRepository).findById(id);
        verify(designRepository).findByNameIgnoreCase("Klasik Upravený");
        verify(designRepository).save(updated);
    }

    @Test
    @DisplayName("updateDesign vyhodí výjimku pro neexistující ID")
    void updateDesign_NotFound() {
        Long id = 99L;
        Design updateData = new Design(); updateData.setName("Nezáleží");
        when(designRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> {
            designService.updateDesign(id, updateData);
        });
        verify(designRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteDesign deaktivuje nepoužitý design")
    void deleteDesign_NotInUse_Deactivates() {
        Long id = 1L;
        // Simulace, že design není přiřazen k produktům
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
        // Simulace, že design je přiřazen
        Set<Product> products = new HashSet<>();
        products.add(new Product()); // Stačí jedna reference
        design1.setProducts(products);
        when(designRepository.findById(id)).thenReturn(Optional.of(design1));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            designService.deleteDesign(id);
        });
        assertTrue(ex.getMessage().contains("nelze deaktivovat, je přiřazen k produktům"));
        assertTrue(design1.isActive()); // Měl by zůstat aktivní
        verify(designRepository).findById(id);
        verify(designRepository, never()).save(any());
    }
}