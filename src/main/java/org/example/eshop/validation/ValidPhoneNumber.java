package org.example.eshop.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validační anotace pro kontrolu platnosti telefonního čísla
 * pomocí knihovny Google libphonenumber.
 */
@Documented
@Constraint(validatedBy = PhoneNumberValidator.class) // Odkaz na validační logiku
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.PARAMETER}) // Kde lze anotaci použít
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPhoneNumber {
    String message() default "Neplatné telefonní číslo (včetně mezinárodní předvolby)"; // Výchozí chybová hláška
    Class<?>[] groups() default {}; // Validační skupiny
    Class<? extends Payload>[] payload() default {}; // Metadata
}