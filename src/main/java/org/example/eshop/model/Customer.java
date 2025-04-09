package org.example.eshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Setter
@Getter
@Entity
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private String firstName;
    @Column(nullable = false) private String lastName;
    @Column(nullable = false, unique = true) private String email; // Unikátní pro přihlášení
    private String phone;
    @Column(nullable = false) private String password; // Uložené HASH heslo
    @Column(nullable = false)
    private boolean isGuest = false;

    // --- Fakturační údaje ---
    private String invoiceCompanyName; // Název firmy
    private String invoiceVatId; // DIČ (VAT ID) - např. CZ12345678
    private String invoiceTaxId; // IČO (Tax ID / Company ID) - např. 12345678
    private String invoiceFirstName; // Jméno na faktuře (může být jiné než název firmy)
    private String invoiceLastName; // Příjmení na faktuře
    @Column(length = 255) private String invoiceStreet; // Ulice a číslo popisné
    @Column(length = 100) private String invoiceCity; // Město
    @Column(length = 20) private String invoiceZipCode; // PSČ
    @Column(length = 100) private String invoiceCountry = "Česká republika"; // Výchozí země

    // --- Dodací údaje ---
    @Column(nullable = false) private boolean useInvoiceAddressAsDelivery = true; // Příznak, zda použít fakturační adresu jako dodací
    private String deliveryFirstName; // Jméno pro doručení
    private String deliveryLastName; // Příjmení pro doručení
    private String deliveryCompanyName; // Firma pro doručení
    @Column(length = 255) private String deliveryStreet; // Ulice a číslo popisné
    @Column(length = 100) private String deliveryCity; // Město
    @Column(length = 20) private String deliveryZipCode; // PSČ
    @Column(length = 100) private String deliveryCountry = "Česká republika"; // Výchozí země
    private String deliveryPhone; // Telefon pro dopravce

    // Vazba na objednávky zákazníka (načítat LAZY)
    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    @OrderBy("orderDate DESC") // Nejnovější objednávky nahoře v seznamu
    private List<Order> orders;

    // --- Role pro Spring Security ---
    @ElementCollection(fetch = FetchType.EAGER) // EAGER je zde vhodný pro snadný přístup k rolím při autentizaci
    @CollectionTable(name = "customer_roles", joinColumns = @JoinColumn(name = "customer_id"))
    @Column(name = "role")
    private Set<String> roles = Set.of("ROLE_USER"); // Výchozí role pro každého zákazníka

    // --- Metadata účtu ---
    @Column(nullable = false) private boolean enabled = true; // Pro aktivaci/deaktivaci účtu administrátorem
    @Column(updatable = false, nullable = false) private LocalDateTime createdAt; // Datum vytvoření účtu
    private LocalDateTime updatedAt; // Datum poslední aktualizace

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        // Zajistit konzistenci adres při vytvoření
        if (useInvoiceAddressAsDelivery) {
            syncDeliveryAddressFromInvoice();
        }
        // Nastavit fakturační jméno/příjmení, pokud není zadáno a není firma
        if (invoiceFirstName == null && invoiceLastName == null && invoiceCompanyName == null) {
            invoiceFirstName = firstName;
            invoiceLastName = lastName;
        }
    }
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        // Zajistit konzistenci adres při aktualizaci
        if (useInvoiceAddressAsDelivery) {
            syncDeliveryAddressFromInvoice();
        }
    }

    // Pomocná metoda pro synchronizaci adres
    private void syncDeliveryAddressFromInvoice() {
        this.deliveryFirstName = this.invoiceFirstName;
        this.deliveryLastName = this.invoiceLastName;
        this.deliveryCompanyName = this.invoiceCompanyName;
        this.deliveryStreet = this.invoiceStreet;
        this.deliveryCity = this.invoiceCity;
        this.deliveryZipCode = this.invoiceZipCode;
        this.deliveryCountry = this.invoiceCountry;
        this.deliveryPhone = this.phone; // Použijeme hlavní telefon zákazníka pro dopravu
    }
}