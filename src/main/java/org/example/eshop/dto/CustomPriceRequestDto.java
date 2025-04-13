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

    @NotNull(message = "Custom dimensions are required.")
    private Map<String, BigDecimal> customDimensions;

    // Odebráno: private String customDesign;
    private boolean customHasDivider;
    private boolean customHasGutter;
    private boolean customHasGardenShed;

    // Pole pro Glaze a RoofColor zde nebyla, což je v pořádku
}