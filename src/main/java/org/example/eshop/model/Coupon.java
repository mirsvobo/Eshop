package org.example.eshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Použít Long místo long

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

    @Column(nullable = false) private LocalDateTime startDate;
    @Column(nullable = false) private LocalDateTime expirationDate;

    private Integer usageLimit;
    private Integer usageLimitPerCustomer;
    @Column(nullable = false) private Integer usedTimes = 0;

    // Minimální hodnota objednávky (bez DPH, před slevami) pro uplatnění kupónu
    @Column(precision = 10, scale = 2)
    private BigDecimal minimumOrderValueCZK;
    @Column(precision = 10, scale = 2)
    private BigDecimal minimumOrderValueEUR;

    @Column(nullable = false) private boolean active = true;
    @Column(nullable = false)
    private boolean freeShipping = false; // Defaultně false
}