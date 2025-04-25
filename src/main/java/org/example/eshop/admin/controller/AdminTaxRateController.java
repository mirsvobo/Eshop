package org.example.eshop.admin.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.eshop.model.TaxRate;
import org.example.eshop.service.TaxRateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin/tax-rates")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTaxRateController {

    private static final Logger log = LoggerFactory.getLogger(AdminTaxRateController.class);

    @Autowired
    private TaxRateService taxRateService;

    @ModelAttribute("currentUri")
    public String getCurrentUri(HttpServletRequest request) {
        // Jednoduché URI pro zvýraznění hlavního odkazu v menu
        return request.getRequestURI().replaceFirst("/(new|\\d+/edit)$", "");
    }

    @GetMapping
    public String listTaxRates(Model model) {
        log.info("Requesting tax rate list view.");
        try {
            // Načteme sazby - předpokládáme, že chceme nějaké řazení, např. podle ID nebo názvu
            // Pokud service nemá metodu pro řazení, můžeme použít findAll() a seřadit zde,
            // ale lepší je mít řazení v service/repository. Prozatím použijeme findAll().
            List<TaxRate> rates = taxRateService.getAllTaxRates();
            // Pokud bychom chtěli řadit zde: rates.sort(Comparator.comparing(TaxRate::getName));
            model.addAttribute("taxRates", rates);
        } catch (Exception e) {
            log.error("Error fetching tax rates: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Nepodařilo se načíst daňové sazby.");
            model.addAttribute("taxRates", Collections.emptyList());
        }
        return "admin/tax-rates-list";
    }

    @GetMapping("/new")
    public String showCreateTaxRateForm(Model model) {
        log.info("Requesting new tax rate form.");
        model.addAttribute("taxRate", new TaxRate());
        model.addAttribute("pageTitle", "Vytvořit novou daňovou sazbu");
        return "admin/tax-rate-form";
    }

    @PostMapping
    public String createTaxRate(@Valid @ModelAttribute("taxRate") TaxRate taxRate,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        log.info("Attempting to create new tax rate with name: {}", taxRate.getName());

        // Dodatečná validace (příklad - i když @Valid by mělo pokrýt NotNull/NotBlank)
        if (taxRate.getRate() == null) {
            bindingResult.rejectValue("rate", "NotNull", "Hodnota sazby nesmí být prázdná.");
        }

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors creating tax rate: {}", bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Vytvořit novou daňovou sazbu (Chyba)");
            return "admin/tax-rate-form";
        }
        try {
            TaxRate savedRate = taxRateService.createTaxRate(taxRate);
            redirectAttributes.addFlashAttribute("successMessage", "Daňová sazba '" + savedRate.getName() + "' byla úspěšně vytvořena.");
            log.info("Tax rate '{}' created successfully with ID: {}", savedRate.getName(), savedRate.getId());
            return "redirect:/admin/tax-rates";
        } catch (IllegalArgumentException e) {
            log.warn("Error creating tax rate '{}': {}", taxRate.getName(), e.getMessage());
            // Chybu specifickou pro pole name nebo rate přidáme k danému poli
            if (e.getMessage().contains("already exists")) {
                bindingResult.rejectValue("name", "error.taxRate.duplicate", e.getMessage());
            } else if (e.getMessage().contains("value must be between")) {
                bindingResult.rejectValue("rate", "error.taxRate.value", e.getMessage());
            } else {
                // Obecná chyba se zobrazí nahoře
                model.addAttribute("errorMessage", e.getMessage());
            }
            model.addAttribute("pageTitle", "Vytvořit novou daňovou sazbu (Chyba)");
            return "admin/tax-rate-form";
        } catch (Exception e) {
            log.error("Unexpected error creating tax rate '{}': {}", taxRate.getName(), e.getMessage(), e);
            model.addAttribute("errorMessage", "Při vytváření sazby nastala neočekávaná chyba: " + e.getMessage());
            model.addAttribute("pageTitle", "Vytvořit novou daňovou sazbu (Chyba)");
            return "admin/tax-rate-form";
        }
    }

    @GetMapping("/{id}/edit")
    public String showEditTaxRateForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Requesting edit form for tax rate ID: {}", id);
        try {
            TaxRate rate = taxRateService.getTaxRateById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Daňová sazba s ID " + id + " nenalezena."));
            model.addAttribute("taxRate", rate);
            model.addAttribute("pageTitle", "Upravit daňovou sazbu: " + rate.getName());
            return "admin/tax-rate-form";
        } catch (EntityNotFoundException e) {
            log.warn("Tax rate with ID {} not found for editing.", id);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/tax-rates";
        } catch (Exception e) {
            log.error("Error loading tax rate ID {} for edit: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Nepodařilo se načíst sazbu k úpravě.");
            return "redirect:/admin/tax-rates";
        }
    }

    @PostMapping("/{id}")
    public String updateTaxRate(@PathVariable Long id,
                                @Valid @ModelAttribute("taxRate") TaxRate taxRateData,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        log.info("Attempting to update tax rate ID: {}", id);

        if (taxRateData.getRate() == null) {
            bindingResult.rejectValue("rate", "NotNull", "Hodnota sazby nesmí být prázdná.");
        }

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors updating tax rate {}: {}", id, bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Upravit daňovou sazbu (Chyba)");
            taxRateData.setId(id); // Zachovat ID pro action formuláře
            return "admin/tax-rate-form";
        }
        try {
            // updateTaxRate v service nyní vrací Object, ale očekáváme Optional<TaxRate>
            // Je třeba upravit service, nebo zde zpracovat Object
            Optional<Object> updatedRateOpt = (Optional<Object>) taxRateService.updateTaxRate(id, taxRateData);

            if (updatedRateOpt.isPresent()) {
                TaxRate updatedRate = (TaxRate) updatedRateOpt.get(); // Přetypování
                redirectAttributes.addFlashAttribute("successMessage", "Daňová sazba '" + updatedRate.getName() + "' byla úspěšně aktualizována.");
                log.info("Tax rate ID {} updated successfully.", id);
                return "redirect:/admin/tax-rates";
            } else {
                // Toto by nemělo nastat, pokud updateTaxRate najde entitu
                throw new EntityNotFoundException("Daňová sazba s ID " + id + " nenalezena (při pokusu o update).");
            }

        } catch (EntityNotFoundException e) {
            log.warn("Cannot update tax rate. Tax rate not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/tax-rates";
        } catch (IllegalArgumentException e) {
            log.warn("Error updating tax rate ID {}: {}", id, e.getMessage());
            if (e.getMessage().contains("already exists")) {
                bindingResult.rejectValue("name", "error.taxRate.duplicate", e.getMessage());
            } else if (e.getMessage().contains("value must be between")) {
                bindingResult.rejectValue("rate", "error.taxRate.value", e.getMessage());
            } else {
                model.addAttribute("errorMessage", e.getMessage());
            }
            model.addAttribute("pageTitle", "Upravit daňovou sazbu (Chyba)");
            taxRateData.setId(id);
            return "admin/tax-rate-form";
        } catch (Exception e) {
            log.error("Unexpected error updating tax rate ID {}: {}", id, e.getMessage(), e);
            model.addAttribute("pageTitle", "Upravit daňovou sazbu (Chyba)");
            taxRateData.setId(id);
            model.addAttribute("errorMessage", "Při aktualizaci sazby nastala neočekávaná chyba: " + e.getMessage());
            return "admin/tax-rate-form";
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteTaxRate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.warn("Attempting to DELETE tax rate ID: {}", id);
        try {
            taxRateService.deleteTaxRate(id);
            redirectAttributes.addFlashAttribute("successMessage", "Daňová sazba byla úspěšně smazána.");
            log.info("Tax rate ID {} deleted successfully.", id);
        } catch (EntityNotFoundException e) {
            log.warn("Cannot delete tax rate. Tax rate not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (IllegalStateException | DataIntegrityViolationException e) {
            log.error("Error deleting tax rate ID {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error deleting tax rate ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při mazání sazby nastala neočekávaná chyba: " + e.getMessage());
        }
        return "redirect:/admin/tax-rates";
    }
}