package org.example.eshop.admin.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.model.Glaze;
import org.example.eshop.repository.GlazeRepository;
import org.example.eshop.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class GlazeService {

    private static final Logger log = LoggerFactory.getLogger(GlazeService.class);

    @Autowired
    private GlazeRepository glazeRepository;
    @Autowired
    private ProductRepository productRepository; // Pro kontrolu použití

    @Cacheable("allGlazes")
    @Transactional(readOnly = true)
    public List<Glaze> getAllGlazesSortedByName() {
        log.debug("Fetching all glazes sorted by name");
        return glazeRepository.findAll(Sort.by("name"));
    }

    @Transactional(readOnly = true)
    public Optional<Glaze> findById(Long id) {
        log.debug("Fetching glaze by ID: {}", id);
        return glazeRepository.findById(id);
    }

    @Caching(evict = {
            @CacheEvict(value = "allGlazes", allEntries = true)
    })
    @Transactional
    public Glaze createGlaze(Glaze glaze) {
        log.info("Creating new glaze: {}", glaze.getName());
        validateGlaze(glaze);

        glazeRepository.findByNameIgnoreCase(glaze.getName().trim()).ifPresent(g -> {
            throw new IllegalArgumentException("Lazura s názvem '" + glaze.getName().trim() + "' již existuje.");
        });
        glaze.setName(glaze.getName().trim());
        // Zajistíme, že ceny jsou buď platné, nebo null (ale ne záporné)
        normalizePrices(glaze);
        glaze.setActive(true);
        return glazeRepository.save(glaze);
    }

    @Caching(evict = {
            @CacheEvict(value = "allGlazes", allEntries = true)
            // @CacheEvict(value = "glazeById", key = "#id")
    })
    @Transactional
    public Glaze updateGlaze(Long id, Glaze glazeData) {
        log.info("Updating glaze with ID: {}", id);
        validateGlaze(glazeData);

        Glaze existingGlaze = glazeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Glaze with ID " + id + " not found."));

        String newName = glazeData.getName().trim();
        if (!existingGlaze.getName().equalsIgnoreCase(newName)) {
            glazeRepository.findByNameIgnoreCase(newName)
                    .filter(found -> !found.getId().equals(id))
                    .ifPresent(found -> {
                        throw new IllegalArgumentException("Lazura s názvem '" + newName + "' již existuje.");
                    });
            existingGlaze.setName(newName);
        }

        existingGlaze.setDescription(glazeData.getDescription());
        existingGlaze.setImageUrl(glazeData.getImageUrl());
        existingGlaze.setActive(glazeData.isActive());
        // Zajistíme ceny
        normalizePrices(glazeData);
        existingGlaze.setPriceSurchargeCZK(glazeData.getPriceSurchargeCZK());
        existingGlaze.setPriceSurchargeEUR(glazeData.getPriceSurchargeEUR());

        Glaze updatedGlaze = glazeRepository.save(existingGlaze);
        log.info("Glaze '{}' (ID: {}) updated successfully.", updatedGlaze.getName(), updatedGlaze.getId());
        return updatedGlaze;
    }

    @Caching(evict = {
            @CacheEvict(value = "allGlazes", allEntries = true)
            // @CacheEvict(value = "glazeById", key = "#id")
    })
    @Transactional
    public void deleteGlaze(Long id) {
        log.warn("Attempting to deactivate (soft delete) glaze with ID: {}", id);
        // Volání NOVÉ repository metody
        Glaze glaze = glazeRepository.findByIdWithProducts(id)
                .orElseThrow(() -> new EntityNotFoundException("Glaze with ID " + id + " not found."));

        // Kontrola použití (produkty jsou již načteny)
        boolean isUsed = glaze.getProducts() != null && !glaze.getProducts().isEmpty();

        if (isUsed) {
            log.error("Cannot deactivate glaze '{}' (ID: {}) because it is assigned to products.", glaze.getName(), id);
            throw new IllegalStateException("Lazuru '" + glaze.getName() + "' nelze deaktivovat, je přiřazena k produktům.");
        } else {
            if (glaze.isActive()) {
                glaze.setActive(false);
                glazeRepository.save(glaze);
                log.info("Glaze '{}' (ID: {}) successfully deactivated.", glaze.getName(), id);
            } else {
                log.info("Glaze '{}' (ID: {}) is already inactive.", glaze.getName(), id);
            }
        }
    }

    private void validateGlaze(Glaze glaze) {
        if (glaze == null) throw new IllegalArgumentException("Glaze object cannot be null.");
        if (!StringUtils.hasText(glaze.getName())) throw new IllegalArgumentException("Glaze name cannot be empty.");
        // Příplatky mohou být null, ale pokud jsou zadány, nesmí být záporné
        if (glaze.getPriceSurchargeCZK() != null && glaze.getPriceSurchargeCZK().signum() < 0) {
            throw new IllegalArgumentException("Price surcharge CZK cannot be negative.");
        }
        if (glaze.getPriceSurchargeEUR() != null && glaze.getPriceSurchargeEUR().signum() < 0) {
            throw new IllegalArgumentException("Price surcharge EUR cannot be negative.");
        }
    }

    // Pomocná metoda pro normalizaci cen (nastaví null, pokud je 0 nebo méně)
    private void normalizePrices(Glaze glaze) {
        if (glaze.getPriceSurchargeCZK() != null && glaze.getPriceSurchargeCZK().compareTo(BigDecimal.ZERO) <= 0) {
            glaze.setPriceSurchargeCZK(null);
        }
        if (glaze.getPriceSurchargeEUR() != null && glaze.getPriceSurchargeEUR().compareTo(BigDecimal.ZERO) <= 0) {
            glaze.setPriceSurchargeEUR(null);
        }
    }
}