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
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/admin/coupons")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCouponController {

    private static final Logger log = LoggerFactory.getLogger(AdminCouponController.class);

    @Autowired
    private CouponService couponService;

    @ModelAttribute("currentUri")
    public String getCurrentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }

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
        newCoupon.setActive(true);
        newCoupon.setPercentage(false); // Default na fixní/dopravu
        newCoupon.setFreeShipping(false);
        model.addAttribute("coupon", newCoupon);
        model.addAttribute("pageTitle", "Vytvořit nový kupón");
        return "admin/coupon-form";
    }

    @PostMapping
    public String createCoupon(@Valid @ModelAttribute("coupon") Coupon coupon, // Použijeme data přímo z coupon objektu
                               BindingResult bindingResult,
                               @RequestParam(value = "startDateString", required = false) String startDateString,
                               @RequestParam(value = "expirationDateString", required = false) String expirationDateString,
                               // Odstraněny @RequestParam pro isPercentage a freeShipping
                               RedirectAttributes redirectAttributes,
                               Model model) {
        // Logujeme hodnoty PŘÍMO z navázaného objektu coupon
        log.info("Attempting to create new coupon: Code='{}', isPercentage={}, freeShipping={}",
                coupon.getCode(), coupon.isPercentage(), coupon.isFreeShipping());

        parseAndSetDates(coupon, startDateString, expirationDateString, bindingResult);

        // @Valid zachytí základní anotace
        if (bindingResult.hasErrors()) {
            log.warn("Initial validation errors creating coupon: {}", bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Vytvořit nový kupón (Chyba)");
            // Vrátíme data z formuláře (včetně isPercentage a freeShipping)
            model.addAttribute("coupon", coupon);
            return "admin/coupon-form";
        }
        try {
            // Service metoda nyní dostane coupon objekt s flagy nastavenými Springem
            Coupon savedCoupon = couponService.createCoupon(coupon);
            redirectAttributes.addFlashAttribute("successMessage", "Kupón '" + savedCoupon.getCode() + "' byl úspěšně vytvořen.");
            log.info("Coupon '{}' created successfully with ID: {}", savedCoupon.getCode(), savedCoupon.getId());
            return "redirect:/admin/coupons";
        } catch (IllegalArgumentException e) {
            log.warn("Error creating coupon '{}': {}", coupon.getCode(), e.getMessage());
            assignValidationError(e, bindingResult, model); // Zůstává stejné
            model.addAttribute("pageTitle", "Vytvořit nový kupón (Chyba)");
            // Vracíme data z formuláře
            model.addAttribute("coupon", coupon);
            return "admin/coupon-form";
        } catch (Exception e) {
            log.error("Unexpected error creating coupon '{}': {}", coupon.getCode(), e.getMessage(), e);
            model.addAttribute("pageTitle", "Vytvořit nový kupón (Chyba)");
            model.addAttribute("errorMessage", "Při vytváření kupónu nastala neočekávaná chyba: " + e.getMessage());
            model.addAttribute("coupon", coupon);
            return "admin/coupon-form";
        }
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        // Zůstává stejné, jen už nepotřebujeme explicitně přidávat isPercentage a freeShipping,
        // protože jsou součástí objektu coupon
        log.info("Requesting edit form for coupon ID: {}", id);
        try {
            Coupon coupon = couponService.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Kupón s ID " + id + " nenalezen."));
            model.addAttribute("coupon", coupon);
            model.addAttribute("pageTitle", "Upravit kupón: " + coupon.getCode());
            // Odstraněno: model.addAttribute("isPercentage", coupon.isPercentage());
            // Odstraněno: model.addAttribute("freeShipping", coupon.isFreeShipping());
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
                               @Valid @ModelAttribute("coupon") Coupon couponData, // isPercentage a freeShipping přijdou v tomto objektu
                               BindingResult bindingResult,
                               @RequestParam(value = "startDateString", required = false) String startDateString,
                               @RequestParam(value = "expirationDateString", required = false) String expirationDateString,
                               // Odstraněny @RequestParam pro isPercentage a freeShipping
                               RedirectAttributes redirectAttributes,
                               Model model) {
        log.info("Attempting to update coupon ID: {}. Received flags: isPercentage={}, freeShipping={}",
                id, couponData.isPercentage(), couponData.isFreeShipping());

        parseAndSetDates(couponData, startDateString, expirationDateString, bindingResult);

        if (bindingResult.hasErrors()) {
            log.warn("Initial validation errors updating coupon {}: {}", id, bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Upravit kupón (Chyba)");
            couponData.setId(id); // Zachovat ID pro formulář
            model.addAttribute("coupon", couponData); // Vrátíme data z formuláře
            return "admin/coupon-form";
        }
        try {
            // Service metoda dostane coupon objekt s flagy nastavenými Springem
            Coupon updatedCoupon = couponService.updateCoupon(id, couponData);
            redirectAttributes.addFlashAttribute("successMessage", "Kupón '" + updatedCoupon.getCode() + "' byl úspěšně aktualizován.");
            log.info("Coupon ID {} updated successfully.", id);
            return "redirect:/admin/coupons";
        } catch (EntityNotFoundException e) {
            log.warn("Cannot update coupon. Coupon not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/coupons";
        } catch (IllegalArgumentException e) {
            log.warn("Error updating coupon ID {}: {}", id, e.getMessage());
            assignValidationError(e, bindingResult, model);
            model.addAttribute("pageTitle", "Upravit kupón (Chyba)");
            couponData.setId(id); // Zachovat ID
            model.addAttribute("coupon", couponData); // Vrátíme data z formuláře
            return "admin/coupon-form";
        } catch (Exception e) {
            log.error("Unexpected error updating coupon ID {}: {}", id, e.getMessage(), e);
            model.addAttribute("pageTitle", "Upravit kupón (Chyba)");
            couponData.setId(id); // Zachovat ID
            model.addAttribute("coupon", couponData); // Vrátíme data z formuláře
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
        if (StringUtils.hasText(startDateStr)) {
            try {
                coupon.setStartDate(LocalDate.parse(startDateStr).atStartOfDay());
            } catch (DateTimeParseException e) {
                bindingResult.rejectValue("startDate", "typeMismatch.coupon.startDate", "Neplatný formát data 'Platnost od'");
            }
        } else {
            coupon.setStartDate(null); // Povolit prázdné datum
        }
        if (StringUtils.hasText(expirationDateStr)) {
            try {
                coupon.setExpirationDate(LocalDate.parse(expirationDateStr).atTime(23, 59, 59));
            } catch (DateTimeParseException e) {
                bindingResult.rejectValue("expirationDate", "typeMismatch.coupon.expirationDate", "Neplatný formát data 'Platnost do'");
            }
        } else {
            coupon.setExpirationDate(null); // Povolit prázdné datum (neomezeno)
        }
    }

    private void assignValidationError(IllegalArgumentException e, BindingResult bindingResult, Model model) {
        String message = e.getMessage();
        if (message == null) message = "Neznámá chyba validace.";

        if (message.contains("Kód kupónu") && message.contains("již existuje")) {
            bindingResult.rejectValue("code", "error.coupon.duplicate", message);
        } else if (message.contains("Procentuální hodnota")) {
            bindingResult.rejectValue("value", "error.coupon.value", message);
        } else if (message.contains("Pevná částka") && (message.contains("CZK") || message.contains("EUR"))) {
            if (message.contains("CZK")) bindingResult.rejectValue("valueCZK", "error.coupon.value", message);
            if (message.contains("EUR")) bindingResult.rejectValue("valueEUR", "error.coupon.value", message);
        } else if (message.contains("musí mít definovanou platnou slevu")) {
            // Chyba obecné validace kombinace polí
            model.addAttribute("errorMessage", message);
        } else if (message.contains("Platnost od") || message.contains("Platnost do")) {
            if (bindingResult.hasFieldErrors("startDate") || bindingResult.hasFieldErrors("expirationDate")) {
                // Chyba už byla přidána při parsování datumu
            } else {
                // Chyba z validace v service
                bindingResult.rejectValue("startDate", "error.coupon.date", message);
                bindingResult.rejectValue("expirationDate", "error.coupon.date", message);
            }
        } else if (message.contains("limit použití")) {
            if (message.contains("nižší než aktuální"))
                bindingResult.rejectValue("usageLimit", "error.coupon.limit.count", message);
            else if (message.contains("na zákazníka"))
                bindingResult.rejectValue("usageLimitPerCustomer", "error.coupon.limit.customer", message);
            else bindingResult.rejectValue("usageLimit", "error.coupon.limit", message);
        } else if (message.contains("Minimální hodnota")) {
            if (message.contains("CZK"))
                bindingResult.rejectValue("minimumOrderValueCZK", "error.coupon.minvalue", message);
            if (message.contains("EUR"))
                bindingResult.rejectValue("minimumOrderValueEUR", "error.coupon.minvalue", message);
        } else {
            model.addAttribute("errorMessage", message); // Obecná chyba
        }
    }
}