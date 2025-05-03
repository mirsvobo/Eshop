package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.Getter;
import org.example.eshop.dto.*;
import org.example.eshop.model.Customer;
import org.example.eshop.model.PasswordResetToken; // Přidáno
import org.example.eshop.repository.CustomerRepository;
import org.example.eshop.repository.PasswordResetTokenRepository; // Přidáno
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy; // Přidáno
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled; // Přidáno
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime; // Přidáno
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID; // Přidáno
import java.util.stream.Collectors;

@Service
public class CustomerService {

    static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Getter
    @Autowired(required = false)
    private Validator validator;

    // --- PŘIDANÉ ZÁVISLOSTI PRO RESET HESLA ---
    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired
    @Lazy // Použijeme Lazy, abychom předešli cyklické závislosti
    private EmailService emailService;
    // --------------------------------------------


    // --- Registrace a Autentizace (BEZE ZMĚNY) ---
    @Transactional
    public void registerCustomer(RegistrationDto dto) {
        log.info("Attempting to register customer with email: {}", dto.getEmail());
        validateObject(dto);
        if (customerRepository.existsByEmailIgnoreCase(dto.getEmail())) {
            throw new IllegalArgumentException("Zákazník s emailem " + dto.getEmail() + " již existuje.");
        }
        Customer customer = new Customer();
        customer.setFirstName(dto.getFirstName());
        customer.setLastName(dto.getLastName());
        customer.setEmail(dto.getEmail().toLowerCase().trim());
        customer.setPhone(dto.getPhone());
        customer.setPassword(passwordEncoder.encode(dto.getPassword()));
        customer.setEnabled(true);
        customer.setGuest(false);
        customer.setRoles(Set.of("ROLE_USER"));
        customer.setInvoiceFirstName(dto.getFirstName());
        customer.setInvoiceLastName(dto.getLastName());
        customer.setUseInvoiceAddressAsDelivery(true);

        Customer savedCustomer = customerRepository.save(customer);
        log.info("Customer registered successfully with ID: {}", savedCustomer.getId());
    }

    // --- Úprava Profilu (BEZE ZMĚNY) ---
    @Transactional
    public void updateProfile(String currentEmail, ProfileUpdateDto dto) {
        log.info("Updating profile for user: {}", currentEmail);
        validateObject(dto);
        Customer customer = customerRepository.findByEmailIgnoreCase(currentEmail)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found with email: " + currentEmail));
        if (customer.isGuest()) {
            throw new IllegalArgumentException("Profil hosta nelze měnit standardním způsobem.");
        }
        customer.setFirstName(dto.getFirstName());
        customer.setLastName(dto.getLastName());
        customer.setPhone(dto.getPhone());
        // Synchronizace jména/příjmení na faktuře, pokud není firma
        if (!StringUtils.hasText(customer.getInvoiceCompanyName())) {
            customer.setInvoiceFirstName(dto.getFirstName());
            customer.setInvoiceLastName(dto.getLastName());
        }
        // Synchronizace telefonu na dodací adrese, pokud není specifický
        if (customer.isUseInvoiceAddressAsDelivery() || !StringUtils.hasText(customer.getDeliveryPhone())) {
            if (StringUtils.hasText(dto.getPhone())) {
                customer.setDeliveryPhone(dto.getPhone());
            }
        }
        customerRepository.save(customer); // Uložení změn
        log.info("Profile updated successfully for user: {}", currentEmail);
    }

    // --- Změna Hesla (BEZE ZMĚNY - metoda už existovala) ---
    @Transactional
    public void changePassword(Long customerId, ChangePasswordDto dto) {
        log.info("Attempting to change password for customer ID: {}", customerId);
        validateObject(dto);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found with ID: " + customerId));
        if (customer.isGuest()) {
            throw new IllegalArgumentException("Host účty nemají heslo.");
        }
        if (!StringUtils.hasText(customer.getPassword())) {
            // Toto by nemělo nastat pro registrovaného uživatele
            log.error("Password change failed for customer {}: Password hash is missing!", customerId);
            throw new IllegalStateException("Chyba účtu: Heslo není nastaveno.");
        }
        if (!passwordEncoder.matches(dto.getCurrentPassword(), customer.getPassword())) {
            throw new IllegalArgumentException("Nesprávné staré heslo.");
        }
        // Kontrola shody nových hesel (pro jistotu, i když by to měl řešit controller)
        if (!dto.getNewPassword().equals(dto.getConfirmNewPassword())) {
            throw new IllegalArgumentException("Nové heslo a potvrzení se neshodují.");
        }

        customer.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        customerRepository.save(customer);
        log.info("Password changed successfully for customer ID: {}", customerId);
    }

    // --- Vytvoření nebo získání hosta (BEZE ZMĚNY) ---
    @Transactional
    public Customer getOrCreateGuestFromCheckoutData(CheckoutFormDataDto dto) throws EmailRegisteredException {
        String email = dto.getEmail() != null ? dto.getEmail().toLowerCase().trim() : null;
        log.info("Attempting to get or create guest customer for email: {}", email);
        Optional<Customer> existingCustomerOpt = customerRepository.findByEmailIgnoreCase(email);
        if (existingCustomerOpt.isPresent()) {
            Customer existingCustomer = existingCustomerOpt.get();
            if (!existingCustomer.isGuest()) {
                throw new EmailRegisteredException("Tento email je již zaregistrován. Prosím, přihlaste se pro dokončení objednávky.");
            } else {
                log.info("Found existing guest account for email {}, updating details.", email);
                updateCustomerFromDto(existingCustomer, dto);
                return customerRepository.save(existingCustomer);
            }
        }
        log.info("Creating new guest customer record for email: {}", email);
        Customer guest = new Customer();
        guest.setGuest(true);
        guest.setEnabled(true);
        guest.setRoles(Set.of("ROLE_GUEST"));
        guest.setPassword(null);
        updateCustomerFromDto(guest, dto);
        Customer savedGuest = customerRepository.save(guest);
        log.info("Guest customer created successfully with ID: {}", savedGuest.getId());
        return savedGuest;
    }

    // --- Update Customer z DTO (BEZE ZMĚNY) ---
    public void updateCustomerFromDto(Customer customer, CheckoutFormDataDto dto) {
        // ... (kód metody beze změny) ...
        // --- Základní / Kontaktní údaje ---
        if (dto.getEmail() != null) customer.setEmail(dto.getEmail().toLowerCase().trim());
        if (dto.getFirstName() != null) customer.setFirstName(dto.getFirstName());
        if (dto.getLastName() != null) customer.setLastName(dto.getLastName());
        if (dto.getPhone() != null) customer.setPhone(dto.getPhone());

        // --- Fakturační údaje ---
        customer.setInvoiceCompanyName(dto.getInvoiceCompanyName());
        customer.setInvoiceTaxId(dto.getInvoiceTaxId());
        customer.setInvoiceVatId(dto.getInvoiceVatId());
        if (dto.getInvoiceStreet() != null) customer.setInvoiceStreet(dto.getInvoiceStreet());
        if (dto.getInvoiceCity() != null) customer.setInvoiceCity(dto.getInvoiceCity());
        if (dto.getInvoiceZipCode() != null) customer.setInvoiceZipCode(dto.getInvoiceZipCode());
        if (dto.getInvoiceCountry() != null) customer.setInvoiceCountry(dto.getInvoiceCountry());

        String customerIdLog = customer.getId() != null ? customer.getId().toString() : "(new)";
        if (StringUtils.hasText(dto.getInvoiceCompanyName())) {
            customer.setInvoiceFirstName(null);
            customer.setInvoiceLastName(null);
            log.debug("Setting invoice name/lastname to null for customer ID {} due to company name.", customerIdLog);
        } else {
            customer.setInvoiceFirstName(dto.getFirstName());
            customer.setInvoiceLastName(dto.getLastName());
            log.debug("Setting invoice name/lastname from main contact for customer ID {}.", customerIdLog);
        }

        // --- Dodací údaje ---
        customer.setUseInvoiceAddressAsDelivery(dto.isUseInvoiceAddressAsDelivery());
        if (!dto.isUseInvoiceAddressAsDelivery()) {
            customer.setDeliveryCompanyName(dto.getDeliveryCompanyName());
            customer.setDeliveryFirstName(dto.getDeliveryFirstName());
            customer.setDeliveryLastName(dto.getDeliveryLastName());
            if (dto.getDeliveryStreet() != null) customer.setDeliveryStreet(dto.getDeliveryStreet());
            if (dto.getDeliveryCity() != null) customer.setDeliveryCity(dto.getDeliveryCity());
            if (dto.getDeliveryZipCode() != null) customer.setDeliveryZipCode(dto.getDeliveryZipCode());
            if (dto.getDeliveryCountry() != null) customer.setDeliveryCountry(dto.getDeliveryCountry());
            if (dto.getDeliveryPhone() != null) customer.setDeliveryPhone(dto.getDeliveryPhone());
        }
    }

    // --- Správa Adres (BEZE ZMĚNY) ---
    @Transactional
    public void updateAddress(Long customerId, AddressType addressType, AddressDto dto) {
        log.info("Updating {} address for customer ID: {}", addressType, customerId);
        validateObject(dto);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found with ID: " + customerId));
        if (customer.isGuest()) {
            throw new IllegalArgumentException("Adresu hosta nelze měnit tímto způsobem.");
        }
        if (!dto.hasRecipient()) { // Použití metody z DTO
            throw new IllegalArgumentException("Musí být vyplněn název firmy nebo jméno a příjmení.");
        }
        if (addressType == AddressType.INVOICE) {
            updateInvoiceAddressFromDto(customer, dto);
        } else {
            updateDeliveryAddressFromDto(customer, dto);
            customer.setUseInvoiceAddressAsDelivery(false);
            log.debug("Set useInvoiceAddressAsDelivery to false for customer {}", customerId);
        }
        customerRepository.save(customer); // Uložení změn
        log.info("{} address updated successfully for customer ID: {}", addressType, customerId);
    }

    // --- Metody pro čtení (BEZE ZMĚNY) ---
    @Transactional(readOnly = true)
    public long countTotalCustomers() {
        log.debug("Counting total customers");
        try {
            return customerRepository.count();
        } catch (Exception e) {
            log.error("Error counting total customers: {}", e.getMessage(), e);
            return 0L;
        }
    }

    private void updateInvoiceAddressFromDto(Customer customer, AddressDto dto) {
        customer.setInvoiceCompanyName(dto.getCompanyName());
        customer.setInvoiceVatId(dto.getVatId());
        customer.setInvoiceTaxId(dto.getTaxId());
        customer.setInvoiceFirstName(dto.getFirstName());
        customer.setInvoiceLastName(dto.getLastName());
        customer.setInvoiceStreet(dto.getStreet());
        customer.setInvoiceCity(dto.getCity());
        customer.setInvoiceZipCode(dto.getZipCode());
        customer.setInvoiceCountry(dto.getCountry());
    }

    private void updateDeliveryAddressFromDto(Customer customer, AddressDto dto) {
        customer.setDeliveryCompanyName(dto.getCompanyName());
        customer.setDeliveryFirstName(dto.getFirstName());
        customer.setDeliveryLastName(dto.getLastName());
        customer.setDeliveryStreet(dto.getStreet());
        customer.setDeliveryCity(dto.getCity());
        customer.setDeliveryZipCode(dto.getZipCode());
        customer.setDeliveryCountry(dto.getCountry());
        customer.setDeliveryPhone(dto.getPhone());
        // Fallback na hlavní telefon, pokud dodací není vyplněn
        if (!StringUtils.hasText(customer.getDeliveryPhone()) && StringUtils.hasText(customer.getPhone())) {
            customer.setDeliveryPhone(customer.getPhone());
        }
    }

    @Transactional
    public void setUseInvoiceAddressAsDelivery(Long customerId, boolean useInvoiceAddress) {
        log.info("Setting useInvoiceAddressAsDelivery to {} for customer ID: {}", useInvoiceAddress, customerId);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found with ID: " + customerId));
        if (customer.isGuest()) {
            throw new IllegalArgumentException("Nastavení adresy nelze měnit pro host účet.");
        }
        customer.setUseInvoiceAddressAsDelivery(useInvoiceAddress);
        customerRepository.save(customer); // Uložení změny
        log.info("useInvoiceAddressAsDelivery flag updated for customer ID: {}", customerId);
    }

    @Transactional(readOnly = true)
    public Optional<Customer> getCustomerById(long id) {
        return customerRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Customer> getCustomerByEmail(String email) {
        if (!StringUtils.hasText(email)) return Optional.empty();
        return customerRepository.findByEmailIgnoreCase(email.trim());
    }

    @Transactional(readOnly = true)
    public Page<Customer> findCustomers(Pageable pageable, String emailFragment, String nameFragment, Boolean enabled) {
        log.debug("Searching for customers with filters - Email: '{}', Name: '{}', Enabled: {}, Page: {}", emailFragment, nameFragment, enabled, pageable);
        boolean hasEmail = StringUtils.hasText(emailFragment);
        boolean hasName = StringUtils.hasText(nameFragment);
        boolean hasEnabled = enabled != null;
        if (hasEmail && hasEnabled)
            return customerRepository.findByEmailContainingIgnoreCaseAndEnabled(emailFragment, enabled, pageable);
        if (hasEmail) return customerRepository.findByEmailContainingIgnoreCase(emailFragment, pageable);
        if (hasName)
            return customerRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(nameFragment, nameFragment, pageable);
        if (hasEnabled) return customerRepository.findByEnabled(enabled, pageable);
        return customerRepository.findAll(pageable);
    }

    @Transactional
    public Customer saveCustomer(Customer customer) {
        log.info("Saving customer ID: {}", customer.getId());
        return customerRepository.save(customer);
    }

    // --- Pomocná metoda pro validaci (BEZE ZMĚNY) ---
    private void validateObject(Object object) {
        if (validator == null || object == null) {
            log.trace("Validator not present or object is null, skipping validation.");
            return;
        }
        Set<ConstraintViolation<Object>> violations = validator.validate(object);
        if (!violations.isEmpty()) {
            String errors = violations.stream().map(v -> v.getPropertyPath() + ": " + v.getMessage()).collect(Collectors.joining(", "));
            log.warn("Validation failed for {}: {}", object.getClass().getSimpleName(), errors);
            throw new IllegalArgumentException("Validation failed: " + errors);
        }
        log.trace("Validation successful for {}", object.getClass().getSimpleName());
    }


    // --- NOVÉ METODY PRO RESET HESLA ---

    @Transactional
    public String createPasswordResetTokenForUser(String userEmail) {
        Customer customer = customerRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("Zákazník s emailem " + userEmail + " nenalezen."));

        if (customer.isGuest()) {
            throw new IllegalArgumentException("Reset hesla není možný pro účet hosta.");
        }

        // Smazat starý token, pokud existuje
        passwordResetTokenRepository.findByCustomerEmailIgnoreCase(userEmail)
                .ifPresent(token -> {
                    log.debug("Deleting existing password reset token for user {}", userEmail);
                    passwordResetTokenRepository.delete(token);
                });


        String token = UUID.randomUUID().toString();
        PasswordResetToken myToken = new PasswordResetToken(token, customer);
        passwordResetTokenRepository.save(myToken);
        log.info("Created new password reset token for user {}", userEmail);

        // Odeslání emailu (předpokládá metodu v EmailService)
        try {
            // Tuto metodu budeš muset vytvořit v EmailService
            emailService.sendPasswordResetEmail(customer, token);
            log.info("Password reset email sent to {}", userEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", userEmail, e.getMessage());
            // Zde je důležité NEVYHAZOVAT výjimku, aby se uživateli zobrazila úspěšná hláška
            // Problém s odesláním emailu by neměl bránit v zobrazení potvrzení uživateli
            // Můžeš ale přidat např. interní notifikaci adminovi
        }

        return token; // Vrací token pro případné logování nebo debug
    }

    @Transactional(readOnly = true)
    public Optional<PasswordResetToken> validatePasswordResetToken(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByToken(token);
        if (tokenOpt.isEmpty()) {
            log.warn("Password reset token not found: {}", token);
            return Optional.empty();
        }
        if (tokenOpt.get().isExpired()) {
            log.warn("Password reset token has expired: {}", token);
            // Zde můžeme expirovaný token rovnou smazat
            // passwordResetTokenRepository.delete(tokenOpt.get()); // Odkomentuj, pokud chceš mazat hned
            return Optional.empty();
        }
        log.debug("Password reset token is valid: {}", token);
        return tokenOpt;
    }

    @Transactional
    public void changeUserPassword(PasswordResetToken token, String newPassword) {
        if (token == null || token.getCustomer() == null) {
            throw new IllegalArgumentException("Neplatný nebo chybějící token pro změnu hesla.");
        }
        // Znovu načteme zákazníka, abychom měli jistotu aktuálních dat
        Customer customer = customerRepository.findById(token.getCustomer().getId())
                .orElseThrow(() -> new EntityNotFoundException("Zákazník asociovaný s tokenem nenalezen (ID: " + token.getCustomer().getId() + ")."));

        // Validace nového hesla (např. minimální délka dle DTO nebo jiných pravidel)
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 6) { // Předpoklad min délky 6
            throw new IllegalArgumentException("Nové heslo nesplňuje požadavky na délku nebo je prázdné.");
        }

        // Převod hosta na registrovaného uživatele při prvním nastavení hesla
        if (customer.isGuest()) {
            log.info("Konvertuji účet hosta {} na registrovaného uživatele během prvního nastavení hesla.", customer.getEmail());
            customer.setGuest(false);

            // --- OPRAVA: Použít MUTABLE Set (např. HashSet) ---
            Set<String> newRoles = new HashSet<>(); // Vytvoříme nový HashSet
            newRoles.add("ROLE_USER");             // Přidáme roli
            customer.setRoles(newRoles);           // Přiřadíme tento nový, modifikovatelný Set
            // -----------------------------------------------

            customer.setEnabled(true); // Zajistíme, že účet je aktivní
        }

        // Nastavíme nové, zahashované heslo
        customer.setPassword(passwordEncoder.encode(newPassword));

        // Uložíme změny v zákazníkovi - Nyní by mělo projít bez UnsupportedOperationException
        customerRepository.save(customer);

        // Smažeme použitý token
        passwordResetTokenRepository.delete(token);

        log.info("Heslo úspěšně nastaveno/změněno pro uživatele: {} pomocí reset tokenu", customer.getEmail());
    }

    // Metoda pro pravidelné čištění expirovaných tokenů (např. jednou denně)
    @Scheduled(cron = "0 0 3 * * ?") // Každý den ve 3:00 ráno
    @Transactional
    public void purgeExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        log.info("Purging expired password reset tokens older than {}", now);
        try {
            passwordResetTokenRepository.deleteByExpiryDateBefore(now);
            log.info("Expired password reset tokens purged successfully.");
        } catch (Exception e) {
            log.error("Error during purging expired password reset tokens: {}", e.getMessage(), e);
        }
    }

    // --- KONEC NOVÝCH METOD PRO RESET HESLA ---


    // --- Ostatní pomocné metody (BEZE ZMĚNY) ---
    public enum AddressType {INVOICE, DELIVERY}

    public static class EmailRegisteredException extends Exception {
        public EmailRegisteredException(String message) {
            super(message);
        }
    }
}
