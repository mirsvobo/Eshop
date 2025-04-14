package org.example.eshop.controller;

import org.example.eshop.model.Product;
import org.example.eshop.service.CurrencyService; // Import CurrencyService
import org.example.eshop.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    private final ProductService productService;
    private final CurrencyService currencyService; // Inject CurrencyService

    // Konstruktor pro injektáž
    @Autowired // Přidáno @Autowired
    public HomeController(ProductService productService, CurrencyService currencyService) {
        this.productService = productService;
        this.currencyService = currencyService; // Uložení instance
    }

    @GetMapping("/")
    public String home(Model model) {
        log.info("Accessing home page");
        String currentCurrency = currencyService.getSelectedCurrency(); // Získání aktuální měny
        model.addAttribute("currentGlobalCurrency", currentCurrency); // Předání do modelu (pro layout)

        try {
            // TODO: Nahradit logikou pro výběr konkrétních 4 variant
            // Příklad: Načtení prvních 4 aktivních standardních produktů seřazených podle ID
            List<Product> allActiveStandard = productService.getAllActiveProducts().stream()
                    .filter(p -> !p.isCustomisable())
                    .limit(4) // Omezit na 4
                    .collect(Collectors.toList()); // Použít .toList() v novějších Javách

            model.addAttribute("featuredProducts", allActiveStandard);

            // Vypočítat ceny pro tyto produkty
            Map<Long, Map<String, Object>> priceMap = new HashMap<>();
            for (Product p : allActiveStandard) {
                priceMap.put(p.getId(), productService.calculateFinalProductPrice(p, currentCurrency));
            }
            model.addAttribute("featuredProductPrices", priceMap);
            log.debug("Featured products and prices added to model for currency: {}", currentCurrency);

        } catch (Exception e) {
            log.error("Error fetching featured products for homepage: {}", e.getMessage(), e);
            model.addAttribute("featuredProducts", Collections.emptyList());
            model.addAttribute("featuredProductPrices", Collections.emptyMap());
        }

        return "index"; // Název Thymeleaf šablony
    }
}