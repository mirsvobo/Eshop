package org.example.eshop.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.math.BigDecimal;
import java.util.*;

@Getter
@Setter
@Entity
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Table(name = "product", indexes = {
        @Index(name = "idx_product_slug", columnList = "slug", unique = true),
        @Index(name = "idx_product_active", columnList = "active")})
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Název produktu nesmí být prázdný.")
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, unique = true, length = 150)
    private String slug;
    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(precision = 10, scale = 2)
    private BigDecimal basePriceCZK;
    @Column(precision = 10, scale = 2)
    private BigDecimal basePriceEUR;
    @Column(length = 500)
    private String shortDescription;
    @Column(length = 100)
    private String model;
    @Column(length = 100)
    private String material;
    @Column(precision = 10, scale = 2)
    private BigDecimal height;
    @Column(precision = 10, scale = 2)
    private BigDecimal length;
    @Column(precision = 10, scale = 2)
    private BigDecimal width;
    @Column(length = 100)
    private String roofOverstep;
    @Version
    @Column(length = 100)
    private Integer version; // Poznámka: @Column(length=100) zde asi nemá smysl pro @Version

    // --- NOVÉ @ManyToMany RELACE ---
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "product_designs",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "design_id"))
    @OrderBy("name ASC")
    private Set<Design> availableDesigns = new HashSet<>(); // Inicializováno

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "product_glazes",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "glaze_id"))
    @OrderBy("name ASC")
    private Set<Glaze> availableGlazes = new HashSet<>(); // Inicializováno

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "product_roof_colors",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "roof_color_id"))
    @OrderBy("name ASC")
    private Set<RoofColor> availableRoofColors = new HashSet<>(); // Inicializováno
    // --- KONEC NOVÝCH RELACÍ ---

    @Column(nullable = false)
    private boolean customisable = false;
    @Column(nullable = false)
    private boolean active = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "product_tax_rates",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "tax_rate_id"))
    @NotEmpty(message = "Produkt musí mít přiřazenu alespoň jednu daňovou sazbu.")
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private Set<TaxRate> availableTaxRates = new HashSet<>(); // Bylo již OK

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "product_available_addons",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "addon_id"))
    private Set<Addon> availableAddons = new HashSet<>(); // Inicializováno

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    // Přidáno FetchType.LAZY pro konzistenci
    private ProductConfigurator configurator; // OneToOne se neinicializuje zde

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC, id ASC")
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE) // Přidána cache pro konzistenci
    private Set<Image> images = new HashSet<>(); // Bylo již OK

    @ManyToMany(mappedBy = "products", fetch = FetchType.LAZY)
    private Set<Discount> discounts = new HashSet<>(); // Inicializováno

    private String metaTitle;
    @Column(length = 1000)
    private String metaDescription;

    // Metoda getImagesOrdered zůstává stejná
    public List<Image> getImagesOrdered() {
        List<Image> sortedList = new ArrayList<>(this.images);
        sortedList.sort(Comparator.comparing(Image::getDisplayOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(Image::getId, Comparator.nullsLast(Long::compareTo)));
        return sortedList;
    }

    // Je dobré mít i equals a hashCode založené na ID, pokud objekty porovnáváš nebo ukládáš do Setů mimo JPA kontext
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return id != null && id.equals(product.id); // Porovnání jen podle ID, pokud není null
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this); // Hash kód podle ID nebo identity
    }
}