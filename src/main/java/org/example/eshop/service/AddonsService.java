package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.model.Addon;
import org.example.eshop.repository.AddonsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal; // Přidán import
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class AddonsService {

    private static final Logger log = LoggerFactory.getLogger(AddonsService.class);

    @Autowired
    private AddonsRepository addonsRepository;

    // --- Metody pro čtení ---

    @Transactional(readOnly = true)
    public List<Addon> getAllAddons() {
        log.debug("Fetching all addons");
        return addonsRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Addon> getAllActiveAddons() {
        // TODO: Pokud potřebujeme filtrovat aktivní, přidat metodu do AddonsRepository findByActiveTrue()
        log.debug("Fetching all active addons (currently fetching all)");
        // return addonsRepository.findByActiveTrue(); // Pokud existuje metoda
        return addonsRepository.findAll().stream().filter(Addon::isActive).toList(); // Filtrování v paměti
    }


    @Transactional(readOnly = true)
    public Optional<Addon> getAddonById(Long id) {
        log.debug("Fetching addon by ID: {}", id);
        return addonsRepository.findById(id);
    }

    /**
     * Najde doplňky podle seznamu jejich ID.
     * Používá se v OrderService pro načtení doplňků přidaných k položce.
     * @param ids Množina ID doplňků.
     * @return Seznam nalezených doplňků. Pokud ID nebylo nalezeno, není ve výsledku.
     */
    @Transactional(readOnly = true)
    public List<Addon> findAddonsByIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        log.debug("Fetching addons by IDs: {}", ids);
        return addonsRepository.findAllById(ids);
    }


    // --- Metody pro CRUD (pro CMS) ---

    @Transactional
    public Addon createAddon(Addon addon) {
        log.info("Creating new addon: Name='{}', SKU='{}'", addon.getName(), addon.getSku());
        // Validace
        if (!StringUtils.hasText(addon.getName())) {
            throw new IllegalArgumentException("Addon name cannot be empty.");
        }
        // Validovat obě ceny
        if ((addon.getPriceCZK() == null || addon.getPriceCZK().signum() < 0) &&
                (addon.getPriceEUR() == null || addon.getPriceEUR().signum() < 0)) {
            throw new IllegalArgumentException("At least one addon price (CZK or EUR) must be non-null and non-negative.");
        }
        // TODO: Zkontrolovat unikátnost SKU
        // if (StringUtils.hasText(addon.getSku()) && addonsRepository.existsBySkuIgnoreCase(addon.getSku())) { ... }

        // Nastavit ceny na 0, pokud jsou null
        if(addon.getPriceCZK() == null) addon.setPriceCZK(BigDecimal.ZERO);
        if(addon.getPriceEUR() == null) addon.setPriceEUR(BigDecimal.ZERO);

        return addonsRepository.save(addon);
    }


    @Transactional
    public Optional<Addon> updateAddon(Long id, Addon addonData) {
        log.info("Updating addon ID: {}", id);

        return addonsRepository.findById(id).map(existingAddon -> {
            // Validace
            if (!StringUtils.hasText(addonData.getName())) {
                throw new IllegalArgumentException("Addon name cannot be empty.");
            }
            // Validovat obě ceny
            if ((addonData.getPriceCZK() == null || addonData.getPriceCZK().signum() < 0) &&
                    (addonData.getPriceEUR() == null || addonData.getPriceEUR().signum() < 0)) {
                throw new IllegalArgumentException("At least one addon price (CZK or EUR) must be non-null and non-negative.");
            }
            // TODO: Validace unikátnosti SKU

            existingAddon.setName(addonData.getName());
            existingAddon.setDescription(addonData.getDescription());
            // Nastavit ceny (null nebo hodnota)
            existingAddon.setPriceCZK(addonData.getPriceCZK());
            existingAddon.setPriceEUR(addonData.getPriceEUR());
            existingAddon.setSku(addonData.getSku());
            existingAddon.setActive(addonData.isActive());

            Addon updatedAddon = addonsRepository.save(existingAddon);
            log.info("Addon {} (ID: {}) updated successfully.", updatedAddon.getName(), updatedAddon.getId());
            return updatedAddon; // Vrátit přímo Addon
        });
    }


    @Transactional
    public void deleteAddon(Long id) {
        log.warn("Attempting to delete addon with ID: {}", id);
        Addon addon = addonsRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Addon with id " + id + " not found for deletion."));

        // Doporučený postup: Označit jako neaktivní
        if (addon.isActive()) {
            addon.setActive(false);
            addonsRepository.save(addon);
            log.warn("Addon {} (ID: {}) marked as inactive instead of hard delete.", addon.getName(), id);
        } else {
            log.info("Addon {} (ID: {}) is already inactive.", addon.getName(), id);
        }
        // Tvrdé smazání (nedoporučeno kvůli referencím)
    }
}