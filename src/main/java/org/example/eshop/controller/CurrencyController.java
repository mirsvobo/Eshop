package org.example.eshop.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.eshop.service.CurrencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class CurrencyController {

    private static final Logger log = LoggerFactory.getLogger(CurrencyController.class);

    @Autowired
    private CurrencyService currencyService;

    @PostMapping("/nastavit-menu") // Endpoint pro změnu měny
    public RedirectView setCurrency(@RequestParam String currency, HttpServletRequest request) {
        log.info("Request received to set currency to: {}", currency);
        currencyService.setSelectedCurrency(currency);

        // Přesměrujeme uživatele zpět na stránku, odkud přišel
        String referer = request.getHeader("Referer");
        log.debug("Redirecting back to referrer: {}", referer);
        // Jednoduché přesměrování - pozor na možné bezpečnostní problémy s otevřeným přesměrováním,
        // v reálné aplikaci by bylo lepší validovat URL nebo přesměrovat na pevnou stránku.
        return new RedirectView(referer != null ? referer : "/");
    }
}