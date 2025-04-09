package org.example.eshop.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.example.eshop.model.EmailTemplateConfig; // Import nové entity
import org.example.eshop.model.Order;
import org.example.eshop.model.OrderState;
import org.example.eshop.repository.EmailTemplateConfigRepository; // Import nového repo
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException; // Pro setFrom
import java.util.Locale;
import java.util.Map;
import java.util.Optional; // Přidán import
import java.util.concurrent.ConcurrentHashMap;
// Lombok Getter/Setter již není potřeba pro vnořenou třídu

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired private JavaMailSender javaMailSender;
    @Autowired private TemplateEngine templateEngine;
    // *** ÚPRAVA: Injektovat repozitář pro konfiguraci emailů ***
    @Autowired private EmailTemplateConfigRepository emailTemplateConfigRepository;

    @Value("${spring.mail.username}") private String mailFrom;
    @Value("${eshop.name:Dřevníky Kolář}") private String shopName;
    @Value("${eshop.url:http://localhost:8080}") private String shopUrl;
    // TODO: Načíst email pro BCC z application.properties
    // @Value("${eshop.admin.bccEmail:}") private String adminBccEmail;

    private final Locale defaultLocale = new Locale("cs", "CZ");
    // Cache pro konfiguraci emailů (klíč = stateCode.toUpperCase())
    private final Map<String, EmailTemplateConfig> configCache = new ConcurrentHashMap<>();


    // --- Metody pro odeslání emailů (sendOrderConfirmationEmail, sendOrderStatusUpdateEmail) ---
    // ... (Metody zůstávají stejné, volají upravenou loadEmailConfigForState) ...
    @Async
    public void sendOrderConfirmationEmail(Order order) {
        if (order == null || order.getCustomer() == null || !StringUtils.hasText(order.getCustomer().getEmail())) {
            log.error("Cannot send confirmation email. Order or customer email is invalid. Order ID: {}", order != null ? order.getId() : "N/A");
            return;
        }
        if (!isMailConfigured()) return;

        String to = order.getCustomer().getEmail();
        String subject = shopName + " - Potvrzení objednávky č. " + order.getOrderCode();
        String templateName = "order-confirmation"; // Pevně daná šablona pro potvrzení

        try {
            Context context = new Context(defaultLocale);
            context.setVariable("order", order);
            context.setVariable("shopName", shopName);
            context.setVariable("shopUrl", shopUrl);
            String trackingUrl = shopUrl + "/muj-ucet/objednavky/" + order.getOrderCode();
            context.setVariable("trackingUrl", trackingUrl);

            String htmlBody = templateEngine.process(templateName, context);
            sendHtmlEmail(to, subject, htmlBody);
            log.info("Order confirmation email sent successfully to {} for order {}", to, order.getOrderCode());

        } catch (TemplateProcessingException tpe) {
            log.error("Failed to process Thymeleaf template '{}' for order confirmation {}: {}", templateName, order.getOrderCode(), tpe.getMessage(), tpe);
        } catch (Exception e) {
            log.error("Failed to send order confirmation email to {} for order {}: {}", to, order.getOrderCode(), e.getMessage(), e);
        }
    }

    @Async
    public void sendOrderStatusUpdateEmail(Order order, OrderState newState) {
        if (order == null || order.getCustomer() == null || !StringUtils.hasText(order.getCustomer().getEmail()) || newState == null) {
            log.error("Cannot send status update email. Order, customer email or new state is invalid. Order ID: {}", order != null ? order.getId() : "N/A");
            return;
        }
        if (!isMailConfigured()) return;

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
            context.setVariable("shopName", shopName);
            context.setVariable("shopUrl", shopUrl);
            String trackingUrl = shopUrl + "/muj-ucet/objednavky/" + order.getOrderCode();
            context.setVariable("trackingUrl", trackingUrl);
            // TODO: Přidat customMessageBody z konfigurace do kontextu?
            // context.setVariable("customMessageBody", config.getCustomMessageBody());

            String htmlBody = templateEngine.process(templateName, context);
            sendHtmlEmail(to, subject, htmlBody);
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
        // TODO: Posílat BCC kopii administrátorovi, pokud je nastavena
        // if (StringUtils.hasText(adminBccEmail)) {
        //    helper.setBcc(adminBccEmail);
        // }
        helper.setSubject(subject);
        helper.setText(htmlBody, true);

        javaMailSender.send(mimeMessage);
    }

    private boolean isMailConfigured() {
        if (!StringUtils.hasText(mailFrom)) {
            log.error("Cannot send email. Sender email address (spring.mail.username) is not configured.");
            return false;
        }
        return true;
    }


    /**
     * Načte konfiguraci emailu pro daný stav objednávky (z cache nebo DB).
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
}