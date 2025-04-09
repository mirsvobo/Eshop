package org.example.eshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

@Getter
@Setter
public class AddressDto {

    // Pole pro firmu (nepovinná)
    @Size(max = 100, message = "Název firmy je příliš dlouhý.")
    private String companyName;

    @Size(max = 20, message = "DIČ je příliš dlouhé.")
    @Pattern(regexp = "^(|[A-Z]{2}.*)$", message = "DIČ by mělo začínat kódem země (např. CZ, SK).") // Velmi základní kontrola
    private String vatId; // DIČ

    @Size(max = 20, message = "IČO je příliš dlouhé.")
    @Pattern(regexp = "^[0-9]{0,15}$", message = "IČO může obsahovat pouze čísla.")
    private String taxId; // IČO

    // Jméno a příjmení (povinné, pokud není firma - validaci řešíme jinde)
    @Size(max = 50, message = "Křestní jméno je příliš dlouhé.")
    private String firstName;

    @Size(max = 50, message = "Příjmení je příliš dlouhé.")
    private String lastName;

    // Adresa (povinná)
    @NotBlank(message = "Ulice a číslo nesmí být prázdné.")
    @Size(max = 255, message = "Ulice je příliš dlouhá.")
    private String street;

    @NotBlank(message = "Město nesmí být prázdné.")
    @Size(max = 100, message = "Město je příliš dlouhé.")
    private String city;

    @NotBlank(message = "PSČ nesmí být prázdné.")
    @Size(max = 20, message = "PSČ je příliš dlouhé.")
    @Pattern(regexp = "^[A-Za-z0-9\\s-]{3,10}$", message = "Neplatný formát PSČ.") // Velmi obecný
    private String zipCode;

    @NotBlank(message = "Země nesmí být prázdná.")
    @Size(max = 100, message = "Země je příliš dlouhá.")
    private String country;

    // Telefon (nepovinný pro adresu, ale může být specifický pro dodací)
    @Pattern(regexp = "^(|[+]?[0-9\\s()-]{9,20})$", message = "Neplatný formát telefonního čísla.")
    @Size(max = 30, message = "Telefonní číslo je příliš dlouhé.")
    private String phone;

    // Metoda pro kontrolu, zda jsou vyplněna alespoň firma nebo jméno/příjmení
    // Tuto logiku je lepší mít v Service při validaci nebo použít validační skupiny/vlastní validátor
    public boolean hasRecipient() {
        return StringUtils.hasText(companyName) || (StringUtils.hasText(firstName) && StringUtils.hasText(lastName));
    }
}

// Potřebujeme import pro StringUtils, pokud ho použijeme v hasRecipient
