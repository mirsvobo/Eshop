package org.example.eshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileUpdateDto {

    @NotBlank(message = "Křestní jméno nesmí být prázdné.")
    @Size(max = 50, message = "Křestní jméno je příliš dlouhé.")
    private String firstName;

    @NotBlank(message = "Příjmení nesmí být prázdné.")
    @Size(max = 50, message = "Příjmení je příliš dlouhé.")
    private String lastName;

    // Telefon může být nepovinný, ale pokud je zadán, měl by mít nějaký formát
    // Tento regex je velmi základní, povoluje čísla, mezery, +, () -
    @Pattern(regexp = "^[+]?[0-9\\s()-]{9,20}$", message = "Neplatný formát telefonního čísla.")
    @Size(max = 30, message = "Telefonní číslo je příliš dlouhé.") // Omezení délky
    private String phone; // Může být null nebo prázdný

    // Email zde neuvádíme, jeho změna je složitější proces
}