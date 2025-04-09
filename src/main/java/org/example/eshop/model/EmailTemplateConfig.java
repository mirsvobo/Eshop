package org.example.eshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
// Tabulka pro konfiguraci emailových notifikací pro jednotlivé stavy objednávky
public class EmailTemplateConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Kód stavu objednávky (např. "SHIPPED", "PROCESSING"), na který se konfigurace vztahuje.
    // Měl by být unikátní.
    @Column(nullable = false, unique = true, length = 50)
    private String stateCode;

    // Má se pro tento stav posílat email zákazníkovi?
    @Column(nullable = false)
    private boolean sendEmail = false;

    // Název Thymeleaf šablony (bez .html), např. "emails/order-status-shipped"
    @Column(length = 100)
    private String templateName;

    // Vzor pro předmět emailu. Může obsahovat zástupné symboly.
    // Např. "{shopName} - Vaše objednávka č. {orderCode} byla odeslána"
    @Column(length = 255)
    private String subjectTemplate;

    // Volitelná poznámka pro administrátora v CMS
    private String description;

    // TODO: Zvážit přidání pole pro BCC (např. email administrátora)
    // private String bccEmail;

    // TODO: Zvážit přidání pole pro vlastní text zprávy z CMS, který by se vložil do šablony
    // @Lob @Column(columnDefinition = "TEXT")
    // private String customMessageBody;
}