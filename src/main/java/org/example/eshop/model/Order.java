package org.example.eshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@Entity
@Table(name = "CustomerOrders", indexes = { // Přidána anotace @Table s definicí indexů
        @Index(name = "idx_order_code", columnList = "orderCode", unique = true),
        @Index(name = "idx_order_customer_id", columnList = "customer_id"),
        @Index(name = "idx_order_order_date", columnList = "orderDate"),
        @Index(name = "idx_order_state_id", columnList = "order_state_id"),
        @Index(name = "idx_order_payment_status", columnList = "paymentStatus")
})
@NamedEntityGraph(
        name = "Order.fetchFullDetail",
        attributeNodes = {
                @NamedAttributeNode("customer"),
                @NamedAttributeNode("stateOfOrder"),
                @NamedAttributeNode("appliedCoupon"),
                @NamedAttributeNode(value = "orderItems", subgraph = "orderItemsGraph")
        },
        subgraphs = {
                @NamedSubgraph( // Definice subgraphu pro položky
                        name = "orderItemsGraph",
                        attributeNodes = {
                                @NamedAttributeNode("product") // Načteme produkt v položce
                                // ODEBRÁNO: @NamedAttributeNode("selectedAddons") <-- TOTO JSME ODEBRALI
                        }
                )
        }
)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(precision = 10, scale = 2)
    private BigDecimal originalTotalPrice; // Původní celková cena s DPH PŘED zaokrouhlením dolů

    @Column(nullable = false, length = 3)
    private String currency = "CZK"; // Výchozí měna

    @Column(unique = true, nullable = false, updatable = false, length = 36)
    private String orderCode; // Unikátní kód objednávky (např. UUID nebo generované číslo)

    @ManyToOne(fetch = FetchType.LAZY) // LAZY načítání zákazníka
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    // LAZY načítání položek
    @OrderBy("id ASC") // Řazení položek podle ID
    private List<OrderItem> orderItems;

    @ManyToOne(fetch = FetchType.LAZY) // LAZY načítání stavu
    @JoinColumn(name = "order_state_id", nullable = false)
    private OrderState stateOfOrder; // Aktuální stav objednávky

    // --- Časová razítka ---
    @Column(nullable = false, updatable = false)
    private LocalDateTime orderDate; // Datum a čas vytvoření objednávky
    private LocalDateTime paymentDate; // Datum a čas plné úhrady
    private LocalDateTime shippedDate; // Datum a čas odeslání
    private LocalDateTime deliveredDate; // Datum a čas doručení
    private LocalDateTime cancelledDate; // Datum a čas zrušení
    private LocalDateTime depositPaidDate; // Datum a čas platby zálohy

    // --- Dodací adresa (uložená kopie v době objednávky) ---
    @Column(length = 100)
    private String deliveryFirstName;
    @Column(length = 100)
    private String deliveryLastName;
    @Column(length = 150)
    private String deliveryCompanyName;
    @Column(nullable = false, length = 255)
    private String deliveryStreet;
    @Column(nullable = false, length = 100)
    private String deliveryCity;
    @Column(nullable = false, length = 20)
    private String deliveryZipCode;
    @Column(nullable = false, length = 100)
    private String deliveryCountry;
    @Column(length = 30)
    private String deliveryPhone; // Telefon pro dopravce

    // --- Fakturační adresa (uložená kopie v době objednávky) ---
    @Column(length = 100)
    private String invoiceFirstName;
    @Column(length = 100)
    private String invoiceLastName;
    @Column(length = 150)
    private String invoiceCompanyName;
    @Column(nullable = false, length = 255)
    private String invoiceStreet;
    @Column(nullable = false, length = 100)
    private String invoiceCity;
    @Column(nullable = false, length = 20)
    private String invoiceZipCode;
    @Column(nullable = false, length = 100)
    private String invoiceCountry;
    @Column(length = 20)
    private String invoiceTaxId; // IČO
    @Column(length = 20)
    private String invoiceVatId; // DIČ

    // --- Poznámka a ceny ---
    @Lob // Pro delší text
    @Column(columnDefinition = "TEXT")
    private String note; // Poznámka od zákazníka

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subTotalWithoutTax = BigDecimal.ZERO; // Mezisoučet položek bez DPH
    @Column(precision = 10, scale = 2)
    private BigDecimal couponDiscountAmount = BigDecimal.ZERO; // Výše slevy z kupónu (bez DPH)
    @Column(precision = 10, scale = 2)
    private BigDecimal shippingCostWithoutTax = BigDecimal.ZERO; // Cena dopravy bez DPH
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalItemsTax = BigDecimal.ZERO; // Celkové DPH z položek
    @Column(precision = 5, scale = 4)
    private BigDecimal shippingTaxRate = BigDecimal.ZERO; // Sazba DPH pro dopravu (např. 0.21)
    @Column(precision = 10, scale = 2)
    private BigDecimal shippingTax = BigDecimal.ZERO; // Výše DPH z dopravy
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPriceWithoutTax = BigDecimal.ZERO; // Celková cena bez DPH (po slevě, vč. dopravy)
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalTax = BigDecimal.ZERO; // Celkové DPH (položky + doprava)
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice = BigDecimal.ZERO; // Celková cena k úhradě (s DPH)

    // --- Záloha ---
    @Column(precision = 10, scale = 2)
    private BigDecimal depositAmount; // Požadovaná výše zálohy (s DPH)

    // --- Platba a kupón ---
    @Column(nullable = false, length = 50)
    private String paymentMethod; // Způsob platby (kód, např. BANK_TRANSFER)
    @Column(nullable = false, length = 50)
    private String paymentStatus = "PENDING"; // Stav platby (např. PENDING, PAID, DEPOSIT_PAID, AWAITING_DEPOSIT)

    @ManyToOne(fetch = FetchType.LAZY) // LAZY načítání kupónu
    @JoinColumn(name = "applied_coupon_id")
    private Coupon appliedCoupon; // Použitý kupón (reference)
    private String appliedCouponCode; // Kód použitého kupónu (historicky)

    // --- Fakturace SuperFaktura ---
    @Column(unique = true)
    private Long sfProformaInvoiceId; // ID zálohové faktury v SF
    @Column(length = 100)
    private String proformaInvoiceNumber; // Číslo zálohové faktury
    @Column(length = 1000)
    private String sfProformaPdfUrl; // Odkaz na PDF zálohové faktury

    @Column(unique = true)
    private Long sfTaxDocumentId; // ID DDKP v SF (nebo ostré f. k záloze)
    @Column(length = 100)
    private String taxDocumentNumber; // Číslo DDKP
    @Column(length = 1000)
    private String sfTaxDocumentPdfUrl; // Odkaz na PDF DDKP

    @Column(unique = true)
    private Long sfFinalInvoiceId; // ID finální faktury v SF
    @Column(length = 100)
    private String finalInvoiceNumber; // Číslo finální faktury
    @Column(length = 1000)
    private String sfFinalInvoicePdfUrl; // Odkaz na PDF finální faktury

    @Column(nullable = false)
    private boolean finalInvoiceGenerated = false; // Příznak, zda byla finální faktura úspěšně generována

    // --- JPA Lifecycle Callbacks ---
    @PrePersist
    protected void onCreate() {
        if (orderDate == null) orderDate = LocalDateTime.now();
        if (currency == null) currency = "CZK";
        if (subTotalWithoutTax == null) subTotalWithoutTax = BigDecimal.ZERO;
        if (totalItemsTax == null) totalItemsTax = BigDecimal.ZERO;
        if (couponDiscountAmount == null) couponDiscountAmount = BigDecimal.ZERO;
        if (shippingCostWithoutTax == null) shippingCostWithoutTax = BigDecimal.ZERO;
        if (shippingTaxRate == null) shippingTaxRate = BigDecimal.ZERO;
        if (shippingTax == null) shippingTax = BigDecimal.ZERO;
        if (totalPriceWithoutTax == null) totalPriceWithoutTax = BigDecimal.ZERO;
        if (totalTax == null) totalTax = BigDecimal.ZERO;
        if (totalPrice == null) totalPrice = BigDecimal.ZERO;
        if (paymentStatus == null) paymentStatus = "PENDING";
        finalInvoiceGenerated = false;
    }

    // --- Konstruktory, equals, hashCode, toString (může generovat Lombok) ---
    // equals a hashCode je vhodné implementovat na základě business klíče (např. orderCode nebo id)

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        // Porovnání podle ID, pokud není null, jinak podle kódu objednávky
        if (id != null) {
            return id.equals(order.id);
        } else {
            return orderCode != null && orderCode.equals(order.orderCode);
        }
    }

    @Override
    public int hashCode() {
        // Hash kód podle ID, pokud není null, jinak podle kódu objednávky
        if (id != null) {
            return Objects.hash(id);
        } else {
            return Objects.hash(orderCode);
        }
        // Nebo jednodušeji, pokud nechcete řešit null ID před uložením:
        // return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Order{" +
                "id=" + id +
                ", orderCode='" + orderCode + '\'' +
                ", customerEmail=" + (customer != null ? customer.getEmail() : "null") +
                ", orderDate=" + orderDate +
                ", state=" + (stateOfOrder != null ? stateOfOrder.getCode() : "null") +
                ", paymentStatus='" + paymentStatus + '\'' +
                ", totalPrice=" + totalPrice +
                ", currency='" + currency + '\'' +
                '}';
    }

    @Transient // Anotace @Transient značí, že toto pole/metoda se nemá mapovat do DB
    public boolean isAddressesMatchInOrder() {
        // Porovnáme relevantní pole (ignorujeme velká/malá písmena a bílé znaky pro jistotu)
        boolean streetsMatch = Objects.equals(
                StringUtils.trimWhitespace(this.invoiceStreet),
                StringUtils.trimWhitespace(this.deliveryStreet)
        );
        boolean citiesMatch = Objects.equals(
                StringUtils.trimWhitespace(this.invoiceCity),
                StringUtils.trimWhitespace(this.deliveryCity)
        );
        boolean zipCodesMatch = Objects.equals(
                StringUtils.trimWhitespace(this.invoiceZipCode),
                StringUtils.trimWhitespace(this.deliveryZipCode)
        );
        boolean countriesMatch = Objects.equals(
                StringUtils.trimWhitespace(this.invoiceCountry),
                StringUtils.trimWhitespace(this.deliveryCountry)
        );
        // Porovnáme příjemce - buď firmy nebo jména
        boolean recipientsMatch;
        if (StringUtils.hasText(this.invoiceCompanyName) || StringUtils.hasText(this.deliveryCompanyName)) {
            recipientsMatch = Objects.equals(
                    StringUtils.trimWhitespace(this.invoiceCompanyName),
                    StringUtils.trimWhitespace(this.deliveryCompanyName)
            );
        } else {
            recipientsMatch = Objects.equals(StringUtils.trimWhitespace(this.invoiceFirstName), StringUtils.trimWhitespace(this.deliveryFirstName)) &&
                    Objects.equals(StringUtils.trimWhitespace(this.invoiceLastName), StringUtils.trimWhitespace(this.deliveryLastName));
        }

        return streetsMatch && citiesMatch && zipCodesMatch && countriesMatch && recipientsMatch;
    }
    // --- KONEC NOVÉ METODY ---

}
