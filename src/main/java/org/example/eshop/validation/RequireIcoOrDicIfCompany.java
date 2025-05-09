package org.example.eshop.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = CompanyIdValidator.class) // Odkaz na logiku validátoru
@Target({ ElementType.TYPE }) // Anotace se použije na třídu
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireIcoOrDicIfCompany {
    String message() default "Pokud vyplňujete název firmy, musíte zadat IČO nebo DIČ."; // Chybová hláška
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    // Názvy polí, které se mají kontrolovat (pro flexibilitu)
    String companyNameField() default "invoiceCompanyName";
    String icoField() default "invoiceTaxId";
    String dicField() default "invoiceVatId";
}