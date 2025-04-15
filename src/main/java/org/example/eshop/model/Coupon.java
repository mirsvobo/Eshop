package org.example.eshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Table(name = "coupon", indexes = { // Přidána anotace @Table
        @Index(name = "idx_coupon_code", columnList = "code", unique = true)})
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50) private String code;
    @Column(nullable = false) private String name;
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
    private boolean isPercentage; // True = procenta (použije 'value'), False = pevná částka (použije 'valueCZK'/'valueEUR')

    /**
     * Pokud je true, kupón poskytne dopravu zdarma,
     * navíc k případné procentuální nebo fixní slevě.
     * Pokud je hodnota/procenta 0 a toto je true, kupón poskytne *pouze* dopravu zdarma.
     */
    @Column(nullable = false)
    private boolean freeShipping = false; // Defaultně false

    // Ostatní pole zůstávají stejná...
    private LocalDateTime startDate; // Změněno na nullable
    private LocalDateTime expirationDate; // Nullable

    private Integer usageLimit;
    private Integer usageLimitPerCustomer;
    @Column(nullable = false) private Integer usedTimes = 0;

    @Column(precision = 10, scale = 2)
    private BigDecimal minimumOrderValueCZK;
    @Column(precision = 10, scale = 2)
    private BigDecimal minimumOrderValueEUR;

    @Column(nullable = false) private boolean active = true;

    /**
     * Pomocná metoda pro logiku "jen doprava zdarma".
     * @return true pokud je kupón jen na dopravu zdarma.
     */
    @Transient // Nebude se ukládat do DB
    public boolean isFreeShippingOnly() {
        boolean noPercentageValue = value == null || value.compareTo(BigDecimal.ZERO) == 0;
        boolean noFixedValue = (valueCZK == null || valueCZK.compareTo(BigDecimal.ZERO) == 0) &&
                (valueEUR == null || valueEUR.compareTo(BigDecimal.ZERO) == 0);

        // Je freeShipping a (není procentuální NEBO procentuální hodnota je nulová) A zároveň nemá fixní hodnotu
        return freeShipping && (!isPercentage || noPercentageValue) && noFixedValue;
    }
}