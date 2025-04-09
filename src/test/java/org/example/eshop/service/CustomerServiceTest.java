// Soubor: src/test/java/org/example/eshop/service/CustomerServiceTest.java
package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
// Importuj validační anotace, pokud je používáš pro testovací DTOs
// import jakarta.validation.ConstraintViolation;
// import jakarta.validation.Validation;
// import jakarta.validation.Validator;
// import jakarta.validation.ValidatorFactory;
import org.example.eshop.dto.*; // Import všech DTO
import org.example.eshop.model.Customer;
import org.example.eshop.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    // Mock Validator, pokud ho CustomerService používá (zde není anotován @Autowired(required=true))
    // @Mock
    // private Validator validator;

    @InjectMocks
    private CustomerService customerService;

    private RegistrationDto registrationDto;
    private ProfileUpdateDto profileUpdateDto;
    private ChangePasswordDto changePasswordDto;
    private AddressDto invoiceAddressDto;
    private AddressDto deliveryAddressDto;
    private CheckoutFormDataDto checkoutFormDataDto_Guest;
    private CheckoutFormDataDto checkoutFormDataDto_Registered;

    private Customer existingCustomer;
    private Customer guestCustomer;

    @BeforeEach
    void setUp() {
        // --- DTOs ---
        registrationDto = new RegistrationDto();
        registrationDto.setFirstName("Test");
        registrationDto.setLastName("Registrace");
        registrationDto.setEmail("registrace@example.com");
        registrationDto.setPassword("password123");
        registrationDto.setPhone("111222333");

        profileUpdateDto = new ProfileUpdateDto();
        profileUpdateDto.setFirstName("Upravene");
        profileUpdateDto.setLastName("Prijmeni");
        profileUpdateDto.setPhone("999888777");

        changePasswordDto = new ChangePasswordDto();
        changePasswordDto.setCurrentPassword("stareHeslo");
        changePasswordDto.setNewPassword("noveHeslo123");
        changePasswordDto.setConfirmNewPassword("noveHeslo123");

        invoiceAddressDto = new AddressDto();
        invoiceAddressDto.setCompanyName("Faktura s.r.o.");
        invoiceAddressDto.setStreet("Fakturacni 10");
        invoiceAddressDto.setCity("Fakturov");
        invoiceAddressDto.setZipCode("10001");
        invoiceAddressDto.setCountry("Česká Republika");
        invoiceAddressDto.setTaxId("12345678");
        invoiceAddressDto.setVatId("CZ12345678");

        deliveryAddressDto = new AddressDto();
        deliveryAddressDto.setFirstName("Doručovací");
        deliveryAddressDto.setLastName("Jméno");
        deliveryAddressDto.setStreet("Dodaci 5");
        deliveryAddressDto.setCity("Dodanov");
        deliveryAddressDto.setZipCode("20002");
        deliveryAddressDto.setCountry("Česká Republika");
        deliveryAddressDto.setPhone("555666777");

        checkoutFormDataDto_Guest = new CheckoutFormDataDto();
        checkoutFormDataDto_Guest.setEmail("guest@checkout.com");
        checkoutFormDataDto_Guest.setFirstName("Guest");
        checkoutFormDataDto_Guest.setLastName("User");
        checkoutFormDataDto_Guest.setPhone("444555666");
        checkoutFormDataDto_Guest.setInvoiceStreet("Guest Street 1");
        checkoutFormDataDto_Guest.setInvoiceCity("Guest City");
        checkoutFormDataDto_Guest.setInvoiceZipCode("30003");
        checkoutFormDataDto_Guest.setInvoiceCountry("ČR");
        checkoutFormDataDto_Guest.setUseInvoiceAddressAsDelivery(true);

        checkoutFormDataDto_Registered = new CheckoutFormDataDto();
        // ... (data pro update existujícího hosta - mohou být stejná jako u guest)
        checkoutFormDataDto_Registered.setEmail("existujici-guest@checkout.com");
        checkoutFormDataDto_Registered.setFirstName("Updated Guest");
        checkoutFormDataDto_Registered.setLastName("User");
        checkoutFormDataDto_Registered.setPhone("444555666");
        checkoutFormDataDto_Registered.setInvoiceStreet("Updated Street 1");
        checkoutFormDataDto_Registered.setInvoiceCity("Updated City");
        checkoutFormDataDto_Registered.setInvoiceZipCode("30003");
        checkoutFormDataDto_Registered.setInvoiceCountry("ČR");
        checkoutFormDataDto_Registered.setUseInvoiceAddressAsDelivery(false); // Změníme na oddělenou dodací
        checkoutFormDataDto_Registered.setDeliveryFirstName("DeliveryName");
        checkoutFormDataDto_Registered.setDeliveryLastName("DeliverySurname");
        checkoutFormDataDto_Registered.setDeliveryStreet("Delivery Street 5");
        checkoutFormDataDto_Registered.setDeliveryCity("Delivery City");
        checkoutFormDataDto_Registered.setDeliveryZipCode("40004");
        checkoutFormDataDto_Registered.setDeliveryCountry("ČR");
        checkoutFormDataDto_Registered.setDeliveryPhone("123123123");


        // --- Entities ---
        existingCustomer = new Customer();
        existingCustomer.setId(1L);
        existingCustomer.setFirstName("Puvodni");
        existingCustomer.setLastName("Zakaznik");
        existingCustomer.setEmail("existing@example.com");
        existingCustomer.setPassword("$2a$10$encodedStareHeslo"); // Mocknuté zakódované staré heslo
        existingCustomer.setPhone("555444333");
        existingCustomer.setEnabled(true);
        existingCustomer.setGuest(false);
        existingCustomer.setRoles(Set.of("ROLE_USER"));
        existingCustomer.setInvoiceStreet("Stara Ulice 1");
        // ... (další adresní údaje)

        guestCustomer = new Customer();
        guestCustomer.setId(2L);
        guestCustomer.setEmail("existujici-guest@checkout.com");
        guestCustomer.setFirstName("Old Guest");
        guestCustomer.setLastName("User");
        guestCustomer.setGuest(true);
        guestCustomer.setEnabled(true);
        guestCustomer.setRoles(Set.of("ROLE_GUEST"));
        guestCustomer.setPassword(null); // Host nemá heslo
    }

    // --- Testy Registrace ---
    @Test
    @DisplayName("registerCustomer úspěšně zaregistruje nového zákazníka")
    void registerCustomer_Success() {
        String rawPassword = registrationDto.getPassword();
        String encodedPassword = "$2a$10$encodedPassword";

        // Mock chování
        when(customerRepository.existsByEmailIgnoreCase(registrationDto.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);
        // Použijeme ArgumentCaptor pro zachycení objektu předaného do save
        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        when(customerRepository.save(customerCaptor.capture())).thenAnswer(i -> i.getArgument(0)); // Vrátíme zachycený objekt

        Customer registered = customerService.registerCustomer(registrationDto);

        // Ověření
        assertNotNull(registered);
        assertEquals(registrationDto.getFirstName(), registered.getFirstName());
        assertEquals(registrationDto.getEmail().toLowerCase().trim(), registered.getEmail());
        assertEquals(encodedPassword, registered.getPassword());
        assertTrue(registered.isEnabled());
        assertFalse(registered.isGuest());
        assertTrue(registered.getRoles().contains("ROLE_USER"));
        // Ověření výchozí synchronizace adresy
        assertEquals(registrationDto.getFirstName(), registered.getInvoiceFirstName());
        assertTrue(registered.isUseInvoiceAddressAsDelivery());

        verify(customerRepository).existsByEmailIgnoreCase(registrationDto.getEmail());
        verify(passwordEncoder).encode(rawPassword);
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    @DisplayName("registerCustomer vyhodí výjimku pro existující email")
    void registerCustomer_ThrowsForExistingEmail() {
        when(customerRepository.existsByEmailIgnoreCase(registrationDto.getEmail())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> customerService.registerCustomer(registrationDto));

        assertTrue(exception.getMessage().contains("již existuje"));
        verify(customerRepository).existsByEmailIgnoreCase(registrationDto.getEmail());
        verify(passwordEncoder, never()).encode(anyString());
        verify(customerRepository, never()).save(any());
    }

    // --- Testy Aktualizace Profilu ---
    @Test
    @DisplayName("updateProfile úspěšně aktualizuje profil")
    void updateProfile_Success() {
        String currentEmail = existingCustomer.getEmail();
        when(customerRepository.findByEmailIgnoreCase(currentEmail)).thenReturn(Optional.of(existingCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        Customer updated = customerService.updateProfile(currentEmail, profileUpdateDto);

        assertNotNull(updated);
        assertEquals(profileUpdateDto.getFirstName(), updated.getFirstName());
        assertEquals(profileUpdateDto.getLastName(), updated.getLastName());
        assertEquals(profileUpdateDto.getPhone(), updated.getPhone());
        // Ověříme, že se invoice jméno aktualizovalo, pokud nebylo vyplněno jméno firmy
        assertEquals(profileUpdateDto.getFirstName(), updated.getInvoiceFirstName());
        assertEquals(profileUpdateDto.getLastName(), updated.getInvoiceLastName());


        verify(customerRepository).findByEmailIgnoreCase(currentEmail);
        verify(customerRepository).save(existingCustomer);
    }

    @Test
    @DisplayName("updateProfile vyhodí výjimku pro neexistujícího uživatele")
    void updateProfile_UserNotFound() {
        String nonExistentEmail = "nobody@example.com";
        when(customerRepository.findByEmailIgnoreCase(nonExistentEmail)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> customerService.updateProfile(nonExistentEmail, profileUpdateDto));
        assertTrue(exception.getMessage().contains("Customer not found"));
        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateProfile vyhodí výjimku při pokusu o úpravu hosta")
    void updateProfile_ThrowsForGuest() {
        String guestEmail = guestCustomer.getEmail();
        when(customerRepository.findByEmailIgnoreCase(guestEmail)).thenReturn(Optional.of(guestCustomer));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> customerService.updateProfile(guestEmail, profileUpdateDto));
        assertTrue(exception.getMessage().contains("Profil hosta nelze měnit"));
        verify(customerRepository, never()).save(any());
    }

    // --- Testy Změny Hesla ---
    @Test
    @DisplayName("changePassword úspěšně změní heslo")
    void changePassword_Success() {
        Long customerId = existingCustomer.getId();
        String newPassword = changePasswordDto.getNewPassword();
        String encodedNewPassword = "$2a$10$encodedNoveHeslo";

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(existingCustomer));
        when(passwordEncoder.matches(changePasswordDto.getCurrentPassword(), existingCustomer.getPassword())).thenReturn(true);
        when(passwordEncoder.encode(newPassword)).thenReturn(encodedNewPassword);
        // Není třeba mockovat save, jen ověříme volání

        customerService.changePassword(customerId, changePasswordDto);

        assertEquals(encodedNewPassword, existingCustomer.getPassword()); // Ověříme, že se heslo změnilo v objektu
        verify(customerRepository).findById(customerId);
        verify(passwordEncoder).matches(changePasswordDto.getCurrentPassword(), "$2a$10$encodedStareHeslo");
        verify(passwordEncoder).encode(newPassword);
        verify(customerRepository).save(existingCustomer);
    }

    @Test
    @DisplayName("changePassword vyhodí výjimku pro nesprávné staré heslo")
    void changePassword_IncorrectOldPassword() {
        Long customerId = existingCustomer.getId();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(existingCustomer));
        // Mockujeme, že hesla nesouhlasí
        when(passwordEncoder.matches(changePasswordDto.getCurrentPassword(), existingCustomer.getPassword())).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> customerService.changePassword(customerId, changePasswordDto));
        assertTrue(exception.getMessage().contains("Nesprávné staré heslo"));
        verify(customerRepository).findById(customerId);
        verify(passwordEncoder).matches(changePasswordDto.getCurrentPassword(), "$2a$10$encodedStareHeslo");
        verify(passwordEncoder, never()).encode(anyString());
        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("changePassword vyhodí výjimku pro hosta")
    void changePassword_ThrowsForGuest() {
        Long guestId = guestCustomer.getId();
        when(customerRepository.findById(guestId)).thenReturn(Optional.of(guestCustomer));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> customerService.changePassword(guestId, changePasswordDto));
        assertTrue(exception.getMessage().contains("Host účty nemají heslo"));
        verify(customerRepository, never()).save(any());
    }

    // --- Testy Správy Adres ---

    @Test
    @DisplayName("updateAddress úspěšně aktualizuje fakturační adresu")
    void updateAddress_Invoice_Success() {
        Long customerId = existingCustomer.getId();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(existingCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        Customer updated = customerService.updateAddress(customerId, CustomerService.AddressType.INVOICE, invoiceAddressDto);

        assertEquals(invoiceAddressDto.getCompanyName(), updated.getInvoiceCompanyName());
        assertEquals(invoiceAddressDto.getStreet(), updated.getInvoiceStreet());
        // ... (ověřit ostatní fakturační pole)
        // Dodací adresa by se neměla změnit (pokud useInvoiceAddressAsDelivery bylo false)
        verify(customerRepository).save(existingCustomer);
    }

    @Test
    @DisplayName("updateAddress úspěšně aktualizuje dodací adresu a nastaví flag")
    void updateAddress_Delivery_Success() {
        Long customerId = existingCustomer.getId();
        // Nastavíme původně useInvoiceAddressAsDelivery na true
        existingCustomer.setUseInvoiceAddressAsDelivery(true);

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(existingCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        Customer updated = customerService.updateAddress(customerId, CustomerService.AddressType.DELIVERY, deliveryAddressDto);

        assertEquals(deliveryAddressDto.getFirstName(), updated.getDeliveryFirstName());
        assertEquals(deliveryAddressDto.getStreet(), updated.getDeliveryStreet());
        // ... (ověřit ostatní dodací pole)
        assertFalse(updated.isUseInvoiceAddressAsDelivery(), "Flag useInvoiceAddressAsDelivery by měl být false");
        verify(customerRepository).save(existingCustomer);
    }

    @Test
    @DisplayName("updateAddress vyhodí výjimku pro chybějící jméno/firmu")
    void updateAddress_ThrowsForMissingRecipient() {
        Long customerId = existingCustomer.getId();
        AddressDto invalidDto = new AddressDto(); // Bez jména i firmy
        invalidDto.setStreet("Někde 1");
        invalidDto.setCity("Město");
        invalidDto.setZipCode("11111");
        invalidDto.setCountry("ČR");

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(existingCustomer));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> customerService.updateAddress(customerId, CustomerService.AddressType.INVOICE, invalidDto));
        assertTrue(exception.getMessage().contains("Musí být vyplněn název firmy nebo jméno a příjmení"));
        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("setUseInvoiceAddressAsDelivery správně nastaví flag")
    void setUseInvoiceAddressAsDelivery_SetsFlag() {
        Long customerId = existingCustomer.getId();
        existingCustomer.setUseInvoiceAddressAsDelivery(false); // Původní stav

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(existingCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        Customer updated = customerService.setUseInvoiceAddressAsDelivery(customerId, true);
        assertTrue(updated.isUseInvoiceAddressAsDelivery());
        verify(customerRepository).save(existingCustomer);

        // Zkusíme nastavit zpět na false
        updated = customerService.setUseInvoiceAddressAsDelivery(customerId, false);
        assertFalse(updated.isUseInvoiceAddressAsDelivery());
        verify(customerRepository, times(2)).save(existingCustomer); // Save se volalo dvakrát
    }


    // --- Testy Hostů ---

    @Test
    @DisplayName("getOrCreateGuestFromCheckoutData vytvoří nového hosta")
    void getOrCreateGuest_CreatesNewGuest() throws CustomerService.EmailRegisteredException {
        String guestEmail = checkoutFormDataDto_Guest.getEmail();
        when(customerRepository.findByEmailIgnoreCase(guestEmail)).thenReturn(Optional.empty());
        // Mock save, abychom mohli ověřit data předaná do save
        ArgumentCaptor<Customer> guestCaptor = ArgumentCaptor.forClass(Customer.class);
        when(customerRepository.save(guestCaptor.capture())).thenAnswer(i -> {
            Customer saved = i.getArgument(0);
            saved.setId(10L); // Simulace ID
            return saved;
        });

        Customer createdGuest = customerService.getOrCreateGuestFromCheckoutData(checkoutFormDataDto_Guest);

        assertNotNull(createdGuest);
        assertTrue(createdGuest.isGuest());
        assertEquals(guestEmail, createdGuest.getEmail());
        assertEquals(checkoutFormDataDto_Guest.getFirstName(), createdGuest.getFirstName());
        assertEquals(checkoutFormDataDto_Guest.getInvoiceStreet(), createdGuest.getInvoiceStreet());
        assertTrue(createdGuest.isUseInvoiceAddressAsDelivery()); // Podle DTO
        verify(customerRepository).findByEmailIgnoreCase(guestEmail);
        verify(customerRepository).save(any(Customer.class));

        // Ověření dat zachycených před save
        Customer savedData = guestCaptor.getValue();
        assertTrue(savedData.isGuest());
        assertEquals(guestEmail, savedData.getEmail());
        assertEquals("Guest", savedData.getFirstName());
        assertTrue(savedData.getRoles().contains("ROLE_GUEST"));
    }

    @Test
    @DisplayName("getOrCreateGuestFromCheckoutData aktualizuje existujícího hosta")
    void getOrCreateGuest_UpdatesExistingGuest() throws CustomerService.EmailRegisteredException {
        String guestEmail = guestCustomer.getEmail(); // Email existujícího hosta
        when(customerRepository.findByEmailIgnoreCase(guestEmail)).thenReturn(Optional.of(guestCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        Customer updatedGuest = customerService.getOrCreateGuestFromCheckoutData(checkoutFormDataDto_Registered); // Použijeme DTO s jinými daty

        assertNotNull(updatedGuest);
        assertEquals(guestCustomer.getId(), updatedGuest.getId()); // ID musí zůstat stejné
        assertTrue(updatedGuest.isGuest());
        assertEquals(checkoutFormDataDto_Registered.getFirstName(), updatedGuest.getFirstName()); // Jméno se aktualizovalo
        assertEquals(checkoutFormDataDto_Registered.getInvoiceStreet(), updatedGuest.getInvoiceStreet()); // Adresa se aktualizovala
        assertFalse(updatedGuest.isUseInvoiceAddressAsDelivery()); // Flag se aktualizoval
        assertEquals(checkoutFormDataDto_Registered.getDeliveryStreet(), updatedGuest.getDeliveryStreet()); // Dodací adresa se nastavila

        verify(customerRepository).findByEmailIgnoreCase(guestEmail);
        verify(customerRepository).save(guestCustomer); // Ověříme, že se ukládal původní objekt s novými daty
    }

    @Test
    @DisplayName("getOrCreateGuestFromCheckoutData vyhodí výjimku pro registrovaný email")
    void getOrCreateGuest_ThrowsForRegisteredEmail() {
        String registeredEmail = existingCustomer.getEmail();
        when(customerRepository.findByEmailIgnoreCase(registeredEmail)).thenReturn(Optional.of(existingCustomer));

        CheckoutFormDataDto dtoWithRegisteredEmail = new CheckoutFormDataDto();
        dtoWithRegisteredEmail.setEmail(registeredEmail);
        // ... (ostatní data nejsou podstatná)

        CustomerService.EmailRegisteredException exception = assertThrows(CustomerService.EmailRegisteredException.class, () -> customerService.getOrCreateGuestFromCheckoutData(dtoWithRegisteredEmail));

        assertTrue(exception.getMessage().contains("Tento email je již zaregistrován"));
        verify(customerRepository).findByEmailIgnoreCase(registeredEmail);
        verify(customerRepository, never()).save(any());
    }

    // --- Test Pomocné Metody (pokud by byla public/package-private) ---
    // Jelikož updateCustomerFromDto je private, testujeme ji nepřímo přes getOrCreateGuest...
    // Pokud bys ji chtěl testovat přímo, musel bys změnit její viditelnost.

}