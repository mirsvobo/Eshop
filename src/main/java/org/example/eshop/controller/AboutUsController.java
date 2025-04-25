package org.example.eshop.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
// Import Model není potřeba, pokud nepředáváme žádná data do šablony
// import org.springframework.ui.Model;

/**
 * Controller pro zobrazení statické stránky "O nás".
 */
@Controller
public class AboutUsController {

    private static final Logger log = LoggerFactory.getLogger(AboutUsController.class);

    /**
     * Zobrazí stránku "O nás".
     *
     * @return Název Thymeleaf šablony "o-nas".
     */
    @GetMapping("/o-nas")
    public String showAboutUsPage() {
        log.debug("Zobrazuji stránku O nás.");

        return "o-nas";
    }
}