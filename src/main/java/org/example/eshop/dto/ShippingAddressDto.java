package org.example.eshop.dto;

import jakarta.validation.constraints.NotBlank; // Pokud chceme validovat i zde
import lombok.Data;

@Data // Lombok pro gettery/settery
public class ShippingAddressDto {
    // Potřebujeme jen pole relevantní pro výpočet dopravy
    @NotBlank
    private String street;
    @NotBlank
    private String city;
    @NotBlank
    private String zipCode;
    @NotBlank
    private String country;
    // Měna, ve které chceme cenu vrátit (nepovinné, můžeme určit podle země)
    // private String currency;
}