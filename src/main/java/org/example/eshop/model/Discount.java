package org.example.eshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@Entity
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Table(name = "discount", indexes = { // Přidána anotace @Table
        @Index(name = "idx_discount_active_dates", columnList = "active, validFrom, validTo") // Složený index
})
public class Discount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Použít Long místo long

    @Column(nullable = false)
    private String name;
    private String description;

    // Hodnota - POUZE pro procentuální slevu
    @Column(precision = 10, scale = 2)
    private BigDecimal value; // Hodnota procenta (např. 10.00 pro 10%)

    // Hodnoty pro fixní slevu (bez DPH)
    @Column(precision = 10, scale = 2)
    private BigDecimal valueCZK; // Fixní částka v CZK
    @Column(precision = 10, scale = 2)
    private BigDecimal valueEUR; // Fixní částka v EUR

    @Column(nullable = false)
    private boolean isPercentage; // True = procenta, False = pevná částka

    @Column(nullable = false)
    private LocalDateTime validFrom;
    @Column(nullable = false)
    private LocalDateTime validTo;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "discount_products",
            joinColumns = @JoinColumn(name = "discount_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id")
    )
    private Set<Product> products;

    @Column(nullable = false)
    private boolean active = true;
}