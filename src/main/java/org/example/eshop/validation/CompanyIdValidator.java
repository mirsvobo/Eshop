package org.example.eshop.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.example.eshop.dto.CheckoutFormDataDto; // Importuj své DTO
import org.springframework.beans.BeanWrapperImpl; // Pro čtení hodnot polí dynamicky
import org.springframework.util.StringUtils;

public class CompanyIdValidator implements ConstraintValidator<RequireIcoOrDicIfCompany, CheckoutFormDataDto> {

    private String companyNameField;
    private String icoField;
    private String dicField;

    @Override
    public void initialize(RequireIcoOrDicIfCompany constraintAnnotation) {
        this.companyNameField = constraintAnnotation.companyNameField();
        this.icoField = constraintAnnotation.icoField();
        this.dicField = constraintAnnotation.dicField();
    }

    @Override
    public boolean isValid(CheckoutFormDataDto checkoutDto, ConstraintValidatorContext context) {
        if (checkoutDto == null) {
            return true; // Pokud je objekt null, validace projde (řeší @NotNull jinde)
        }

        BeanWrapperImpl beanWrapper = new BeanWrapperImpl(checkoutDto);
        String companyName = (String) beanWrapper.getPropertyValue(companyNameField);
        String ico = (String) beanWrapper.getPropertyValue(icoField);
        String dic = (String) beanWrapper.getPropertyValue(dicField);

        // Pokud není vyplněna firma, validace je OK
        if (!StringUtils.hasText(companyName)) {
            return true;
        }

        // Pokud je firma vyplněna, musí být vyplněno IČO nebo DIČ
        boolean icoPresent = StringUtils.hasText(ico);
        boolean dicPresent = StringUtils.hasText(dic);

        boolean isValid = icoPresent || dicPresent;

        // Pokud validace selže, přidáme chybovou hlášku k oběma polím (IČO i DIČ)
        // aby se zvýraznila u obou.
        if (!isValid) {
            context.disableDefaultConstraintViolation(); // Vypneme defaultní hlášku u třídy
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode(icoField) // Přidá chybu k poli IČO
                    .addConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode(dicField) // Přidá chybu k poli DIČ
                    .addConstraintViolation();
        }

        return isValid;
    }
}