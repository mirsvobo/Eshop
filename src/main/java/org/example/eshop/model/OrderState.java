package org.example.eshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.util.List;

@Getter
@Setter
@Entity
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@Table(name = "order_state", indexes = { // Přidána anotace @Table
        @Index(name = "idx_orderstate_code", columnList = "code", unique = true),
        @Index(name = "idx_orderstate_displayorder", columnList = "displayOrder")
})
public class OrderState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Krátký kód stavu pro interní použití a logiku
    @Column(nullable = false, unique = true, length = 50)
    private String code; // Např. "NEW", "AWAITING_DEPOSIT", "AWAITING_PAYMENT", "PROCESSING", "IN_PRODUCTION", "AT_ZINC_PLATING", "SHIPPED", "DELIVERED", "CANCELLED"

    // Název stavu pro zobrazení zákazníkovi a v CMS
    @Column(nullable = false, length = 100)
    private String name; // Např. "Nová", "Čeká na zálohu", "Čeká na platbu", "Zpracovává se", "Ve výrobě", "V zinkovně", "Odesláno", "Doručeno", "Zrušeno"

    @Column(length = 255)
    private String description; // Interní popis účelu stavu

    // Vazba na objednávky v tomto stavu (pro CMS/reporty)
    @OneToMany(mappedBy = "stateOfOrder", fetch = FetchType.LAZY)
    private List<Order> orders;

    private Integer displayOrder = 0; // Pořadí pro zobrazení v select boxech v CMS

    // Příznak, zda je stav konečný (objednávka je považována za uzavřenou)
    @Column(nullable = false)
    private boolean isFinalState = false; // Např. true pro DELIVERED, CANCELLED
}