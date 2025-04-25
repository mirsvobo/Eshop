package org.example.eshop.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomPriceResponseDto {

    // Celková vypočtená cena (základ + atributy + doplňky)
    private BigDecimal totalPriceCZK;
    private BigDecimal totalPriceEUR;

    // Rozpis ceny
    private BigDecimal basePriceCZK; // Cena jen za rozměry
    private BigDecimal basePriceEUR;
    private BigDecimal designPriceCZK;
    private BigDecimal designPriceEUR;
    private BigDecimal glazePriceCZK;
    private BigDecimal glazePriceEUR;
    private BigDecimal roofColorPriceCZK;
    private BigDecimal roofColorPriceEUR;
    private Map<String, BigDecimal> addonPricesCZK; // Mapa: Název doplňku -> Cena CZK
    private Map<String, BigDecimal> addonPricesEUR; // Mapa: Název doplňku -> Cena EUR

    private String errorMessage; // Pro případ chyby
}