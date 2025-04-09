package org.example.eshop.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor // Potřeba pro JSON deserializaci/serializaci
@AllArgsConstructor // Pro snadnější vytváření instance
public class CustomPriceResponseDto {
    private BigDecimal priceCZK;
    private BigDecimal priceEUR;
    private String errorMessage; // Pro případ chyby
}