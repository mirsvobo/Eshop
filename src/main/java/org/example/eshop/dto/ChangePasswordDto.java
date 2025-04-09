package org.example.eshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data // Lombok pro gettery, settery, toString, equals, hashCode
public class ChangePasswordDto {

    @NotBlank(message = "Aktuální heslo nesmí být prázdné")
    private String currentPassword;

    @NotBlank(message = "Nové heslo nesmí být prázdné")
    @Size(min = 8, message = "Nové heslo musí mít alespoň 8 znaků")
    // Můžete přidat další validace (např. @Pattern pro komplexitu), pokud je potřeba
    private String newPassword;

    @NotBlank(message = "Potvrzení hesla nesmí být prázdné")
    private String confirmNewPassword;

    // Můžeme přidat validaci na úrovni třídy, aby se ověřilo, že newPassword a confirmNewPassword jsou stejné
    // Ale jednodušší je to ověřit v controlleru nebo service
}