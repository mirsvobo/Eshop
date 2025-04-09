package org.example.eshop.advice; // Balíček dle tvého kódu

import org.example.eshop.service.CurrencyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// Přidáno pro logování
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Přidáno pro @SessionScope beany (pokud používáš Cart) a HttpServletRequest
import jakarta.servlet.http.HttpServletRequest; // Pro logování URL
import org.example.eshop.service.Cart; // Pokud používáš Cart
import java.util.Collections;
import java.util.List;


@ControllerAdvice
public class GlobalModelAttributeAdvice {

    // Inicializace loggeru
    private static final Logger logger = LoggerFactory.getLogger(GlobalModelAttributeAdvice.class);

    @Autowired
    private CurrencyService currencyService;

    // Pokud používáš i Cart, odkomentuj:
    // @Autowired
    // private Cart cart;

    /**
     * Původní metoda pro přidání měny.
     * Přidává aktuálně zvolenou měnu do modelu pod názvem "currentGlobalCurrency".
     */
    @ModelAttribute("currentGlobalCurrency") // Název atributu v modelu
    public String addGlobalCurrencyToModel(HttpServletRequest request) { // Přidán request pro logování
        logger.info(">>> [GlobalModelAttributeAdvice] Vstupuji do addGlobalCurrencyToModel pro request: {}", request.getRequestURI()); // <-- NOVÝ LOG
        String selectedCurrency = "CZK"; // Default hodnota pro případ chyby
        try {
            selectedCurrency = currencyService.getSelectedCurrency(); // Původní volání
            logger.info("[GlobalModelAttributeAdvice] Získána měna ze CurrencyService: {}", selectedCurrency); // <-- NOVÝ LOG
        } catch (Exception e) {
            logger.error("!!! [GlobalModelAttributeAdvice] Chyba při získávání měny z CurrencyService: {} !!!", e.getMessage(), e); // <-- NOVÝ LOG
            // selectedCurrency zůstane "CZK"
        }
        logger.info(">>> [GlobalModelAttributeAdvice] Opouštím addGlobalCurrencyToModel. Vracím měnu: {}", selectedCurrency); // <-- NOVÝ LOG
        return selectedCurrency;
    }

    /*
    // Pokud bys potřeboval přidávat i další atributy (např. počet položek v košíku)
    @ModelAttribute // Bez explicitního názvu - přidá všechny atributy z metody
    public void addOtherGlobalAttributes(Model model, HttpServletRequest request) {
         logger.info(">>> [GlobalModelAttributeAdvice] Vstupuji do addOtherGlobalAttributes pro request: {}", request.getRequestURI());
         try {
            // Příklad pro počet položek v košíku (pokud máš Cart bean)
            // if (cart != null) {
            //     int itemCount = cart.getItemCount();
            //     model.addAttribute("cartItemCount", itemCount);
            //     logger.info("[GlobalModelAttributeAdvice] Přidán cartItemCount: {}", itemCount);
            // } else {
            //     logger.warn("[GlobalModelAttributeAdvice] Cart bean není dostupná, nelze přidat cartItemCount.");
            //     model.addAttribute("cartItemCount", 0);
            // }

            // Příklad pro dostupné měny
            List<String> availableCurrencies = Collections.emptyList();
             try {
                 availableCurrencies = currencyService.getAvailableCurrencies(); // Pokud existuje tato metoda
                 model.addAttribute("availableCurrencies", availableCurrencies);
                 logger.info("[GlobalModelAttributeAdvice] Přidány availableCurrencies: {}", availableCurrencies);
             } catch (Exception e) {
                 logger.error("!!! [GlobalModelAttributeAdvice] Chyba při získávání dostupných měn: {} !!!", e.getMessage(), e);
                 model.addAttribute("availableCurrencies", List.of("CZK", "EUR")); // Fallback
             }

         } catch (Exception e) {
             logger.error("!!! [GlobalModelAttributeAdvice] Chyba v addOtherGlobalAttributes: {} !!!", e.getMessage(), e);
         }
         logger.info(">>> [GlobalModelAttributeAdvice] Opouštím addOtherGlobalAttributes.");
    }
    */
}