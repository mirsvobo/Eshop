package org.example.eshop.admin.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.eshop.dto.AddressDto;
import org.example.eshop.dto.ProfileUpdateDto;
import org.example.eshop.model.Customer;
import org.example.eshop.service.CustomerService;
import org.example.eshop.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/customers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCustomerController {

    private static final Logger log = LoggerFactory.getLogger(AdminCustomerController.class);

    private final CustomerService customerService;

    // Konstruktor pro injektáž závislostí
    public AdminCustomerController(CustomerService customerService, OrderService orderService) {
        this.customerService = customerService;
        // Může se hodit pro budoucí rozšíření
    }

    @ModelAttribute("currentUri")
    public String getCurrentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }

    // Helper method to populate model for detail view (also used in error paths)
    private void populateDetailModel(Long customerId, Model model) {
        Customer customer = customerService.getCustomerById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Zákazník s ID " + customerId + " nenalezen při přípravě modelu."));

        model.addAttribute("customer", customer);

        // Ensure DTOs are added even if not already present (e.g., initial load or error path)
        if (!model.containsAttribute("profileUpdateDto")) {
            ProfileUpdateDto profileDto = new ProfileUpdateDto();
            profileDto.setFirstName(customer.getFirstName());
            profileDto.setLastName(customer.getLastName());
            profileDto.setPhone(customer.getPhone());
            model.addAttribute("profileUpdateDto", profileDto);
        }
        if (!model.containsAttribute("invoiceAddressDto")) {
            model.addAttribute("invoiceAddressDto", mapCustomerToAddressDto(customer, CustomerService.AddressType.INVOICE));
        }
        if (!model.containsAttribute("deliveryAddressDto")) {
            model.addAttribute("deliveryAddressDto", mapCustomerToAddressDto(customer, CustomerService.AddressType.DELIVERY));
        }
    }


    @GetMapping
    public String listCustomers(Model model,
                                @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
                                @RequestParam Optional<String> email,
                                @RequestParam Optional<String> name,
                                @RequestParam Optional<Boolean> enabled) {

        String emailFilter = email.filter(StringUtils::hasText).orElse(null);
        String nameFilter = name.filter(StringUtils::hasText).orElse(null);
        Boolean enabledFilter = enabled.orElse(null);

        log.info("Requesting admin customer list view. Filters: email={}, name={}, enabled={}. Pageable: {}",
                emailFilter, nameFilter, enabledFilter, pageable);

        try {
            Page<Customer> customerPage = customerService.findCustomers(pageable, emailFilter, nameFilter, enabledFilter);

            model.addAttribute("customerPage", customerPage);
            model.addAttribute("emailFilter", emailFilter);
            model.addAttribute("nameFilter", nameFilter);
            model.addAttribute("enabledFilter", enabledFilter);

            String currentSort = pageable.getSort().stream()
                    .map(order -> order.getProperty() + "," + order.getDirection())
                    .collect(Collectors.joining("&sort="));
            // --- FIX: Handle potential empty or invalid sort string ---
            if (!StringUtils.hasText(currentSort) || ",".equals(currentSort)) {
                currentSort = pageable.getSortOr(Sort.by(Sort.Direction.DESC, "id")).stream()
                        .map(order -> order.getProperty() + "," + order.getDirection())
                        .collect(Collectors.joining("&sort="));
                if (!StringUtils.hasText(currentSort) || ",".equals(currentSort)) {
                    currentSort = "id,DESC"; // Default fallback
                }
            }
            // --- END FIX ---
            model.addAttribute("currentSort", currentSort);

        } catch (Exception e) {
            log.error("Error fetching customers for admin view: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Nepodařilo se načíst zákazníky.");
            model.addAttribute("customerPage", Page.empty(pageable));
            // Add filters back even on error
            model.addAttribute("emailFilter", emailFilter);
            model.addAttribute("nameFilter", nameFilter);
            model.addAttribute("enabledFilter", enabledFilter);
            model.addAttribute("currentSort", "id,DESC"); // Default sort on error
        }
        return "admin/customers-list";
    }

    // Upravená metoda pro zobrazení detailu
    @GetMapping("/{id}")
    @Transactional(readOnly = true) // Přidáno pro konzistenci a případné lazy loading
    public String viewCustomerDetail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Requesting admin customer detail view for ID: {}", id);
        try {
            // Použijeme pomocnou metodu pro naplnění modelu
            populateDetailModel(id, model);
        } catch (EntityNotFoundException e) {
            log.warn("Customer with ID {} not found.", id);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/customers";
        } catch (Exception e) {
            log.error("Error fetching customer detail for ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Nepodařilo se načíst detail zákazníka.");
            return "redirect:/admin/customers";
        }
        return "admin/customer-detail";
    }

    // Upravená metoda pro aktualizaci základních údajů
    @PostMapping("/{id}/update-basic")
    public String updateCustomerBasicInfo(@PathVariable Long id,
                                          @Valid @ModelAttribute("profileUpdateDto") ProfileUpdateDto profileUpdateDto,
                                          BindingResult bindingResult, // BindingResult pro profileUpdateDto
                                          RedirectAttributes redirectAttributes,
                                          Model model) { // Přidán Model

        log.info("Attempting to update basic info for customer ID: {}", id);

        // 1. Validace DTO
        if (bindingResult.hasErrors()) {
            log.warn("Validation errors updating basic info for customer {}: {}", id, bindingResult.getAllErrors());
            try {
                // Znovu naplnit model pro zobrazení formuláře s chybami
                populateDetailModel(id, model);
                model.addAttribute("errorMessage", "Formulář základních údajů obsahuje chyby."); // Přidat obecnou chybovou zprávu
            } catch (EntityNotFoundException e) {
                // Pokud zákazník mezitím zmizel, přesměrujeme
                redirectAttributes.addFlashAttribute("errorMessage", "Zákazník nenalezen.");
                return "redirect:/admin/customers";
            }
            return "admin/customer-detail"; // Vrátit šablonu detailu s chybami
        }

        // 2. Úspěšná cesta - aktualizace
        try {
            // V CustomerService se načte Customer podle ID a aktualizují se pole
            // Není třeba načítat zde znovu, pokud saveCustomer() dělá findById
            Customer customer = customerService.getCustomerById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Zákazník s ID " + id + " nenalezen."));

            customer.setFirstName(profileUpdateDto.getFirstName());
            customer.setLastName(profileUpdateDto.getLastName());
            customer.setPhone(profileUpdateDto.getPhone());
            // Automatická synchronizace adres by měla proběhnout v @PreUpdate v Customer entitě

            customerService.saveCustomer(customer); // Uložíme aktualizovaného zákazníka

            redirectAttributes.addFlashAttribute("successMessage", "Základní údaje zákazníka byly aktualizovány.");
            log.info("Basic info updated successfully for customer ID: {}", id);
            return "redirect:/admin/customers/" + id; // Přesměrování na detail

        } catch (EntityNotFoundException e) {
            log.warn("Cannot update basic info. Customer not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/customers"; // Přesměrování na seznam, pokud zákazník neexistuje
        } catch (Exception e) {
            log.error("Error updating basic info for customer ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při aktualizaci údajů nastala neočekávaná chyba: " + e.getMessage());
            // Při neočekávané chybě také vrátit na detail s chybou (může být např. DB chyba)
            try {
                populateDetailModel(id, model);
                model.addAttribute("errorMessage", "Při aktualizaci údajů nastala neočekávaná chyba: " + e.getMessage());
            } catch (EntityNotFoundException enf) {
                return "redirect:/admin/customers";
            }
            return "admin/customer-detail";
        }
    }


    // Upravená interní metoda pro zpracování aktualizace adres
    private String updateAddressInternal(Long customerId, AddressDto addressDto, BindingResult bindingResult,
                                         RedirectAttributes redirectAttributes, Model model, CustomerService.AddressType addressType) {

        String addressTypeName = (addressType == CustomerService.AddressType.INVOICE ? "fakturační" : "dodací");
        String dtoName = (addressType == CustomerService.AddressType.INVOICE ? "invoiceAddressDto" : "deliveryAddressDto");

        // Validace: Firma nebo Jméno+Příjmení
        if (!addressDto.hasRecipient()) {
            bindingResult.rejectValue("companyName", "recipient.required", "Musí být vyplněn název firmy nebo jméno a příjmení.");
        }

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors updating {} address for customer {}: {}", addressTypeName, customerId, bindingResult.getAllErrors());
            try {
                // Znovu naplnit model pro zobrazení formuláře s chybami
                populateDetailModel(customerId, model);
                // Přidáme DTO s chybami zpět do modelu, aby se zobrazily chyby ve správném formuláři
                model.addAttribute(dtoName, addressDto);
                model.addAttribute("errorMessage", "Formulář pro " + addressTypeName + " adresu obsahuje chyby.");
            } catch (EntityNotFoundException e) {
                redirectAttributes.addFlashAttribute("errorMessage", "Zákazník nenalezen.");
                return "redirect:/admin/customers";
            }
            return "admin/customer-detail"; // Vrátit šablonu detailu s chybami
        }

        // Úspěšná cesta
        try {
            customerService.updateAddress(customerId, addressType, addressDto);
            redirectAttributes.addFlashAttribute("successMessage", addressTypeName.substring(0, 1).toUpperCase() + addressTypeName.substring(1) + " adresa byla úspěšně aktualizována.");
            log.info("{} address updated successfully for customer ID: {}", addressTypeName.substring(0, 1).toUpperCase() + addressTypeName.substring(1), customerId);
            return "redirect:/admin/customers/" + customerId; // Přesměrování na detail

        } catch (EntityNotFoundException e) {
            log.warn("Cannot update {} address. Customer not found: ID={}", addressTypeName, customerId, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/customers"; // Přesměrování na seznam
        } catch (Exception e) {
            log.error("Error updating {} address for customer ID {}: {}", addressTypeName, customerId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při aktualizaci " + addressTypeName + " adresy nastala neočekávaná chyba: " + e.getMessage());
            // Zde je sporné, zda přesměrovat nebo vrátit formulář. Vrátíme formulář.
            try {
                populateDetailModel(customerId, model);
                model.addAttribute(dtoName, addressDto); // Vracíme původní data z formuláře
                model.addAttribute("errorMessage", "Při aktualizaci " + addressTypeName + " adresy nastala neočekávaná chyba: " + e.getMessage());
            } catch (EntityNotFoundException enf) {
                return "redirect:/admin/customers";
            }
            return "admin/customer-detail";
        }
    }

    // POST metody pro /update-invoice-address a /update-delivery-address zůstávají, jen volají upravenou updateAddressInternal

    @PostMapping("/{id}/update-invoice-address")
    public String updateInvoiceAddress(@PathVariable Long id,
                                       @Valid @ModelAttribute("invoiceAddressDto") AddressDto addressDto,
                                       BindingResult bindingResult,
                                       RedirectAttributes redirectAttributes,
                                       Model model) {
        log.info("Handling POST for INVOICE address update, customer ID: {}", id);
        return updateAddressInternal(id, addressDto, bindingResult, redirectAttributes, model, CustomerService.AddressType.INVOICE);
    }

    @PostMapping("/{id}/update-delivery-address")
    public String updateDeliveryAddress(@PathVariable Long id,
                                        @Valid @ModelAttribute("deliveryAddressDto") AddressDto addressDto,
                                        BindingResult bindingResult,
                                        RedirectAttributes redirectAttributes,
                                        Model model) {
        log.info("Handling POST for DELIVERY address update, customer ID: {}", id);
        return updateAddressInternal(id, addressDto, bindingResult, redirectAttributes, model, CustomerService.AddressType.DELIVERY);
    }

    // Metody toggleDeliveryAddressUsage a toggleCustomerEnabled zůstávají stejné

    @PostMapping("/{id}/toggle-delivery-address")
    public String toggleDeliveryAddressUsage(@PathVariable Long id,
                                             @RequestParam(name = "useInvoiceAddress", required = false, defaultValue = "false") boolean useInvoiceAddress,
                                             RedirectAttributes redirectAttributes) {
        log.info("Attempting to set useInvoiceAddressAsDelivery to {} for customer ID: {}", useInvoiceAddress, id);
        try {
            customerService.setUseInvoiceAddressAsDelivery(id, useInvoiceAddress);
            redirectAttributes.addFlashAttribute("successMessage", "Nastavení dodací adresy bylo aktualizováno.");
            log.info("Successfully set useInvoiceAddressAsDelivery to {} for customer ID: {}", useInvoiceAddress, id);
        } catch (EntityNotFoundException e) {
            log.warn("Cannot toggle delivery address usage. Customer not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/customers";
        } catch (Exception e) {
            log.error("Error toggling delivery address usage for customer ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při změně nastavení dodací adresy nastala chyba: " + e.getMessage());
        }
        return "redirect:/admin/customers/" + id;
    }

    @PostMapping("/{id}/toggle-enabled")
    public String toggleCustomerEnabled(@PathVariable Long id,
                                        @RequestParam boolean enable,
                                        RedirectAttributes redirectAttributes) {
        log.info("Attempting to {} customer ID: {}", (enable ? "enable" : "disable"), id);
        try {
            Customer customer = customerService.getCustomerById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Zákazník s ID " + id + " nenalezen."));
            customer.setEnabled(enable);
            customerService.saveCustomer(customer);
            redirectAttributes.addFlashAttribute("successMessage", "Stav zákazníka byl " + (enable ? "aktivován" : "deaktivován") + ".");
            log.info("Successfully {}d customer ID: {}", (enable ? "enable" : "disable"), id);
        } catch (EntityNotFoundException e) {
            log.warn("Cannot toggle enabled state. Customer not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/customers";
        } catch (Exception e) {
            log.error("Error toggling enabled state for customer ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při změně stavu zákazníka nastala chyba: " + e.getMessage());
        }
        return "redirect:/admin/customers/" + id;
    }

    // Pomocná metoda pro mapování (zůstává stejná)
    private AddressDto mapCustomerToAddressDto(Customer customer, CustomerService.AddressType type) {
        AddressDto dto = new AddressDto();
        if (type == CustomerService.AddressType.INVOICE) {
            dto.setCompanyName(customer.getInvoiceCompanyName());
            dto.setVatId(customer.getInvoiceVatId());
            dto.setTaxId(customer.getInvoiceTaxId());
            dto.setFirstName(customer.getInvoiceFirstName());
            dto.setLastName(customer.getInvoiceLastName());
            dto.setStreet(customer.getInvoiceStreet());
            dto.setCity(customer.getInvoiceCity());
            dto.setZipCode(customer.getInvoiceZipCode());
            dto.setCountry(customer.getInvoiceCountry());
            dto.setPhone(customer.getPhone()); // Main phone for invoice DTO
        } else { // DELIVERY
            dto.setCompanyName(customer.getDeliveryCompanyName());
            dto.setFirstName(customer.getDeliveryFirstName());
            dto.setLastName(customer.getDeliveryLastName());
            dto.setStreet(customer.getDeliveryStreet());
            dto.setCity(customer.getDeliveryCity());
            dto.setZipCode(customer.getDeliveryZipCode());
            dto.setCountry(customer.getDeliveryCountry());
            dto.setPhone(customer.getDeliveryPhone());
            if (!StringUtils.hasText(dto.getPhone())) {
                dto.setPhone(customer.getPhone());
            } // Fallback to main phone
        }
        return dto;
    }
}