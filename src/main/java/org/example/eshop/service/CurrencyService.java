package org.example.eshop.service;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

@Component
@SessionScope(proxyMode = ScopedProxyMode.TARGET_CLASS) // Důležité pro session scope
@Getter // Lombok getter pro selectedCurrency
public class CurrencyService implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(CurrencyService.class);

    private static final String DEFAULT_CURRENCY = "CZK";
    private static final Set<String> ALLOWED_CURRENCIES = Set.of("CZK", "EUR");

    private String selectedCurrency = DEFAULT_CURRENCY;

    /**
     * Nastaví vybranou měnu, pokud je povolena.
     *
     * @param currency Kód měny ("CZK" nebo "EUR").
     */
    public void setSelectedCurrency(String currency) {
        if (currency != null && ALLOWED_CURRENCIES.contains(currency.toUpperCase())) {
            String upperCaseCurrency = currency.toUpperCase();
            if (!upperCaseCurrency.equals(this.selectedCurrency)) {
                log.info("Session currency changed to: {}", upperCaseCurrency);
                this.selectedCurrency = upperCaseCurrency;
            }
        } else {
            log.warn("Attempted to set invalid currency: {}. Keeping current: {}", currency, this.selectedCurrency);
        }
    }

    // Getter getSelectedCurrency() je generován Lombokem (@Getter)
}