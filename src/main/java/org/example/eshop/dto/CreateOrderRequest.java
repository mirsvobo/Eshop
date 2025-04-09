package org.example.eshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal; // <-- Přidat import
import java.util.List;

/**
 * DTO pro vytvoření nové objednávky přes OrderService.
 */
@Data // Lombok pro gettery, settery, toString atd.
public class CreateOrderRequest {

    @NotNull(message = "ID zákazníka nesmí být null.")
    private Long customerId;

    // Příznak, zda se mají adresy brát z Customer entity (true)
    // nebo zda jsou součástí tohoto requestu (false - není implementováno)
    private boolean useCustomerAddresses = true;

    @NotBlank(message = "Způsob platby je povinný.")
    private String paymentMethod; // Např. "BANK_TRANSFER", "CASH_ON_DELIVERY"

    @NotBlank(message = "Měna objednávky je povinná.")
    @Size(min = 3, max = 3, message = "Kód měny musí mít 3 znaky.")
    private String currency; // Např. "CZK", "EUR"

    private String customerNote; // Poznámka zákazníka (volitelné)

    private String couponCode; // Použitý kód kupónu (volitelné)

    @NotEmpty(message = "Objednávka musí obsahovat alespoň jednu položku.")
    private List<CartItemDto> items; // Seznam položek v objednávce

    // --- PŘIDANÁ POLE PRO DOPRAVU ---
    @NotNull(message = "Cena dopravy bez DPH nesmí být null.")
    private BigDecimal shippingCostNoTax;

    @NotNull(message = "DPH z dopravy nesmí být null.")
    private BigDecimal shippingTax;
    // ---------------------------------

    // Konstruktory, gettery, settery... jsou generovány Lombokem (@Data)
}
