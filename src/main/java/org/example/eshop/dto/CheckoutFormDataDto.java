package org.example.eshop.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.example.eshop.model.Customer; // <-- Přidat import
import org.springframework.util.StringUtils; // <-- Přidat import

/**
 * DTO pro data odesílaná z formuláře pokladny.
 * Nyní obsahuje i pole pro hosta.
 */
@Getter
@Setter
public class CheckoutFormDataDto {

    // --- Údaje zákazníka (vyžadováno pro hosta) ---
    @NotBlank(message = "Email je povinný.", groups = GuestValidation.class)
    @Email(message = "Zadejte platný email.", groups = GuestValidation.class)
    @Size(max = 255, message = "Email je příliš dlouhý.")
    private String email;

    @NotBlank(message = "Křestní jméno je povinné.", groups = GuestValidation.class)
    @Size(max = 100, message = "Křestní jméno je příliš dlouhé.")
    private String firstName;

    @NotBlank(message = "Příjmení je povinné.", groups = GuestValidation.class)
    @Size(max = 100, message = "Příjmení je příliš dlouhé.")
    private String lastName;

    @NotBlank(message = "Telefonní číslo je povinné.", groups = GuestValidation.class)
    @Size(max = 30, message = "Telefonní číslo je příliš dlouhé.")
    private String phone;

    // --- Fakturační adresa ---
    @Size(max = 255, message = "Název firmy je příliš dlouhý.")
    private String invoiceCompanyName;

    @NotBlank(message = "Ulice a číslo popisné (fakturační) jsou povinné.", groups = GuestValidation.class)
    @Size(max = 255, message = "Ulice (fakturační) je příliš dlouhá.")
    private String invoiceStreet;

    @NotBlank(message = "Město (fakturační) je povinné.", groups = GuestValidation.class)
    @Size(max = 100, message = "Město (fakturační) je příliš dlouhé.")
    private String invoiceCity;

    @NotBlank(message = "PSČ (fakturační) je povinné.", groups = GuestValidation.class)
    @Size(max = 20, message = "PSČ (fakturační) je příliš dlouhé.")
    private String invoiceZipCode;

    @NotBlank(message = "Země (fakturační) je povinná.", groups = GuestValidation.class)
    @Size(max = 100, message = "Země (fakturační) je příliš dlouhá.")
    private String invoiceCountry = "Česká republika"; // Defaultní hodnota

    @Size(max = 50, message = "IČO je příliš dlouhé.")
    private String invoiceTaxId; // IČO

    @Size(max = 50, message = "DIČ je příliš dlouhé.")
    private String invoiceVatId; // DIČ

    // --- Dodací adresa ---
    private boolean useInvoiceAddressAsDelivery = true; // Checkbox "Dodací adresa je stejná jako fakturační"

    @Size(max = 255, message = "Název firmy (dodací) je příliš dlouhý.")
    private String deliveryCompanyName;

    @NotBlank(message = "Křestní jméno (dodací) je povinné.", groups = DeliveryAddressValidation.class)
    @Size(max = 100, message = "Křestní jméno (dodací) je příliš dlouhé.")
    private String deliveryFirstName;

    @NotBlank(message = "Příjmení (dodací) je povinné.", groups = DeliveryAddressValidation.class)
    @Size(max = 100, message = "Příjmení (dodací) je příliš dlouhé.")
    private String deliveryLastName;

    @NotBlank(message = "Ulice a číslo popisné (dodací) jsou povinné.", groups = DeliveryAddressValidation.class)
    @Size(max = 255, message = "Ulice (dodací) je příliš dlouhá.")
    private String deliveryStreet;

    @NotBlank(message = "Město (dodací) je povinné.", groups = DeliveryAddressValidation.class)
    @Size(max = 100, message = "Město (dodací) je příliš dlouhé.")
    private String deliveryCity;

    @NotBlank(message = "PSČ (dodací) je povinné.", groups = DeliveryAddressValidation.class)
    @Size(max = 20, message = "PSČ (dodací) je příliš dlouhé.")
    private String deliveryZipCode;

    @NotBlank(message = "Země (dodací) je povinná.", groups = DeliveryAddressValidation.class)
    @Size(max = 100, message = "Země (dodací) je příliš dlouhá.")
    private String deliveryCountry;

    @NotBlank(message = "Telefonní číslo (dodací) je povinné.", groups = DeliveryAddressValidation.class)
    @Size(max = 30, message = "Telefonní číslo (dodací) je příliš dlouhé.")
    private String deliveryPhone;


    // --- Ostatní ---
    @NotBlank(message = "Musíte vybrat způsob platby.")
    private String paymentMethod;

    @Size(max = 1000, message = "Poznámka je příliš dlouhá (max 1000 znaků).")
    private String customerNote;

    @AssertTrue(message = "Musíte souhlasit s obchodními podmínkami.")
    private boolean agreeTerms;

    // --- Validační skupiny ---
    public interface GuestValidation {}
    public interface DeliveryAddressValidation {}

    // --- PŘIDANÁ METODA ---
    /**
     * Naplní DTO daty z existující Customer entity.
     * Používá se pro předvyplnění formuláře pro přihlášeného uživatele.
     * @param customer Customer entita.
     */
    public void initializeFromCustomer(Customer customer) {
        if (customer == null) {
            return;
        }
        this.email = customer.getEmail();
        this.firstName = customer.getFirstName();
        this.lastName = customer.getLastName();
        this.phone = customer.getPhone();

        this.invoiceCompanyName = customer.getInvoiceCompanyName();
        this.invoiceStreet = customer.getInvoiceStreet();
        this.invoiceCity = customer.getInvoiceCity();
        this.invoiceZipCode = customer.getInvoiceZipCode();
        this.invoiceCountry = StringUtils.hasText(customer.getInvoiceCountry()) ? customer.getInvoiceCountry() : "Česká republika"; // Default if null
        this.invoiceTaxId = customer.getInvoiceTaxId();
        this.invoiceVatId = customer.getInvoiceVatId();

        this.useInvoiceAddressAsDelivery = customer.isUseInvoiceAddressAsDelivery();

        this.deliveryCompanyName = customer.getDeliveryCompanyName();
        this.deliveryFirstName = customer.getDeliveryFirstName();
        this.deliveryLastName = customer.getDeliveryLastName();
        this.deliveryStreet = customer.getDeliveryStreet();
        this.deliveryCity = customer.getDeliveryCity();
        this.deliveryZipCode = customer.getDeliveryZipCode();
        this.deliveryCountry = StringUtils.hasText(customer.getDeliveryCountry()) ? customer.getDeliveryCountry() : "Česká republika"; // Default if null
        this.deliveryPhone = customer.getDeliveryPhone();

        // Výchozí hodnoty pro zbytek formuláře
        this.paymentMethod = "BANK_TRANSFER";
        this.agreeTerms = false; // Souhlas musí uživatel vždy znovu zaškrtnout
    }
}
