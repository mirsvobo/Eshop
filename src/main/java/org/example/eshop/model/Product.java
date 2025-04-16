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
@Table(name = "product", indexes = { // Přidána anotace @Table s definicí indexů
        @Index(name = "idx_product_slug", columnList = "slug", unique = true),
        @Index(name = "idx_product_active", columnList = "active")})
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Název produktu nesmí být prázdný.")
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, unique = true, length = 150) private String slug;
    @Lob @Column(columnDefinition = "TEXT") private String description;

    @Column(precision = 10, scale = 2) private BigDecimal basePriceCZK;
    @Column(precision = 10, scale = 2) private BigDecimal basePriceEUR;
    // *** NOVÉ POLE pro krátký popis ***
    @Column(length = 500) // Omezení délky krátkého popisu
    private String shortDescription;
    @Column(length = 100) private String model; // Informativní popis
    @Column(length = 100) private String material;
    @Column(precision = 10, scale = 2) private BigDecimal height;
    @Column(precision = 10, scale = 2) private BigDecimal length;
    @Column(precision = 10, scale = 2) private BigDecimal width;
    @Column(length = 100) private String roofOverstep;

    // --- NOVÉ @ManyToMany RELACE ---
    @ManyToMany(fetch = FetchType.LAZY) // Zůstáváme u LAZY, budeme inicializovat v controlleru
    @JoinTable(name = "product_designs",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "design_id"))
    @OrderBy("name ASC")
    private Set<Design> availableDesigns;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "product_glazes",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "glaze_id"))
    @OrderBy("name ASC")
    private Set<Glaze> availableGlazes;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "product_roof_colors",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "roof_color_id"))
    @OrderBy("name ASC")
    private Set<RoofColor> availableRoofColors;
    // --- KONEC NOVÝCH RELACÍ ---

    @Column(nullable = false) private boolean customisable = false;
    @Column(nullable = false) private boolean active = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "product_tax_rates",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "tax_rate_id"))
    @NotEmpty(message = "Produkt musí mít přiřazenu alespoň jednu daňovou sazbu.") // Validace
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE) // Cache pro asociaci
    private Set<TaxRate> availableTaxRates = new HashSet<>();
    @ManyToMany(fetch = FetchType.LAZY) @JoinTable(name = "product_available_addons", joinColumns = @JoinColumn(name = "product_id"), inverseJoinColumns = @JoinColumn(name = "addon_id")) private Set<Addon> availableAddons;
    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true) private ProductConfigurator configurator;
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY) @OrderBy("displayOrder ASC, id ASC") private Set<Image> images = new HashSet<>();
    @ManyToMany(mappedBy = "products", fetch = FetchType.LAZY) private Set<Discount> discounts;

    private String metaTitle;
    @Column(length = 1000) private String metaDescription;

    public List<Image> getImagesOrdered() {
        if (this.images == null) {
            return new ArrayList<>();
        }
        List<Image> sortedList = new ArrayList<>(this.images);
        // Řadíme podle displayOrder a pak ID (stejně jako @OrderBy)
        sortedList.sort(Comparator.comparing(Image::getDisplayOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(Image::getId, Comparator.nullsLast(Long::compareTo)));
        return sortedList;
}}