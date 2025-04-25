package org.example.eshop.advice; // Balíček dle tvého kódu

import jakarta.servlet.http.HttpServletRequest;
import org.example.eshop.service.CurrencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;


@ControllerAdvice
public class GlobalModelAttributeAdvice {

    // Inicializace loggeru
    private static final Logger logger = LoggerFactory.getLogger(GlobalModelAttributeAdvice.class);

    @Autowired
    private CurrencyService currencyService;

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
}