package org.example.eshop.admin.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.example.eshop.model.Order;
import org.example.eshop.model.OrderState;
import org.example.eshop.service.OrderService;
import org.example.eshop.service.OrderStateService;
import org.example.eshop.service.SuperFakturaInvoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderController.class);

    // Použití final a konstruktoru pro injektáž
    private final OrderService orderService;
    private final OrderStateService orderStateService;
    private final SuperFakturaInvoiceService superFakturaInvoiceService;

    @Value("${superfaktura.api.url:https://moje.superfaktura.cz}")
    private String superFakturaBaseUrl;

    // Konstruktor pro dependency injection
    @Autowired
    public AdminOrderController(OrderService orderService, OrderStateService orderStateService, SuperFakturaInvoiceService superFakturaInvoiceService) {
        this.orderService = orderService;
        this.orderStateService = orderStateService;
        this.superFakturaInvoiceService = superFakturaInvoiceService;
    }


    @ModelAttribute("currentUri")
    public String getCurrentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }

    // --- Seznam objednávek (Metoda listOrders zůstává, jak byla upravena dříve) ---
    @GetMapping
    public String listOrders(Model model,
                             @PageableDefault(size = 20, sort = "orderDate", direction = Sort.Direction.DESC) Pageable pageable,
                             @RequestParam Optional<Long> stateId,
                             @RequestParam Optional<String> paymentStatus,
                             @RequestParam Optional<String> customerEmail,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Optional<LocalDate> dateFrom,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Optional<LocalDate> dateTo,
                             HttpServletRequest request /* currentUri bude přidán přes @ModelAttribute */) {

        log.info("Requesting admin order list view. Filters: stateId={}, paymentStatus={}, customerEmail={}, dateFrom={}, dateTo={}. Pageable: {}",
                stateId.orElse(null), paymentStatus.orElse(""), customerEmail.orElse(""), dateFrom.orElse(null), dateTo.orElse(null), pageable);

        try {
            Optional<LocalDateTime> dateTimeFrom = dateFrom.map(d -> d.atStartOfDay());
            Optional<LocalDateTime> dateTimeTo = dateTo.map(d -> d.atTime(LocalTime.MAX));

            Page<Order> orderPage = orderService.findOrders(
                    pageable,
                    customerEmail.filter(StringUtils::hasText),
                    stateId,
                    paymentStatus.filter(StringUtils::hasText),
                    dateTimeFrom,
                    dateTimeTo
            );

            List<OrderState> allOrderStates = orderStateService.getAllOrderStatesSorted();

            log.info("[AdminOrderController.listOrders] Service returned page: Number={}, Elements={}, TotalElements={}",
                    orderPage.getNumber(), orderPage.getNumberOfElements(), orderPage.getTotalElements());

            model.addAttribute("orderPage", orderPage);
            model.addAttribute("allOrderStates", allOrderStates);
            // model.addAttribute("currentUri", request.getRequestURI()); // Již není potřeba, řeší @ModelAttribute

            stateId.ifPresent(id -> model.addAttribute("selectedStateId", id));
            paymentStatus.ifPresent(status -> model.addAttribute("selectedPaymentStatus", status));
            customerEmail.ifPresent(email -> model.addAttribute("customerEmailFilter", email));
            dateFrom.ifPresent(date -> model.addAttribute("selectedDateFrom", date));
            dateTo.ifPresent(date -> model.addAttribute("selectedDateTo", date));

            String currentSort = pageable.getSort().stream()
                    .map(order -> order.getProperty() + "," + order.getDirection())
                    .collect(Collectors.joining("&sort="));

            if (!StringUtils.hasText(currentSort) || ",".equals(currentSort)) {
                currentSort = pageable.getSortOr(Sort.by(Sort.Direction.DESC, "orderDate")).stream()
                        .map(order -> order.getProperty() + "," + order.getDirection())
                        .collect(Collectors.joining("&sort="));
                if (!StringUtils.hasText(currentSort) || ",".equals(currentSort)) {
                    currentSort = "orderDate,DESC";
                }
            }
            model.addAttribute("currentSort", currentSort);
            log.debug("Adding currentSort to model: {}", currentSort);

        } catch (Exception e) {
            log.error("Error fetching orders for admin view: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Nepodařilo se načíst objednávky.");
            model.addAttribute("orderPage", Page.empty(pageable));
            try {
                model.addAttribute("allOrderStates", orderStateService.getAllOrderStatesSorted());
            } catch (Exception serviceEx) {
                log.error("Could not load order states even for error page: {}", serviceEx.getMessage());
                model.addAttribute("allOrderStates", List.of());
            }
            // model.addAttribute("currentUri", request.getRequestURI()); // Již není potřeba
            stateId.ifPresent(id -> model.addAttribute("selectedStateId", id));
            paymentStatus.ifPresent(status -> model.addAttribute("selectedPaymentStatus", status));
            customerEmail.ifPresent(email -> model.addAttribute("customerEmailFilter", email));
            dateFrom.ifPresent(date -> model.addAttribute("selectedDateFrom", date));
            dateTo.ifPresent(date -> model.addAttribute("selectedDateTo", date));
            model.addAttribute("currentSort", "orderDate,DESC");
        }
        return "admin/orders-list";
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true) // Transakce může zůstat, pokud OrderService potřebuje
    public String viewOrderDetail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Requesting admin order detail view for ID: {}", id);
        try {
            // Voláme optimalizovanou metodu service, která používá findFullDetailById
            Order order = orderService.findOrderById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Objednávka s ID " + id + " nenalezena."));

            model.addAttribute("order", order); // Data jsou již načtena
            List<OrderState> allStates = orderStateService.getAllOrderStatesSorted();
            model.addAttribute("allOrderStates", allStates);
            model.addAttribute("superFakturaBaseUrl", this.superFakturaBaseUrl);

            log.info("Order detail loaded successfully for ID: {}", id);

        } catch (EntityNotFoundException e) {
            log.warn("Order with ID {} not found.", id);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/orders";
        } catch (Exception e) {
            log.error("Error fetching order detail for ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Nepodařilo se načíst detail objednávky.");
            return "redirect:/admin/orders";
        }
        return "admin/order-detail";
    }

    @PostMapping("/{id}/update-state")
    public String updateOrderState(@PathVariable Long id,
                                   @RequestParam Long newStateId,
                                   RedirectAttributes redirectAttributes) {
        log.info("Attempting to update state for order ID: {} to state ID: {}", id, newStateId);
        try {
            orderService.updateOrderState(id, newStateId);
            redirectAttributes.addFlashAttribute("successMessage", "Stav objednávky byl úspěšně změněn.");
            log.info("Successfully updated state for order ID: {}", id);
        } catch (EntityNotFoundException e) {
            log.warn("Cannot update state. Order or OrderState not found. OrderID: {}, NewStateID: {}", id, newStateId, e);
            redirectAttributes.addFlashAttribute("errorMessage", "Chyba: " + e.getMessage());
            return "redirect:/admin/orders"; // Chyba -> zpět na seznam
        } catch (Exception e) {
            log.error("Error updating order state for order ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při změně stavu nastala neočekávaná chyba: " + e.getMessage());
            return "redirect:/admin/orders/" + id; // Chyba -> zpět na detail
        }
        return "redirect:/admin/orders/" + id; // Úspěch -> zpět na detail
    }

    /**
     * Manuálně označí zálohu jako zaplacenou.
     */
    @PostMapping("/{id}/mark-deposit-paid")
    public String markDepositAsPaid(@PathVariable Long id,
                                    RedirectAttributes redirectAttributes) {
        log.info("Admin action: Attempting to mark deposit as paid for order ID: {}", id);
        try {
            // Voláme service metodu, která se postará o DB i SF API
            // Použijeme aktuální datum pro platbu
            orderService.markDepositAsPaid(id, LocalDate.now());
            // Service metoda může případně nastavit vlastní flash zprávu, pokud selže SF
            if (!redirectAttributes.containsAttribute("successMessage") && !redirectAttributes.containsAttribute("errorMessage")) {
                redirectAttributes.addFlashAttribute("successMessage", "Záloha byla označena jako zaplacená.");
            }
            log.info("Admin action: Deposit marked as paid for order ID: {}", id);
        } catch (EntityNotFoundException | IllegalStateException | IllegalArgumentException e) {
            log.warn("Admin action failed: Cannot mark deposit paid for order ID {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Chyba: " + e.getMessage());
        } catch (Exception e) {
            log.error("Admin action error: Error marking deposit as paid for order ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při označování zálohy nastala neočekávaná chyba.");
        }
        return "redirect:/admin/orders/" + id;
    }

    /**
     * Manuálně označí objednávku jako plně zaplacenou.
     */
    @PostMapping("/{id}/mark-fully-paid")
    public String markFullyPaid(@PathVariable Long id,
                                RedirectAttributes redirectAttributes) {
        log.info("Admin action: Attempting to mark order as fully paid for order ID: {}", id);
        try {
            // Voláme service metodu, která se postará o DB i SF API
            // Použijeme aktuální datum pro platbu
            orderService.markOrderAsFullyPaid(id, LocalDate.now());
            // Service metoda může případně nastavit vlastní flash zprávu, pokud selže SF
            if (!redirectAttributes.containsAttribute("successMessage") && !redirectAttributes.containsAttribute("errorMessage")) {
                redirectAttributes.addFlashAttribute("successMessage", "Objednávka byla označena jako plně zaplacená.");
            }
            log.info("Admin action: Order marked as fully paid for order ID: {}", id);
        } catch (EntityNotFoundException | IllegalStateException | IllegalArgumentException e) {
            log.warn("Admin action failed: Cannot mark as fully paid for order ID {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Chyba: " + e.getMessage());
        } catch (Exception e) {
            log.error("Admin action error: Error marking order as fully paid for order ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při označování platby nastala neočekávaná chyba.");
        }
        return "redirect:/admin/orders/" + id;
    }

    // --- Akce pro SuperFakturu ---

    /**
     * Spustí generování zálohové faktury (proforma) v SuperFaktuře.
     */
    @PostMapping("/{id}/generate-proforma")
    public String generateProforma(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Admin action: Trigger SuperFaktura PROFORMA for order ID: {}", id);
        try {
            Order order = orderService.findOrderById(id).orElseThrow(() -> new EntityNotFoundException("Objednávka nenalezena."));
            // Kontrola, zda už nebyla generována (může být i v service)
            if (order.getSfProformaInvoiceId() != null) {
                redirectAttributes.addFlashAttribute("warningMessage", "Zálohová faktura již byla vygenerována (SF ID: " + order.getSfProformaInvoiceId() + ").");
                return "redirect:/admin/orders/" + id;
            }
            // Kontrola, zda je záloha požadována
            if (order.getDepositAmount() == null || order.getDepositAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Objednávka nevyžaduje zálohu.");
            }
            superFakturaInvoiceService.generateProformaInvoice(order);
            redirectAttributes.addFlashAttribute("successMessage", "Požadavek na generování zálohové faktury odeslán.");
        } catch (EntityNotFoundException | IllegalStateException e) {
            log.warn("Proforma generation validation failed for order {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error generating proforma for order {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Generování zálohové faktury selhalo: " + e.getMessage());
        }
        return "redirect:/admin/orders/" + id;
    }

    /**
     * Spustí generování daňového dokladu k přijaté platbě (DDKP) v SuperFaktuře.
     */
    @PostMapping("/{id}/generate-tax-doc")
    public String generateTaxDoc(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Admin action: Trigger SuperFaktura TAX DOC for order ID: {}", id);
        try {
            Order order = orderService.findOrderById(id).orElseThrow(() -> new EntityNotFoundException("Objednávka nenalezena."));
            // Kontrola, zda už nebyla generována
            if (order.getSfTaxDocumentId() != null) {
                redirectAttributes.addFlashAttribute("warningMessage", "Daňový doklad již byl vygenerován (SF ID: " + order.getSfTaxDocumentId() + ").");
                return "redirect:/admin/orders/" + id;
            }
            // Kontrola, zda je záloha zaplacena
            if (order.getDepositPaidDate() == null && (order.getDepositAmount() != null && order.getDepositAmount().compareTo(BigDecimal.ZERO) > 0)) {
                throw new IllegalStateException("Záloha pro tuto objednávku ještě nebyla označena jako zaplacená.");
            }
            if (order.getDepositAmount() == null || order.getDepositAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Pro tuto objednávku nebyla požadována záloha.");
            }
            superFakturaInvoiceService.generateTaxDocumentForDeposit(order);
            redirectAttributes.addFlashAttribute("successMessage", "Požadavek na generování daňového dokladu odeslán.");
        } catch (EntityNotFoundException | IllegalStateException e) {
            log.warn("Tax doc generation validation failed for order {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error generating tax doc for order {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Generování daňového dokladu selhalo: " + e.getMessage());
        }
        return "redirect:/admin/orders/" + id;
    }

    /**
     * Spustí generování finální faktury v SuperFaktuře.
     */
    @PostMapping("/{id}/generate-final")
    public String generateFinal(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Admin action: Trigger SuperFaktura FINAL invoice for order ID: {}", id);
        try {
            Order order = orderService.findOrderById(id).orElseThrow(() -> new EntityNotFoundException("Objednávka nenalezena."));
            // Kontrola, zda už nebyla generována
            if (order.isFinalInvoiceGenerated() || order.getSfFinalInvoiceId() != null) {
                redirectAttributes.addFlashAttribute("warningMessage", "Finální faktura již byla vygenerována (SF ID: " + order.getSfFinalInvoiceId() + ").");
                return "redirect:/admin/orders/" + id;
            }
            // Kontrola zaplacení zálohy, pokud byla požadována
            if (order.getDepositAmount() != null && order.getDepositAmount().compareTo(BigDecimal.ZERO) > 0 && order.getDepositPaidDate() == null) {
                throw new IllegalStateException("Nelze generovat finální fakturu, dokud není zaplacena záloha.");
            }

            superFakturaInvoiceService.generateFinalInvoice(order);
            redirectAttributes.addFlashAttribute("successMessage", "Požadavek na generování finální faktury odeslán.");
        } catch (EntityNotFoundException | IllegalStateException e) {
            log.warn("Final invoice generation validation failed for order {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error generating final invoice for order {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Generování finální faktury selhalo: " + e.getMessage());
        }
        return "redirect:/admin/orders/" + id;
    }

    /**
     * Spustí odeslání vybrané faktury zákazníkovi emailem přes SuperFaktura API.
     */
    @PostMapping("/{id}/send-invoice-email")
    public String sendInvoiceEmail(@PathVariable Long id,
                                   @RequestParam Long sfInvoiceId,
                                   @RequestParam String invoiceType,
                                   RedirectAttributes redirectAttributes) {
        log.info("Admin action: Sending SF email for invoice type '{}' (SF ID: {}) for order ID: {}", invoiceType, sfInvoiceId, id);
        try {
            Order order = orderService.findOrderById(id).orElseThrow(() -> new EntityNotFoundException("Objednávka nenalezena."));
            if (order.getCustomer() == null || !StringUtils.hasText(order.getCustomer().getEmail())) {
                throw new IllegalStateException("Objednávka nemá zákazníka nebo email.");
            }
            superFakturaInvoiceService.sendInvoiceByEmail(sfInvoiceId, order.getCustomer().getEmail(), invoiceType, order.getOrderCode());
            redirectAttributes.addFlashAttribute("successMessage", "Požadavek na odeslání faktury emailem byl odeslán.");
        } catch (EntityNotFoundException | IllegalStateException e) {
            log.warn("Send email validation failed for order {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error sending invoice email for order {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Odeslání emailu selhalo: " + e.getMessage());
        }
        return "redirect:/admin/orders/" + id;
    }

    /**
     * Manuálně označí fakturu jako odeslanou v SuperFaktuře.
     */
    @PostMapping("/{id}/mark-invoice-sent")
    public String markInvoiceSent(@PathVariable Long id,
                                  @RequestParam Long sfInvoiceId,
                                  @RequestParam String invoiceType, // Pro logování
                                  RedirectAttributes redirectAttributes) {
        log.info("Admin action: Marking invoice type '{}' (SF ID: {}) as sent for order ID: {}", invoiceType, sfInvoiceId, id);
        try {
            Order order = orderService.findOrderById(id).orElseThrow(() -> new EntityNotFoundException("Objednávka nenalezena."));
            String email = order.getCustomer() != null ? order.getCustomer().getEmail() : "neuvedeno";
            // Vytvoříme předmět emailu (pro SF API)
            String subject = "Faktura " + (invoiceType.equals("proforma") ? order.getProformaInvoiceNumber() : (invoiceType.equals("tax_document") ? order.getTaxDocumentNumber() : order.getFinalInvoiceNumber())) + " | Objednávka " + order.getOrderCode();

            superFakturaInvoiceService.markInvoiceAsSent(sfInvoiceId, email, subject);
            redirectAttributes.addFlashAttribute("successMessage", "Faktura (" + invoiceType + ") byla označena jako odeslaná.");
        } catch (EntityNotFoundException e) {
            log.warn("Mark sent validation failed for order {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error marking invoice sent for order {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Označení faktury jako odeslané selhalo: " + e.getMessage());
        }
        return "redirect:/admin/orders/" + id;
    }
}