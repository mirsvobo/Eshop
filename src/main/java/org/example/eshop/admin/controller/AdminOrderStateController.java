package org.example.eshop.admin.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.eshop.model.OrderState;
import org.example.eshop.service.OrderStateService;
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
@RequestMapping("/admin/order-states")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderStateController {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderStateController.class);

    @Autowired
    private OrderStateService orderStateService;

    @ModelAttribute("currentUri")
    public String getCurrentUri(HttpServletRequest request) {
        return request.getRequestURI().replaceFirst("/(new|\\d+/edit)$", "");
    }

    @GetMapping
    public String listOrderStates(Model model) {
        log.info("Requesting order state list view.");
        try {
            // Použijeme metodu, která vrací seřazené stavy
            List<OrderState> states = orderStateService.getAllOrderStatesSorted();
            model.addAttribute("orderStates", states);
        } catch (Exception e) {
            log.error("Error fetching order states: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Nepodařilo se načíst stavy objednávek.");
            model.addAttribute("orderStates", Collections.emptyList());
        }
        return "admin/order-states-list"; // Název šablony pro seznam
    }

    @GetMapping("/new")
    public String showCreateOrderStateForm(Model model) {
        log.info("Requesting new order state form.");
        OrderState newState = new OrderState();
        newState.setDisplayOrder(0); // Defaultní hodnota pro řazení
        newState.setFinalState(false); // Defaultně není finální
        model.addAttribute("orderState", newState);
        model.addAttribute("pageTitle", "Vytvořit nový stav objednávky");
        return "admin/order-state-form"; // Název šablony pro formulář
    }

    @PostMapping
    public String createOrderState(@Valid @ModelAttribute("orderState") OrderState orderState,
                                   BindingResult bindingResult,
                                   RedirectAttributes redirectAttributes,
                                   Model model) {
        log.info("Attempting to create new order state with code: {}", orderState.getCode());
        if (bindingResult.hasErrors()) {
            log.warn("Validation errors creating order state: {}", bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Vytvořit nový stav (Chyba)");
            return "admin/order-state-form";
        }
        try {
            OrderState savedState = orderStateService.createOrderState(orderState);
            redirectAttributes.addFlashAttribute("successMessage", "Stav objednávky '" + savedState.getName() + "' byl úspěšně vytvořen.");
            log.info("Order state '{}' (Code: {}) created successfully with ID: {}", savedState.getName(), savedState.getCode(), savedState.getId());
            return "redirect:/admin/order-states";
        } catch (IllegalArgumentException e) {
            log.warn("Error creating order state '{}': {}", orderState.getCode(), e.getMessage());
            if (e.getMessage().contains("already exists")) {
                bindingResult.rejectValue("code", "error.orderState.duplicate", e.getMessage());
            } else {
                model.addAttribute("errorMessage", e.getMessage());
            }
            model.addAttribute("pageTitle", "Vytvořit nový stav (Chyba)");
            return "admin/order-state-form";
        } catch (Exception e) {
            log.error("Unexpected error creating order state '{}': {}", orderState.getCode(), e.getMessage(), e);
            model.addAttribute("errorMessage", "Při vytváření stavu nastala neočekávaná chyba: " + e.getMessage());
            model.addAttribute("pageTitle", "Vytvořit nový stav (Chyba)");
            return "admin/order-state-form";
        }
    }

    @GetMapping("/{id}/edit")
    public String showEditOrderStateForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Requesting edit form for order state ID: {}", id);
        try {
            OrderState state = orderStateService.getOrderStateById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Stav objednávky s ID " + id + " nenalezen."));
            model.addAttribute("orderState", state);
            model.addAttribute("pageTitle", "Upravit stav objednávky: " + state.getName());
            return "admin/order-state-form";
        } catch (EntityNotFoundException e) {
            log.warn("Order state with ID {} not found for editing.", id);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/order-states";
        } catch (Exception e) {
            log.error("Error loading order state ID {} for edit: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Nepodařilo se načíst stav k úpravě.");
            return "redirect:/admin/order-states";
        }
    }

    @PostMapping("/{id}")
    public String updateOrderState(@PathVariable Long id,
                                   @Valid @ModelAttribute("orderState") OrderState orderStateData,
                                   BindingResult bindingResult,
                                   RedirectAttributes redirectAttributes,
                                   Model model) {
        log.info("Attempting to update order state ID: {}", id);
        if (bindingResult.hasErrors()) {
            log.warn("Validation errors updating order state {}: {}", id, bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Upravit stav (Chyba)");
            orderStateData.setId(id); // Zachovat ID
            return "admin/order-state-form";
        }
        try {
            // Podle signatury metody v service vrací Optional<Object>, musíme přetypovat
            Optional<Object> updatedStateOpt = (Optional<Object>) orderStateService.updateOrderState(id, orderStateData);

            if (updatedStateOpt.isPresent() && updatedStateOpt.get() instanceof Optional) {
                Optional<OrderState> innerOpt = (Optional<OrderState>) updatedStateOpt.get();
                if (innerOpt.isPresent()) {
                    OrderState updatedState = innerOpt.get();
                    redirectAttributes.addFlashAttribute("successMessage", "Stav objednávky '" + updatedState.getName() + "' byl úspěšně aktualizován.");
                    log.info("Order state ID {} updated successfully.", id);
                    return "redirect:/admin/order-states";
                }
            }
            // Pokud Optional nebo vnitřní Optional je prázdný, znamená to, že entita nebyla nalezena
            throw new EntityNotFoundException("Stav objednávky s ID " + id + " nenalezen při pokusu o update.");

        } catch (EntityNotFoundException e) {
            log.warn("Cannot update order state. State not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/order-states";
        } catch (IllegalArgumentException e) {
            log.warn("Error updating order state ID {}: {}", id, e.getMessage());
            if (e.getMessage().contains("already exists")) {
                bindingResult.rejectValue("code", "error.orderState.duplicate", e.getMessage());
            } else {
                model.addAttribute("errorMessage", e.getMessage());
            }
            model.addAttribute("pageTitle", "Upravit stav (Chyba)");
            orderStateData.setId(id);
            return "admin/order-state-form";
        } catch (Exception e) {
            log.error("Unexpected error updating order state ID {}: {}", id, e.getMessage(), e);
            model.addAttribute("pageTitle", "Upravit stav (Chyba)");
            orderStateData.setId(id);
            model.addAttribute("errorMessage", "Při aktualizaci stavu nastala neočekávaná chyba: " + e.getMessage());
            return "admin/order-state-form";
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteOrderState(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.warn("Attempting to DELETE order state ID: {}", id);
        try {
            orderStateService.deleteOrderStateById(id);
            redirectAttributes.addFlashAttribute("successMessage", "Stav objednávky byl úspěšně smazán.");
            log.info("Order state ID {} deleted successfully.", id);
        } catch (EntityNotFoundException e) {
            log.warn("Cannot delete order state. State not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (IllegalStateException | DataIntegrityViolationException e) {
            log.error("Error deleting order state ID {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error deleting order state ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při mazání stavu nastala chyba: " + e.getMessage());
        }
        return "redirect:/admin/order-states";
    }
}