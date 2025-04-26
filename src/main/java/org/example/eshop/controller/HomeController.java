package org.example.eshop.controller;

import org.example.eshop.model.Product;
import org.example.eshop.service.CurrencyService;
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
    private final CurrencyService currencyService;

    @Autowired
    public HomeController(ProductService productService, CurrencyService currencyService) {
        this.productService = productService;
        this.currencyService = currencyService;
    }

    @GetMapping("/")
    public String home(Model model) {
        log.info("Accessing home page");
        String currentCurrency = currencyService.getSelectedCurrency();
        model.addAttribute("currentGlobalCurrency", currentCurrency); // Pro layout

        try {
            // Načtení produktů (stejná logika jako předtím, nebo jiná dle potřeby)
            List<Product> featuredProducts = productService.getAllActiveProducts().stream()
                    .filter(p -> !p.isCustomisable()) // Pouze standardní produkty
                    .limit(4) // Omezení na 4
                    .collect(Collectors.toList()); // .toList() pro Java 16+

            model.addAttribute("featuredProducts", featuredProducts);

            // ***** ZAČÁTEK NOVÉ ČÁSTI PRO CENY *****
            Map<Long, Map<String, Object>> featuredProductPrices = new HashMap<>();
            for (Product product : featuredProducts) {
                if (product != null && product.getId() != null) { // Kontrola pro jistotu
                    try {
                        Map<String, Object> priceInfo = productService.calculateFinalProductPrice(product, currentCurrency);
                        featuredProductPrices.put(product.getId(), priceInfo);
                    } catch (Exception priceEx) {
                        log.error("Error calculating price for featured product ID {}: {}", product.getId(), priceEx.getMessage());
                        // Můžeme přidat null nebo prázdnou mapu, aby Thymeleaf nespadl
                        featuredProductPrices.put(product.getId(), Collections.emptyMap());
                    }
                }
            }
            model.addAttribute("featuredProductPrices", featuredProductPrices); // Předání mapy cen do modelu
            log.debug("Featured products and prices added to model for currency: {}", currentCurrency);
            // ***** KONEC NOVÉ ČÁSTI PRO CENY *****

        } catch (Exception e) {
            log.error("Error fetching featured products for homepage: {}", e.getMessage(), e);
            model.addAttribute("featuredProducts", Collections.emptyList());
            model.addAttribute("featuredProductPrices", Collections.emptyMap()); // Přidat prázdnou mapu i při chybě
        }

        return "index";
    }
}