package org.example.eshop.admin.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.model.Addon;
// Potřebujeme AddonsRepository
import org.example.eshop.repository.AddonsRepository;
// ProductRepository zde nepotřebujeme pro kontrolu mazání, protože addon můžeme jen deaktivovat
// import org.example.eshop.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort; // Import pro Sort
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;

@Service
public class AddonsService {

    private static final Logger log = LoggerFactory.getLogger(AddonsService.class);

    @Autowired
    private AddonsRepository addonsRepository;
    // ProductRepository zde prozatím nepotřebujeme

    // --- Metody pro čtení (zůstávají) ---
    @Cacheable("allActiveAddons") // Použijeme cache definovanou v ehcache.xml
    @Transactional(readOnly = true)
    public List<Addon> getAllAddons() {
        log.debug("Fetching all addons");
        // Můžeme přidat výchozí řazení, např. podle názvu
        return addonsRepository.findAll(Sort.by("name"));
    }

    @Transactional(readOnly = true)
    public List<Addon> getAllActiveAddons() {
        log.debug("Fetching all active addons");
        // Předpokládáme, že budeme chtít metodu v repozitáři
        // Prozatím filtrujeme v paměti
        return addonsRepository.findAll(Sort.by("name")).stream()
                .filter(Addon::isActive)
                .toList();
        // Lepší řešení: Přidat do AddonsRepository: List<Addon> findByActiveTrue(Sort sort);
        // return addonsRepository.findByActiveTrue(Sort.by("name"));
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


    // --- Metody pro CRUD (pro CMS) - NOVÉ ---
    @Caching(evict = {
            @CacheEvict(value = "allActiveAddons", allEntries = true)
            // Můžeš přidat invalidaci i pro jiné cache, pokud addony ovlivňují např. produktovou cache
    })
    @Transactional
    public Addon createAddon(Addon addon) {
        log.info("Creating new addon: Name='{}', SKU='{}'", addon.getName(), addon.getSku());
        validateAddon(addon); // Validace

        // Kontrola unikátnosti názvu (case-insensitive)
        addonsRepository.findByNameIgnoreCase(addon.getName().trim()).ifPresent(a -> {
            throw new IllegalArgumentException("Doplněk s názvem '" + addon.getName().trim() + "' již existuje.");
        });
        addon.setName(addon.getName().trim());


         if (StringUtils.hasText(addon.getSku())) {
             addonsRepository.findBySkuIgnoreCase(addon.getSku().trim()).ifPresent(a -> {
                  throw new IllegalArgumentException("Doplněk s SKU '" + addon.getSku().trim() + "' již existuje.");
             });
             addon.setSku(addon.getSku().trim());
        }

        // Ceny musí být kladné (validace už kontroluje záporné, zde zajistíme > 0)
        if (addon.getPriceCZK() == null || addon.getPriceCZK().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Cena CZK musí být kladná.");
        }
        if (addon.getPriceEUR() == null || addon.getPriceEUR().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Cena EUR musí být kladná.");
        }

        addon.setActive(true); // Nový addon je defaultně aktivní
        return addonsRepository.save(addon);
    }

    @Caching(evict = {
            @CacheEvict(value = "allActiveAddons", allEntries = true)
            // Můžeš přidat @CacheEvict(value = "addonById", key = "#id") pokud bys cachoval i jednotlivě
    })
    @Transactional
    public Addon updateAddon(Long id, Addon addonData) {
        log.info("Updating addon with ID: {}", id);
        validateAddon(addonData); // Validace

        // Najde addon nebo vyhodí EntityNotFoundException
        Addon existingAddon = addonsRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Addon with ID " + id + " not found."));

        // ... (zbytek logiky pro update, kontrola unikátnosti názvu/SKU atd.) ...
        String newName = addonData.getName().trim();
        // Kontrola unikátnosti názvu, pokud se mění
        if (!existingAddon.getName().equalsIgnoreCase(newName)) {
            addonsRepository.findByNameIgnoreCase(newName)
                    .filter(found -> !found.getId().equals(id))
                    .ifPresent(found -> {
                        throw new IllegalArgumentException("Doplněk s názvem '" + newName + "' již existuje.");
                    });
            existingAddon.setName(newName);
        }
        // TODO: Kontrola unikátnosti SKU

        existingAddon.setDescription(addonData.getDescription());
        existingAddon.setActive(addonData.isActive());
        // Ceny musí být kladné
        if (addonData.getPriceCZK() == null || addonData.getPriceCZK().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Cena CZK musí být kladná.");
        }
        if (addonData.getPriceEUR() == null || addonData.getPriceEUR().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Cena EUR musí být kladná.");
        }
        existingAddon.setPriceCZK(addonData.getPriceCZK());
        existingAddon.setPriceEUR(addonData.getPriceEUR());
        existingAddon.setSku(addonData.getSku() != null ? addonData.getSku().trim() : null);


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

        // Pro doplňky nemusíme striktně kontrolovat použití, stačí deaktivovat.
        // Produkty, které ho mají přiřazený, ho jen nebudou moci znovu vybrat.
        // Historie v objednávkách zůstane zachována.
        if (addon.isActive()) {
            addon.setActive(false);
            addonsRepository.save(addon);
            log.info("Addon '{}' (ID: {}) successfully deactivated.", addon.getName(), id);
        } else {
            log.info("Addon '{}' (ID: {}) is already inactive.", addon.getName(), id);
        }
        // Fyzické mazání bychom zde také nedělali kvůli možným vazbám v OrderItemAddon
        // addonsRepository.delete(addon);
    }

    private void validateAddon(Addon addon) {
        if (addon == null) throw new IllegalArgumentException("Addon object cannot be null.");
        if (!StringUtils.hasText(addon.getName())) throw new IllegalArgumentException("Addon name cannot be empty.");
        // Ceny nesmí být záporné (nulové mohou být normalizovány, ale kladné jsou vyžadovány při create/update)
        if (addon.getPriceCZK() != null && addon.getPriceCZK().signum() < 0) {
            throw new IllegalArgumentException("Price CZK cannot be negative.");
        }
        if (addon.getPriceEUR() != null && addon.getPriceEUR().signum() < 0) {
            throw new IllegalArgumentException("Price EUR cannot be negative.");
        }
        // Případně validace SKU formátu, pokud bude implementováno
    }
}