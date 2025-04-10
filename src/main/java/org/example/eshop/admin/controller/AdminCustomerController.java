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
// @Autowired již není potřeba importovat
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/customers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCustomerController {

    private static final Logger log = LoggerFactory.getLogger(AdminCustomerController.class);

    private final CustomerService customerService; // Odebráno @Autowired
    private final OrderService orderService;       // Odebráno @Autowired

    // Konstruktor pro injektáž závislostí
    public AdminCustomerController(CustomerService customerService, OrderService orderService) {
        this.customerService = customerService;
        this.orderService = orderService;
    }

    @ModelAttribute("currentUri")
    public String getCurrentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }



    @GetMapping
    public String listCustomers(Model model,
                                @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
                                @RequestParam Optional<String> email,
                                @RequestParam Optional<String> name,
                                @RequestParam Optional<Boolean> enabled) { // HttpServletRequest už není třeba pro currentUri

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
            model.addAttribute("currentSort", StringUtils.hasText(currentSort) ? currentSort : "id,DESC");

            // currentUri se přidá automaticky přes @ModelAttribute

        } catch (Exception e) {
            log.error("Error fetching customers for admin view: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Nepodařilo se načíst zákazníky.");
            model.addAttribute("customerPage", Page.empty(pageable));
            // currentUri se přidá automaticky
        }
        return "admin/customers-list";
    }



    @PostMapping("/{id}/update-basic")
    public String updateCustomerBasicInfo(@PathVariable Long id,
                                          @Validated @ModelAttribute("profileUpdateDto") ProfileUpdateDto profileUpdateDto,
                                          BindingResult bindingResult, // <- BindingResult for profileUpdateDto
                                          RedirectAttributes redirectAttributes,
                                          Model model) {

        log.info("Attempting to update basic info for customer ID: {}", id);
        if (bindingResult.hasErrors()) { // <--- ERROR PATH for profileUpdateDto validation
            log.warn("Validation errors updating basic info for customer {}: {}", id, bindingResult.getAllErrors());
            try {
                Customer customer = customerService.getCustomerById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Zákazník s ID " + id + " nenalezen při zpracování chyby formuláře."));

                model.addAttribute("customer", customer);
                // profileUpdateDto is automatically added back by Spring

                // *** FIX: Add the Address DTOs back to the model ***
                model.addAttribute("invoiceAddressDto", mapCustomerToAddressDto(customer, CustomerService.AddressType.INVOICE));
                model.addAttribute("deliveryAddressDto", mapCustomerToAddressDto(customer, CustomerService.AddressType.DELIVERY));
                // *****************************************************

                model.addAttribute("errorMessage", "Formulář základních údajů obsahuje chyby."); // General error message

            } catch (EntityNotFoundException e) {
                redirectAttributes.addFlashAttribute("errorMessage", "Zákazník nenalezen.");
                return "redirect:/admin/customers";
            }
            // Return the view name, the model is now fully populated
            return "admin/customer-detail";
        }

        // --- Success path (Remains the same) ---
        try {
            Customer customer = customerService.getCustomerById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Zákazník s ID " + id + " nenalezen."));

            customer.setFirstName(profileUpdateDto.getFirstName());
            customer.setLastName(profileUpdateDto.getLastName());
            customer.setPhone(profileUpdateDto.getPhone());
            // ... (rest of the update logic) ...

            customerService.saveCustomer(customer);

            redirectAttributes.addFlashAttribute("successMessage", "Základní údaje zákazníka byly aktualizovány.");
            log.info("Basic info updated successfully for customer ID: {}", id);
        } catch (EntityNotFoundException e) {
            log.warn("Cannot update basic info. Customer not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/customers";
        } catch (Exception e) {
            log.error("Error updating basic info for customer ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při aktualizaci údajů nastala neočekávaná chyba: " + e.getMessage());
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
    // --- Nové metody pro správu adres ---

    /**
     * Zpracuje formulář pro aktualizaci fakturační adresy zákazníka.
     */
    @PostMapping("/{id}/update-invoice-address")
    public String updateInvoiceAddress(@PathVariable Long id,
                                       @Valid @ModelAttribute("invoiceAddressDto") AddressDto addressDto, // Použijeme nové DTO pro formulář
                                       BindingResult bindingResult,
                                       RedirectAttributes redirectAttributes,
                                       Model model) {
        log.info("Attempting to update INVOICE address for customer ID: {}", id);
        return updateAddressInternal(id, addressDto, bindingResult, redirectAttributes, model, CustomerService.AddressType.INVOICE);
    }

    /**
     * Zpracuje formulář pro aktualizaci dodací adresy zákazníka.
     */
    @PostMapping("/{id}/update-delivery-address")
    public String updateDeliveryAddress(@PathVariable Long id,
                                        @Valid @ModelAttribute("deliveryAddressDto") AddressDto addressDto, // Použijeme nové DTO pro formulář
                                        BindingResult bindingResult,
                                        RedirectAttributes redirectAttributes,
                                        Model model) {
        log.info("Attempting to update DELIVERY address for customer ID: {}", id);
        return updateAddressInternal(id, addressDto, bindingResult, redirectAttributes, model, CustomerService.AddressType.DELIVERY);
    }

    /**
     * Interní metoda pro zpracování aktualizace adresy (fakturační nebo dodací).
     */
    private String updateAddressInternal(Long customerId, AddressDto addressDto, BindingResult bindingResult,
                                         RedirectAttributes redirectAttributes, Model model, CustomerService.AddressType addressType) {

        String addressTypeName = (addressType == CustomerService.AddressType.INVOICE ? "fakturační" : "dodací");
        String dtoName = (addressType == CustomerService.AddressType.INVOICE ? "invoiceAddressDto" : "deliveryAddressDto");
        String errorAttrName = (addressType == CustomerService.AddressType.INVOICE ? "invoiceAddressError" : "deliveryAddressError");

        // Validace: Firma nebo Jméno+Příjmení
        if (!addressDto.hasRecipient()) {
            bindingResult.rejectValue("companyName", "recipient.required", "Musí být vyplněn název firmy nebo jméno a příjmení.");
            // Můžeme chybu přidat i k firstName pro zobrazení
            // bindingResult.rejectValue("firstName", "recipient.required", "Musí být vyplněn název firmy nebo jméno a příjmení.");
        }

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors updating {} address for customer {}: {}", addressTypeName, customerId, bindingResult.getAllErrors());
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult." + dtoName, bindingResult);
            redirectAttributes.addFlashAttribute(dtoName, addressDto); // Vrátíme vyplněná data zpět
            redirectAttributes.addFlashAttribute(errorAttrName, "Formulář pro " + addressTypeName + " adresu obsahuje chyby.");
            return "redirect:/admin/customers/" + customerId; // Přesměrování zpět na detail s chybami
        }

        try {
            customerService.updateAddress(customerId, addressType, addressDto);
            redirectAttributes.addFlashAttribute("successMessage", addressTypeName.substring(0, 1).toUpperCase() + addressTypeName.substring(1) + " adresa byla úspěšně aktualizována.");
            log.info("{} address updated successfully for customer ID: {}", addressTypeName.substring(0, 1).toUpperCase() + addressTypeName.substring(1), customerId);
        } catch (EntityNotFoundException e) {
            log.warn("Cannot update {} address. Customer not found: ID={}", addressTypeName, customerId, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/customers";
        } catch (Exception e) {
            log.error("Error updating {} address for customer ID {}: {}", addressTypeName, customerId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při aktualizaci " + addressTypeName + " adresy nastala neočekávaná chyba: " + e.getMessage());
        }
        return "redirect:/admin/customers/" + customerId;
    }


    /**
     * Přepne příznak 'useInvoiceAddressAsDelivery' pro zákazníka.
     */
    @PostMapping("/{id}/toggle-delivery-address")
    public String toggleDeliveryAddressUsage(@PathVariable Long id,
                                             // Pokud checkbox není zaškrtnutý, hodnota 'true' nepřijde.
                                             // defaultValue="false" zajistí, že 'useInvoiceAddress' bude false, když parametr chybí.
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
        return "redirect:/admin/customers/" + id; // Vždy přesměruj na detail
    }

    // --- Pomocná metoda pro naplnění DTO z Customer entity (pro GET request) ---
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
            // Telefon pro fakturační DTO můžeme brát hlavní
            dto.setPhone(customer.getPhone());
        } else { // DELIVERY
            dto.setCompanyName(customer.getDeliveryCompanyName());
            dto.setFirstName(customer.getDeliveryFirstName());
            dto.setLastName(customer.getDeliveryLastName());
            dto.setStreet(customer.getDeliveryStreet());
            dto.setCity(customer.getDeliveryCity());
            dto.setZipCode(customer.getDeliveryZipCode());
            dto.setCountry(customer.getDeliveryCountry());
            dto.setPhone(customer.getDeliveryPhone());
            // Fallback na hlavní telefon, pokud dodací není vyplněn
            if (!StringUtils.hasText(dto.getPhone())) {
                dto.setPhone(customer.getPhone());
            }
        }
        return dto;
    }

    // Upravíme metodu viewCustomerDetail, aby do modelu přidávala i AddressDto
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public String viewCustomerDetail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Requesting admin customer detail view for ID: {}", id);
        try {
            Customer customer = customerService.getCustomerById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Zákazník s ID " + id + " nenalezen."));

            model.addAttribute("customer", customer);

            // Přidání DTO pro formulář základních údajů (pokud není z redirectu)
            if (!model.containsAttribute("profileUpdateDto")) {
                ProfileUpdateDto profileDto = new ProfileUpdateDto();
                profileDto.setFirstName(customer.getFirstName());
                profileDto.setLastName(customer.getLastName());
                profileDto.setPhone(customer.getPhone());
                model.addAttribute("profileUpdateDto", profileDto);
            }

            // --- PŘIDÁNO: Naplnění DTO pro adresní formuláře (pokud nejsou z redirectu) ---
            if (!model.containsAttribute("invoiceAddressDto")) {
                model.addAttribute("invoiceAddressDto", mapCustomerToAddressDto(customer, CustomerService.AddressType.INVOICE));
            }
            if (!model.containsAttribute("deliveryAddressDto")) {
                model.addAttribute("deliveryAddressDto", mapCustomerToAddressDto(customer, CustomerService.AddressType.DELIVERY));
            }
            // --------------------------------------------------------------------------

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

}