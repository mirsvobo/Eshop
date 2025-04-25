package org.example.eshop.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Cacheable
// Pro TaxRate můžeme nechat READ_ONLY, pokud se nemění často. Pokud ano, změnit na READ_WRITE.
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Table(name = "tax_rate", indexes = {
        @Index(name = "idx_taxrate_name", columnList = "name", unique = true)
})
@NoArgsConstructor
@AllArgsConstructor
public class TaxRate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, precision = 5, scale = 4) // scale=4 pro sazby jako 0.2100
    private BigDecimal rate;

    @Column(nullable = false)
    private boolean reverseCharge = false; // Příznak přenesené daňové povinnosti

    // --- OPRAVENÁ RELACE ---
    @ManyToMany(mappedBy = "availableTaxRates", fetch = FetchType.LAZY) // mappedBy ukazuje na pole v Product
    // Cache pro ManyToMany asociaci (nepovinné, ale může pomoci)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private Set<Product> products = new HashSet<>(); // Použijeme Set a inicializujeme
    // --- KONEC OPRAVY ---

    // equals a hashCode by měly být založeny na 'id' nebo 'name' pro konzistenci
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaxRate taxRate = (TaxRate) o;
        // Porovnání podle ID, pokud není null, jinak podle názvu
        if (id != null) {
            return id.equals(taxRate.id);
        } else {
            return name != null && name.equals(taxRate.name);
        }
    }

    @Override
    public int hashCode() {
        // Hash kód podle ID, pokud není null, jinak podle názvu
        if (id != null) {
            return java.util.Objects.hash(id);
        } else {
            return java.util.Objects.hash(name);
        }
    }

    @Override
    public String toString() {
        return "TaxRate{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", rate=" + rate +
                ", reverseCharge=" + reverseCharge +
                '}';
    }
}