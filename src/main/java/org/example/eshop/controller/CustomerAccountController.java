package org.example.eshop.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.example.eshop.dto.AddressDto;
import org.example.eshop.dto.ChangePasswordDto;
import org.example.eshop.dto.ProfileUpdateDto;
import org.example.eshop.model.Conversation;
import org.example.eshop.model.Customer;
import org.example.eshop.model.Message;
import org.example.eshop.model.Order;
import org.example.eshop.repository.ConversationRepository;
import org.example.eshop.service.ConversationService;
import org.example.eshop.service.CustomerService;
import org.example.eshop.service.OrderService;
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

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/muj-ucet")
@PreAuthorize("isAuthenticated()")
public class CustomerAccountController {

    private static final Logger log = LoggerFactory.getLogger(CustomerAccountController.class);

    private final CustomerService customerService;
    private final OrderService orderService;
    @Autowired
    private ConversationService conversationService;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    public CustomerAccountController(CustomerService customerService, OrderService orderService) {
        this.customerService = customerService;
        this.orderService = orderService;
    }

    private Customer getCurrentCustomer(Principal principal) {
        if (principal == null) {
            throw new IllegalStateException("Uživatel není přihlášen.");
        }
        String userEmail = principal.getName();
        return customerService.getCustomerByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Profil přihlášeného uživatele nebyl nalezen."));
    }

    // --- Profil Zákazníka (Beze změny) ---
    @GetMapping("/profil")
    public String viewProfile(Model model, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            Customer customer = getCurrentCustomer(principal);
            ProfileUpdateDto profileDto = new ProfileUpdateDto();
            profileDto.setFirstName(customer.getFirstName());
            profileDto.setLastName(customer.getLastName());
            profileDto.setPhone(customer.getPhone());
            model.addAttribute("profile", profileDto);
            model.addAttribute("customerEmail", customer.getEmail());
        } catch (IllegalStateException e) {
            log.error("Chyba při zobrazování profilu: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("profileError", e.getMessage());
            return "redirect:/prihlaseni";
        }
        return "muj-ucet/profil";
    }

    @PostMapping("/profil")
    public String updateProfile(@Valid @ModelAttribute("profile") ProfileUpdateDto profileUpdateDto,
                                BindingResult bindingResult, Principal principal, RedirectAttributes redirectAttributes, Model model) {
        if (bindingResult.hasErrors()) {
            log.warn("Validation errors while updating profile for {}", principal.getName());
            model.addAttribute("customerEmail", principal.getName());
            return "muj-ucet/profil";
        }
        try {
            customerService.updateProfile(principal.getName(), profileUpdateDto);
            redirectAttributes.addFlashAttribute("profileSuccess", "Profil byl úspěšně aktualizován.");
        } catch (Exception e) {
            log.error("Chyba při aktualizaci profilu pro {}: {}", principal.getName(), e.getMessage(), e);
            redirectAttributes.addFlashAttribute("profileError", "Aktualizace profilu selhala: " + e.getMessage());
        }
        return "redirect:/muj-ucet/profil";
    }

    // --- Změna Hesla (Beze změny) ---
    @GetMapping("/zmena-hesla")
    public String showChangePasswordForm(Model model) {
        model.addAttribute("passwordChange", new ChangePasswordDto());
        return "muj-ucet/zmena-hesla";
    }

    @PostMapping("/zmena-hesla")
    public String processChangePassword(@Valid @ModelAttribute("passwordChange") ChangePasswordDto passwordDto,
                                        BindingResult bindingResult, Principal principal, RedirectAttributes redirectAttributes, Model model) {
        if (passwordDto.getNewPassword() != null && !passwordDto.getNewPassword().equals(passwordDto.getConfirmNewPassword())) {
            bindingResult.rejectValue("confirmNewPassword", "error.passwordChange", "Nové heslo a potvrzení se neshodují.");
        }
        if (bindingResult.hasErrors()) {
            return "muj-ucet/zmena-hesla";
        }
        try {
            Customer customer = getCurrentCustomer(principal);
            customerService.changePassword(customer.getId(), passwordDto);
            redirectAttributes.addFlashAttribute("passwordSuccess", "Heslo bylo úspěšně změněno.");
            return "redirect:/muj-ucet/profil";
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("currentPassword", "error.passwordChange", e.getMessage());
            return "muj-ucet/zmena-hesla";
        } catch (Exception e) {
            log.error("Chyba při změně hesla pro {}: {}", principal.getName(), e.getMessage(), e);
            model.addAttribute("passwordError", "Při změně hesla nastala neočekávaná chyba.");
            return "muj-ucet/zmena-hesla";
        }
    }

    // --- Objednávky (Beze změny) ---
    @GetMapping("/objednavky")
    public String viewOrders(Model model, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            Customer customer = getCurrentCustomer(principal);
            List<Order> orders = orderService.findAllOrdersByCustomerId(customer.getId());
            model.addAttribute("orders", orders);
        } catch (Exception e) {
            log.error("Chyba při načítání objednávek pro {}: {}", principal.getName(), e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při načítání objednávek nastala chyba.");
            return "redirect:/";
        }
        return "muj-ucet/objednavky";
    }

    @GetMapping("/objednavky/{orderCode}")
    @Transactional() // Ponecháme, pokud OrderService potřebuje
    public String viewOrderDetail(@PathVariable String orderCode,
                                  Model model,
                                  Principal principal,
                                  RedirectAttributes redirectAttributes) {
        Customer loggedInCustomer = null;
        try {
            // ... (kód pro získání zákazníka a základní kontroly) ...
            if (principal == null) {
                throw new IllegalStateException("Uživatel není přihlášen.");
            }
            String userEmail = principal.getName();
            loggedInCustomer = customerService.getCustomerByEmail(userEmail)
                    .orElseThrow(() -> new IllegalStateException("Profil přihlášeného uživatele nebyl nalezen. Email: " + userEmail));
            log.debug("Customer {} (ID: {}) viewing order detail for CODE: {}", loggedInCustomer.getEmail(), loggedInCustomer.getId(), orderCode);

            // Voláme optimalizovanou metodu service, která používá findFullDetailByOrderCode
            Order order = orderService.findOrderByCode(orderCode)
                    .orElseThrow(() -> new EntityNotFoundException("Objednávka s kódem '" + orderCode + "' nenalezena."));

            if (order.getCustomer() == null || !order.getCustomer().getId().equals(loggedInCustomer.getId())) {
                throw new SecurityException("K této objednávce nemáte přístup.");
            }
            List<Message> externalMessages = conversationRepository
                    .findByOrderIdAndType(order.getId(), Conversation.ConversationType.EXTERNAL)
                    .map(conversation -> conversationRepository.findByIdWithMessages(conversation.getId())) // Načteme i zprávy
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(Conversation::getMessages)
                    .orElse(new ArrayList<>()); // Vrátí prázdný seznam, pokud konverzace nebo zprávy neexistují

            model.addAttribute("externalMessages", externalMessages);

            model.addAttribute("order", order); // Data jsou již načtena
            log.info("Order detail for CODE {} loaded successfully for customer {}", orderCode, loggedInCustomer.getEmail());
            return "muj-ucet/objednavka-detail";

        } catch (EntityNotFoundException | SecurityException | IllegalStateException e) {
            log.warn("Chyba přístupu nebo nenalezení objednávky CODE: {} pro uživatele {}: {}",
                    orderCode, (principal != null ? principal.getName() : "null"), e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/muj-ucet/objednavky";
        } catch (Exception e) {
            log.error("Neočekávaná chyba při zobrazení detailu objednávky CODE: {} pro {}: {}",
                    orderCode, (principal != null ? principal.getName() : "null"), e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při zobrazení detailu objednávky nastala neočekávaná chyba.");
            return "redirect:/muj-ucet/objednavky";
        }
    }
    // Endpoint pro odeslání zprávy zákazníkem
    @PostMapping("/objednavky/{orderCode}/poslat-zpravu")
    public String sendCustomerMessage(@PathVariable String orderCode,
                                      @RequestParam String content,
                                      Principal principal,
                                      RedirectAttributes redirectAttributes) {

        if (!StringUtils.hasText(content)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Obsah zprávy nesmí být prázdný.");
            return "redirect:/muj-ucet/objednavky/" + orderCode;
        }

        try {
            // 1. Získat přihlášeného zákazníka
            Customer customer = getCurrentCustomer(principal); // Použij existující metodu

            // 2. Najít objednávku a ověřit vlastnictví
            Order order = orderService.findOrderByCode(orderCode)
                    .orElseThrow(() -> new EntityNotFoundException("Objednávka nenalezena."));
            if (!order.getCustomer().getId().equals(customer.getId())) {
                throw new SecurityException("Nemáte oprávnění k této objednávce.");
            }

            // 3. Získat nebo vytvořit externí konverzaci
            Conversation externalConversation = conversationService.getOrCreateConversation(order.getId(), Conversation.ConversationType.EXTERNAL);

            // 4. Přidat zprávu
            conversationService.addMessage(
                    externalConversation.getId(),
                    content,
                    Message.SenderType.CUSTOMER,
                    customer.getId(),
                    customer.getFirstName() + " " + customer.getLastName() // Jméno zákazníka
            );

            redirectAttributes.addFlashAttribute("successMessage", "Vaše zpráva byla odeslána.");
            log.info("Customer {} sent message for order {}", customer.getEmail(), orderCode);

        } catch (EntityNotFoundException | SecurityException | IllegalStateException e) {
            log.warn("Error sending customer message for order {}: {}", orderCode, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Chyba: " + e.getMessage());
            return "redirect:/muj-ucet/objednavky"; // Zpět na přehled při chybě
        } catch (Exception e) {
            log.error("Unexpected error sending customer message for order {}: {}", orderCode, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při odesílání zprávy nastala neočekávaná chyba.");
        }

        return "redirect:/muj-ucet/objednavky/" + orderCode; // Zpět na detail objednávky
    }
    @GetMapping("/adresy")
    public String viewAddresses(Model model, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            setupAddressModel(model, principal);
        } catch (Exception e) {
            log.error("Chyba při načítání adres pro {}: {}", principal.getName(), e.getMessage(), e);
            redirectAttributes.addFlashAttribute("addressError", "Při načítání adres nastala neočekávaná chyba.");
            return "redirect:/";
        }
        return "muj-ucet/adresy";
    }

    @PostMapping("/adresy/fakturacni")
    public String updateInvoiceAddress(@Valid @ModelAttribute("invoiceAddress") AddressDto addressDto,
                                       BindingResult bindingResult, Principal principal, RedirectAttributes redirectAttributes, Model model) {
        return updateAddressInternal(addressDto, bindingResult, principal, redirectAttributes, model, CustomerService.AddressType.INVOICE);
    }

    @PostMapping("/adresy/dodaci")
    public String updateDeliveryAddress(@Valid @ModelAttribute("deliveryAddress") AddressDto addressDto,
                                        BindingResult bindingResult, Principal principal, RedirectAttributes redirectAttributes, Model model) {
        if (!bindingResult.hasFieldErrors("companyName") &&
                !org.springframework.util.StringUtils.hasText(addressDto.getCompanyName())) {
            if (!org.springframework.util.StringUtils.hasText(addressDto.getFirstName())) {
                bindingResult.rejectValue("firstName", "error.deliveryAddress", "Jméno nebo firma musí být vyplněno.");
            }
            if (!org.springframework.util.StringUtils.hasText(addressDto.getLastName())) {
                bindingResult.rejectValue("lastName", "error.deliveryAddress", "Příjmení nebo firma musí být vyplněno.");
            }
        }
        return updateAddressInternal(addressDto, bindingResult, principal, redirectAttributes, model, CustomerService.AddressType.DELIVERY);
    }

    private String updateAddressInternal(AddressDto addressDto, BindingResult bindingResult, Principal principal,
                                         RedirectAttributes redirectAttributes, Model model, CustomerService.AddressType addressType) {
        String addressTypeName = (addressType == CustomerService.AddressType.INVOICE ? "fakturační" : "dodací");
        String errorModelAttributeName = (addressType == CustomerService.AddressType.INVOICE ? "invoiceAddressError" : "deliveryAddressError");
        if (!bindingResult.hasFieldErrors("companyName") && !org.springframework.util.StringUtils.hasText(addressDto.getCompanyName()) &&
                (!org.springframework.util.StringUtils.hasText(addressDto.getFirstName()) || !org.springframework.util.StringUtils.hasText(addressDto.getLastName()))) {
            bindingResult.rejectValue("firstName", "error." + addressTypeName + "Address", "Musí být vyplněn název firmy nebo jméno a příjmení.");
        }
        if (bindingResult.hasErrors()) {
            log.warn("Validation errors while updating {} address for {}", addressTypeName, principal.getName());
            model.addAttribute(errorModelAttributeName, "Formulář pro " + addressTypeName + " adresu obsahuje chyby.");
            setupAddressModel(model, principal);
            return "muj-ucet/adresy";
        }
        try {
            Customer customer = getCurrentCustomer(principal);
            customerService.updateAddress(customer.getId(), addressType, addressDto);
            redirectAttributes.addFlashAttribute("addressSuccess", Character.toUpperCase(addressTypeName.charAt(0)) + addressTypeName.substring(1) + " adresa byla úspěšně aktualizována.");
        } catch (Exception e) {
            log.error("Chyba při aktualizaci {} adresy pro {}: {}", addressTypeName, principal.getName(), e.getMessage(), e);
            redirectAttributes.addFlashAttribute(errorModelAttributeName, "Aktualizace " + addressTypeName + " adresy selhala: " + e.getMessage());
        }
        return "redirect:/muj-ucet/adresy";
    }

    // *** UPRAVENÁ METODA ZDE ***
    @PostMapping("/adresy/prepnout-dodaci")
    public String setUseInvoiceAddressAsDelivery(
            // Parametr 'useInvoiceAddress' již není povinný (required = false)
            // Pokud nepřijde (checkbox je odškrtnutý), použije se výchozí hodnota "false"
            @RequestParam(name = "useInvoiceAddress", required = false, defaultValue = "false") boolean useInvoiceAddress,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            Customer customer = getCurrentCustomer(principal);
            // Service metoda přijímá boolean a uloží ho
            customerService.setUseInvoiceAddressAsDelivery(customer.getId(), useInvoiceAddress);
            log.info("Set useInvoiceAddressAsDelivery to {} for customer {}", useInvoiceAddress, principal.getName()); // Logování
            redirectAttributes.addFlashAttribute("addressSuccess", "Nastavení dodací adresy bylo aktualizováno.");
        } catch (Exception e) {
            log.error("Chyba při nastavování useInvoiceAddressAsDelivery pro {}: {}", principal.getName(), e.getMessage(), e);
            redirectAttributes.addFlashAttribute("addressError", "Nastavení adresy selhalo: " + e.getMessage());
        }
        return "redirect:/muj-ucet/adresy";
    }

    // --- Pomocné metody (Beze změny) ---
    private void setupAddressModel(Model model, Principal principal) {
        Customer customer = getCurrentCustomer(principal);
        model.addAttribute("customer", customer);
        if (!model.containsAttribute("invoiceAddress")) {
            AddressDto invoiceAddressDto = new AddressDto();
            mapCustomerToAddressDto(customer, invoiceAddressDto, CustomerService.AddressType.INVOICE);
            model.addAttribute("invoiceAddress", invoiceAddressDto);
        }
        if (!model.containsAttribute("deliveryAddress")) {
            AddressDto deliveryAddressDto = new AddressDto();
            mapCustomerToAddressDto(customer, deliveryAddressDto, CustomerService.AddressType.DELIVERY);
            model.addAttribute("deliveryAddress", deliveryAddressDto);
        }
    }

    private void mapCustomerToAddressDto(Customer customer, AddressDto dto, CustomerService.AddressType type) {
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
            if (!org.springframework.util.StringUtils.hasText(dto.getPhone())) {
                dto.setPhone(customer.getPhone());
            }
        }
    }
}