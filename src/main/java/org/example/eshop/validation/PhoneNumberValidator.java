package org.example.eshop.validation;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Implementace validátoru pro anotaci @ValidPhoneNumber.
 * Používá Google libphonenumber pro ověření platnosti čísla.
 */
public class PhoneNumberValidator implements ConstraintValidator<ValidPhoneNumber, String> {

    private static final Logger log = LoggerFactory.getLogger(PhoneNumberValidator.class);
    private static final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();

    @Override
    public void initialize(ValidPhoneNumber constraintAnnotation) {
        // Není potřeba inicializace z anotace
    }

    @Override
    public boolean isValid(String phoneField, ConstraintValidatorContext context) {
        // @NotBlank řeší povinnost, zde povolíme prázdné/null
        if (!StringUtils.hasText(phoneField)) {
            return true;
        }

        try {
            // Předpokládáme, že číslo obsahuje mezinárodní předvolbu (např. +420, +421)
            // null jako defaultRegion se na to spoléhá
            Phonenumber.PhoneNumber numberProto = phoneUtil.parse(phoneField, null);

            // isValidNumber() kontroluje základní platnost formátu, délku, platnost předvolby atd.
            boolean isValid = phoneUtil.isValidNumber(numberProto);
            if (!isValid) {
                log.warn("Phone number validation failed for '{}': Not a valid number according to libphonenumber.", phoneField);
            } else {
                log.trace("Phone number validation successful for '{}'", phoneField);
            }
            return isValid;

        } catch (NumberParseException e) {
            // Chyba parsování znamená neplatné číslo
            log.warn("Phone number validation failed for '{}': Parsing error - {}", phoneField, e.getMessage());
            return false;
        } catch (Exception e) {
            // Zachycení jakýchkoli jiných neočekávaných chyb během validace
            log.error("Unexpected error during phone number validation for '{}': {}", phoneField, e.getMessage(), e);
            return false;
        }
    }
}