package org.example.eshop.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.example.eshop.model.Customer;
import org.example.eshop.validation.RequireIcoOrDicIfCompany;
import org.example.eshop.validation.ValidPhoneNumber;
import org.springframework.util.StringUtils;

@Getter
@Setter
@RequireIcoOrDicIfCompany
public class CheckoutFormDataDto {

    // --- Údaje zákazníka / Kontakt ---
    @NotBlank(message = "Email je povinný.", groups = {GuestValidation.class, DefaultValidationGroup.class})
    @Email(message = "Zadejte platný email.", groups = {GuestValidation.class, DefaultValidationGroup.class})
    @Size(max = 255, message = "Email je příliš dlouhý.")
    private String email;
    @NotBlank(message = "Křestní jméno je povinné.", groups = {GuestValidation.class, DefaultValidationGroup.class})
    @Size(max = 100, message = "Křestní jméno je příliš dlouhé.")
    private String firstName; // Hlavní kontaktní jméno
    @NotBlank(message = "Příjmení je povinné.", groups = {GuestValidation.class, DefaultValidationGroup.class})
    @Size(max = 100, message = "Příjmení je příliš dlouhé.")
    private String lastName; // Hlavní kontaktní příjmení
    @NotBlank(message = "Telefonní číslo je povinné.", groups = {GuestValidation.class, DefaultValidationGroup.class})
    @Size(max = 30, message = "Telefonní číslo je příliš dlouhé.")
    @NotBlank(message = "Telefonní číslo je povinné.", groups = {GuestValidation.class, DefaultValidationGroup.class})
    @Size(max = 30, message = "Telefonní číslo je příliš dlouhé.")
    @ValidPhoneNumber(message = "Neplatný formát telefonního čísla (uveďte i předvolbu +420/+421).", groups = {GuestValidation.class, DefaultValidationGroup.class}) // <-- POUŽITÍ NOVÉ ANOTACE
    private String phone;
    // --- Fakturační adresa ---
    @Size(max = 255, message = "Název firmy je příliš dlouhý.")
    private String invoiceCompanyName; // Název firmy (nepovinné)
    @NotBlank(message = "Ulice a číslo popisné (fakturační) jsou povinné.", groups = DefaultValidationGroup.class)
    @Size(max = 255, message = "Ulice (fakturační) je příliš dlouhá.")
    private String invoiceStreet;
    @NotBlank(message = "Město (fakturační) je povinné.", groups = DefaultValidationGroup.class)
    @Size(max = 100, message = "Město (fakturační) je příliš dlouhé.")
    private String invoiceCity;
    @NotBlank(message = "PSČ (fakturační) je povinné.", groups = DefaultValidationGroup.class)
    @Size(max = 20, message = "PSČ (fakturační) je příliš dlouhé.")
    @Pattern(regexp = "^[A-Za-z0-9\\s-]{3,10}$", message = "Neplatný formát PSČ.")
    private String invoiceZipCode;
    @NotBlank(message = "Země (fakturační) je povinná.", groups = DefaultValidationGroup.class)
    @Size(max = 100, message = "Země (fakturační) je příliš dlouhá.")
    private String invoiceCountry = "Česká republika";
    @Size(max = 50, message = "IČO je příliš dlouhé.")
    @Pattern(regexp = "^[0-9]{0,15}$", message = "IČO může obsahovat pouze čísla.")
    private String invoiceTaxId; // IČO
    @Size(max = 50, message = "DIČ je příliš dlouhé.")
    @Pattern(regexp = "^(|[A-Z]{2}.*)$", message = "DIČ by mělo začínat kódem země (např. CZ, SK).")
    private String invoiceVatId; // DIČ
    private boolean useInvoiceAddressAsDelivery = true;

    @Size(max = 255, message = "Název firmy (dodací) je příliš dlouhý.", groups = DeliveryAddressValidation.class) // <-- PŘIDÁNA SKUPINA
    private String deliveryCompanyName;

    @NotBlank(message = "Křestní jméno (dodací) je povinné.", groups = DeliveryAddressValidation.class)
    @Size(max = 100, message = "Křestní jméno (dodací) je příliš dlouhé.", groups = DeliveryAddressValidation.class) // <-- PŘIDÁNA SKUPINA
    private String deliveryFirstName;

    @NotBlank(message = "Příjmení (dodací) je povinné.", groups = DeliveryAddressValidation.class)
    @Size(max = 100, message = "Příjmení (dodací) je příliš dlouhé.", groups = DeliveryAddressValidation.class) // <-- PŘIDÁNA SKUPINA
    private String deliveryLastName;

    @NotBlank(message = "Ulice a číslo popisné (dodací) jsou povinné.", groups = DeliveryAddressValidation.class)
    @Size(max = 255, message = "Ulice (dodací) je příliš dlouhá.", groups = DeliveryAddressValidation.class) // <-- PŘIDÁNA SKUPINA
    private String deliveryStreet;

    @NotBlank(message = "Město (dodací) je povinné.", groups = DeliveryAddressValidation.class)
    @Size(max = 100, message = "Město (dodací) je příliš dlouhé.", groups = DeliveryAddressValidation.class) // <-- PŘIDÁNA SKUPINA
    private String deliveryCity;

    @NotBlank(message = "PSČ (dodací) je povinné.", groups = DeliveryAddressValidation.class)
    @Size(max = 20, message = "PSČ (dodací) je příliš dlouhé.", groups = DeliveryAddressValidation.class) // <-- PŘIDÁNA SKUPINA
    @Pattern(regexp = "^[A-Za-z0-9\\s-]{3,10}$", message = "Neplatný formát PSČ.", groups = DeliveryAddressValidation.class) // <-- PŘIDÁNA SKUPINA
    private String deliveryZipCode;

    @NotBlank(message = "Země (dodací) je povinná.", groups = DeliveryAddressValidation.class)
    @Size(max = 100, message = "Země (dodací) je příliš dlouhá.", groups = DeliveryAddressValidation.class) // <-- PŘIDÁNA SKUPINA
    private String deliveryCountry;

    @Size(max = 30, message = "Telefonní číslo (dodací) je příliš dlouhé.", groups = DeliveryAddressValidation.class) // <-- PŘIDÁNA SKUPINA
    @ValidPhoneNumber(message = "Neplatný formát dodacího telefonního čísla (uveďte i předvolbu +420/+421).", groups = DeliveryAddressValidation.class)
    private String deliveryPhone;
    private String paymentMethod;
    @Size(max = 1000, message = "Poznámka je příliš dlouhá (max 1000 znaků).")
    private String customerNote;
    @AssertTrue(message = "Musíte souhlasit s obchodními podmínkami.", groups = DefaultValidationGroup.class)
    private boolean agreeTerms;
    private boolean applyReverseCharge = false;

    // --- Metoda initializeFromCustomer (upravená) ---
    public void initializeFromCustomer(Customer customer) {
        if (customer == null) {
            return;
        }
        // Základní údaje
        this.email = customer.getEmail();
        this.firstName = customer.getFirstName();
        this.lastName = customer.getLastName();
        this.phone = customer.getPhone();

        // Fakturační údaje
        this.invoiceCompanyName = customer.getInvoiceCompanyName();
        this.invoiceStreet = customer.getInvoiceStreet();
        this.invoiceCity = customer.getInvoiceCity();
        this.invoiceZipCode = customer.getInvoiceZipCode();
        this.invoiceCountry = StringUtils.hasText(customer.getInvoiceCountry()) ? customer.getInvoiceCountry() : "Česká republika";
        this.invoiceTaxId = customer.getInvoiceTaxId();
        this.invoiceVatId = customer.getInvoiceVatId();
        // invoiceFirstName a invoiceLastName se již nepřenáší přímo do DTO

        // Dodací údaje
        this.useInvoiceAddressAsDelivery = customer.isUseInvoiceAddressAsDelivery();
        this.deliveryCompanyName = customer.getDeliveryCompanyName();
        this.deliveryFirstName = customer.getDeliveryFirstName();
        this.deliveryLastName = customer.getDeliveryLastName();
        this.deliveryStreet = customer.getDeliveryStreet();
        this.deliveryCity = customer.getDeliveryCity();
        this.deliveryZipCode = customer.getDeliveryZipCode();
        this.deliveryCountry = StringUtils.hasText(customer.getDeliveryCountry()) ? customer.getDeliveryCountry() : "Česká republika";
        this.deliveryPhone = customer.getDeliveryPhone();

        // Výchozí hodnoty formuláře
        this.paymentMethod = "BANK_TRANSFER";
        this.agreeTerms = false;
    }

    // --- Validační skupiny ---
    public interface GuestValidation {
    }

    public interface DeliveryAddressValidation {
    }

    public interface DefaultValidationGroup {
    } // Pro standardní validace
}