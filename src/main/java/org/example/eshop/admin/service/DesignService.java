package org.example.eshop.admin.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.model.Design;
import org.example.eshop.repository.DesignRepository;
import org.example.eshop.repository.ProductRepository; // Pro kontrolu závislostí
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
public class DesignService {

    private static final Logger log = LoggerFactory.getLogger(DesignService.class);

    @Autowired private DesignRepository designRepository;
    @Autowired private ProductRepository productRepository; // Pro kontrolu použití

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
        validateDesign(design);

        // Kontrola unikátnosti názvu
        designRepository.findByNameIgnoreCase(design.getName().trim()).ifPresent(d -> {
            throw new IllegalArgumentException("Design s názvem '" + design.getName().trim() + "' již existuje.");
        });
        design.setName(design.getName().trim());
        design.setActive(true); // Nový design je defaultně aktivní
        return designRepository.save(design);
    }

    @Transactional
    public Design updateDesign(Long id, Design designData) {
        log.info("Updating design with ID: {}", id);
        validateDesign(designData);

        Design existingDesign = designRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Design with ID " + id + " not found."));

        String newName = designData.getName().trim();
        // Kontrola unikátnosti názvu, pokud se mění
        if (!existingDesign.getName().equalsIgnoreCase(newName)) {
            designRepository.findByNameIgnoreCase(newName)
                    .filter(found -> !found.getId().equals(id)) // Zkontrolovat, zda nalezený není ten samý
                    .ifPresent(found -> {
                        throw new IllegalArgumentException("Design s názvem '" + newName + "' již existuje.");
                    });
            existingDesign.setName(newName);
        }

        existingDesign.setDescription(designData.getDescription());
        existingDesign.setActive(designData.isActive()); // Umožníme měnit aktivitu

        Design updatedDesign = designRepository.save(existingDesign);
        log.info("Design '{}' (ID: {}) updated successfully.", updatedDesign.getName(), updatedDesign.getId());
        return updatedDesign;
    }

    @Transactional
    public void deleteDesign(Long id) {
        log.warn("Attempting to deactivate (soft delete) design with ID: {}", id);
        Design design = designRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Design with ID " + id + " not found."));

        // Kontrola závislostí - jsou produkty, které TENTO design používají?
        // Efektivnější by bylo mít metodu v ProductRepository: countByAvailableDesignsContains(Design design)
        boolean isUsed = design.getProducts() != null && !design.getProducts().isEmpty();
        // Alternativa (méně efektivní, pokud je mnoho produktů):
        // boolean isUsed = productRepository.findAll().stream()
        //        .anyMatch(p -> p.getAvailableDesigns() != null && p.getAvailableDesigns().contains(design));


        if (isUsed) {
            log.error("Cannot deactivate design '{}' (ID: {}) because it is assigned to products.", design.getName(), id);
            // Můžeme buď vyhodit výjimku, nebo jen deaktivovat a nechat ho přiřazený
            // Prozatím vyhodíme výjimku, aby admin musel nejprve odebrat asociace
            throw new IllegalStateException("Design '" + design.getName() + "' nelze deaktivovat, je přiřazen k produktům. Nejprve odstraňte přiřazení u produktů.");
            // Alternativa: Jen deaktivovat
            // design.setActive(false);
            // designRepository.save(design);
            // log.warn("Design '{}' (ID: {}) deactivated, but it remains assigned to some products.", design.getName(), id);
        } else {
            // Pokud není použit, můžeme ho deaktivovat (nebo smazat - preferujeme deaktivaci)
            if (design.isActive()) {
                design.setActive(false);
                designRepository.save(design);
                log.info("Design '{}' (ID: {}) successfully deactivated.", design.getName(), id);
            } else {
                log.info("Design '{}' (ID: {}) is already inactive.", design.getName(), id);
            }
            // Fyzické smazání:
            // designRepository.delete(design);
            // log.info("Design '{}' (ID: {}) successfully deleted.", design.getName(), id);
        }
    }

    private void validateDesign(Design design) {
        if (design == null) {
            throw new IllegalArgumentException("Design object cannot be null.");
        }
        if (!StringUtils.hasText(design.getName())) {
            throw new IllegalArgumentException("Design name cannot be empty.");
        }
        // Případně další validace (délka názvu, popisu atd.)
    }
}