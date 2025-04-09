package org.example.eshop.controller;

import org.example.eshop.service.ProductService;
// @Autowired již není potřeba
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    private final ProductService productService; // Odebráno @Autowired

    // Konstruktor pro injektáž
    public HomeController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/")
    public String home(Model model) {
        log.info("Accessing home page");
        // Zde můžeš přidat logiku pro načtení doporučených produktů, pokud chcete
        // model.addAttribute("featuredProducts", productService.getFeaturedProducts());
        return "index";
    }
}