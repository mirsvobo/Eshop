package org.example.eshop.service;

import org.example.eshop.model.Order;

import java.math.BigDecimal;

public interface ShippingService {
    /**
     * Vypočítá cenu dopravy pro danou objednávku.
     * Měla by zohlednit dodací adresu, případně váhu/rozměry.
     *
     * @param order Objekt objednávky obsahující relevantní data.
     * @return Cena dopravy BEZ DPH.
     */
    BigDecimal calculateShippingCost(Order order, String currency);

    /**
     * Vrátí sazbu DPH aplikovatelnou na dopravu.
     * Může být pevně daná nebo konfigurovatelná.
     *
     * @return Sazba DPH jako desetinné číslo (např. 0.21 pro 21%).
     */
    BigDecimal getShippingTaxRate();
}