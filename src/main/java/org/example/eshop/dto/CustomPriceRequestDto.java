package org.example.eshop.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
public class CustomPriceRequestDto {

    @NotNull(message = "Product ID is required.")
    private Long productId;

    // V mapě očekáváme klíče "length", "width", "height"
    @NotNull(message = "Custom dimensions are required.")
    private Map<String, BigDecimal> customDimensions;

    // Ostatní parametry konfigurace
    private String customDesign;
    private boolean customHasDivider;
    private boolean customHasGutter;
    private boolean customHasGardenShed;

    // Můžeme přidat i další, pokud je potřeba pro výpočet (lazura, barva střechy...)
    // private String customGlaze;
    // private String customRoofColor;
}