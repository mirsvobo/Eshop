package org.example.eshop.dto;

import lombok.Getter;
import lombok.Setter;


import java.math.BigDecimal;

// DTO pro předání pouze nezbytných dat konfigurátoru do JavaScriptu v šabloně
// Neobsahuje referenci na Product, aby se zabránilo cyklické závislosti při serializaci
@Getter
@Setter
public class ProductConfiguratorDto {

    private BigDecimal minLength;
    private BigDecimal maxLength;
    private BigDecimal stepLength;
    private BigDecimal defaultLength;

    private BigDecimal minWidth;
    private BigDecimal maxWidth;
    private BigDecimal stepWidth;
    private BigDecimal defaultWidth;

    private BigDecimal minHeight;
    private BigDecimal maxHeight;
    private BigDecimal stepHeight;
    private BigDecimal defaultHeight;

}