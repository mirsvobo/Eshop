package org.example.eshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class ProductConfigurator {
    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "id")
    private Product product;

    // --- Limity rozměrů (v mm nebo cm - musí být konzistentní!) ---
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal minLength;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal maxLength;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal minWidth;  // Hloubka
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal maxWidth;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal minHeight;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal maxHeight;

    // --- NOVÉ: Kroky a výchozí hodnoty (v mm nebo cm - stejná jednotka jako min/max) ---
    @Column(precision = 10, scale = 2) private BigDecimal stepLength = BigDecimal.TEN;  // Výchozí krok 10 (1 cm)
    @Column(precision = 10, scale = 2) private BigDecimal stepWidth = BigDecimal.TEN;   // Výchozí krok 10 (1 cm)
    @Column(precision = 10, scale = 2) private BigDecimal stepHeight = BigDecimal.valueOf(5); // Výchozí krok 5 (0.5 cm)

    @Column(precision = 10, scale = 2) private BigDecimal defaultLength; // Výchozí délka (může být null, použije se min)
    @Column(precision = 10, scale = 2) private BigDecimal defaultWidth;  // Výchozí hloubka
    @Column(precision = 10, scale = 2) private BigDecimal defaultHeight; // Výchozí výška

    // --- Ceny ---
    // Ceny za rozměry (za cm nebo mm - musí odpovídat jednotce min/max/step!)
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal pricePerCmHeightCZK;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal pricePerCmHeightEUR;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal pricePerCmLengthCZK;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal pricePerCmLengthEUR;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal pricePerCmWidthCZK;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal pricePerCmWidthEUR;

}