package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.model.TaxRate;
import org.example.eshop.repository.ProductRepository; // Pro kontrolu závislostí
import org.example.eshop.repository.TaxRateRepository;
import org.slf4j.Logger;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class TaxRateService {

    private static final Logger log = LoggerFactory.getLogger(TaxRateService.class);

    @Autowired private TaxRateRepository taxRateRepository;
    @Autowired private ProductRepository productRepository; // Pro kontrolu použití sazby

    /**
     * Vrátí seznam všech daňových sazeb.
     * @return Seznam TaxRate.
     */
    @Cacheable("allTaxRates")
    @Transactional(readOnly = true)
    public List<TaxRate> getAllTaxRates() {
        log.debug("Fetching all tax rates");
        return taxRateRepository.findAll();
    }

    /**
     * Najde daňovou sazbu podle jejího ID.
     * @param taxRateId ID sazby.
     * @return Optional obsahující TaxRate, pokud existuje.
     */
    @Transactional(readOnly = true)
    public Optional<TaxRate> getTaxRateById(Long taxRateId){
        log.debug("Fetching tax rate by ID: {}", taxRateId);
        return taxRateRepository.findById(taxRateId);
    }

    /**
     * Vytvoří novou daňovou sazbu.
     * @param taxRate Objekt sazby k vytvoření.
     * @return Uložená sazba.
     */
    @Caching(evict = {
            @CacheEvict(value = "allTaxRates", allEntries = true)
    })
    @Transactional
    public TaxRate createTaxRate(TaxRate taxRate) {
        if (!StringUtils.hasText(taxRate.getName())) {
            throw new IllegalArgumentException("Tax rate name cannot be empty.");
        }
        log.info("Creating new tax rate: {}", taxRate.getName());
        // Validace: Unikátnost názvu? Platná hodnota sazby?
        if (taxRate.getRate() == null || taxRate.getRate().compareTo(BigDecimal.ZERO) < 0 || taxRate.getRate().compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Tax rate value must be between 0.0000 and 1.0000 (e.g., 0.21 for 21%).");
        }
        // Zkontrolovat unikátnost názvu
        if (taxRateRepository.existsByNameIgnoreCase(taxRate.getName().trim())) {
            throw new IllegalArgumentException("TaxRate with name '" + taxRate.getName().trim() + "' already exists.");
        }
        taxRate.setName(taxRate.getName().trim());
        return taxRateRepository.save(taxRate);
    }

    /**
     * Aktualizuje existující daňovou sazbu.
     * @param taxRateId ID sazby k aktualizaci.
     * @param taxRateData Objekt s novými daty.
     * @return Optional s aktualizovanou sazbou.
     */
    @Caching(evict = {
            @CacheEvict(value = "allTaxRates", allEntries = true)
    })
    @Transactional
    public Object updateTaxRate(Long taxRateId, TaxRate taxRateData) {
        log.info("Updating tax rate with ID: {}", taxRateId);
        if (!StringUtils.hasText(taxRateData.getName())) {
            throw new IllegalArgumentException("Tax rate name cannot be empty.");
        }
        if (taxRateData.getRate() == null || taxRateData.getRate().compareTo(BigDecimal.ZERO) < 0 || taxRateData.getRate().compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Tax rate value must be between 0.0000 and 1.0000.");
        }
        String newName = taxRateData.getName().trim();

        return taxRateRepository.findById(taxRateId)
                .map(existingRate -> {
                    // Validace unikátnosti názvu, pokud se mění
                    if (!existingRate.getName().equalsIgnoreCase(newName)) {
                        if (taxRateRepository.existsByNameIgnoreCase(newName)) {
                            taxRateRepository.findByNameIgnoreCase(newName).filter(found -> !found.getId().equals(taxRateId)).ifPresent(existing -> {
                                throw new IllegalArgumentException("TaxRate with name '" + newName + "' already exists.");
                            });
                        }
                        existingRate.setName(newName);
                    }
                    // Aktualizovat ostatní pole
                    existingRate.setRate(taxRateData.getRate());
                    existingRate.setReverseCharge(taxRateData.isReverseCharge());

                    TaxRate updatedRate = taxRateRepository.save(existingRate);
                    log.info("Tax rate {} (ID: {}) updated successfully.", updatedRate.getName(), updatedRate.getId());
                    return Optional.of(updatedRate);
                });
    }

    /**
     * Smaže daňovou sazbu podle ID.
     * @param taxRateId ID sazby ke smazání.
     * @throws EntityNotFoundException pokud sazba neexistuje.
     * @throws IllegalStateException pokud je sazba přiřazena k nějakým produktům.
     */
    @Caching(evict = {
            @CacheEvict(value = "allTaxRates", allEntries = true)
    })
    @Transactional
    public void deleteTaxRate(Long taxRateId) {
        log.warn("Attempting to delete tax rate with ID: {}", taxRateId);
        TaxRate rate = getTaxRateById(taxRateId)
                .orElseThrow(() -> new EntityNotFoundException("TaxRate with id " + taxRateId + " not found for deletion."));

        long countProductsUsingRate = productRepository.findAll().stream()
                .filter(product -> product.getAvailableTaxRates() != null && product.getAvailableTaxRates().contains(rate))
                .count();

        if (countProductsUsingRate > 0) {
            log.error("Cannot delete tax rate '{}' (ID: {}) because it is assigned to {} product(s).",
                    rate.getName(), taxRateId, countProductsUsingRate);
            throw new IllegalStateException("Cannot delete tax rate '" + rate.getName() + "' as it is currently assigned to " + countProductsUsingRate + " products. Please reassign products first.");
        }
        try {
            taxRateRepository.deleteById(taxRateId);
            log.info("Tax rate {} (ID: {}) deleted successfully.", rate.getName(), taxRateId);
        } catch (DataIntegrityViolationException e) {
            log.error("Cannot delete tax rate '{}' (ID: {}) due to unexpected data integrity violation.", rate.getName(), taxRateId, e);
            throw new IllegalStateException("Cannot delete tax rate '" + rate.getName() + "' due to existing references.", e);
        }
    }
}