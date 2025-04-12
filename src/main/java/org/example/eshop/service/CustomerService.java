package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.Getter;
import org.example.eshop.model.Customer;
import org.example.eshop.dto.AddressDto;
import org.example.eshop.dto.ChangePasswordDto;
import org.example.eshop.dto.CheckoutFormDataDto;
import org.example.eshop.dto.ProfileUpdateDto;
import org.example.eshop.dto.RegistrationDto;
import org.example.eshop.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomerService {

    static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    @Autowired private CustomerRepository customerRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Getter
    @Autowired(required = false) private Validator validator;

    public enum AddressType { INVOICE, DELIVERY }

    public static class EmailRegisteredException extends Exception {
        public EmailRegisteredException(String message) { super(message); }
    }

    // --- Registrace a Autentizace (BEZE ZMĚNY) ---
    @Transactional
    public Customer registerCustomer(RegistrationDto dto) {
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
        return savedCustomer;
    }

    @Transactional(readOnly = true)
    public Optional<Customer> authenticateCustomer(String email, String rawPassword) {
        log.debug("Attempting to authenticate customer: {}", email);
        if (!StringUtils.hasText(email) || !StringUtils.hasText(rawPassword)) return Optional.empty();
        Optional<Customer> customerOpt = customerRepository.findByEmailIgnoreCase(email.trim());
        if (customerOpt.isPresent()) {
            Customer customer = customerOpt.get();
            if (customer.isGuest()) { log.warn("Auth failed for {}: Account is a guest account.", email); return Optional.empty(); }
            if (customer.getPassword() != null && passwordEncoder.matches(rawPassword, customer.getPassword())) {
                if (!customer.isEnabled()) { log.warn("Auth failed for {}: Account disabled.", email); return Optional.empty(); }
                log.info("Auth successful for: {}", email); return Optional.of(customer);
            } else { log.warn("Auth failed for {}: Invalid password.", email); }
        } else { log.warn("Auth failed: Customer not found with email: {}", email); }
        return Optional.empty();
    }

    // --- Úprava Profilu (BEZE ZMĚNY) ---
    @Transactional
    public Customer updateProfile(String currentEmail, ProfileUpdateDto dto) {
        log.info("Updating profile for user: {}", currentEmail);
        validateObject(dto);
        Customer customer = customerRepository.findByEmailIgnoreCase(currentEmail)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found with email: " + currentEmail));
        if (customer.isGuest()) { throw new IllegalArgumentException("Profil hosta nelze měnit standardním způsobem."); }
        customer.setFirstName(dto.getFirstName());
        customer.setLastName(dto.getLastName());
        customer.setPhone(dto.getPhone());
        if (!StringUtils.hasText(customer.getInvoiceCompanyName())) {
            customer.setInvoiceFirstName(dto.getFirstName());
            customer.setInvoiceLastName(dto.getLastName());
        }
        if (!StringUtils.hasText(customer.getDeliveryPhone())) { customer.setDeliveryPhone(dto.getPhone()); }
        Customer updatedCustomer = customerRepository.save(customer);
        log.info("Profile updated successfully for user: {}", currentEmail);
        return updatedCustomer;
    }

    // --- Změna Hesla (BEZE ZMĚNY) ---
    @Transactional
    public void changePassword(Long customerId, ChangePasswordDto dto) {
        log.info("Attempting to change password for customer ID: {}", customerId);
        validateObject(dto);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found with ID: " + customerId));
        if (customer.isGuest()) { throw new IllegalArgumentException("Host účty nemají heslo."); }
        if (!StringUtils.hasText(customer.getPassword())) { throw new IllegalStateException("Chyba účtu: Heslo není nastaveno."); }
        if (!passwordEncoder.matches(dto.getCurrentPassword(), customer.getPassword())) { throw new IllegalArgumentException("Nesprávné staré heslo."); }
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
            if (!existingCustomer.isGuest()) { throw new EmailRegisteredException("Tento email je již zaregistrován. Prosím, přihlaste se pro dokončení objednávky."); }
            else { log.info("Found existing guest account for email {}, updating details.", email); updateCustomerFromDto(existingCustomer, dto); return customerRepository.save(existingCustomer); }
        }
        log.info("Creating new guest customer record for email: {}", email);
        Customer guest = new Customer();
        guest.setGuest(true); guest.setEnabled(true); guest.setRoles(Set.of("ROLE_GUEST")); guest.setPassword(null);
        updateCustomerFromDto(guest, dto);
        Customer savedGuest = customerRepository.save(guest);
        log.info("Guest customer created successfully with ID: {}", savedGuest.getId());
        return savedGuest;
    }

    /**
     * Pomocná metoda pro naplnění/aktualizaci Customer entity daty z CheckoutFormDataDto.
     * NEVOLÁ save() - to musí zajistit volající metoda.
     * *** UPRAVENO: Přidány null checky pro NOT NULL sloupce ***
     * @param customer Customer entita k naplnění/aktualizaci.
     * @param dto DTO s daty z formuláře.
     */
    public void updateCustomerFromDto(Customer customer, CheckoutFormDataDto dto) {
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

        // Nastavení jména/příjmení na faktuře v Customer entitě
        String customerIdLog = customer.getId() != null ? customer.getId().toString() : "(new)"; // Bezpečné získání ID pro log
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
        // @PreUpdate/@PrePersist v Customer entitě se postará o synchronizaci/vymazání dodací adresy
    }


    // --- Správa Adres (BEZE ZMĚNY) ---
    @Transactional
    public Customer updateAddress(Long customerId, AddressType addressType, AddressDto dto) {
        log.info("Updating {} address for customer ID: {}", addressType, customerId);
        validateObject(dto);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found with ID: " + customerId));
        if (customer.isGuest()) { throw new IllegalArgumentException("Adresu hosta nelze měnit tímto způsobem."); }
        if (!StringUtils.hasText(dto.getCompanyName()) && (!StringUtils.hasText(dto.getFirstName()) || !StringUtils.hasText(dto.getLastName()))) { throw new IllegalArgumentException("Musí být vyplněn název firmy nebo jméno a příjmení."); }
        if (addressType == AddressType.INVOICE) { updateInvoiceAddressFromDto(customer, dto); }
        else { updateDeliveryAddressFromDto(customer, dto); customer.setUseInvoiceAddressAsDelivery(false); log.debug("Set useInvoiceAddressAsDelivery to false for customer {}", customerId); }
        Customer updatedCustomer = customerRepository.save(customer);
        log.info("{} address updated successfully for customer ID: {}", addressType, customerId);
        return updatedCustomer;
    }
    private void updateInvoiceAddressFromDto(Customer customer, AddressDto dto) {
        customer.setInvoiceCompanyName(dto.getCompanyName()); customer.setInvoiceVatId(dto.getVatId()); customer.setInvoiceTaxId(dto.getTaxId());
        customer.setInvoiceFirstName(dto.getFirstName()); customer.setInvoiceLastName(dto.getLastName()); customer.setInvoiceStreet(dto.getStreet());
        customer.setInvoiceCity(dto.getCity()); customer.setInvoiceZipCode(dto.getZipCode()); customer.setInvoiceCountry(dto.getCountry());
    }
    private void updateDeliveryAddressFromDto(Customer customer, AddressDto dto) {
        customer.setDeliveryCompanyName(dto.getCompanyName()); customer.setDeliveryFirstName(dto.getFirstName()); customer.setDeliveryLastName(dto.getLastName());
        customer.setDeliveryStreet(dto.getStreet()); customer.setDeliveryCity(dto.getCity()); customer.setDeliveryZipCode(dto.getZipCode());
        customer.setDeliveryCountry(dto.getCountry()); customer.setDeliveryPhone(dto.getPhone());
        if (!StringUtils.hasText(customer.getDeliveryPhone())) { customer.setDeliveryPhone(customer.getPhone()); }
    }
    @Transactional
    public Customer setUseInvoiceAddressAsDelivery(Long customerId, boolean useInvoiceAddress) {
        log.info("Setting useInvoiceAddressAsDelivery to {} for customer ID: {}", useInvoiceAddress, customerId);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found with ID: " + customerId));
        if (customer.isGuest()) { throw new IllegalArgumentException("Nastavení adresy nelze měnit pro host účet."); }
        customer.setUseInvoiceAddressAsDelivery(useInvoiceAddress);
        Customer updatedCustomer = customerRepository.save(customer);
        log.info("useInvoiceAddressAsDelivery flag updated for customer ID: {}", customerId);
        return updatedCustomer;
    }

    // --- Metody pro čtení (BEZE ZMĚNY) ---
    @Transactional(readOnly = true)
    public Optional<Customer> getCustomerById(long id){ return customerRepository.findById(id); }
    @Transactional(readOnly = true)
    public Optional<Customer> getCustomerByEmail(String email){
        if (!StringUtils.hasText(email)) return Optional.empty();
        return customerRepository.findByEmailIgnoreCase(email.trim());
    }
    @Transactional(readOnly = true)
    public List<Customer> getAllCustomers() { return customerRepository.findAll(); }
    @Transactional(readOnly = true)
    public Page<Customer> findCustomers(Pageable pageable, String emailFragment, String nameFragment, Boolean enabled) {
        log.debug("Searching for customers with filters - Email: '{}', Name: '{}', Enabled: {}, Page: {}", emailFragment, nameFragment, enabled, pageable);
        boolean hasEmail = StringUtils.hasText(emailFragment); boolean hasName = StringUtils.hasText(nameFragment); boolean hasEnabled = enabled != null;
        if (hasEmail && hasEnabled) return customerRepository.findByEmailContainingIgnoreCaseAndEnabled(emailFragment, enabled, pageable);
        if (hasEmail) return customerRepository.findByEmailContainingIgnoreCase(emailFragment, pageable);
        if (hasName) return customerRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(nameFragment, nameFragment, pageable);
        if (hasEnabled) return customerRepository.findByEnabled(enabled, pageable);
        return customerRepository.findAll(pageable);
    }
    @Transactional
    public Customer saveCustomer(Customer customer) {
        log.info("Saving customer ID: {}", customer.getId());
        return customerRepository.save(customer);
    }

    // --- Správa rolí (BEZE ZMĚNY) ---
    @Transactional
    public void addRoleToCustomer(Long customerId, String role) { log.warn("addRoleToCustomer not implemented"); }
    @Transactional
    public void removeRoleFromCustomer(Long customerId, String role) { log.warn("removeRoleFromCustomer not implemented"); }

    // --- Pomocná metoda pro validaci (BEZE ZMĚNY) ---
    private void validateObject(Object object) {
        if (validator == null || object == null) { log.trace("Validator not present or object is null, skipping validation."); return; }
        Set<ConstraintViolation<Object>> violations = validator.validate(object);
        if (!violations.isEmpty()) {
            String errors = violations.stream().map(v -> v.getPropertyPath() + ": " + v.getMessage()).collect(Collectors.joining(", "));
            log.warn("Validation failed for {}: {}", object.getClass().getSimpleName(), errors);
            throw new IllegalArgumentException("Validation failed: " + errors);
        }
        log.trace("Validation successful for {}", object.getClass().getSimpleName());
    }
}