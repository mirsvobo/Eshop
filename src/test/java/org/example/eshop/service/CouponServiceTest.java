// Soubor: src/test/java/org/example/eshop/service/CouponServiceTest.java
package org.example.eshop.service;

import org.example.eshop.model.Coupon;
import org.example.eshop.model.Customer;
import org.example.eshop.repository.CouponRepository;
import org.example.eshop.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Povolí použití Mockito anotací
class CouponServiceTest {

    @Mock // Mockujeme závislosti CouponService
    private CouponRepository couponRepository;
    @Mock
    private OrderRepository orderRepository; // Potřebujeme pro checkCustomerUsageLimit

    @InjectMocks // Vytvoří instanci CouponService a injektuje mocky
    private CouponService couponService;

    private Coupon activePercentCoupon;
    private Coupon activeFixedCoupon;
    private Coupon inactiveCoupon;
    private Coupon expiredCoupon;
    private Coupon futureCoupon;
    private Coupon usageLimitCoupon;
    private Coupon minOrderValueCoupon;
    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();

        activePercentCoupon = new Coupon();
        activePercentCoupon.setId(1L);
        activePercentCoupon.setCode("PERCENT10");
        activePercentCoupon.setActive(true);
        activePercentCoupon.setPercentage(true);
        activePercentCoupon.setValue(new BigDecimal("10.00")); // 10%
        activePercentCoupon.setStartDate(now.minusDays(1));
        activePercentCoupon.setExpirationDate(now.plusDays(1));
        activePercentCoupon.setUsedTimes(0);

        activeFixedCoupon = new Coupon();
        activeFixedCoupon.setId(2L);
        activeFixedCoupon.setCode("FIXED100CZK");
        activeFixedCoupon.setActive(true);
        activeFixedCoupon.setPercentage(false);
        activeFixedCoupon.setValueCZK(new BigDecimal("100.00"));
        activeFixedCoupon.setValueEUR(new BigDecimal("4.00"));
        activeFixedCoupon.setStartDate(now.minusDays(1));
        activeFixedCoupon.setExpirationDate(now.plusDays(1));
        activeFixedCoupon.setUsedTimes(0);

        inactiveCoupon = new Coupon();
        inactiveCoupon.setId(3L);
        inactiveCoupon.setCode("INACTIVE");
        inactiveCoupon.setActive(false); // Neaktivní
        inactiveCoupon.setStartDate(now.minusDays(1));
        inactiveCoupon.setExpirationDate(now.plusDays(1));

        expiredCoupon = new Coupon();
        expiredCoupon.setId(4L);
        expiredCoupon.setCode("EXPIRED");
        expiredCoupon.setActive(true);
        expiredCoupon.setStartDate(now.minusDays(10));
        expiredCoupon.setExpirationDate(now.minusDays(1)); // Vypršel včera

        futureCoupon = new Coupon();
        futureCoupon.setId(5L);
        futureCoupon.setCode("FUTURE");
        futureCoupon.setActive(true);
        futureCoupon.setStartDate(now.plusDays(1)); // Platí až od zítřka
        futureCoupon.setExpirationDate(now.plusDays(10));

        usageLimitCoupon = new Coupon();
        usageLimitCoupon.setId(6L);
        usageLimitCoupon.setCode("LIMIT1");
        usageLimitCoupon.setActive(true);
        usageLimitCoupon.setUsageLimit(1); // Limit 1 použití
        usageLimitCoupon.setUsedTimes(1); // Už byl použit jednou
        usageLimitCoupon.setStartDate(now.minusDays(1));
        usageLimitCoupon.setExpirationDate(now.plusDays(1));

        minOrderValueCoupon = new Coupon();
        minOrderValueCoupon.setId(7L);
        minOrderValueCoupon.setCode("MIN500CZK");
        minOrderValueCoupon.setActive(true);
        minOrderValueCoupon.setStartDate(now.minusDays(1));
        minOrderValueCoupon.setExpirationDate(now.plusDays(1));
        minOrderValueCoupon.setMinimumOrderValueCZK(new BigDecimal("500.00"));
        minOrderValueCoupon.setMinimumOrderValueEUR(new BigDecimal("20.00"));
        // Tento kupón je fixní, ale pro test min value je to jedno
        minOrderValueCoupon.setPercentage(false);
        minOrderValueCoupon.setValueCZK(new BigDecimal("50"));
        minOrderValueCoupon.setValueEUR(new BigDecimal("2"));


        testCustomer = new Customer();
        testCustomer.setId(1L);
        testCustomer.setEmail("test@customer.com");
    }

    // --- Testy Obecné Platnosti Kupónu ---

    @Test
    @DisplayName("isCouponGenerallyValid vrátí true pro platný kupón")
    void isCouponGenerallyValid_ValidCoupon_ReturnsTrue() {
        assertTrue(couponService.isCouponGenerallyValid(activePercentCoupon));
    }

    @Test
    @DisplayName("isCouponGenerallyValid vrátí false pro neaktivní kupón")
    void isCouponGenerallyValid_InactiveCoupon_ReturnsFalse() {
        assertFalse(couponService.isCouponGenerallyValid(inactiveCoupon));
    }

    @Test
    @DisplayName("isCouponGenerallyValid vrátí false pro expirovaný kupón")
    void isCouponGenerallyValid_ExpiredCoupon_ReturnsFalse() {
        assertFalse(couponService.isCouponGenerallyValid(expiredCoupon));
    }

    @Test
    @DisplayName("isCouponGenerallyValid vrátí false pro budoucí kupón")
    void isCouponGenerallyValid_FutureCoupon_ReturnsFalse() {
        assertFalse(couponService.isCouponGenerallyValid(futureCoupon));
    }

    @Test
    @DisplayName("isCouponGenerallyValid vrátí false pro kupón s dosaženým limitem použití")
    void isCouponGenerallyValid_UsageLimitReached_ReturnsFalse() {
        assertFalse(couponService.isCouponGenerallyValid(usageLimitCoupon));
    }

    @Test
    @DisplayName("isCouponGenerallyValid vrátí true pro kupón s limitem použití, který ještě nebyl dosažen")
    void isCouponGenerallyValid_UsageLimitNotReached_ReturnsTrue() {
        usageLimitCoupon.setUsedTimes(0); // Snížíme počet použití
        assertTrue(couponService.isCouponGenerallyValid(usageLimitCoupon));
    }

    @Test
    @DisplayName("isCouponGenerallyValid vrátí true pro kupón bez limitu použití")
    void isCouponGenerallyValid_NoUsageLimit_ReturnsTrue() {
        activeFixedCoupon.setUsageLimit(null); // Odstraníme limit
        assertTrue(couponService.isCouponGenerallyValid(activeFixedCoupon));
    }

    @Test
    @DisplayName("isCouponGenerallyValid vrátí false pro null kupón")
    void isCouponGenerallyValid_NullCoupon_ReturnsFalse() {
        assertFalse(couponService.isCouponGenerallyValid(null));
    }


    // --- Testy Výpočtu Slevy ---

    @Test
    @DisplayName("calculateDiscountAmount spočítá procentuální slevu správně (CZK)")
    void calculateDiscountAmount_Percentage_CZK() {
        BigDecimal price = new BigDecimal("1500.00");
        BigDecimal expectedDiscount = new BigDecimal("150.00"); // 10% z 1500
        assertEquals(0, expectedDiscount.compareTo(couponService.calculateDiscountAmount(price, activePercentCoupon, "CZK")));
    }

    @Test
    @DisplayName("calculateDiscountAmount spočítá fixní slevu správně (CZK)")
    void calculateDiscountAmount_Fixed_CZK() {
        BigDecimal price = new BigDecimal("1500.00");
        BigDecimal expectedDiscount = new BigDecimal("100.00"); // Pevná sleva z kupónu
        assertEquals(0, expectedDiscount.compareTo(couponService.calculateDiscountAmount(price, activeFixedCoupon, "CZK")));
    }

    @Test
    @DisplayName("calculateDiscountAmount spočítá fixní slevu správně (EUR)")
    void calculateDiscountAmount_Fixed_EUR() {
        BigDecimal price = new BigDecimal("60.00");
        BigDecimal expectedDiscount = new BigDecimal("4.00"); // Pevná sleva z kupónu
        assertEquals(0, expectedDiscount.compareTo(couponService.calculateDiscountAmount(price, activeFixedCoupon, "EUR")));
    }

    @Test
    @DisplayName("calculateDiscountAmount omezí fixní slevu na cenu produktu")
    void calculateDiscountAmount_FixedHigherThanPrice() {
        BigDecimal price = new BigDecimal("50.00"); // Cena je nižší než sleva 100 CZK
        BigDecimal expectedDiscount = new BigDecimal("50.00"); // Sleva nesmí být vyšší než cena
        assertEquals(0, expectedDiscount.compareTo(couponService.calculateDiscountAmount(price, activeFixedCoupon, "CZK")));
    }

    @Test
    @DisplayName("calculateDiscountAmount vrátí nulu pro neplatný kupón nebo cenu")
    void calculateDiscountAmount_InvalidInput_ReturnsZero() {
        BigDecimal price = new BigDecimal("1000");
        assertEquals(0, BigDecimal.ZERO.compareTo(couponService.calculateDiscountAmount(price, inactiveCoupon, "CZK")));
        assertEquals(0, BigDecimal.ZERO.compareTo(couponService.calculateDiscountAmount(null, activePercentCoupon, "CZK")));
        assertEquals(0, BigDecimal.ZERO.compareTo(couponService.calculateDiscountAmount(price, null, "CZK")));
        assertEquals(0, BigDecimal.ZERO.compareTo(couponService.calculateDiscountAmount(BigDecimal.ZERO, activePercentCoupon, "CZK")));
    }

    // --- Testy Minimální Hodnoty Objednávky ---

    @Test
    @DisplayName("checkMinimumOrderValue vrátí true, pokud je hodnota splněna (CZK)")
    void checkMinimumOrderValue_ValueMet_CZK_ReturnsTrue() {
        assertTrue(couponService.checkMinimumOrderValue(minOrderValueCoupon, new BigDecimal("500.00"), "CZK"));
        assertTrue(couponService.checkMinimumOrderValue(minOrderValueCoupon, new BigDecimal("600.00"), "CZK"));
    }

    @Test
    @DisplayName("checkMinimumOrderValue vrátí false, pokud hodnota není splněna (CZK)")
    void checkMinimumOrderValue_ValueNotMet_CZK_ReturnsFalse() {
        assertFalse(couponService.checkMinimumOrderValue(minOrderValueCoupon, new BigDecimal("499.99"), "CZK"));
    }

    @Test
    @DisplayName("checkMinimumOrderValue vrátí true, pokud je hodnota splněna (EUR)")
    void checkMinimumOrderValue_ValueMet_EUR_ReturnsTrue() {
        assertTrue(couponService.checkMinimumOrderValue(minOrderValueCoupon, new BigDecimal("20.00"), "EUR"));
        assertTrue(couponService.checkMinimumOrderValue(minOrderValueCoupon, new BigDecimal("25.00"), "EUR"));
    }

    @Test
    @DisplayName("checkMinimumOrderValue vrátí false, pokud hodnota není splněna (EUR)")
    void checkMinimumOrderValue_ValueNotMet_EUR_ReturnsFalse() {
        assertFalse(couponService.checkMinimumOrderValue(minOrderValueCoupon, new BigDecimal("19.99"), "EUR"));
    }

    @Test
    @DisplayName("checkMinimumOrderValue vrátí true, pokud není minimální hodnota nastavena")
    void checkMinimumOrderValue_NotSet_ReturnsTrue() {
        activePercentCoupon.setMinimumOrderValueCZK(null);
        activePercentCoupon.setMinimumOrderValueEUR(null);
        assertTrue(couponService.checkMinimumOrderValue(activePercentCoupon, new BigDecimal("10.00"), "CZK"));
        assertTrue(couponService.checkMinimumOrderValue(activePercentCoupon, new BigDecimal("10.00"), "EUR"));
    }

    // --- Testy Limitu Použití pro Zákazníka ---

    @Test
    @DisplayName("checkCustomerUsageLimit vrátí true, pokud není limit nastaven")
    void checkCustomerUsageLimit_NoLimitSet_ReturnsTrue() {
        activePercentCoupon.setUsageLimitPerCustomer(null);
        assertTrue(couponService.checkCustomerUsageLimit(testCustomer, activePercentCoupon));
        // Ověříme, že se DB ani neptáme
        verify(orderRepository, never()).countByCustomerAndAppliedCoupon(any(), any());
    }

    @Test
    @DisplayName("checkCustomerUsageLimit vrátí true, pokud limit není dosažen")
    void checkCustomerUsageLimit_LimitNotReached_ReturnsTrue() {
        activePercentCoupon.setUsageLimitPerCustomer(2); // Limit 2 použití
        // Mockujeme, že zákazník použil kupón jednou
        when(orderRepository.countByCustomerAndAppliedCoupon(testCustomer, activePercentCoupon)).thenReturn(1L);

        assertTrue(couponService.checkCustomerUsageLimit(testCustomer, activePercentCoupon));
        verify(orderRepository).countByCustomerAndAppliedCoupon(testCustomer, activePercentCoupon);
    }

    @Test
    @DisplayName("checkCustomerUsageLimit vrátí false, pokud je limit dosažen")
    void checkCustomerUsageLimit_LimitReached_ReturnsFalse() {
        activePercentCoupon.setUsageLimitPerCustomer(1); // Limit 1 použití
        // Mockujeme, že zákazník použil kupón jednou
        when(orderRepository.countByCustomerAndAppliedCoupon(testCustomer, activePercentCoupon)).thenReturn(1L);

        assertFalse(couponService.checkCustomerUsageLimit(testCustomer, activePercentCoupon));
        verify(orderRepository).countByCustomerAndAppliedCoupon(testCustomer, activePercentCoupon);
    }

    @Test
    @DisplayName("checkCustomerUsageLimit vrátí true pro null zákazníka")
    void checkCustomerUsageLimit_NullCustomer_ReturnsTrue() {
        activePercentCoupon.setUsageLimitPerCustomer(1);
        // Pro hosta/nepřihlášeného by kontrola měla projít (řeší se až v pokladně)
        assertTrue(couponService.checkCustomerUsageLimit(null, activePercentCoupon));
        // Ověříme, že se DB ani neptáme
        verify(orderRepository, never()).countByCustomerAndAppliedCoupon(any(), any());
    }


    // --- Testy CRUD Operací ---

    @Test
    @DisplayName("markCouponAsUsed zvýší počet použití")
    void markCouponAsUsed_IncrementsUsedTimes() {
        Long couponId = activePercentCoupon.getId();
        int initialUsedTimes = activePercentCoupon.getUsedTimes();

        // Mock findById a save
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(activePercentCoupon));
        when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

        couponService.markCouponAsUsed(activePercentCoupon);

        // Ověříme, že se volalo save se správným objektem
        verify(couponRepository).save(activePercentCoupon);
        // Ověříme, že počet použití byl navýšen
        assertEquals(initialUsedTimes + 1, activePercentCoupon.getUsedTimes());
    }

    @Test
    @DisplayName("markCouponAsUsed nic neudělá pro neexistující kupón")
    void markCouponAsUsed_NonExistingCoupon() {
        Long nonExistentId = 999L;
        Coupon nonExistentCoupon = new Coupon(); nonExistentCoupon.setId(nonExistentId);

        when(couponRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // Očekáváme, že metoda jen zaloguje chybu a nespadne
        assertDoesNotThrow(() -> couponService.markCouponAsUsed(nonExistentCoupon));

        verify(couponRepository).findById(nonExistentId);
        verify(couponRepository, never()).save(any()); // Save se nesmí volat
    }

    @Test
    @DisplayName("deleteCoupon označí kupón jako neaktivní")
    void deleteCoupon_MarksInactive() {
        Long couponId = activePercentCoupon.getId();
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(activePercentCoupon));
        when(couponRepository.save(any(Coupon.class))).thenReturn(activePercentCoupon);

        assertTrue(activePercentCoupon.isActive());
        couponService.deleteCoupon(couponId);

        assertFalse(activePercentCoupon.isActive(), "Kupón by měl být neaktivní po smazání");
        verify(couponRepository).findById(couponId);
        verify(couponRepository).save(activePercentCoupon); // Ověříme uložení
    }

    @Test
    @DisplayName("createCoupon úspěšně vytvoří a uloží kupón")
    void createCoupon_Success() {
        Coupon newCoupon = new Coupon();
        newCoupon.setCode("  NOVY15 "); // Otestujeme i oříznutí a velká písmena
        newCoupon.setName("Nový kupón 15%");
        newCoupon.setPercentage(true);
        newCoupon.setValue(new BigDecimal("15.00"));
        newCoupon.setStartDate(LocalDateTime.now());
        newCoupon.setExpirationDate(LocalDateTime.now().plusMonths(1));

        // Mock chování repozitáře
        when(couponRepository.findByCodeIgnoreCase("NOVY15")).thenReturn(Optional.empty()); // Kód zatím neexistuje
        // Při volání save vrátíme objekt, který byl předán, s nastaveným ID a usedTimes=0
        when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> {
            Coupon saved = invocation.getArgument(0);
            saved.setId(100L); // Simulace přidělení ID databází
            assertEquals("NOVY15", saved.getCode()); // Kód by měl být upravený
            assertEquals(0, saved.getUsedTimes()); // usedTimes by mělo být 0
            return saved;
        });

        Coupon created = couponService.createCoupon(newCoupon);

        assertNotNull(created);
        assertEquals(100L, created.getId());
        assertEquals("NOVY15", created.getCode());
        assertEquals(0, created.getUsedTimes());
        verify(couponRepository).findByCodeIgnoreCase("NOVY15");
        verify(couponRepository).save(created);
    }

    @Test
    @DisplayName("createCoupon vyhodí výjimku pro duplicitní kód")
    void createCoupon_ThrowsForDuplicateCode() {
        Coupon newCoupon = new Coupon();
        newCoupon.setCode("PERCENT10"); // Tento kód už existuje v setUp datech
        newCoupon.setName("Duplicitní kupón");
        newCoupon.setStartDate(LocalDateTime.now());
        newCoupon.setExpirationDate(LocalDateTime.now().plusDays(1));

        // Mockujeme, že kód již existuje
        when(couponRepository.findByCodeIgnoreCase("PERCENT10")).thenReturn(Optional.of(activePercentCoupon));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            couponService.createCoupon(newCoupon);
        });

        assertTrue(exception.getMessage().contains("already exists"));
        verify(couponRepository).findByCodeIgnoreCase("PERCENT10");
        verify(couponRepository, never()).save(any()); // Save se nesmí volat
    }

    @Test
    @DisplayName("createCoupon vyhodí výjimku pro chybějící kód")
    void createCoupon_ThrowsForMissingCode() {
        Coupon newCoupon = new Coupon();
        newCoupon.setCode(null); // Chybějící kód
        newCoupon.setName("Kupón bez kódu");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            couponService.createCoupon(newCoupon);
        });

        assertTrue(exception.getMessage().contains("cannot be empty"));
        verify(couponRepository, never()).findByCodeIgnoreCase(any());
        verify(couponRepository, never()).save(any());
    }


    @Test
    @DisplayName("updateCoupon úspěšně aktualizuje kupón")
    void updateCoupon_Success() {
        Long couponId = activeFixedCoupon.getId(); // Budeme aktualizovat FIXE100CZK
        Coupon updateData = new Coupon();
        updateData.setCode("FIXED100CZK"); // Kód se nemění
        updateData.setName("Přejmenovaný Fixní Kupón");
        updateData.setDescription("Nový popis");
        updateData.setPercentage(false); // Zůstává fixní
        updateData.setValueCZK(new BigDecimal("120.00")); // Změna hodnoty
        updateData.setValueEUR(new BigDecimal("5.00"));
        updateData.setStartDate(LocalDateTime.now().minusDays(5));
        updateData.setExpirationDate(LocalDateTime.now().plusMonths(2));
        updateData.setUsageLimit(10);
        updateData.setUsageLimitPerCustomer(2);
        updateData.setMinimumOrderValueCZK(new BigDecimal("600.00"));
        updateData.setActive(false); // Deaktivujeme ho

        // Mock repozitáře
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(activeFixedCoupon));
        when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Coupon> result = couponService.updateCoupon(couponId, updateData);

        assertTrue(result.isPresent());
        Coupon updated = result.get();

        assertEquals("Přejmenovaný Fixní Kupón", updated.getName());
        assertEquals("Nový popis", updated.getDescription());
        assertEquals(0, new BigDecimal("120.00").compareTo(updated.getValueCZK()));
        assertEquals(0, new BigDecimal("5.00").compareTo(updated.getValueEUR()));
        assertNull(updated.getValue(), "Procentuální hodnota by měla být null pro fixní kupón");
        assertEquals(10, updated.getUsageLimit());
        assertEquals(2, updated.getUsageLimitPerCustomer());
        assertEquals(0, new BigDecimal("600.00").compareTo(updated.getMinimumOrderValueCZK()));
        assertFalse(updated.isActive());

        verify(couponRepository).findById(couponId);
        verify(couponRepository).save(updated); // Ověříme save
        // Protože se kód neměnil, findByCodeIgnoreCase by se nemělo volat
        verify(couponRepository, never()).findByCodeIgnoreCase(anyString());
    }

    @Test
    @DisplayName("updateCoupon úspěšně změní kód kupónu")
    void updateCoupon_CodeChangeSuccess() {
        Long couponId = activePercentCoupon.getId();
        Coupon updateData = new Coupon();
        updateData.setCode("   NEWCODE123   "); // Nový kód s mezerami
        updateData.setName(activePercentCoupon.getName()); // Ostatní neměníme pro jednoduchost
        updateData.setPercentage(true);
        updateData.setValue(activePercentCoupon.getValue());
        updateData.setStartDate(activePercentCoupon.getStartDate());
        updateData.setExpirationDate(activePercentCoupon.getExpirationDate());
        updateData.setActive(true);


        when(couponRepository.findById(couponId)).thenReturn(Optional.of(activePercentCoupon));
        // Mockujeme, že nový kód 'NEWCODE123' neexistuje
        when(couponRepository.findByCodeIgnoreCase("NEWCODE123")).thenReturn(Optional.empty());
        when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Coupon> result = couponService.updateCoupon(couponId, updateData);

        assertTrue(result.isPresent());
        assertEquals("NEWCODE123", result.get().getCode()); // Ověříme oříznutý a upravený kód

        verify(couponRepository).findById(couponId);
        verify(couponRepository).findByCodeIgnoreCase("NEWCODE123"); // Ověříme kontrolu unikátnosti
        verify(couponRepository).save(result.get());
    }

    @Test
    @DisplayName("updateCoupon vyhodí výjimku při změně kódu na existující")
    void updateCoupon_ThrowsForExistingCodeConflict() {
        Long couponIdToUpdate = activePercentCoupon.getId(); // Chceme změnit kód pro PERCENT10
        Coupon updateData = new Coupon();
        updateData.setCode("FIXED100CZK"); // Chceme změnit na kód, který už má activeFixedCoupon
        // ... (ostatní data nejsou pro tento test podstatná)

        when(couponRepository.findById(couponIdToUpdate)).thenReturn(Optional.of(activePercentCoupon));
        // Mockujeme, že kód "FIXED100CZK" již existuje a patří kupónu activeFixedCoupon (ID 2)
        when(couponRepository.findByCodeIgnoreCase("FIXED100CZK")).thenReturn(Optional.of(activeFixedCoupon));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            couponService.updateCoupon(couponIdToUpdate, updateData);
        });

        assertTrue(exception.getMessage().contains("is already used by another coupon"));
        verify(couponRepository).findById(couponIdToUpdate);
        verify(couponRepository).findByCodeIgnoreCase("FIXED100CZK");
        verify(couponRepository, never()).save(any()); // Save se nesmí volat
    }

    @Test
    @DisplayName("updateCoupon vrátí empty Optional pro neexistující ID")
    void updateCoupon_NotFound() {
        Long nonExistentId = 999L;
        Coupon updateData = new Coupon();
        updateData.setCode("NEEXISTUJE");
        // ...

        when(couponRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        Optional<Coupon> result = couponService.updateCoupon(nonExistentId, updateData);

        assertTrue(result.isEmpty());
        verify(couponRepository).findById(nonExistentId);
        verify(couponRepository, never()).findByCodeIgnoreCase(anyString());
        verify(couponRepository, never()).save(any());
    }

}