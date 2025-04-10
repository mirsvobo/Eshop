package org.example.eshop.admin.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.model.RoofColor;
import org.example.eshop.repository.ProductRepository;
import org.example.eshop.repository.RoofColorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class RoofColorService {

    private static final Logger log = LoggerFactory.getLogger(RoofColorService.class);

    @Autowired private RoofColorRepository roofColorRepository;
    @Autowired private ProductRepository productRepository; // Pro kontrolu použití

    @Transactional(readOnly = true)
    public List<RoofColor> getAllRoofColorsSortedByName() {
        log.debug("Fetching all roof colors sorted by name");
        return roofColorRepository.findAll(Sort.by("name"));
    }

    @Transactional(readOnly = true)
    public Optional<RoofColor> findById(Long id) {
        log.debug("Fetching roof color by ID: {}", id);
        return roofColorRepository.findById(id);
    }

    @Transactional
    public RoofColor createRoofColor(RoofColor roofColor) {
        log.info("Creating new roof color: {}", roofColor.getName());
        validateRoofColor(roofColor);

        roofColorRepository.findByNameIgnoreCase(roofColor.getName().trim()).ifPresent(rc -> {
            throw new IllegalArgumentException("Barva střechy s názvem '" + roofColor.getName().trim() + "' již existuje.");
        });
        roofColor.setName(roofColor.getName().trim());
        normalizePrices(roofColor);
        roofColor.setActive(true);
        return roofColorRepository.save(roofColor);
    }

    @Transactional
    public RoofColor updateRoofColor(Long id, RoofColor roofColorData) {
        log.info("Updating roof color with ID: {}", id);
        validateRoofColor(roofColorData);

        RoofColor existingColor = roofColorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("RoofColor with ID " + id + " not found."));

        String newName = roofColorData.getName().trim();
        if (!existingColor.getName().equalsIgnoreCase(newName)) {
            roofColorRepository.findByNameIgnoreCase(newName)
                    .filter(found -> !found.getId().equals(id))
                    .ifPresent(found -> {
                        throw new IllegalArgumentException("Barva střechy s názvem '" + newName + "' již existuje.");
                    });
            existingColor.setName(newName);
        }

        existingColor.setDescription(roofColorData.getDescription());
        existingColor.setImageUrl(roofColorData.getImageUrl());
        existingColor.setActive(roofColorData.isActive());
        normalizePrices(roofColorData);
        existingColor.setPriceSurchargeCZK(roofColorData.getPriceSurchargeCZK());
        existingColor.setPriceSurchargeEUR(roofColorData.getPriceSurchargeEUR());

        RoofColor updatedColor = roofColorRepository.save(existingColor);
        log.info("RoofColor '{}' (ID: {}) updated successfully.", updatedColor.getName(), updatedColor.getId());
        return updatedColor;
    }

    @Transactional
    public void deleteRoofColor(Long id) {
        log.warn("Attempting to deactivate (soft delete) roof color with ID: {}", id);
        RoofColor color = roofColorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("RoofColor with ID " + id + " not found."));

        boolean isUsed = color.getProducts() != null && !color.getProducts().isEmpty();
        // boolean isUsed = productRepository.countByAvailableRoofColorsContains(color) > 0; // Efektivnější

        if (isUsed) {
            log.error("Cannot deactivate roof color '{}' (ID: {}) because it is assigned to products.", color.getName(), id);
            throw new IllegalStateException("Barvu střechy '" + color.getName() + "' nelze deaktivovat, je přiřazena k produktům.");
        } else {
            if (color.isActive()) {
                color.setActive(false);
                roofColorRepository.save(color);
                log.info("RoofColor '{}' (ID: {}) successfully deactivated.", color.getName(), id);
            } else {
                log.info("RoofColor '{}' (ID: {}) is already inactive.", color.getName(), id);
            }
        }
    }

    private void validateRoofColor(RoofColor color) {
        if (color == null) throw new IllegalArgumentException("RoofColor object cannot be null.");
        if (!StringUtils.hasText(color.getName())) throw new IllegalArgumentException("RoofColor name cannot be empty.");
        if (color.getPriceSurchargeCZK() != null && color.getPriceSurchargeCZK().signum() < 0) {
            throw new IllegalArgumentException("Price surcharge CZK cannot be negative.");
        }
        if (color.getPriceSurchargeEUR() != null && color.getPriceSurchargeEUR().signum() < 0) {
            throw new IllegalArgumentException("Price surcharge EUR cannot be negative.");
        }
    }

    private void normalizePrices(RoofColor color) {
        if (color.getPriceSurchargeCZK() != null && color.getPriceSurchargeCZK().compareTo(BigDecimal.ZERO) <= 0) {
            color.setPriceSurchargeCZK(null);
        }
        if (color.getPriceSurchargeEUR() != null && color.getPriceSurchargeEUR().compareTo(BigDecimal.ZERO) <= 0) {
            color.setPriceSurchargeEUR(null);
        }
    }
}