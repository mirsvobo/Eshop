// src/test/java/org/example/eshop/controller/HomeControllerTest.java

package org.example.eshop.controller;

import org.example.eshop.config.SecurityTestConfig; // <--- 1. Import sdílené konfigurace
import org.example.eshop.service.CurrencyService;
import org.example.eshop.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import; // <--- 2. Import pro @Import
import org.springframework.security.core.userdetails.UserDetailsService; // Ponechán import
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HomeController.class)
@Import(SecurityTestConfig.class) // <--- 3. Aplikace sdílené konfigurace
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private ProductService productService;
    @MockBean private CurrencyService currencyService;
    @MockBean private UserDetailsService userDetailsService; // Ponecháno - může být potřeba pro Security

    @Test
    @DisplayName("GET / - Zobrazí domovskou stránku (index)")
    void home_ShouldReturnIndexView() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/"))
                .andExpect(status().isOk()) // Očekáváme 200 OK
                .andExpect(view().name("index"));
    }
}