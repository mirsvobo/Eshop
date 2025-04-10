package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.admin.service.RoofColorService;
import org.example.eshop.model.Product;
import org.example.eshop.model.RoofColor;
import org.example.eshop.repository.ProductRepository;
import org.example.eshop.repository.RoofColorRepository;
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
class RoofColorServiceTest {

    @Mock private RoofColorRepository roofColorRepository;
    @Mock private ProductRepository productRepository;

    @InjectMocks private RoofColorService roofColorService;

    private RoofColor color1;
    private RoofColor color2;

    @BeforeEach
    void setUp() {
        color1 = new RoofColor(); color1.setId(1L); color1.setName("Antracit"); color1.setActive(true);
        color2 = new RoofColor(); color2.setId(2L); color2.setName("Červená Special"); color2.setActive(true); color2.setPriceSurchargeCZK(new BigDecimal("300.00"));
    }

    @Test
    @DisplayName("getAllRoofColorsSortedByName vrátí seřazený seznam")
    void getAllRoofColorsSortedByName_ReturnsSortedList() {
        when(roofColorRepository.findAll(Sort.by("name"))).thenReturn(Arrays.asList(color1, color2)); // Antracit < Červená
        List<RoofColor> result = roofColorService.getAllRoofColorsSortedByName();
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Antracit", result.get(0).getName());
        assertEquals("Červená Special", result.get(1).getName());
        verify(roofColorRepository).findAll(Sort.by("name"));
    }

    @Test
    @DisplayName("createRoofColor úspěšně vytvoří novou barvu")
    void createRoofColor_Success() {
        RoofColor newColor = new RoofColor(); newColor.setName(" Zelená "); newColor.setPriceSurchargeCZK(BigDecimal.TEN);
        when(roofColorRepository.findByNameIgnoreCase("Zelená")).thenReturn(Optional.empty());
        when(roofColorRepository.save(any(RoofColor.class))).thenAnswer(i -> {
            RoofColor saved = i.getArgument(0);
            saved.setId(3L);
            assertEquals("Zelená", saved.getName());
            assertTrue(saved.isActive());
            assertNotNull(saved.getPriceSurchargeCZK());
            assertNull(saved.getPriceSurchargeEUR());
            return saved;
        });

        RoofColor created = roofColorService.createRoofColor(newColor);

        assertNotNull(created);
        assertEquals(3L, created.getId());
        assertEquals("Zelená", created.getName());
        verify(roofColorRepository).findByNameIgnoreCase("Zelená");
        verify(roofColorRepository).save(any(RoofColor.class));
    }

    @Test
    @DisplayName("updateRoofColor úspěšně aktualizuje barvu")
    void updateRoofColor_Success() {
        Long id = 1L;
        RoofColor updateData = new RoofColor();
        updateData.setName("Antracit Matný"); updateData.setActive(false); updateData.setPriceSurchargeEUR(new BigDecimal("5.00"));
        when(roofColorRepository.findById(id)).thenReturn(Optional.of(color1));
        when(roofColorRepository.findByNameIgnoreCase("Antracit Matný")).thenReturn(Optional.empty());
        when(roofColorRepository.save(any(RoofColor.class))).thenAnswer(i -> i.getArgument(0));

        RoofColor updated = roofColorService.updateRoofColor(id, updateData);

        assertNotNull(updated);
        assertEquals("Antracit Matný", updated.getName());
        assertFalse(updated.isActive());
        assertNull(updated.getPriceSurchargeCZK());
        assertEquals(0, new BigDecimal("5.00").compareTo(updated.getPriceSurchargeEUR()));
        verify(roofColorRepository).save(updated);
    }

    @Test
    @DisplayName("deleteRoofColor deaktivuje nepoužitou barvu")
    void deleteRoofColor_NotInUse_Deactivates() {
        Long id = 1L;
        color1.setProducts(Collections.emptySet());
        when(roofColorRepository.findById(id)).thenReturn(Optional.of(color1));
        when(roofColorRepository.save(any(RoofColor.class))).thenReturn(color1);

        roofColorService.deleteRoofColor(id);

        assertFalse(color1.isActive());
        verify(roofColorRepository).save(color1);
    }

    @Test
    @DisplayName("deleteRoofColor vyhodí výjimku pro použitou barvu")
    void deleteRoofColor_InUse_ThrowsException() {
        Long id = 1L;
        Set<Product> products = new HashSet<>(); products.add(new Product());
        color1.setProducts(products);
        when(roofColorRepository.findById(id)).thenReturn(Optional.of(color1));

        assertThrows(IllegalStateException.class, () -> {
            roofColorService.deleteRoofColor(id);
        });
        verify(roofColorRepository, never()).save(any());
    }
}