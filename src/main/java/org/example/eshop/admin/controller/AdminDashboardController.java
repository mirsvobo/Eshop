// src/main/java/org/example/eshop/admin/controller/AdminDashboardController.java
package org.example.eshop.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.eshop.service.CustomerService;
import org.example.eshop.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Optional;

@Controller
@RequestMapping("/admin") // Hlavní admin cesta
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardController.class);

    private final OrderService orderService;
    private final CustomerService customerService;
    // TODO: Přidat další služby podle potřeby

    @Autowired
    public AdminDashboardController(OrderService orderService, CustomerService customerService) {
        this.orderService = orderService;
        this.customerService = customerService;
    }

    @ModelAttribute("currentUri")
    public String getCurrentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @GetMapping
    public String showDashboard(Model model) {
        log.info("Requesting admin dashboard view.");
        try {
            // Stávající metriky
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
            long ordersTodayCount = orderService.countOrdersCreatedBetween(todayStart, todayEnd);
            long awaitingDepositCount = orderService.countOrdersByPaymentStatus("AWAITING_DEPOSIT");
            long processingCount = orderService.countOrdersByStatusCode("PROCESSING");
            long newOrdersCount = orderService.countOrdersByStatusCode("NEW");

            // *** NOVÉ METRIKY ***
            long inProductionCount = orderService.countOrdersByStatusCode("IN_PRODUCTION");
            long atZincPlatingCount = orderService.countOrdersByStatusCode("AT_ZINC_PLATING");
            long readyToShipCount = orderService.countOrdersByStatusCode("READY_TO_SHIP");
            // *** KONEC NOVÝCH METRIK ***

            // Poslední objednávky (zůstává)
            Pageable recentOrdersPageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "orderDate"));
            var recentOrdersPage = orderService.findOrders(
                    recentOrdersPageable, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
            );

            // Metriky zákazníků (odebráno newCustomersToday)
            long totalCustomers = customerService.countTotalCustomers();

            // Přidání metrik do modelu
            model.addAttribute("ordersTodayCount", ordersTodayCount);
            model.addAttribute("awaitingDepositCount", awaitingDepositCount);
            model.addAttribute("processingCount", processingCount);
            model.addAttribute("newOrdersCount", newOrdersCount);
            // *** PŘIDÁNÍ NOVÝCH METRIK DO MODELU ***
            model.addAttribute("inProductionCount", inProductionCount);
            model.addAttribute("atZincPlatingCount", atZincPlatingCount);
            model.addAttribute("readyToShipCount", readyToShipCount);
            // *** KONEC PŘIDÁNÍ NOVÝCH METRIK ***
            model.addAttribute("recentOrders", recentOrdersPage.getContent());
            // model.addAttribute("newCustomersToday", newCustomersToday); // ODEBRÁNO
            model.addAttribute("totalCustomers", totalCustomers);

        } catch (Exception e) {
            log.error("Error loading dashboard data: {}", e.getMessage(), e);
            model.addAttribute("dashboardError", "Nepodařilo se načíst data pro dashboard.");
            // Nastavit výchozí hodnoty
            model.addAttribute("ordersTodayCount", 0L);
            model.addAttribute("awaitingDepositCount", 0L);
            model.addAttribute("processingCount", 0L);
            model.addAttribute("newOrdersCount", 0L);
            // *** VÝCHOZÍ HODNOTY PRO NOVÉ METRIKY ***
            model.addAttribute("inProductionCount", 0L);
            model.addAttribute("atZincPlatingCount", 0L);
            model.addAttribute("readyToShipCount", 0L);
            // *** KONEC VÝCHOZÍCH HODNOT ***
            model.addAttribute("recentOrders", Collections.emptyList());
            // model.addAttribute("newCustomersToday", 0L); // ODEBRÁNO
            model.addAttribute("totalCustomers", 0L);
        }
        return "admin/dashboard";
    }
}