package org.example.eshop.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.example.eshop.model.Customer;
import org.example.eshop.model.EmailTemplateConfig;
import org.example.eshop.model.Order;
import org.example.eshop.model.OrderState;
import org.example.eshop.repository.EmailTemplateConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
// Lombok Getter/Setter již není potřeba pro vnořenou třídu

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final Locale defaultLocale = Locale.forLanguageTag("cs-CZ");
    // Cache pro konfiguraci emailů (klíč = stateCode.toUpperCase())
    private final Map<String, EmailTemplateConfig> configCache = new ConcurrentHashMap<>();
    @Autowired
    private JavaMailSender javaMailSender;
    @Autowired
    private TemplateEngine templateEngine;
    // *** ÚPRAVA: Injektovat repozitář pro konfiguraci emailů ***
    @Autowired
    private EmailTemplateConfigRepository emailTemplateConfigRepository;
    @Value("${spring.mail.username}")
    private String mailFrom;
    // TODO: Načíst email pro BCC z application.properties
    // @Value("${eshop.admin.bccEmail:}") private String adminBccEmail;
    @Value("${eshop.name:Dřevníky Kolář}")
    private String shopName;
    @Value("${eshop.url:http://localhost:8080}")
    private String shopUrl;

    // --- Metody pro odeslání emailů (sendOrderConfirmationEmail, sendOrderStatusUpdateEmail) ---
    // ... (Metody zůstávají stejné, volají upravenou loadEmailConfigForState) ...
    @Async
    public void sendOrderConfirmationEmail(Order order) {
        if (order == null || order.getCustomer() == null || !StringUtils.hasText(order.getCustomer().getEmail())) {
            log.error("Cannot send confirmation email. Order or customer email is invalid. Order ID: {}", order != null ? order.getId() : "N/A");
            return;
        }
        if (isMailConfigured()) return;

        String to = order.getCustomer().getEmail();
        String subject = shopName + " - Potvrzení objednávky č. " + order.getOrderCode();
        String templateName = "emails/order-confirmation"; // Pevně daná šablona pro potvrzení

        try {
            Context context = new Context(defaultLocale);
            context.setVariable("order", order);
            setEshopVariables(order, to, subject, templateName, context);
            log.info("Order confirmation email sent successfully to {} for order {}", to, order.getOrderCode());

        } catch (TemplateProcessingException tpe) {
            log.error("Failed to process Thymeleaf template '{}' for order confirmation {}: {}", templateName, order.getOrderCode(), tpe.getMessage(), tpe);
        } catch (Exception e) {
            log.error("Failed to send order confirmation email to {} for order {}: {}", to, order.getOrderCode(), e.getMessage(), e);
        }
    }

    private void setEshopVariables(Order order, String to, String subject, String templateName, Context context) throws MessagingException, UnsupportedEncodingException {
        context.setVariable("shopName", shopName);
        context.setVariable("shopUrl", shopUrl);
        String trackingUrl = shopUrl + "/muj-ucet/objednavky/" + order.getOrderCode();
        context.setVariable("trackingUrl", trackingUrl);

        String htmlBody = templateEngine.process(templateName, context);
        sendHtmlEmail(to, subject, htmlBody);
    }

    @Async
    public void sendOrderStatusUpdateEmail(Order order, OrderState newState) {
        if (order == null || order.getCustomer() == null || !StringUtils.hasText(order.getCustomer().getEmail()) || newState == null) {
            log.error("Cannot send status update email. Order, customer email or new state is invalid. Order ID: {}", order != null ? order.getId() : "N/A");
            return;
        }
        if (isMailConfigured()) return;

        EmailTemplateConfig config = loadEmailConfigForState(newState.getCode());

        if (!config.isSendEmail()) {
            log.info("Email notification is disabled for order state '{}'. No email sent for order {}.", newState.getCode(), order.getOrderCode());
            return;
        }
        if (!StringUtils.hasText(config.getTemplateName()) || !StringUtils.hasText(config.getSubjectTemplate())) {
            log.error("Email template configuration is incomplete for state '{}'. Cannot send email for order {}.", newState.getCode(), order.getOrderCode());
            return;
        }

        String to = order.getCustomer().getEmail();
        String subject = config.getSubjectTemplate()
                .replace("{orderCode}", order.getOrderCode())
                .replace("{stateName}", newState.getName())
                .replace("{shopName}", shopName);
        String templateName = config.getTemplateName();

        try {
            Context context = new Context(defaultLocale);
            context.setVariable("order", order);
            context.setVariable("newState", newState);
            setEshopVariables(order, to, subject, templateName, context);
            log.info("Order status update email ('{}' - Template: {}) sent successfully to {} for order {}", newState.getName(), templateName, to, order.getOrderCode());

        } catch (TemplateProcessingException tpe) {
            log.error("Failed to process Thymeleaf template '{}' for status update order {}: {}", templateName, order.getOrderCode(), tpe.getMessage(), tpe);
        } catch (Exception e) {
            log.error("Failed to send order status update email ('{}') to {} for order {}: {}", newState.getName(), to, order.getOrderCode(), e.getMessage(), e);
        }
    }

    // --- Privátní Pomocné Metody ---

    private void sendHtmlEmail(String to, String subject, String htmlBody) throws MessagingException, UnsupportedEncodingException {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

        helper.setFrom(mailFrom, shopName); // Nastavit i jméno odesílatele
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);

        javaMailSender.send(mimeMessage);
    }

    private boolean isMailConfigured() {
        if (!StringUtils.hasText(mailFrom)) {
            log.error("Cannot send email. Sender email address (spring.mail.username) is not configured.");
            return true;
        }
        return false;
    }


    /**
     * Načte konfiguraci emailu pro daný stav objednávky (z cache nebo DB).
     *
     * @param stateCode Kód stavu objednávky (např. "SHIPPED").
     * @return Objekt s konfigurací emailu. Nikdy nevrací null.
     */
    private EmailTemplateConfig loadEmailConfigForState(String stateCode) {
        if (!StringUtils.hasText(stateCode)) {
            // Vrátit prázdnou konfiguraci s vypnutým odesíláním
            EmailTemplateConfig defaultConfig = new EmailTemplateConfig();
            defaultConfig.setSendEmail(false);
            return defaultConfig;
        }
        String cacheKey = stateCode.toUpperCase();

        // Zkusit načíst z cache
        EmailTemplateConfig cachedConfig = configCache.get(cacheKey);
        if (cachedConfig != null) {
            log.trace("Loaded email config for state '{}' from cache.", stateCode);
            return cachedConfig;
        }

        // *** ÚPRAVA: Načíst z databáze ***
        log.debug("Loading email config for state '{}' from database.", stateCode);
        Optional<EmailTemplateConfig> dbConfigOpt = emailTemplateConfigRepository.findByStateCodeIgnoreCase(stateCode);

        EmailTemplateConfig config = dbConfigOpt.orElseGet(() -> {
            // Pokud konfigurace pro daný stav v DB není, vytvoříme defaultní (neodesílat)
            log.warn("No specific email template config found in DB for order state code '{}'. Using default (sending disabled).", stateCode);
            EmailTemplateConfig defaultConfig = new EmailTemplateConfig();
            defaultConfig.setStateCode(cacheKey); // Uložit kód pro informaci
            defaultConfig.setSendEmail(false);
            // Můžeme zde tuto defaultní konfiguraci i uložit do DB pro příští použití? Ne, raději nechat na adminovi, aby ji vytvořil v CMS.
            return defaultConfig;
        });

        // Uložit načtenou (nebo defaultní) konfiguraci do cache
        configCache.put(cacheKey, config);
        log.debug("Loaded and cached email config for state '{}'. SendEmail: {}", stateCode, config.isSendEmail());

        return config;
    }

    // *** ODSTRANĚNO: Vnořená třída EmailTemplateConfig ***

    /**
     * Vymaže interní cache konfigurací emailů.
     * Voláno z OrderStateService po úpravě stavů.
     */
    public void clearConfigCache() {
        log.info("Clearing email template configuration cache.");
        configCache.clear();
    }
    /**
     * Odešle email zákazníkovi obsahující externí poznámku k objednávce.
     * Email se odešle pouze pokud je poznámka vyplněna.
     * Metoda nyní přijímá pouze potřebná data, ne celou entitu Order.
     *
     * @param customerEmail Email zákazníka.
     * @param customerFirstName Křestní jméno zákazníka.
     * @param orderCode Kód objednávky.
     * @param externalNote Text externí poznámky.
     * @param locale Locale pro email (ovlivňuje formátování).
     */
    @Async
    public void sendOrderExternalNoteEmail(String customerEmail, String customerFirstName, String orderCode, String externalNote, Locale locale) {
        // Kontrola základních parametrů
        if (!StringUtils.hasText(customerEmail) || !StringUtils.hasText(orderCode) || !StringUtils.hasText(externalNote)) {
            log.warn("Cannot send external note email. Required parameters (email, orderCode, externalNote) are missing or empty. Order Code: {}", orderCode != null ? orderCode : "N/A");
            return;
        }
        if (isMailConfigured()) { // isMailConfigured() kontroluje nastavení SMTP
            log.warn("Mail is not configured, skipping external note email for order {}.", orderCode);
            return;
        }

        String to = customerEmail;
        // Předmět emailu - můžeš ho upravit dle potřeby
        String subject = shopName + " - Důležitá informace k objednávce č. " + orderCode;
        // Název šablony je pevně daný
        String templateName = "emails/order-external-note";

        try {
            Context context = new Context(locale != null ? locale : defaultLocale);
            // Předáme pouze potřebné proměnné do šablony
            context.setVariable("orderCode", orderCode);
            context.setVariable("customerFirstName", "Vážený zákazníku"); // Předáme jméno pro oslovení
            context.setVariable("externalNote", externalNote); // Předáme text poznámky
            context.setVariable("shopName", shopName);
            context.setVariable("shopUrl", shopUrl);
            // Tracking URL nyní sestavíme přímo zde
            String trackingUrl = shopUrl + "/muj-ucet/objednavky/" + orderCode;
            context.setVariable("trackingUrl", trackingUrl);


            String htmlBody = templateEngine.process(templateName, context);
            sendHtmlEmail(to, subject, htmlBody); // sendHtmlEmail zůstává stejná
            log.info("External note email sent successfully to {} for order {}", to, orderCode);

        } catch (TemplateProcessingException tpe) {
            log.error("Failed to process Thymeleaf template '{}' for external note email (Order {}): {}", templateName, orderCode, tpe.getMessage(), tpe);
        } catch (Exception e) {
            log.error("Failed to send external note email to {} for order {}: {}", to, orderCode, e.getMessage(), e);
        }
    }
    /**
     * Odešle notifikační email administrátorovi o nové zprávě od zákazníka.
     *
     * @param adminEmail       Email administrátora (kam se má email poslat).
     * @param orderCode        Kód objednávky.
     * @param customerEmail    Email zákazníka, který zprávu poslal.
     * @param customerName     Jméno zákazníka.
     * @param messageContent   Obsah zprávy od zákazníka.
     */
    @Async
    public void sendAdminNotificationEmail(String adminEmail, String orderCode, String customerEmail, String customerName, String messageContent) {
        if (!StringUtils.hasText(adminEmail) || !StringUtils.hasText(orderCode) || !StringUtils.hasText(messageContent)) {
            log.warn("Cannot send admin notification email. Admin email, order code, or message content is missing. Order Code: {}", orderCode != null ? orderCode : "N/A");
            return;
        }
        if (isMailConfigured()) {
            log.warn("Mail is not configured, skipping admin notification email for order {}.", orderCode);
            return;
        }

        String subject = shopName + " - Nová zpráva od zákazníka k objednávce č. " + orderCode;
        String templateName = "emails/new-customer-message-admin-notification"; // Nová šablona

        try {
            Context context = new Context(defaultLocale);
            context.setVariable("orderCode", orderCode);
            context.setVariable("customerEmail", customerEmail);
            context.setVariable("customerName", customerName != null ? customerName : customerEmail); // Fallback na email
            context.setVariable("messageContent", messageContent);
            context.setVariable("shopName", shopName);
            context.setVariable("adminOrderDetailUrl", shopUrl + "/admin/orders/" + orderCode); // Odkaz do adminu

            String htmlBody = templateEngine.process(templateName, context);
            sendHtmlEmail(adminEmail, subject, htmlBody);
            log.info("Admin notification email sent successfully to {} for order {}", adminEmail, orderCode);

        } catch (Exception e) {
            log.error("Failed to send admin notification email to {} for order {}: {}", adminEmail, orderCode, e.getMessage(), e);
        }
    }
    /**
     * Odešle email s odkazem pro resetování hesla zákazníkovi.
     *
     * @param customer Zákazník, kterému se má email poslat.
     * @param token Unikátní token pro reset hesla.
     */
    @Async // Doporučeno pro odesílání emailů, aby neblokovalo hlavní vlákno
    public void sendPasswordResetEmail(Customer customer, String token) {
        if (customer == null || !StringUtils.hasText(customer.getEmail()) || !StringUtils.hasText(token)) {
            log.error("Cannot send password reset email. Customer, email or token is missing. Customer ID: {}",
                    (customer != null ? customer.getId() : "N/A"));
            return;
        }
        if (isMailConfigured()) { // Použij tvou existující metodu pro kontrolu konfigurace SMTP
            log.warn("Mail is not configured, skipping password reset email for {}.", customer.getEmail());
            return;
        }

        String recipientEmail = customer.getEmail();
        String subject = shopName + " - Resetování hesla";
        String templateName = "emails/password-reset-email"; // Název Thymeleaf šablony

        try {
            // Sestavení odkazu pro reset hesla
            // Je bezpečnější token URL-encodovat, i když UUID by nemělo obsahovat problematické znaky
            String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8.toString());
            String resetUrl = UriComponentsBuilder.fromHttpUrl(shopUrl) // shopUrl z @Value
                    .path("/resetovat-heslo")
                    .queryParam("token", encodedToken) // Použijeme enkodovaný token
                    .build()
                    .toUriString();

            log.debug("Generated password reset URL for {}: {}", recipientEmail, resetUrl);

            Context context = new Context(defaultLocale); // defaultLocale by mělo být definováno ve třídě
            context.setVariable("customerName", customer.getFirstName()); // Jméno pro oslovení
            context.setVariable("resetUrl", resetUrl); // Odkaz pro šablonu
            context.setVariable("shopName", shopName); // Název obchodu
            context.setVariable("shopUrl", shopUrl);   // Adresa obchodu

            String htmlBody = templateEngine.process(templateName, context);

            // Použijeme existující privátní metodu pro odeslání
            sendHtmlEmail(recipientEmail, subject, htmlBody);

            log.info("Password reset email sent successfully to {} (Token: ...{})", recipientEmail, token.substring(Math.max(0, token.length() - 6)));

        } catch (TemplateProcessingException tpe) {
            log.error("Failed to process Thymeleaf template '{}' for password reset email to {}: {}",
                    templateName, recipientEmail, tpe.getMessage(), tpe);
            // Zde můžeš přidat specifické zpracování chyby šablony
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send password reset email to {}: {}", recipientEmail, e.getMessage(), e);
            // Zde můžeš přidat specifické zpracování chyby odeslání (např. opakování)
        } catch (Exception e) {
            // Zachycení jakýchkoli jiných neočekávaných chyb
            log.error("Unexpected error sending password reset email to {}: {}", recipientEmail, e.getMessage(), e);
        }
    }
}