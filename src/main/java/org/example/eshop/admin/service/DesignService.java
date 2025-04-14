package org.example.eshop.admin.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.model.Design;
import org.example.eshop.repository.DesignRepository;
import org.example.eshop.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal; // <-- Přidat import
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set; // <-- Přidat import

@Service
public class DesignService {

    private static final Logger log = LoggerFactory.getLogger(DesignService.class);

    @Autowired private DesignRepository designRepository;
    @Autowired private ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<Design> getAllDesignsSortedByName() {
        log.debug("Fetching all designs sorted by name");
        return designRepository.findAll(Sort.by("name"));
    }

    @Transactional(readOnly = true)
    public Optional<Design> findById(Long id) {
        log.debug("Fetching design by ID: {}", id);
        return designRepository.findById(id);
    }

    @Transactional
    public Design createDesign(Design design) {
        log.info("Creating new design: {}", design.getName());
        validateDesign(design); // Validace nyní kontroluje i ceny

        designRepository.findByNameIgnoreCase(design.getName().trim()).ifPresent(d -> {
            throw new IllegalArgumentException("Design s názvem '" + design.getName().trim() + "' již existuje.");
        });
        design.setName(design.getName().trim());
        normalizePrices(design); // Normalizace cen
        design.setActive(true);
        return designRepository.save(design);
    }

    @Transactional
    public Design updateDesign(Long id, Design designData) {
        log.info("Updating design with ID: {}", id);
        validateDesign(designData); // Validace nyní kontroluje i ceny

        Design existingDesign = designRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Design with ID " + id + " not found."));

        String newName = designData.getName().trim();
        if (!existingDesign.getName().equalsIgnoreCase(newName)) {
            designRepository.findByNameIgnoreCase(newName)
                    .filter(found -> !found.getId().equals(id))
                    .ifPresent(found -> {
                        throw new IllegalArgumentException("Design s názvem '" + newName + "' již existuje.");
                    });
            existingDesign.setName(newName);
        }

        existingDesign.setDescription(designData.getDescription());
        existingDesign.setImageUrl(designData.getImageUrl()); // <-- Aktualizace imageUrl
        existingDesign.setActive(designData.isActive());
        normalizePrices(designData); // Normalizace cen z DTO
        existingDesign.setPriceSurchargeCZK(designData.getPriceSurchargeCZK()); // <-- Aktualizace ceny CZK
        existingDesign.setPriceSurchargeEUR(designData.getPriceSurchargeEUR()); // <-- Aktualizace ceny EUR

        Design updatedDesign = designRepository.save(existingDesign);
        log.info("Design '{}' (ID: {}) updated successfully.", updatedDesign.getName(), updatedDesign.getId());
        return updatedDesign;
    }

    public void deleteDesign(Long id) {
        log.warn("Attempting to deactivate (soft delete) design with ID: {}", id);
        // Volání NOVÉ repository metody
        Design design = designRepository.findByIdWithProducts(id)
                .orElseThrow(() -> new EntityNotFoundException("Design with ID " + id + " not found."));

        // Kontrola použití (produkty jsou již načteny)
        boolean isUsed = design.getProducts() != null && !design.getProducts().isEmpty();

        if (isUsed) {
            log.error("Cannot deactivate design '{}' (ID: {}) because it is assigned to products.", design.getName(), id);
            throw new IllegalStateException("Design '" + design.getName() + "' nelze deaktivovat, je přiřazen k produktům. Nejprve odstraňte přiřazení u produktů.");
        } else {
            if (design.isActive()) {
                design.setActive(false);
                designRepository.save(design);
                log.info("Design '{}' (ID: {}) successfully deactivated.", design.getName(), id);
            } else {
                log.info("Design '{}' (ID: {}) is already inactive.", design.getName(), id);
            }
        }
    }

    private void validateDesign(Design design) {
        if (design == null) throw new IllegalArgumentException("Design object cannot be null.");
        if (!StringUtils.hasText(design.getName())) throw new IllegalArgumentException("Design name cannot be empty.");
        // Validace cen - nesmí být záporné
        if (design.getPriceSurchargeCZK() != null && design.getPriceSurchargeCZK().signum() < 0) {
            throw new IllegalArgumentException("Price surcharge CZK cannot be negative.");
        }
        if (design.getPriceSurchargeEUR() != null && design.getPriceSurchargeEUR().signum() < 0) {
            throw new IllegalArgumentException("Price surcharge EUR cannot be negative.");
        }
    }

    // Přidána pomocná metoda pro normalizaci cen
    private void normalizePrices(Design design) {
        if (design.getPriceSurchargeCZK() != null && design.getPriceSurchargeCZK().compareTo(BigDecimal.ZERO) <= 0) {
            design.setPriceSurchargeCZK(null); // Nulový nebo záporný příplatek uložíme jako null
        }
        if (design.getPriceSurchargeEUR() != null && design.getPriceSurchargeEUR().compareTo(BigDecimal.ZERO) <= 0) {
            design.setPriceSurchargeEUR(null); // Nulový nebo záporný příplatek uložíme jako null
        }
    }
}