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
import org.springframework.beans.factory.annotation.Value; // <-- Ujistěte se, že tento import existuje
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
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

@Service
@EnableAsync
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final Locale defaultLocale = Locale.forLanguageTag("cs-CZ");
    // Cache pro konfiguraci emailů (klíč = stateCode.toUpperCase())
    private final Map<String, EmailTemplateConfig> configCache = new ConcurrentHashMap<>();

    @Autowired
    private JavaMailSender javaMailSender;

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private EmailTemplateConfigRepository emailTemplateConfigRepository;

    @Value("${spring.mail.username}")
    private String mailFrom;

    @Value("${eshop.name:Dřevníky Kolář}")
    private String shopName;

    @Value("${eshop.url:https://www.drevniknamiru.cz}")
    private String shopUrl; // Tento baseUrl se použije pro ostatní emaily

    @Value("${eshop.replyToEmail:info@drevniky-kolar.cz}")
    private String replyToEmail;

    // --- ZAČÁTEK ZMĚN ---
    @Value("${gcs.bucket.name}") // Načtení názvu bucketu z application.properties
    private String bucketName;
    // --- KONEC ZMĚN ---

    // @Value("${eshop.admin.bccEmail:}") private String adminBccEmail;

    /**
     * Odešle potvrzovací email o nové objednávce zákazníkovi.
     * Používá šablonu 'emails/order-confirmation-new.html'.
     * --- UPRAVENO: Přijímá isGuest a baseUrl ---
     * @param order Objekt objednávky.
     * @param isGuest Příznak, zda je zákazník host.
     * @param baseUrl Základní URL aplikace (předáno z OrderService).
     */
    @Async
    // --- ZMĚNA: Přidány parametry isGuest a baseUrl ---
    public void sendOrderConfirmationEmail(Order order, boolean isGuest, String baseUrl) {
        String orderCode = (order != null && order.getOrderCode() != null) ? order.getOrderCode() : "N/A";
        // --- ZMĚNA: Přidáno logování nových parametrů ---
        log.debug("Attempting to send order confirmation email for order {} (isGuest: {}, baseUrl: {}).", orderCode, isGuest, baseUrl);

        if (order == null || order.getCustomer() == null || !StringUtils.hasText(order.getCustomer().getEmail()) || !StringUtils.hasText(baseUrl)) {
            log.error("Cannot send confirmation email for order {}. Order, customer email, or baseUrl is invalid.", orderCode);
            return;
        }
        if (!isMailConfigured()) {
            log.warn("Mail sending skipped for order confirmation {}: Mail is not configured.", orderCode);
            return;
        }

        String to = order.getCustomer().getEmail();
        String templateName = "emails/order-confirmation-new";
        String subject = shopName + " - Potvrzení objednávky č. " + orderCode;

        try {
            Context context = new Context(defaultLocale);
            context.setVariable("order", order);
            String currencySymbol = "EUR".equals(order.getCurrency()) ? "€" : "Kč";
            context.setVariable("currentGlobalCurrency", order.getCurrency()); // Není nutné, ale může zůstat
            context.setVariable("currencySymbol", currencySymbol); // Není nutné, ale může zůstat

            // --- ZMĚNA: Přidání isGuest a baseUrl do kontextu ---
            context.setVariable("isGuest", isGuest);
            context.setVariable("baseUrl", baseUrl); // Přidáme baseUrl získaný z OrderService
            // --------------------------------------------------

            log.debug("Context prepared for template '{}', order {}, isGuest: {}", templateName, orderCode, isGuest);
            // Volání metody, která nastaví OSTATNÍ (společné) proměnné a odešle email
            // Nepotřebujeme jí předávat isGuest a baseUrl, ty už jsou v contextu
            setEshopVariablesAndSend(order, to, subject, templateName, context);

            // Log o úspěchu se přesunul do setEshopVariablesAndSend

        } catch (TemplateProcessingException tpe) {
            // Logováno uvnitř setEshopVariablesAndSend
        } catch (MessagingException | MailException me) {
            // Logováno uvnitř setEshopVariablesAndSend nebo sendHtmlEmail
        } catch (Exception e) {
            log.error("Unexpected error during sendOrderConfirmationEmail preparation for order {}: {}", orderCode, e.getMessage(), e);
        }
    }

    /**
     * Odešle email o změně stavu objednávky zákazníkovi.
     * Použije šablonu a předmět definované v EmailTemplateConfig pro daný stav.
     * @param order Objekt objednávky.
     * @param newState Nový stav objednávky.
     */
    @Async
    public void sendOrderStatusUpdateEmail(Order order, OrderState newState) {
        String orderCode = (order != null && order.getOrderCode() != null) ? order.getOrderCode() : "N/A";
        String stateCode = (newState != null && newState.getCode() != null) ? newState.getCode() : "N/A";
        log.debug("Attempting to send status update email for order {} to state {}", orderCode, stateCode);

        if (order == null || order.getCustomer() == null || !StringUtils.hasText(order.getCustomer().getEmail()) || newState == null || !StringUtils.hasText(newState.getCode())) {
            log.error("Cannot send status update email for order {}. Order, customer email or new state/code is invalid.", orderCode);
            return;
        }
        if (!isMailConfigured()) {
            log.warn("Mail sending skipped for status update {} (order {}): Mail is not configured.", stateCode, orderCode);
            return;
        }

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
            // Můžeme zde přidat i baseUrl, pokud ho šablona potřebuje (použijeme @Value shopUrl)
            // context.setVariable("baseUrl", this.shopUrl);

            log.debug("Context prepared for template '{}', order {}, new state {}", templateName, orderCode, stateCode);
            // Volání metody, která nastaví SPOLEČNÉ proměnné (shopName, logoUrl, trackingUrl) a odešle email
            setEshopVariablesAndSend(order, to, subject, templateName, context);

            // Log o úspěchu se přesunul do setEshopVariablesAndSend

        } catch (TemplateProcessingException tpe) {
            // Logováno uvnitř setEshopVariablesAndSend
        } catch (MessagingException | MailException me) {
            // Logováno uvnitř setEshopVariablesAndSend nebo sendHtmlEmail
        } catch (Exception e) {
            log.error("Unexpected error during sendOrderStatusUpdateEmail preparation for order {}, state {}: {}", orderCode, stateCode, e.getMessage(), e);
        }
    }

    // --- Ostatní metody pro odesílání emailů (sendOrderExternalNoteEmail, sendAdminNotificationEmail, sendPasswordResetEmail, sendNewOrderAdminNotification) ---
    // Tyto metody mohou zůstat beze změny, pokud nepotřebují specificky 'isGuest' a používají 'this.shopUrl' pro generování odkazů.
    // Metoda setEshopVariablesAndSend se o nastavení shopName, logoUrl, trackingUrl (pokud je order != null) postará.

    @Async
    public void sendOrderExternalNoteEmail(String customerEmail, String orderCode, String externalNote, Locale locale) {
        log.debug("Attempting to send external note email for order {}", orderCode);
        if (!StringUtils.hasText(customerEmail) || !StringUtils.hasText(orderCode) || !StringUtils.hasText(externalNote)) {
            log.warn("Cannot send external note email for order {}. Required parameters missing.", orderCode != null ? orderCode : "N/A");
            return;
        }
        if (!isMailConfigured()) {
            log.warn("Mail sending skipped for external note (order {}): Mail is not configured.", orderCode);
            return;
        }

        String to = customerEmail;
        String subject = shopName + " - Důležitá informace k objednávce č. " + orderCode;
        String templateName = "emails/new-external-message";

        try {
            Context context = new Context(locale != null ? locale : defaultLocale);
            context.setVariable("orderCode", orderCode);
            context.setVariable("externalNote", externalNote);

            // Volání metody, která nastaví SPOLEČNÉ proměnné a odešle email
            // 'order' je null, takže trackingUrl se nastaví na shopUrl
            setEshopVariablesAndSend(null, to, subject, templateName, context);

            // Log o úspěchu se přesunul do setEshopVariablesAndSend

        } catch (TemplateProcessingException tpe) {
            // Logováno uvnitř setEshopVariablesAndSend
        } catch (MessagingException | MailException me) {
            // Logováno uvnitř setEshopVariablesAndSend nebo sendHtmlEmail
        } catch (Exception e) {
            log.error("!!! Unexpected error sending external note email to {} for order {}: ", to, orderCode, e);
        }
    }

    @Async
    public void sendAdminNotificationEmail(String adminEmail, String orderCode, String customerEmail, String customerName, String messageContent) {
        log.debug("Attempting to send admin notification email for order {}", orderCode);
        if (!StringUtils.hasText(adminEmail) || !StringUtils.hasText(orderCode) || !StringUtils.hasText(messageContent)) {
            log.warn("Cannot send admin notification email for order {}. Admin email, order code, or message content is missing.", orderCode != null ? orderCode : "N/A");
            return;
        }
        if (!isMailConfigured()) {
            log.warn("Mail sending skipped for admin notification (order {}): Mail is not configured.", orderCode);
            return;
        }

        String subject = shopName + " - Nová zpráva od zákazníka k objednávce č. " + orderCode;
        String templateName = "emails/new-customer-message-admin-notification";

        try {
            Context context = new Context(defaultLocale);
            context.setVariable("orderCode", orderCode);
            context.setVariable("customerEmail", customerEmail);
            context.setVariable("customerName", customerName != null ? customerName : customerEmail);
            context.setVariable("messageContent", messageContent);

            String adminOrderListUrl = UriComponentsBuilder.fromHttpUrl(this.shopUrl) // Použijeme this.shopUrl
                    .path("/admin/orders")
                    .queryParam("customerEmail", customerEmail)
                    .build().toUriString();
            context.setVariable("adminOrderDetailUrl", adminOrderListUrl);

            // Volání metody, která nastaví SPOLEČNÉ proměnné (shopName, logoUrl) a odešle email
            // 'order' je null, trackingUrl nebude relevantní
            setEshopVariablesAndSend(null, adminEmail, subject, templateName, context);

            // Log o úspěchu se přesunul do setEshopVariablesAndSend

        } catch (TemplateProcessingException tpe) {
            // Logováno uvnitř setEshopVariablesAndSend
        } catch (MessagingException | MailException me) {
            // Logováno uvnitř setEshopVariablesAndSend nebo sendHtmlEmail
        } catch (Exception e) {
            log.error("!!! Unexpected error sending admin notification email to {} for order {}: ", adminEmail, orderCode, e);
        }
    }


    @Async
    public void sendPasswordResetEmail(Customer customer, String token) {
        String customerEmail = (customer != null && customer.getEmail() != null) ? customer.getEmail() : "N/A";
        log.debug("Attempting to send password reset email to {}", customerEmail);
        if (customer == null || !StringUtils.hasText(customer.getEmail()) || !StringUtils.hasText(token)) {
            log.error("Cannot send password reset email. Customer, email or token is missing. Customer ID: {}",
                    (customer != null ? customer.getId() : "N/A"));
            return;
        }
        if (!isMailConfigured()) {
            log.warn("Mail sending skipped for password reset for {}: Mail is not configured.", customerEmail);
            return;
        }

        String recipientEmail = customer.getEmail();
        String subject = shopName + " - Resetování hesla";
        String templateName = "emails/password-reset-email";

        try {
            String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
            String resetUrl = UriComponentsBuilder.fromHttpUrl(this.shopUrl) // Použijeme this.shopUrl
                    .path("/resetovat-heslo")
                    .queryParam("token", encodedToken)
                    .build()
                    .toUriString();

            log.debug("Generated password reset URL for {}: {}", recipientEmail, resetUrl);

            Context context = new Context(defaultLocale);
            context.setVariable("resetUrl", resetUrl);

            // Volání metody, která nastaví SPOLEČNÉ proměnné (shopName, logoUrl) a odešle email
            // 'order' je null, trackingUrl nebude relevantní
            setEshopVariablesAndSend(null, recipientEmail, subject, templateName, context);

            // Log o úspěchu se přesunul do setEshopVariablesAndSend

        } catch (TemplateProcessingException tpe) {
            // Logováno uvnitř setEshopVariablesAndSend
        } catch (MessagingException | MailException me) {
            // Logováno uvnitř setEshopVariablesAndSend nebo sendHtmlEmail
        } catch (UnsupportedEncodingException uee) {
            log.error("!!! ERROR encoding password reset token for email to {}: ", recipientEmail, uee);
        } catch (Exception e) {
            log.error("!!! Unexpected error sending password reset email to {}: ", recipientEmail, e);
        }
    }

    @Async
    public void sendNewOrderAdminNotification(Order order, String adminEmail) {
        String orderCode = (order != null && order.getOrderCode() != null) ? order.getOrderCode() : "N/A";
        log.debug("Attempting to send new order admin notification email for order {}.", orderCode);
        if (order == null || !StringUtils.hasText(adminEmail)) {
            log.error("Cannot send new order admin notification for order {}. Order is null or admin email is missing.", orderCode);
            return;
        }
        if (!isMailConfigured()) {
            log.warn("Mail sending skipped for new order admin notification (order {}): Mail is not configured.", orderCode);
            return;
        }

        String subject = shopName + " - Nová objednávka č. " + orderCode;
        String templateName = "emails/order-confirmation-admin";

        try {
            Context context = new Context(defaultLocale);
            context.setVariable("order", order); // Předáme celou objednávku šabloně

            // Volání metody, která nastaví SPOLEČNÉ proměnné (shopName, logoUrl, trackingUrl) a odešle email
            setEshopVariablesAndSend(order, adminEmail, subject, templateName, context);

            // Log o úspěchu se přesunul do setEshopVariablesAndSend

        } catch (TemplateProcessingException tpe) {
            // Logováno uvnitř setEshopVariablesAndSend
        } catch (MessagingException | MailException me) {
            // Logováno uvnitř setEshopVariablesAndSend nebo sendHtmlEmail
        } catch (Exception e) {
            log.error("!!! Unexpected error sending new order admin notification to {} for order {}: ", adminEmail, orderCode, e);
        }
    }


    // --- Privátní Pomocné Metody ---

    /**
     * Nastaví základní proměnné eshopu (shopName, shopUrl, logoUrl) do kontextu.
     * @param context Thymeleaf context.
     */
    private void setEshopVariables(Context context) {
        context.setVariable("shopName", shopName);
        context.setVariable("shopUrl", this.shopUrl); // Použijeme hodnotu z @Value

        // Konstrukce URL loga z GCS
        String gcsLogoPath = "images/logo.webp"; // Cesta k logu ve vašem bucketu
        String logoUrl = String.format("https://storage.googleapis.com/%s/%s", bucketName, gcsLogoPath);
        context.setVariable("logoUrl", logoUrl);
        log.trace("Common eshop variables set. Logo URL: {}", logoUrl);
    }

    /**
     * Nastaví základní proměnné eshopu (shopName, shopUrl, logoUrl) a trackingUrl (pokud je objednávka)
     * do kontextu a poté odešle email.
     * @param order Objednávka (pro získání kódu a URL pro tracking). Může být null.
     * @param to Příjemce.
     * @param subject Předmět.
     * @param templateName Název Thymeleaf šablony.
     * @param context Thymeleaf context (již může obsahovat specifické proměnné).
     * @throws TemplateProcessingException Pokud selže zpracování šablony.
     * @throws MessagingException Pokud selže sestavení MimeMessage.
     * @throws MailException Pokud selže odeslání přes JavaMailSender.
     * @throws UnsupportedEncodingException Pokud selže kódování adresy (z sendHtmlEmail).
     */
    private void setEshopVariablesAndSend(Order order, String to, String subject, String templateName, Context context)
            throws TemplateProcessingException, MessagingException, MailException, UnsupportedEncodingException {

        String orderCode = (order != null && order.getOrderCode() != null) ? order.getOrderCode() : "N/A";
        log.debug("Setting common variables and preparing to send email for template: '{}', order: {}", templateName, orderCode);

        // Nastavení základních proměnných (včetně logoUrl)
        setEshopVariables(context);

        // Nastavení trackingUrl POUZE pokud máme objednávku a kód
        if (order != null && order.getOrderCode() != null) {
            String trackingUrl = UriComponentsBuilder.fromHttpUrl(this.shopUrl) // Použijeme this.shopUrl
                    .path("/muj-ucet/objednavky/")
                    .path(order.getOrderCode()) // Přidáme kód objednávky
                    .build().toUriString();
            context.setVariable("trackingUrl", trackingUrl);
            log.debug("Tracking URL set to: {}", trackingUrl);
        } else {
            // Pokud nemáme objednávku, trackingUrl nemá smysl nebo ukazuje na obecnou stránku
            context.setVariable("trackingUrl", this.shopUrl + "/muj-ucet/objednavky"); // Odkaz na seznam objednávek
            log.debug("Tracking URL set to general orders page (no specific order code)");
        }

        String htmlBody;
        try {
            log.debug("Attempting to process Thymeleaf template: '{}' for order {}", templateName, orderCode);
            htmlBody = templateEngine.process(templateName, context);
            log.debug("Successfully processed Thymeleaf template: '{}' for order {}", templateName, orderCode);
        } catch (TemplateProcessingException tpe) {
            log.error("!!! ERROR processing template '{}' for order {}: ", templateName, orderCode, tpe);
            throw tpe; // Propagate exception
        } catch (Exception e) {
            log.error("!!! Unexpected error during template processing for template '{}', order {}: ", templateName, orderCode, e);
            throw new RuntimeException("Unexpected template processing error", e);
        }

        // Odeslání emailu
        sendHtmlEmail(to, subject, htmlBody);
        // Přesunuté logování úspěchu sem
        log.info("Email sending initiated successfully via sendHtmlEmail for template '{}', recipient {}, order {}", templateName, to, orderCode);
    }


    /**
     * Interní metoda pro odeslání HTML emailu s nastavením Reply-To.
     * Nyní zahrnuje robustnější logování a zachytávání chyb.
     * @param to Adresa příjemce.
     * @param subject Předmět emailu.
     * @param htmlBody Tělo emailu ve formátu HTML.
     * @throws MessagingException Pokud nastane chyba při sestavování MimeMessage.
     * @throws MailException Pokud nastane chyba při odesílání přes JavaMailSender.
     * @throws UnsupportedEncodingException Pokud selže kódování jména odesílatele.
     */
    private void sendHtmlEmail(String to, String subject, String htmlBody)
            throws MessagingException, MailException, UnsupportedEncodingException {

        log.debug("Preparing MimeMessage to: {}, subject: {}", to, subject);
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper;

        try {
            helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name()); // multipart=true, encoding=UTF-8

            helper.setFrom(mailFrom, shopName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = HTML

            if (StringUtils.hasText(replyToEmail)) {
                helper.setReplyTo(replyToEmail);
                log.debug("Setting Reply-To header to: {}", replyToEmail);
            } else {
                log.warn("Reply-To email address is not configured (eshop.replyToEmail). Skipping Reply-To header.");
            }
            // TODO: Add BCC if configured
            // if (StringUtils.hasText(adminBccEmail)) helper.setBcc(adminBccEmail);

        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("!!! ERROR preparing MimeMessage headers/content for recipient {}: ", to, e);
            throw e; // Propagate exception
        }

        try {
            log.debug("Attempting to send email via javaMailSender to {}...", to);
            javaMailSender.send(mimeMessage);
            log.debug("Email successfully sent (request accepted by MailSender) to: {}", to);
        } catch (MailException me) {
            log.error("!!! ERROR sending email via javaMailSender to {}: ", to, me);
            // Propagate the specific MailException (includes subtypes like AuthenticationFailedException, MailSendException etc.)
            throw me;
        } catch (Exception e) {
            log.error("!!! Unexpected ERROR during javaMailSender.send() to {}: ", to, e);
            // Wrap unexpected errors
            throw new RuntimeException("Unexpected error during email sending", e);
        }
    }

    /**
     * Zkontroluje, zda je nakonfigurován email odesílatele.
     * @return true, pokud je email nakonfigurován, jinak false.
     */
    private boolean isMailConfigured() {
        if (!StringUtils.hasText(mailFrom)) {
            log.error("Mail sending check FAILED: Sender email address (spring.mail.username) is not configured.");
            return false; // Mail NENÍ nakonfigurován
        }
        log.trace("Mail sending check PASSED: Sender email address is configured.");
        return true; // Mail JE nakonfigurován
    }

    /**
     * Načte konfiguraci emailu pro daný stav objednávky (z cache nebo DB).
     * @param stateCode Kód stavu objednávky (např. "SHIPPED").
     * @return Objekt s konfigurací emailu. Nikdy nevrací null.
     */
    private EmailTemplateConfig loadEmailConfigForState(String stateCode) {
        if (!StringUtils.hasText(stateCode)) {
            log.warn("loadEmailConfigForState called with empty stateCode. Returning default (disabled).");
            EmailTemplateConfig defaultConfig = new EmailTemplateConfig();
            defaultConfig.setSendEmail(false);
            return defaultConfig;
        }
        String cacheKey = stateCode.toUpperCase();

        EmailTemplateConfig cachedConfig = configCache.get(cacheKey);
        if (cachedConfig != null) {
            log.trace("Loaded email config for state '{}' from cache.", stateCode);
            return cachedConfig;
        }

        log.debug("Loading email config for state '{}' from database.", stateCode);
        Optional<EmailTemplateConfig> dbConfigOpt = emailTemplateConfigRepository.findByStateCodeIgnoreCase(stateCode);

        EmailTemplateConfig config = dbConfigOpt.orElseGet(() -> {
            log.warn("No specific email template config found in DB for order state code '{}'. Using default (sending disabled).", stateCode);
            EmailTemplateConfig defaultConfig = new EmailTemplateConfig();
            defaultConfig.setStateCode(cacheKey);
            defaultConfig.setSendEmail(false);
            return defaultConfig;
        });

        configCache.put(cacheKey, config);
        log.debug("Loaded and cached email config for state '{}'. SendEmail: {}", stateCode, config.isSendEmail());
        return config;
    }

    /**
     * Vymaže interní cache konfigurací emailů.
     * Voláno např. z OrderStateService po úpravě stavů nebo z AdminEmailConfigController.
     */
    public void clearConfigCache() {
        log.info("Clearing email template configuration cache.");
        configCache.clear();
    }
}