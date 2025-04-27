package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.model.*;
import org.example.eshop.repository.ConversationRepository;
import org.example.eshop.repository.MessageRepository;
import org.example.eshop.repository.OrderRepository;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CustomerService customerService; // Pro získání jména zákazníka
    // @Autowired private AdminUserService adminUserService; // Pokud máš service pro admin uživatele

    // Lazy inject EmailService to avoid potential circular dependencies
    @Autowired
    @Lazy
    private EmailService emailService;

    /**
     * Získá nebo vytvoří konverzaci daného typu pro objednávku.
     * @param orderId ID objednávky.
     * @param type Typ konverzace (INTERNAL nebo EXTERNAL).
     * @return Objekt Conversation.
     */
    @Transactional
    public Conversation getOrCreateConversation(Long orderId, Conversation.ConversationType type) {
        log.debug("Getting or creating conversation for order ID {} and type {}", orderId, type);
        return conversationRepository.findByOrderIdAndType(orderId, type)
                .orElseGet(() -> {
                    log.info("Conversation of type {} not found for order ID {}. Creating new one.", type, orderId);
                    Order order = orderRepository.findById(orderId)
                            .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId + " when creating conversation."));
                    Conversation newConversation = new Conversation();
                    newConversation.setOrder(order);
                    newConversation.setType(type);
                    return conversationRepository.save(newConversation);
                });
    }

    /**
     * Přidá novou zprávu do konverzace.
     * @param conversationId ID konverzace.
     * @param content Obsah zprávy.
     * @param senderType Typ odesílatele (CUSTOMER nebo ADMIN).
     * @param senderId ID odesílatele (ID zákazníka nebo admina, může být null).
     * @param senderName Zobrazované jméno odesílatele.
     * @return Uložená zpráva.
     */
    @Transactional
    public Message addMessage(Long conversationId, String content, Message.SenderType senderType, Long senderId, String senderName) {
        log.info("Adding new message to conversation ID {}. Sender: {} ({}), Type: {}", conversationId, senderName, senderId, senderType);
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("Message content cannot be empty.");
        }
        if (senderType == null || !StringUtils.hasText(senderName)) {
            throw new IllegalArgumentException("Sender type and name must be provided.");
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new EntityNotFoundException("Conversation not found: " + conversationId));

        Message message = new Message();
        message.setConversation(conversation);
        message.setContent(content.trim());
        message.setSenderType(senderType);
        message.setSenderId(senderId);
        message.setSenderName(senderName);
        message.setSentAt(LocalDateTime.now()); // Explicitní nastavení pro jistotu

        // Nastavení příznaků přečtení
        if (senderType == Message.SenderType.ADMIN) {
            message.setReadByAdmin(true);
            message.setReadByCustomer(false); // Nová zpráva od admina není přečtena zákazníkem
        } else { // SenderType.CUSTOMER
            message.setReadByAdmin(false); // Nová zpráva od zákazníka není přečtena adminem
            message.setReadByCustomer(true); // Zákazník svou zprávu "přečetl"
        }

        Message savedMessage = messageRepository.save(message);
        log.info("Message (ID: {}) added successfully to conversation ID {}.", savedMessage.getId(), conversationId);

        // Odeslání notifikace pro externí zprávy
        if (conversation.getType() == Conversation.ConversationType.EXTERNAL) {
            sendNotificationEmail(savedMessage);
        }

        return savedMessage;
    }

    /**
     * Odešle notifikační email o nové zprávě.
     * @param message Nově přidaná zpráva.
     */
    private void sendNotificationEmail(Message message) {
        if (message == null || message.getConversation() == null || message.getConversation().getOrder() == null) {
            log.error("Cannot send notification email, message or related order data is missing.");
            return;
        }

        Order order = message.getConversation().getOrder();
        // Explicitně načteme zákazníka, pokud je proxy
        Customer customer = order.getCustomer();
        if (customer != null && !Hibernate.isInitialized(customer)) {
            customer = customerService.getCustomerById(customer.getId()).orElse(null); // Znovu načteme
        }

        if (customer == null) {
            log.error("Cannot send notification email for message ID {}, customer is null.", message.getId());
            return;
        }


        if (message.getSenderType() == Message.SenderType.ADMIN) {
            // Admin poslal zprávu -> notifikace zákazníkovi
            String customerEmail = customer.getEmail();
            if (StringUtils.hasText(customerEmail)) {
                log.info("Sending notification email to customer {} about new message in order {}", customerEmail, order.getOrderCode());
                // Použijeme Locale z kontextu nebo default
                emailService.sendOrderExternalNoteEmail(
                        customerEmail,
                        order.getOrderCode(),
                        message.getContent(), // Předáme obsah nové zprávy
                        Locale.forLanguageTag("cs-CZ") // Nebo jiný způsob získání locale
                );
            } else {
                log.warn("Cannot send notification email to customer for order {}: email is missing.", order.getOrderCode());
            }
        } else { // Message.SenderType.CUSTOMER
            // Zákazník poslal zprávu -> notifikace adminovi
            // TODO: Získat email administrátora z konfigurace
            String adminEmail = "info@drevniky-kolar.cz"; // Příklad, načíst z properties!
            if (StringUtils.hasText(adminEmail)) {
                log.info("Sending notification email to admin {} about new message from customer in order {}", adminEmail, order.getOrderCode());
                emailService.sendAdminNotificationEmail(
                        adminEmail,
                        order.getOrderCode(),
                        customer.getEmail(),
                        customer.getFirstName() + " " + customer.getLastName(),
                        message.getContent()
                );
            } else {
                log.warn("Cannot send notification email to admin for order {}: admin email is not configured.", order.getOrderCode());
            }
        }
    }


    /**
     * Načte zprávy pro danou konverzaci.
     * @param conversationId ID konverzace.
     * @return Seznam zpráv seřazených podle času odeslání.
     */
    @Transactional(readOnly = true)
    public List<Message> getMessagesForConversation(Long conversationId) {
        log.debug("Fetching messages for conversation ID: {}", conversationId);
        // Pokud chceš načíst i konverzaci, použij metodu z ConversationRepository
        // Optional<Conversation> conversationOpt = conversationRepository.findByIdWithMessages(conversationId);
        // return conversationOpt.map(Conversation::getMessages).orElse(Collections.emptyList());
        // Nebo jen zprávy:
        return messageRepository.findByConversationIdOrderBySentAtAsc(conversationId);
    }

    /**
     * Označí zprávy v externí konverzaci jako přečtené daným typem uživatele.
     * @param orderId ID objednávky.
     * @param readerType Kdo čte (ADMIN nebo CUSTOMER).
     */
    @Transactional
    public void markExternalMessagesAsRead(Long orderId, Message.SenderType readerType) {
        log.info("Marking EXTERNAL messages as read for order ID {} by {}", orderId, readerType);
        Optional<Conversation> externalConvOpt = conversationRepository.findByOrderIdAndType(orderId, Conversation.ConversationType.EXTERNAL);

        if (externalConvOpt.isPresent()) {
            Conversation conversation = externalConvOpt.get();
            List<Message> messagesToUpdate = conversation.getMessages().stream()
                    .filter(msg -> {
                        // Označujeme jako přečtené adminem zprávy od zákazníka, které admin ještě nečetl
                        if (readerType == Message.SenderType.ADMIN && msg.getSenderType() == Message.SenderType.CUSTOMER && !msg.isReadByAdmin()) {
                            return true;
                        }
                        // Označujeme jako přečtené zákazníkem zprávy od admina, které zákazník ještě nečetl
                        if (readerType == Message.SenderType.CUSTOMER && msg.getSenderType() == Message.SenderType.ADMIN && !msg.isReadByCustomer()) {
                            return true;
                        }
                        return false;
                    })
                    .toList(); // Collect to list first

            if (!messagesToUpdate.isEmpty()) {
                log.debug("Found {} messages to mark as read by {} for order ID {}", messagesToUpdate.size(), readerType, orderId);
                for (Message msg : messagesToUpdate) {
                    if (readerType == Message.SenderType.ADMIN) {
                        msg.setReadByAdmin(true);
                    } else {
                        msg.setReadByCustomer(true);
                    }
                    messageRepository.save(msg); // Uložíme změnu pro každou zprávu
                }
                log.info("Successfully marked {} messages as read by {} for order ID {}", messagesToUpdate.size(), readerType, orderId);
            } else {
                log.debug("No new messages to mark as read by {} for order ID {}", readerType, orderId);
            }
        } else {
            log.debug("No external conversation found for order ID {} to mark messages as read.", orderId);
        }
    }


    // Pomocná metoda pro získání jména aktuálně přihlášeného admina
    // Tuto metodu budeš muset přizpůsobit podle toho, jak spravuješ admin uživatele
    // Možná budeš potřebovat AdminUserService a AdminUser entitu.
    public String getCurrentAdminUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() &&
                authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            // Můžeš vrátit email nebo jméno z principal objektu
            return authentication.getName();
        }
        log.warn("Could not determine current admin username.");
        return "Admin"; // Fallback
    }

    // Metoda pro získání ID aktuálně přihlášeného admina (pokud je potřeba)
    // Záleží na implementaci AdminUserService/Repository
    public Long getCurrentAdminId() {
        // Zde implementuj logiku pro získání ID admina, např.:
        // Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // String username = auth.getName();
        // Optional<AdminUser> admin = adminUserService.findByUsername(username);
        // return admin.map(AdminUser::getId).orElse(null);
        return null; // Prozatím null
    }
}