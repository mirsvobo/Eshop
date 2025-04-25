package org.example.eshop.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class CustomPriceRequestDto {

    private Long productId;
    private Map<String, BigDecimal> customDimensions; // length, width, height jako Stringy pro BigDecimal

    // Přidáno pro rozpis ceny
    private Long selectedDesignId;
    private Long selectedGlazeId;
    private Long selectedRoofColorId;
    private List<Long> selectedAddonIds; // Seznam ID vybraných doplňků (mimo 'Ne')

}