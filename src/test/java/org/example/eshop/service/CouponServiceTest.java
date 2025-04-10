package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
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
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime; // Používáme LocalDateTime
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock private CouponRepository couponRepository;
    @Mock private OrderRepository orderRepository;

    @InjectMocks private CouponService couponService;

    private Coupon couponPercent;
    private Coupon couponFixed;
    private Coupon couponFreeShipping;
    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        // Stejná data jako předtím, odpovídají vašemu modelu Coupon
        couponPercent = new Coupon(); couponPercent.setId(1L); couponPercent.setCode("SLEVA10"); couponPercent.setName("Sleva 10%");
        couponPercent.setPercentage(true); couponPercent.setValue(new BigDecimal("10.00"));
        couponPercent.setStartDate(LocalDateTime.now().minusDays(1)); couponPercent.setExpirationDate(LocalDateTime.now().plusDays(10));
        couponPercent.setUsageLimit(100); couponPercent.setUsedTimes(10); couponPercent.setUsageLimitPerCustomer(1);
        couponPercent.setMinimumOrderValueCZK(new BigDecimal("500")); couponPercent.setActive(true);

        couponFixed = new Coupon(); couponFixed.setId(2L); couponFixed.setCode("KC200"); couponFixed.setName("Sleva 200 Kč");
        couponFixed.setPercentage(false); couponFixed.setValueCZK(new BigDecimal("200.00")); couponFixed.setValueEUR(new BigDecimal("8.00"));
        couponFixed.setStartDate(LocalDateTime.now().minusDays(1)); couponFixed.setExpirationDate(null); // No expiration
        couponFixed.setUsageLimit(null); couponFixed.setUsedTimes(5); couponFixed.setUsageLimitPerCustomer(null);
        couponFixed.setMinimumOrderValueCZK(new BigDecimal("1000")); couponFixed.setActive(true);

        couponFreeShipping = new Coupon(); couponFreeShipping.setId(3L); couponFreeShipping.setCode("DOPRAVAZDARMA"); couponFreeShipping.setName("Doprava Zdarma");
        couponFreeShipping.setPercentage(false); couponFreeShipping.setFreeShipping(true); // Flag pro dopravu
        couponFreeShipping.setStartDate(LocalDateTime.now().minusDays(1)); couponFreeShipping.setExpirationDate(LocalDateTime.now().plusDays(5));
        couponFreeShipping.setUsageLimit(50); couponFreeShipping.setUsedTimes(0); couponFreeShipping.setActive(true);

        testCustomer = new Customer(); testCustomer.setId(1L); testCustomer.setEmail("test@user.com"); testCustomer.setGuest(false);
    }

    // --- Testy pro validaci a aplikaci (zůstávají stejné) ---
    @Test
    void isCouponGenerallyValid_ValidCoupon_ReturnsTrue() {
        assertTrue(couponService.isCouponGenerallyValid(couponPercent));
    }
    // ... (ostatní isCouponGenerallyValid testy) ...
    @Test
    void isCouponGenerallyValid_Inactive_ReturnsFalse() {
        couponPercent.setActive(false);
        assertFalse(couponService.isCouponGenerallyValid(couponPercent));
    }
    @Test
    void isCouponGenerallyValid_NotYetValid_ReturnsFalse() {
        couponPercent.setStartDate(LocalDateTime.now().plusDays(1));
        assertFalse(couponService.isCouponGenerallyValid(couponPercent));
    }
    @Test
    void isCouponGenerallyValid_Expired_ReturnsFalse() {
        couponPercent.setExpirationDate(LocalDateTime.now().minusDays(1));
        assertFalse(couponService.isCouponGenerallyValid(couponPercent));
    }
    @Test
    void isCouponGenerallyValid_UsageLimitReached_ReturnsFalse() {
        couponPercent.setUsedTimes(100); // usedTimes >= usageLimit
        assertFalse(couponService.isCouponGenerallyValid(couponPercent));
    }
    @Test
    void isCouponGenerallyValid_NoExpiration_ReturnsTrue() {
        assertTrue(couponService.isCouponGenerallyValid(couponFixed));
    }
    @Test
    void isCouponGenerallyValid_NoUsageLimit_ReturnsTrue() {
        assertTrue(couponService.isCouponGenerallyValid(couponFixed));
    }


    @Test
    void calculateDiscountAmount_Percentage_CalculatesCorrectly() {
        BigDecimal price = new BigDecimal("1000.00");
        assertEquals(0, new BigDecimal("100.00").compareTo(couponService.calculateDiscountAmount(price, couponPercent, "CZK")));
    }
    // ... (ostatní calculateDiscountAmount testy) ...
    @Test
    void calculateDiscountAmount_FixedCZK_ReturnsCorrectValue() {
        BigDecimal price = new BigDecimal("1000.00");
        assertEquals(0, new BigDecimal("200.00").compareTo(couponService.calculateDiscountAmount(price, couponFixed, "CZK")));
    }
    @Test
    void calculateDiscountAmount_FixedEUR_ReturnsCorrectValue() {
        BigDecimal price = new BigDecimal("100.00");
        assertEquals(0, new BigDecimal("8.00").compareTo(couponService.calculateDiscountAmount(price, couponFixed, "EUR")));
    }
    @Test
    void calculateDiscountAmount_FixedExceedsPrice_ReturnsPrice() {
        BigDecimal price = new BigDecimal("150.00");
        assertEquals(0, new BigDecimal("150.00").compareTo(couponService.calculateDiscountAmount(price, couponFixed, "CZK")));
    }
    @Test
    void calculateDiscountAmount_FreeShipping_ReturnsZero() {
        BigDecimal price = new BigDecimal("1000.00");
        assertEquals(0, BigDecimal.ZERO.compareTo(couponService.calculateDiscountAmount(price, couponFreeShipping, "CZK")));
    }


    @Test
    void checkCustomerUsageLimit_LimitNotReached_ReturnsTrue() {
        couponPercent.setUsageLimitPerCustomer(2);
        when(orderRepository.countByCustomerIdAndAppliedCouponId(testCustomer.getId(), couponPercent.getId())).thenReturn(1L);
        assertTrue(couponService.checkCustomerUsageLimit(testCustomer, couponPercent));
    }
    // ... (ostatní checkCustomerUsageLimit testy) ...
    @Test
    void checkCustomerUsageLimit_NoLimit_ReturnsTrue() {
        couponFixed.setUsageLimitPerCustomer(null);
        assertTrue(couponService.checkCustomerUsageLimit(testCustomer, couponFixed));
    }
    @Test
    void checkCustomerUsageLimit_LimitReached_ReturnsFalse() {
        couponPercent.setUsageLimitPerCustomer(1);
        when(orderRepository.countByCustomerIdAndAppliedCouponId(testCustomer.getId(), couponPercent.getId())).thenReturn(1L);
        assertFalse(couponService.checkCustomerUsageLimit(testCustomer, couponPercent));
    }
    @Test
    void checkCustomerUsageLimit_Guest_ReturnsTrue() {
        testCustomer.setGuest(true);
        couponPercent.setUsageLimitPerCustomer(1);
        assertTrue(couponService.checkCustomerUsageLimit(testCustomer, couponPercent));
        verify(orderRepository, never()).countByCustomerIdAndAppliedCouponId(any(), any());
    }

    @Test
    void markCouponAsUsed_IncrementsCount() {
        Long id = 1L;
        int initialCount = couponPercent.getUsedTimes();
        when(couponRepository.findById(id)).thenReturn(Optional.of(couponPercent));
        when(couponRepository.save(any(Coupon.class))).thenReturn(couponPercent);

        couponService.markCouponAsUsed(couponPercent);

        assertEquals(initialCount + 1, couponPercent.getUsedTimes());
        verify(couponRepository).save(couponPercent);
    }


    // --- Testy pro CMS metody (opravené) ---

    @Test
    @DisplayName("[CMS] getAllCoupons vrátí seznam")
    void getAllCoupons_ReturnsList() {
        List<Coupon> couponList = Arrays.asList(couponPercent, couponFixed);
        when(couponRepository.findAll(Sort.by("code"))).thenReturn(couponList);
        List<Coupon> result = couponService.getAllCoupons();
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(couponRepository).findAll(Sort.by("code"));
    }

    @Test
    @DisplayName("[CMS] createCoupon úspěšně vytvoří kupón")
    void createCoupon_Success() {
        Coupon newCoupon = new Coupon();
        newCoupon.setCode("  CMSNEW "); newCoupon.setName("CMS Test");
        newCoupon.setPercentage(true); newCoupon.setValue(new BigDecimal("5.00"));
        newCoupon.setStartDate(LocalDateTime.now()); newCoupon.setActive(true);

        when(couponRepository.findByCodeIgnoreCase("CMSNEW")).thenReturn(Optional.empty());
        when(couponRepository.save(any(Coupon.class))).thenAnswer(i -> {
            Coupon saved = i.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        Coupon created = couponService.createCoupon(newCoupon);

        assertNotNull(created);
        assertEquals("CMSNEW", created.getCode());
        assertEquals(0, created.getUsedTimes());
        verify(couponRepository).save(any(Coupon.class));
    }

    @Test
    @DisplayName("[CMS] updateCoupon úspěšně aktualizuje kupón")
    void updateCoupon_Success() {
        Long id = 1L;
        Coupon couponData = new Coupon();
        couponData.setCode("SLEVA10EDIT"); couponData.setName("Upravený Název");
        couponData.setPercentage(false); couponData.setFreeShipping(true); // Změna typu
        couponData.setActive(false);

        when(couponRepository.findById(id)).thenReturn(Optional.of(couponPercent));
        when(couponRepository.findByCodeIgnoreCase("SLEVA10EDIT")).thenReturn(Optional.empty());
        // Mock save, aby vracel předaný argument (simulace uložení)
        when(couponRepository.save(any(Coupon.class))).thenAnswer(i -> i.getArgument(0));

        // Voláme metodu updateCoupon, která vrací Coupon
        Coupon updated = couponService.updateCoupon(id, couponData);

        assertNotNull(updated);
        assertEquals("SLEVA10EDIT", updated.getCode());
        assertFalse(updated.isPercentage());
        assertTrue(updated.isFreeShipping());
        assertFalse(updated.isActive());
        assertEquals(10, updated.getUsedTimes()); // Počet použití se nemění
        verify(couponRepository).save(updated); // Ověříme, že se uložil aktualizovaný objekt
    }

    @Test
    @DisplayName("[CMS] updateCoupon vyhodí EntityNotFoundException, pokud ID neexistuje")
    void updateCoupon_NotFound_ThrowsException() {
        Long id = 99L;
        Coupon couponData = new Coupon(); // Data nejsou důležitá
        couponData.setCode("ANYCODE"); couponData.setName("ANYNAME"); couponData.setPercentage(false);
        when(couponRepository.findById(id)).thenReturn(Optional.empty()); // Nenalezeno

        // Očekáváme, že service vyhodí výjimku
        assertThrows(EntityNotFoundException.class, () -> {
            couponService.updateCoupon(id, couponData);
        });

        verify(couponRepository, never()).save(any()); // Save se nesmí volat
    }


    @Test
    @DisplayName("[CMS] deactivateCoupon nastaví active na false")
    void deactivateCoupon_SetsInactive() {
        Long id = 1L;
        when(couponRepository.findById(id)).thenReturn(Optional.of(couponPercent)); // Vrací aktivní
        when(couponRepository.save(any(Coupon.class))).thenReturn(couponPercent);

        // Voláme metodu deactivateCoupon (OPRAVA názvu metody)
        couponService.deactivateCoupon(id);

        assertFalse(couponPercent.isActive());
        verify(couponRepository).save(couponPercent); // Ověříme, že se uložil
    }

    @Test
    @DisplayName("[CMS] deactivateCoupon pro již neaktivní nic neudělá")
    void deactivateCoupon_AlreadyInactive_DoesNothing() {
        Long id = 1L;
        couponPercent.setActive(false); // Nastavíme jako neaktivní
        when(couponRepository.findById(id)).thenReturn(Optional.of(couponPercent));

        couponService.deactivateCoupon(id); // Voláme správnou metodu

        assertFalse(couponPercent.isActive()); // Stále neaktivní
        verify(couponRepository).findById(id);
        verify(couponRepository, never()).save(any()); // Save se nevolá
    }

    @Test
    @DisplayName("[CMS] deactivateCoupon vyhodí EntityNotFoundException, pokud ID neexistuje")
    void deactivateCoupon_NotFound_ThrowsException() {
        Long id = 99L;
        when(couponRepository.findById(id)).thenReturn(Optional.empty()); // Nenalezeno

        assertThrows(EntityNotFoundException.class, () -> {
            couponService.deactivateCoupon(id); // Voláme správnou metodu
        });

        verify(couponRepository, never()).save(any()); // Save se nevolá
    }

    // Test pro validační logiku (příklad)
    @Test
    @DisplayName("[CMS Validation] updateCoupon vyhodí chybu při pokusu nastavit limit nižší než počet použití")
    void updateCoupon_LimitLowerThanUsed_ThrowsException() {
        Long id = 1L; // couponPercent má usedTimes = 10
        Coupon couponData = new Coupon();
        couponData.setCode("SLEVA10"); couponData.setName("Sleva 10%");
        couponData.setPercentage(true); couponData.setValue(new BigDecimal("10"));
        couponData.setActive(true);
        couponData.setUsageLimit(5); // Neplatný limit (menší než 10)

        when(couponRepository.findById(id)).thenReturn(Optional.of(couponPercent));

        // Očekáváme IllegalArgumentException z validateCouponData
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            couponService.updateCoupon(id, couponData);
        });
        assertTrue(ex.getMessage().contains("nižší než aktuální počet použití"));
        verify(couponRepository, never()).save(any());
    }
}