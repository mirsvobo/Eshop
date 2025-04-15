package org.example.eshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor; // Přidat NoArgsConstructor
import lombok.Setter;
import lombok.AllArgsConstructor; // Přidat AllArgsConstructor
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Entity
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@Table(name = "tax_rate", indexes = { // Přidána anotace @Table
        @Index(name = "idx_taxrate_name", columnList = "name", unique = true)
})
@NoArgsConstructor // Lombok vygeneruje konstruktor bez argumentů (potřeba pro JPA)
@AllArgsConstructor // Lombok vygeneruje konstruktor se všemi argumenty
public class TaxRate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length=100)
    private String name;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal rate;

    @Column(nullable = false)
    private boolean reverseCharge = false;

    @OneToMany(mappedBy = "taxRate", fetch = FetchType.LAZY)
    private List<Product> products;

}