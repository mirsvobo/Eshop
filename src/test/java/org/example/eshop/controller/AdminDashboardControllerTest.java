package org.example.eshop.controller;

import org.example.eshop.admin.controller.AdminDashboardController;
import org.example.eshop.config.SecurityTestConfig;
import org.example.eshop.model.Order;
import org.example.eshop.service.CustomerService;
import org.example.eshop.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminDashboardController.class)
@WithMockUser(roles = "ADMIN")
@Import(SecurityTestConfig.class)
class AdminDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private OrderService orderService;
    @MockBean private CustomerService customerService;

    private LocalDateTime todayStart;
    private LocalDateTime todayEnd;

    @BeforeEach
    void setUp() {
        todayStart = LocalDate.now().atStartOfDay();
        todayEnd = LocalDate.now().atTime(LocalTime.MAX);
    }

    @Test
    @DisplayName("GET /admin - Zobrazí dashboard s daty")
    void showDashboard_Success() throws Exception {
        // Mock service calls
        when(orderService.countOrdersCreatedBetween(eq(todayStart), eq(todayEnd))).thenReturn(5L);
        when(orderService.countOrdersByPaymentStatus("AWAITING_DEPOSIT")).thenReturn(2L);
        when(orderService.countOrdersByStatusCode("PROCESSING")).thenReturn(10L);
        when(orderService.countOrdersByStatusCode("NEW")).thenReturn(3L);
        when(customerService.countCustomersCreatedBetween(eq(todayStart), eq(todayEnd))).thenReturn(1L);
        when(customerService.countTotalCustomers()).thenReturn(150L);

        // Mock recent orders (předpokládáme, že findOrders vrací Page)
        when(orderService.findOrders(any(Pageable.class), eq(Optional.empty()), eq(Optional.empty()), eq(Optional.empty()), eq(Optional.empty()), eq(Optional.empty())))
                .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 5), 0)); // Prázdný Page pro jednoduchost

        mockMvc.perform(MockMvcRequestBuilders.get("/admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attributeExists(
                        "ordersTodayCount", "awaitingDepositCount", "processingCount",
                        "newOrdersCount", "recentOrders", "newCustomersToday", "totalCustomers"
                ))
                .andExpect(model().attribute("ordersTodayCount", is(5L)))
                .andExpect(model().attribute("awaitingDepositCount", is(2L)))
                .andExpect(model().attribute("processingCount", is(10L)))
                .andExpect(model().attribute("newOrdersCount", is(3L)))
                .andExpect(model().attribute("newCustomersToday", is(1L)))
                .andExpect(model().attribute("totalCustomers", is(150L)))
                .andExpect(model().attribute("recentOrders", hasSize(0))); // Očekáváme prázdný list z Page
    }

    @Test
    @DisplayName("GET /admin - Zobrazí dashboard i při chybě načítání dat")
    void showDashboard_ServiceError() throws Exception {
        // Simulace chyby
        when(orderService.countOrdersCreatedBetween(any(), any())).thenThrow(new RuntimeException("Simulated DB error"));
        // Nastavení ostatních mocků na vrácení 0 nebo prázdného listu
        when(orderService.countOrdersByPaymentStatus(any())).thenReturn(0L);
        when(orderService.countOrdersByStatusCode(any())).thenReturn(0L);
        when(customerService.countCustomersCreatedBetween(any(), any())).thenReturn(0L);
        when(customerService.countTotalCustomers()).thenReturn(0L);
        when(orderService.findOrders(any(Pageable.class), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(MockMvcRequestBuilders.get("/admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attributeExists("dashboardError"))
                .andExpect(model().attribute("ordersTodayCount", is(0L))) // Očekáváme výchozí hodnoty
                .andExpect(model().attribute("recentOrders", hasSize(0)));
    }
}