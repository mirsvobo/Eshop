package org.example.eshop.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * DTO pro odpověď AJAXového výpočtu dopravy.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShippingCalculationResponseDto {

    // --- Upravená pole ---
    private BigDecimal shippingCostNoTax;   // Cena dopravy bez DPH (může být 0 nebo -1 pro chybu)
    private BigDecimal shippingTax;         // DPH z dopravy (může být 0)
    private BigDecimal totalPrice;          // Finální celková cena objednávky VČETNĚ dopravy
    private Map<BigDecimal, BigDecimal> vatBreakdown; // Rozpis DPH ze ZBOŽÍ (pro případnou aktualizaci)
    private BigDecimal totalVatWithShipping; // Celkové DPH (zboží + doprava)
    private String errorMessage;        // Chybová zpráva (null pokud OK)
    private String currencySymbol;// Symbol měny (např. "Kč" nebo "€")
    private BigDecimal originalShippingCostNoTax; // Původní cena dopravy bez DPH (před slevou)
    private BigDecimal originalShippingTax;       // Původní DPH z dopravy (před slevou)
    private BigDecimal shippingDiscountAmount;
    // ---------------------

    // Konstruktor, gettery, settery... jsou generovány Lombokem
}
