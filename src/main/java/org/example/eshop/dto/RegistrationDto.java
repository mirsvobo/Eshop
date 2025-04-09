package org.example.eshop.dto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// DTO třídy pro CustomerService (přesunout sem z CustomerService)

@Getter
@Setter
public class RegistrationDto {
    @NotBlank(message = "First name cannot be blank.")
    @Size(max = 100, message = "First name too long (max 100 chars).")
    private String firstName;

    @NotBlank(message = "Last name cannot be blank.")
    @Size(max = 100, message = "Last name too long (max 100 chars).")
    private String lastName;

    @NotBlank(message = "Email cannot be blank.")
    @Email(message = "Invalid email format.")
    private String email;

    // Telefon může být volitelný, záleží na požadavcích
    @Size(max = 30, message = "Phone number too long (max 30 chars).")
    private String phone;

    @NotBlank(message = "Password cannot be blank.")
    @Size(min = 6, message = "Password must be at least 6 characters long.")
    private String password;
}