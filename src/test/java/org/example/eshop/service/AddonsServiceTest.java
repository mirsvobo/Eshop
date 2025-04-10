package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.admin.service.AddonsService;
import org.example.eshop.model.Addon;
import org.example.eshop.repository.AddonsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddonsServiceTest {

    @Mock private AddonsRepository addonsRepository;

    @InjectMocks private AddonsService addonsService;

    private Addon addon1;
    private Addon addon2;

    @BeforeEach
    void setUp() {
        addon1 = new Addon(); addon1.setId(1L); addon1.setName("Polička"); addon1.setSku("POL-01"); addon1.setActive(true); addon1.setPriceCZK(new BigDecimal("350.00")); addon1.setPriceEUR(new BigDecimal("15.00"));
        addon2 = new Addon(); addon2.setId(2L); addon2.setName("Držák"); addon2.setSku("DRZ-01"); addon2.setActive(false); addon2.setPriceCZK(new BigDecimal("500.00")); addon2.setPriceEUR(new BigDecimal("20.00"));
    }

    @Test
    @DisplayName("getAllAddons vrátí seřazený seznam")
    void getAllAddons_ReturnsSortedList() {
        when(addonsRepository.findAll(Sort.by("name"))).thenReturn(Arrays.asList(addon2, addon1)); // Držák < Polička
        List<Addon> result = addonsService.getAllAddons();
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Držák", result.get(0).getName());
        assertEquals("Polička", result.get(1).getName());
        verify(addonsRepository).findAll(Sort.by("name"));
    }

    @Test
    @DisplayName("createAddon úspěšně vytvoří nový doplněk")
    void createAddon_Success() {
        Addon newAddon = new Addon(); newAddon.setName(" Nový Doplněk "); newAddon.setSku("NEW-01");
        newAddon.setPriceCZK(new BigDecimal("100.00")); newAddon.setPriceEUR(new BigDecimal("4.00"));

        when(addonsRepository.findByNameIgnoreCase("Nový Doplněk")).thenReturn(Optional.empty());
        // Mock pro SKU, pokud bude implementována kontrola unikátnosti
        // when(addonsRepository.findBySkuIgnoreCase("NEW-01")).thenReturn(Optional.empty());
        when(addonsRepository.save(any(Addon.class))).thenAnswer(i -> {
            Addon saved = i.getArgument(0);
            saved.setId(3L);
            assertEquals("Nový Doplněk", saved.getName());
            assertTrue(saved.isActive());
            assertEquals(0, new BigDecimal("100.00").compareTo(saved.getPriceCZK()));
            return saved;
        });

        Addon created = addonsService.createAddon(newAddon);

        assertNotNull(created);
        assertEquals(3L, created.getId());
        assertEquals("Nový Doplněk", created.getName());
        verify(addonsRepository).findByNameIgnoreCase("Nový Doplněk");
        verify(addonsRepository).save(any(Addon.class));
    }

    @Test
    @DisplayName("createAddon vyhodí výjimku pro nekladnou cenu CZK")
    void createAddon_ThrowsForNonPositiveCZKPrice() {
        Addon invalidAddon = new Addon(); invalidAddon.setName("Neplatná Cena");
        invalidAddon.setPriceCZK(BigDecimal.ZERO);
        invalidAddon.setPriceEUR(BigDecimal.TEN);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            addonsService.createAddon(invalidAddon);
        });
        assertTrue(ex.getMessage().contains("Cena CZK musí být kladná"));
    }

    @Test
    @DisplayName("updateAddon úspěšně aktualizuje doplněk")
    void updateAddon_Success() {
        Long id = 1L;
        Addon updateData = new Addon();
        updateData.setName("Polička Prodloužená"); updateData.setSku("POL-01-EXT"); updateData.setActive(false);
        updateData.setPriceCZK(new BigDecimal("400.00")); updateData.setPriceEUR(new BigDecimal("16.00"));

        when(addonsRepository.findById(id)).thenReturn(Optional.of(addon1));
        when(addonsRepository.findByNameIgnoreCase("Polička Prodloužená")).thenReturn(Optional.empty());
        // Mock pro SKU
        // when(addonsRepository.findBySkuIgnoreCase("POL-01-EXT")).thenReturn(Optional.empty());
        when(addonsRepository.save(any(Addon.class))).thenAnswer(i -> i.getArgument(0));

        Addon updated = addonsService.updateAddon(id, updateData);

        assertNotNull(updated);
        assertEquals("Polička Prodloužená", updated.getName());
        assertEquals("POL-01-EXT", updated.getSku());
        assertFalse(updated.isActive());
        assertEquals(0, new BigDecimal("400.00").compareTo(updated.getPriceCZK()));
        verify(addonsRepository).save(updated);
    }

    @Test
    @DisplayName("deleteAddon deaktivuje doplněk")
    void deleteAddon_Deactivates() {
        Long id = 1L; // Deaktivujeme addon1
        when(addonsRepository.findById(id)).thenReturn(Optional.of(addon1));
        when(addonsRepository.save(any(Addon.class))).thenReturn(addon1);

        assertTrue(addon1.isActive());
        addonsService.deleteAddon(id);

        assertFalse(addon1.isActive());
        verify(addonsRepository).findById(id);
        verify(addonsRepository).save(addon1);
    }

    @Test
    @DisplayName("deleteAddon pro již neaktivní nic neudělá")
    void deleteAddon_AlreadyInactive_DoesNothing() {
        Long id = 2L; // Deaktivujeme addon2 (už je neaktivní)
        when(addonsRepository.findById(id)).thenReturn(Optional.of(addon2));

        assertFalse(addon2.isActive());
        addonsService.deleteAddon(id);

        assertFalse(addon2.isActive()); // Stále neaktivní
        verify(addonsRepository).findById(id);
        verify(addonsRepository, never()).save(any()); // Save se nevolá
    }
}