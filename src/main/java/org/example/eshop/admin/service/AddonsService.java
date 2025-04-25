package org.example.eshop.admin.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.model.Addon;
import org.example.eshop.repository.AddonsRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class AddonsService {

    private static final Logger log = LoggerFactory.getLogger(AddonsService.class);
    private static final List<String> VALID_PRICING_TYPES = List.of("FIXED", "PER_CM_WIDTH", "PER_CM_LENGTH", "PER_CM_HEIGHT", "PER_SQUARE_METER");
    @Autowired
    private AddonsRepository addonsRepository;

    // --- Metody pro čtení (zůstávají) ---
    @Cacheable("allActiveAddons")
    @Transactional(readOnly = true)
    public List<Addon> getAllAddons() {
        log.debug("Fetching all addons");
        return addonsRepository.findAll(Sort.by("name"));
    }

    @Transactional(readOnly = true)
    public List<Addon> getAllActiveAddons() {
        log.debug("Fetching all active addons");
        // Lepší řešení: Přidat do AddonsRepository: List<Addon> findByActiveTrueOrderByNameAsc();
        return addonsRepository.findByActiveTrueOrderByNameAsc();


    }


    @Transactional(readOnly = true)
    public Optional<Addon> getAddonById(Long id) {
        log.debug("Fetching addon by ID: {}", id);
        return addonsRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Addon> findAddonsByIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        log.debug("Fetching addons by IDs: {}", ids);
        return addonsRepository.findAllById(ids);
    }


    // --- Metody pro CRUD (pro CMS) ---
    @Caching(evict = {
            @CacheEvict(value = "allActiveAddons", allEntries = true)
    })
    @Transactional
    public Addon createAddon(Addon addon) {
        log.info("Creating new addon: Name='{}', Category='{}', Type='{}', SKU='{}'",
                addon.getName(), addon.getCategory(), addon.getPricingType(), addon.getSku());

        // Základní validace + Validace nových polí
        validateAddonCommonFields(addon); // Zvaliduje jméno, kategorii, typ ceny

        // Validace cen podle typu
        validateAndNormalizePrices(addon); // Zvaliduje ceny a vynuluje nepoužívané

        // Trim a kontrola unikátnosti
        addon.setName(addon.getName().trim());
        addonsRepository.findByNameIgnoreCase(addon.getName()).ifPresent(a -> {
            throw new IllegalArgumentException("Doplněk s názvem '" + addon.getName() + "' již existuje.");
        });

        if (StringUtils.hasText(addon.getSku())) {
            addon.setSku(addon.getSku().trim());
            addonsRepository.findBySkuIgnoreCase(addon.getSku()).ifPresent(a -> {
                throw new IllegalArgumentException("Doplněk s SKU '" + addon.getSku() + "' již existuje.");
            });
        } else {
            addon.setSku(null); // Zajistíme, že prázdné SKU je null
        }

        // Kategorie - jen trimneme, povinnost je validována výše
        addon.setCategory(addon.getCategory().trim());

        addon.setActive(true); // Nový addon je defaultně aktivní
        Addon savedAddon = addonsRepository.save(addon);
        log.info("Addon '{}' (ID: {}) created successfully.", savedAddon.getName(), savedAddon.getId());
        return savedAddon;
    }
// V třídě AddonsService

    // V třídě AddonsService

    @Caching(evict = {
            @CacheEvict(value = "allActiveAddons", allEntries = true)
            // @CacheEvict(value = "addonById", key = "#id")
    })
    @Transactional
    public Addon updateAddon(Long id, Addon addonData) {
        log.info("Updating addon with ID: {}", id);

        // Najde addon nebo vyhodí EntityNotFoundException
        Addon existingAddon = addonsRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Addon with ID " + id + " not found."));

        // Validace dat z formuláře (addonData)
        validateAddonCommonFields(addonData); // Zvaliduje jméno, kategorii, typ ceny
        validateAndNormalizePrices(addonData); // Zvaliduje ceny a vynuluje nepoužívané

        // Kontrola unikátnosti názvu, pokud se mění
        String newName = addonData.getName().trim();
        if (!existingAddon.getName().equalsIgnoreCase(newName)) {
            // --- OPRAVA: Explicitní práce s Optional<Addon> ---
            Optional<Addon> foundByNameOpt = addonsRepository.findByNameIgnoreCase(newName);
            if (foundByNameOpt.isPresent()) {
                Addon foundAddon = foundByNameOpt.get();
                // Zkontrolujeme, jestli nalezený addon není ten samý, který upravujeme
                if (!foundAddon.getId().equals(id)) {
                    throw new IllegalArgumentException("Doplněk s názvem '" + newName + "' již existuje (ID: " + foundAddon.getId() + ").");
                }
            }
            // --- KONEC OPRAVY ---
        }

        // Kontrola unikátnosti SKU, pokud se mění a není prázdné
        String newSku = StringUtils.hasText(addonData.getSku()) ? addonData.getSku().trim() : null;
        String existingSku = StringUtils.hasText(existingAddon.getSku()) ? existingAddon.getSku().trim() : null;

        if (newSku != null && !newSku.equalsIgnoreCase(existingSku)) {
            // --- OPRAVA: Explicitní práce s Optional<Addon> ---
            Optional<Addon> foundBySkuOpt = addonsRepository.findBySkuIgnoreCase(newSku); // Použije opravenou metodu
            if (foundBySkuOpt.isPresent()) {
                Addon foundAddon = foundBySkuOpt.get();
                // Zkontrolujeme, jestli nalezený addon není ten samý, který upravujeme
                if (!foundAddon.getId().equals(id)) {
                    throw new IllegalArgumentException("Doplněk s SKU '" + newSku + "' již existuje (ID: " + foundAddon.getId() + ").");
                }
            }
            // --- KONEC OPRAVY ---
        }

        // *** AKTUALIZACE POLÍ NA EXISTUJÍCÍ ENTITĚ ***
        existingAddon.setName(newName);
        existingAddon.setDescription(addonData.getDescription());
        existingAddon.setActive(addonData.isActive());
        existingAddon.setSku(newSku); // Uložíme trimnuté nebo null

        // Aktualizace nových polí
        existingAddon.setCategory(addonData.getCategory().trim()); // Ukládáme trimnutou kategorii
        existingAddon.setPricingType(addonData.getPricingType()); // Typ ceny (už je validovaný)

        // Nastavení cen podle typu (data už jsou validovaná a případně vynulovaná výše)
        existingAddon.setPriceCZK(addonData.getPriceCZK());
        existingAddon.setPriceEUR(addonData.getPriceEUR());
        existingAddon.setPricePerUnitCZK(addonData.getPricePerUnitCZK());
        existingAddon.setPricePerUnitEUR(addonData.getPricePerUnitEUR());
        // *** KONEC AKTUALIZACE POLÍ ***


        Addon updatedAddon = addonsRepository.save(existingAddon); // Uložíme změny
        log.info("Addon '{}' (ID: {}) updated successfully.", updatedAddon.getName(), updatedAddon.getId());
        return updatedAddon; // Vrátíme uložený Addon
    }

    @Caching(evict = {
            @CacheEvict(value = "allActiveAddons", allEntries = true)
            // @CacheEvict(value = "addonById", key = "#id")
    })
    @Transactional
    public void deleteAddon(Long id) {
        log.warn("Attempting to deactivate (soft delete) addon with ID: {}", id);
        Addon addon = addonsRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Addon with ID " + id + " not found."));

        if (addon.isActive()) {
            addon.setActive(false);
            addonsRepository.save(addon);
            log.info("Addon '{}' (ID: {}) successfully deactivated.", addon.getName(), id);
        } else {
            log.info("Addon '{}' (ID: {}) is already inactive.", addon.getName(), id);
        }
    }

    // --- Pomocné validační metody ---

    private void validateAddonCommonFields(Addon addon) {
        if (addon == null) throw new IllegalArgumentException("Addon object cannot be null.");
        if (!StringUtils.hasText(addon.getName()))
            throw new IllegalArgumentException("Název doplňku nesmí být prázdný.");
        if (!StringUtils.hasText(addon.getCategory()))
            throw new IllegalArgumentException("Kategorie doplňku musí být vyplněna.");
        if (!StringUtils.hasText(addon.getPricingType()))
            throw new IllegalArgumentException("Typ ceny musí být vybrán.");
        if (!VALID_PRICING_TYPES.contains(addon.getPricingType())) {
            throw new IllegalArgumentException("Neplatný typ ceny: " + addon.getPricingType() + ". Povolené hodnoty: " + VALID_PRICING_TYPES);
        }
    }

    private void validateAndNormalizePrices(Addon addon) {
        if (addon == null) { // Přidána kontrola null pro addon
            throw new IllegalArgumentException("Addon object cannot be null for price validation.");
        }

        String pricingType = addon.getPricingType();
        if (pricingType == null) { // Přidána kontrola null pro pricingType
            throw new IllegalArgumentException("Pricing type cannot be null for price validation.");
        }

        if ("FIXED".equals(pricingType)) {
            // Validace pro FIXNÍ ceny (musí být > 0)
            if (addon.getPriceCZK() == null || addon.getPriceCZK().compareTo(BigDecimal.ZERO) <= 0) {

                throw new IllegalArgumentException("Pro typ ceny 'FIXED' musí být kladná 'Cena CZK'.");
            }
            if (addon.getPriceEUR() == null || addon.getPriceEUR().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Pro typ ceny 'FIXED' musí být kladná 'Cena EUR'.");
            }

            // --- KLÍČOVÁ ZMĚNA: Nastavení cen za jednotku na NULU místo NULL ---
            addon.setPricePerUnitCZK(BigDecimal.ZERO);
            addon.setPricePerUnitEUR(BigDecimal.ZERO);
            log.debug("Pricing type is FIXED. Setting per-unit prices to ZERO for addon '{}'.", addon.getName());

        } else { // Dimensional pricing (PER_CM_*, PER_SQUARE_METER)
            // Validace pro DIMENZIONÁLNÍ ceny (musí být > 0)
            if (addon.getPricePerUnitCZK() == null || addon.getPricePerUnitCZK().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Pro dimenzionální ceny musí být kladná 'Cena za jednotku CZK'.");
            }
            if (addon.getPricePerUnitEUR() == null || addon.getPricePerUnitEUR().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Pro dimenzionální ceny musí být kladná 'Cena za jednotku EUR'.");
            }

            // --- KLÍČOVÁ ZMĚNA: Nastavení fixních cen na NULU místo NULL ---
            addon.setPriceCZK(BigDecimal.ZERO);
            addon.setPriceEUR(BigDecimal.ZERO);
            log.debug("Pricing type is '{}'. Setting fixed prices to ZERO for addon '{}'.", pricingType, addon.getName());
        }
    }
}
