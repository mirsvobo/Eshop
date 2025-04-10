package org.example.eshop.admin.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.eshop.model.Coupon;
import org.example.eshop.service.CouponService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime; // Import pro LocalDateTime
import java.time.format.DateTimeParseException; // Import pro chybu parsování data
import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/admin/coupons")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCouponController {

    private static final Logger log = LoggerFactory.getLogger(AdminCouponController.class);

    @Autowired private CouponService couponService;

    @ModelAttribute("currentUri")
    public String getCurrentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }

    // Odstraněno @ModelAttribute("couponTypes")

    @GetMapping
    public String listCoupons(Model model) {
        log.info("Requesting coupon list view.");
        try {
            List<Coupon> coupons = couponService.getAllCoupons();
            model.addAttribute("coupons", coupons);
        } catch (Exception e) {
            log.error("Error fetching coupons: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Nepodařilo se načíst seznam kupónů.");
            model.addAttribute("coupons", Collections.emptyList());
        }
        return "admin/coupons-list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        log.info("Requesting new coupon form.");
        Coupon newCoupon = new Coupon();
        newCoupon.setActive(true); // Defaultně aktivní
        model.addAttribute("coupon", newCoupon);
        model.addAttribute("pageTitle", "Vytvořit nový kupón");
        return "admin/coupon-form";
    }

    @PostMapping
    public String createCoupon(@Valid @ModelAttribute("coupon") Coupon coupon,
                               BindingResult bindingResult,
                               // Přidáváme parametry pro datum, protože @DateTimeFormat nemusí fungovat správně s @ModelAttribute
                               @RequestParam(value = "startDateString", required = false) String startDateString,
                               @RequestParam(value = "expirationDateString", required = false) String expirationDateString,
                               RedirectAttributes redirectAttributes,
                               Model model) {
        log.info("Attempting to create new coupon: {}", coupon.getCode());

        // Manuální zpracování datumů
        parseAndSetDates(coupon, startDateString, expirationDateString, bindingResult);

        // Spoléháme na validaci v CouponService, ale @Valid zachytí základní chyby
        if (bindingResult.hasErrors()) {
            log.warn("Initial validation errors creating coupon: {}", bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Vytvořit nový kupón (Chyba)");
            return "admin/coupon-form";
        }
        try {
            // Service provede vlastní detailní validaci
            Coupon savedCoupon = couponService.createCoupon(coupon);
            redirectAttributes.addFlashAttribute("successMessage", "Kupón '" + savedCoupon.getCode() + "' byl úspěšně vytvořen.");
            log.info("Coupon '{}' created successfully with ID: {}", savedCoupon.getCode(), savedCoupon.getId());
            return "redirect:/admin/coupons";
        } catch (IllegalArgumentException e) {
            log.warn("Error creating coupon '{}': {}", coupon.getCode(), e.getMessage());
            assignValidationError(e, bindingResult, model); // Přiřazení validační chyby
            model.addAttribute("pageTitle", "Vytvořit nový kupón (Chyba)");
            return "admin/coupon-form";
        } catch (Exception e) {
            log.error("Unexpected error creating coupon '{}': {}", coupon.getCode(), e.getMessage(), e);
            model.addAttribute("pageTitle", "Vytvořit nový kupón (Chyba)");
            model.addAttribute("errorMessage", "Při vytváření kupónu nastala neočekávaná chyba: " + e.getMessage());
            return "admin/coupon-form";
        }
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Requesting edit form for coupon ID: {}", id);
        try {
            Coupon coupon = couponService.findById(id) // findById vrací Optional
                    .orElseThrow(() -> new EntityNotFoundException("Kupón s ID " + id + " nenalezen."));
            model.addAttribute("coupon", coupon);
            model.addAttribute("pageTitle", "Upravit kupón: " + coupon.getCode());
            return "admin/coupon-form";
        } catch (EntityNotFoundException e) {
            log.warn("Coupon with ID {} not found for editing.", id);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/coupons";
        } catch (Exception e) {
            log.error("Error loading coupon ID {} for edit: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Nepodařilo se načíst kupón k úpravě.");
            return "redirect:/admin/coupons";
        }
    }

    @PostMapping("/{id}")
    public String updateCoupon(@PathVariable Long id,
                               @Valid @ModelAttribute("coupon") Coupon couponData,
                               BindingResult bindingResult,
                               @RequestParam(value = "startDateString", required = false) String startDateString,
                               @RequestParam(value = "expirationDateString", required = false) String expirationDateString,
                               RedirectAttributes redirectAttributes,
                               Model model) {
        log.info("Attempting to update coupon ID: {}", id);

        // Manuální zpracování datumů
        parseAndSetDates(couponData, startDateString, expirationDateString, bindingResult);

        if (bindingResult.hasErrors()) {
            log.warn("Initial validation errors updating coupon {}: {}", id, bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Upravit kupón (Chyba)");
            couponData.setId(id); // Zachovat ID pro formulář
            model.addAttribute("coupon", couponData); // Vrátíme data z formuláře
            return "admin/coupon-form";
        }
        try {
            // Service provede vlastní validaci
            Coupon updatedCoupon = couponService.updateCoupon(id, couponData); // Vrací Coupon
            redirectAttributes.addFlashAttribute("successMessage", "Kupón '" + updatedCoupon.getCode() + "' byl úspěšně aktualizován.");
            log.info("Coupon ID {} updated successfully.", id);
            return "redirect:/admin/coupons";
        } catch (EntityNotFoundException e) {
            log.warn("Cannot update coupon. Coupon not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/coupons";
        } catch (IllegalArgumentException e) {
            log.warn("Error updating coupon ID {}: {}", id, e.getMessage());
            assignValidationError(e, bindingResult, model); // Přiřazení chyby
            model.addAttribute("pageTitle", "Upravit kupón (Chyba)");
            couponData.setId(id);
            model.addAttribute("coupon", couponData); // Vrátíme data z formuláře
            return "admin/coupon-form";
        } catch (Exception e) {
            log.error("Unexpected error updating coupon ID {}: {}", id, e.getMessage(), e);
            model.addAttribute("pageTitle", "Upravit kupón (Chyba)");
            couponData.setId(id);
            model.addAttribute("coupon", couponData);
            model.addAttribute("errorMessage", "Při aktualizaci kupónu nastala neočekávaná chyba: " + e.getMessage());
            return "admin/coupon-form";
        }
    }

    @PostMapping("/{id}/delete")
    public String deactivateCoupon(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.warn("Attempting to DEACTIVATE coupon ID: {}", id);
        try {
            couponService.deactivateCoupon(id);
            redirectAttributes.addFlashAttribute("successMessage", "Kupón byl úspěšně deaktivován.");
            log.info("Coupon ID {} successfully deactivated.", id);
        } catch (EntityNotFoundException e) {
            log.warn("Cannot deactivate coupon. Coupon not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error deactivating coupon ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při deaktivaci kupónu nastala chyba: " + e.getMessage());
        }
        return "redirect:/admin/coupons";
    }

    // --- Pomocné metody ---

    private void parseAndSetDates(Coupon coupon, String startDateStr, String expirationDateStr, BindingResult bindingResult) {
        // Zpracování startDate
        if (StringUtils.hasText(startDateStr)) {
            try {
                // Zpracujeme YYYY-MM-DD a přidáme začátek dne
                coupon.setStartDate(LocalDate.parse(startDateStr).atStartOfDay());
            } catch (DateTimeParseException e) {
                log.warn("Invalid start date format: {}", startDateStr);
                bindingResult.rejectValue("startDate", "typeMismatch.coupon.startDate", "Neplatný formát data 'Platnost od'");
            }
        } else {
            coupon.setStartDate(null); // Povolit prázdné datum
        }

        // Zpracování expirationDate
        if (StringUtils.hasText(expirationDateStr)) {
            try {
                // Zpracujeme YYYY-MM-DD a přidáme konec dne pro platnost DO včetně
                coupon.setExpirationDate(LocalDate.parse(expirationDateStr).atTime(23, 59, 59));
            } catch (DateTimeParseException e) {
                log.warn("Invalid expiration date format: {}", expirationDateStr);
                bindingResult.rejectValue("expirationDate", "typeMismatch.coupon.expirationDate", "Neplatný formát data 'Platnost do'");
            }
        } else {
            coupon.setExpirationDate(null); // Povolit prázdné datum (neomezeno)
        }
    }


    // Pomocná metoda pro přiřazení validační chyby z IllegalArgumentException
    private void assignValidationError(IllegalArgumentException e, BindingResult bindingResult, Model model) {
        String message = e.getMessage();
        if (message == null) message = "Neznámá chyba validace.";

        if (message.contains("kódem")) {
            bindingResult.rejectValue("code", "error.coupon.duplicate", message);
        } else if (message.contains("Percentage value") || message.contains("Procentuální hodnota")) {
            bindingResult.rejectValue("value", "error.coupon.value", message); // Změněno na 'value'
        } else if (message.contains("fixed amount") || message.contains("Pevná částka")) {
            bindingResult.rejectValue("valueCZK", "error.coupon.value", message); // Změněno na 'valueCZK'
            bindingResult.rejectValue("valueEUR", "error.coupon.value", message); // Změněno na 'valueEUR'
        } else if (message.contains("Valid From") || message.contains("Valid To") || message.contains("Platnost od")) {
            bindingResult.rejectValue("startDate", "error.coupon.date", message); // Změněno na 'startDate'
            bindingResult.rejectValue("expirationDate", "error.coupon.date", message); // Změněno na 'expirationDate'
        } else if (message.contains("usage limit") || message.contains("limit použití")) {
            if (message.contains("nižší než aktuální")) {
                bindingResult.rejectValue("usageLimit", "error.coupon.limit.count", message); // Změněno na 'usageLimit'
            } else if (message.contains("na zákazníka")) {
                bindingResult.rejectValue("usageLimitPerCustomer", "error.coupon.limit.customer", message);
            } else {
                bindingResult.rejectValue("usageLimit", "error.coupon.limit", message); // Změněno na 'usageLimit'
            }
        } else if (message.contains("Minimální hodnota")) {
            if (message.contains("CZK")) bindingResult.rejectValue("minimumOrderValueCZK", "error.coupon.minvalue", message);
            if (message.contains("EUR")) bindingResult.rejectValue("minimumOrderValueEUR", "error.coupon.minvalue", message);
        }
        // Odstraněna větev pro 'typ kupónu', protože validace isPercentage je jinde
        else {
            model.addAttribute("errorMessage", message); // Obecná chyba
        }
    }
}