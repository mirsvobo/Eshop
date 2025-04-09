package org.example.eshop.model;

import jakarta.persistence.*;
import lombok.Getter; // Ujisti se, že import je správný
import lombok.Setter; // Ujisti se, že import je správný
import java.math.BigDecimal;

@Getter // Tato anotace generuje všechny gettery
@Setter // Tato anotace generuje všechny settery
@Entity
public class ProductConfigurator {
    @Id
    private Long id; // Getter: getId(), Setter: setId(Long id)

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "id")
    private Product product; // Getter: getProduct(), Setter: setProduct(Product product)

    // Limity rozměrů (v cm)
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal minLength; // getMinLength(), setMinLength(...)
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal maxLength; // getMaxLength(), setMaxLength(...)
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal minWidth;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal maxWidth;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal minHeight;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal maxHeight;

    // Konstanty pro výpočet ceny z rozměrů (Kč/cm a EUR/cm)
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal pricePerCmHeightCZK; // getPricePerCmHeightCZK(), setPricePerCmHeightCZK(...)
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal pricePerCmHeightEUR; // getPricePerCmHeightEUR(), setPricePerCmHeightEUR(...)
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal pricePerCmLengthCZK; // getPricePerCmLengthCZK(), setPricePerCmLengthCZK(...)
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal pricePerCmLengthEUR; // getPricePerCmLengthEUR(), setPricePerCmLengthEUR(...)
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal pricePerCmDepthCZK;  // getPricePerCmDepthCZK(), setPricePerCmDepthCZK(...)
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal pricePerCmDepthEUR;  // getPricePerCmDepthEUR(), setPricePerCmDepthEUR(...)

    // Konstanty/ceny pro "dynamické" doplňky/volby
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal designPriceCZK;      // getDesignPriceCZK(), setDesignPriceCZK(...)
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal designPriceEUR;      // getDesignPriceEUR(), setDesignPriceEUR(...)
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal dividerPricePerCmDepthCZK; // getDividerPricePerCmDepthCZK(), setDividerPricePerCmDepthCZK(...)
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal dividerPricePerCmDepthEUR; // getDividerPricePerCmDepthEUR(), setDividerPricePerCmDepthEUR(...)
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal gutterPriceCZK;      // getGutterPriceCZK(), setGutterPriceCZK(...)
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal gutterPriceEUR;      // getGutterPriceEUR(), setGutterPriceEUR(...)
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal shedPriceCZK;        // getShedPriceCZK(), setShedPriceCZK(...)
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal shedPriceEUR;        // getShedPriceEUR(), setShedPriceEUR(...)
}