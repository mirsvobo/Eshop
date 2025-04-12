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
import java.time.LocalDateTime;
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
    private Coupon couponFreeShippingOnly;
    private Coupon couponPercentAndShipping;
    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        couponPercent = new Coupon(); couponPercent.setId(1L); couponPercent.setCode("SLEVA10"); couponPercent.setName("Sleva 10%");
        couponPercent.setPercentage(true); couponPercent.setFreeShipping(false);
        couponPercent.setValue(new BigDecimal("10.00"));
        couponPercent.setStartDate(LocalDateTime.now().minusDays(1)); couponPercent.setExpirationDate(LocalDateTime.now().plusDays(10));
        couponPercent.setUsageLimit(100); couponPercent.setUsedTimes(10); couponPercent.setUsageLimitPerCustomer(1);
        couponPercent.setMinimumOrderValueCZK(new BigDecimal("500")); couponPercent.setActive(true);

        couponFixed = new Coupon(); couponFixed.setId(2L); couponFixed.setCode("KC200"); couponFixed.setName("Sleva 200 Kč");
        couponFixed.setPercentage(false); couponFixed.setFreeShipping(false);
        couponFixed.setValueCZK(new BigDecimal("200.00")); couponFixed.setValueEUR(new BigDecimal("8.00"));
        couponFixed.setStartDate(LocalDateTime.now().minusDays(1)); couponFixed.setExpirationDate(null); // No expiration
        couponFixed.setUsageLimit(null); couponFixed.setUsedTimes(5); couponFixed.setUsageLimitPerCustomer(null);
        couponFixed.setMinimumOrderValueCZK(new BigDecimal("1000")); couponFixed.setActive(true);

        couponFreeShippingOnly = new Coupon(); couponFreeShippingOnly.setId(3L); couponFreeShippingOnly.setCode("DOPRAVAZDARMA"); couponFreeShippingOnly.setName("Doprava Zdarma");
        couponFreeShippingOnly.setPercentage(false); couponFreeShippingOnly.setFreeShipping(true);
        couponFreeShippingOnly.setValue(null); couponFreeShippingOnly.setValueCZK(null); couponFreeShippingOnly.setValueEUR(null);
        couponFreeShippingOnly.setStartDate(LocalDateTime.now().minusDays(1)); couponFreeShippingOnly.setExpirationDate(LocalDateTime.now().plusDays(5));
        couponFreeShippingOnly.setUsageLimit(50); couponFreeShippingOnly.setUsedTimes(0); couponFreeShippingOnly.setActive(true);

        couponPercentAndShipping = new Coupon(); couponPercentAndShipping.setId(4L); couponPercentAndShipping.setCode("KOMBO5"); couponPercentAndShipping.setName("Sleva 5% + Doprava Zdarma");
        couponPercentAndShipping.setPercentage(true); couponPercentAndShipping.setFreeShipping(true);
        couponPercentAndShipping.setValue(new BigDecimal("5.00"));
        couponPercentAndShipping.setValueCZK(null); couponPercentAndShipping.setValueEUR(null);
        couponPercentAndShipping.setStartDate(LocalDateTime.now().minusDays(1)); couponPercentAndShipping.setExpirationDate(LocalDateTime.now().plusDays(10));
        couponPercentAndShipping.setActive(true);

        testCustomer = new Customer(); testCustomer.setId(1L); testCustomer.setEmail("test@user.com"); testCustomer.setGuest(false);
    }

    // --- Testy pro validaci a aplikaci ---
    @Test
    void isCouponGenerallyValid_ValidCoupon_ReturnsTrue() { assertTrue(couponService.isCouponGenerallyValid(couponPercent)); }
    @Test
    void isCouponGenerallyValid_Inactive_ReturnsFalse() { couponPercent.setActive(false); assertFalse(couponService.isCouponGenerallyValid(couponPercent)); }
    @Test
    void isCouponGenerallyValid_NotYetValid_ReturnsFalse() { couponPercent.setStartDate(LocalDateTime.now().plusDays(1)); assertFalse(couponService.isCouponGenerallyValid(couponPercent)); }
    @Test
    void isCouponGenerallyValid_Expired_ReturnsFalse() { couponPercent.setExpirationDate(LocalDateTime.now().minusDays(1)); assertFalse(couponService.isCouponGenerallyValid(couponPercent)); }
    @Test
    void isCouponGenerallyValid_UsageLimitReached_ReturnsFalse() { couponPercent.setUsedTimes(100); assertFalse(couponService.isCouponGenerallyValid(couponPercent)); }
    @Test
    void isCouponGenerallyValid_NoExpiration_ReturnsTrue() { assertTrue(couponService.isCouponGenerallyValid(couponFixed)); }
    @Test
    void isCouponGenerallyValid_NoUsageLimit_ReturnsTrue() { assertTrue(couponService.isCouponGenerallyValid(couponFixed)); }

    @Test
    void calculateDiscountAmount_Percentage_CalculatesCorrectly() { BigDecimal price = new BigDecimal("1000.00"); assertEquals(0, new BigDecimal("100.00").compareTo(couponService.calculateDiscountAmount(price, couponPercent, "CZK"))); }
    @Test
    void calculateDiscountAmount_FixedCZK_ReturnsCorrectValue() { BigDecimal price = new BigDecimal("1000.00"); assertEquals(0, new BigDecimal("200.00").compareTo(couponService.calculateDiscountAmount(price, couponFixed, "CZK"))); }
    @Test
    void calculateDiscountAmount_FixedEUR_ReturnsCorrectValue() { BigDecimal price = new BigDecimal("100.00"); assertEquals(0, new BigDecimal("8.00").compareTo(couponService.calculateDiscountAmount(price, couponFixed, "EUR"))); }
    @Test
    void calculateDiscountAmount_FixedExceedsPrice_ReturnsPrice() { BigDecimal price = new BigDecimal("150.00"); assertEquals(0, new BigDecimal("150.00").compareTo(couponService.calculateDiscountAmount(price, couponFixed, "CZK"))); }
    @Test
    @DisplayName("calculateDiscountAmount vrátí 0 pro kupón 'pouze doprava zdarma'")
    void calculateDiscountAmount_FreeShippingOnly_ReturnsZero() { BigDecimal price = new BigDecimal("1000.00"); assertEquals(0, BigDecimal.ZERO.compareTo(couponService.calculateDiscountAmount(price, couponFreeShippingOnly, "CZK"))); }
    @Test
    @DisplayName("calculateDiscountAmount spočítá procentuální slevu pro kombinovaný kupón")
    void calculateDiscountAmount_PercentageAndShipping_CalculatesPercentage() { BigDecimal price = new BigDecimal("1000.00"); assertEquals(0, new BigDecimal("50.00").compareTo(couponService.calculateDiscountAmount(price, couponPercentAndShipping, "CZK"))); }

    @Test
    void checkCustomerUsageLimit_LimitNotReached_ReturnsTrue() { couponPercent.setUsageLimitPerCustomer(2); when(orderRepository.countByCustomerIdAndAppliedCouponId(testCustomer.getId(), couponPercent.getId())).thenReturn(1L); assertTrue(couponService.checkCustomerUsageLimit(testCustomer, couponPercent)); }
    @Test
    void checkCustomerUsageLimit_NoLimit_ReturnsTrue() { couponFixed.setUsageLimitPerCustomer(null); assertTrue(couponService.checkCustomerUsageLimit(testCustomer, couponFixed)); }
    @Test
    void checkCustomerUsageLimit_LimitReached_ReturnsFalse() { couponPercent.setUsageLimitPerCustomer(1); when(orderRepository.countByCustomerIdAndAppliedCouponId(testCustomer.getId(), couponPercent.getId())).thenReturn(1L); assertFalse(couponService.checkCustomerUsageLimit(testCustomer, couponPercent)); }
    @Test
    void checkCustomerUsageLimit_Guest_ReturnsTrue() { testCustomer.setGuest(true); couponPercent.setUsageLimitPerCustomer(1); assertTrue(couponService.checkCustomerUsageLimit(testCustomer, couponPercent)); verify(orderRepository, never()).countByCustomerIdAndAppliedCouponId(any(), any()); }

    @Test
    void markCouponAsUsed_IncrementsCount() { Long id = 1L; int initialCount = couponPercent.getUsedTimes(); when(couponRepository.findById(id)).thenReturn(Optional.of(couponPercent)); when(couponRepository.save(any(Coupon.class))).thenReturn(couponPercent); couponService.markCouponAsUsed(couponPercent); assertEquals(initialCount + 1, couponPercent.getUsedTimes()); verify(couponRepository).save(couponPercent); }

    // --- Testy pro CMS metody ---
    @Test
    @DisplayName("[CMS] getAllCoupons vrátí seznam")
    void getAllCoupons_ReturnsList() { List<Coupon> couponList = Arrays.asList(couponPercent, couponFixed); when(couponRepository.findAll(Sort.by("code"))).thenReturn(couponList); List<Coupon> result = couponService.getAllCoupons(); assertNotNull(result); assertEquals(2, result.size()); verify(couponRepository).findAll(Sort.by("code")); }

    @Test
    @DisplayName("[CMS] createCoupon úspěšně vytvoří kupón")
    void createCoupon_Success() {
        Coupon newCoupon = new Coupon(); newCoupon.setCode("  CMSNEW "); newCoupon.setName("CMS Test");
        newCoupon.setPercentage(true); newCoupon.setValue(new BigDecimal("5.00")); newCoupon.setFreeShipping(false);
        newCoupon.setStartDate(LocalDateTime.now()); newCoupon.setActive(true);
        when(couponRepository.findByCodeIgnoreCase("CMSNEW")).thenReturn(Optional.empty());
        when(couponRepository.save(any(Coupon.class))).thenAnswer(i -> { Coupon saved = i.getArgument(0); saved.setId(10L); return saved; });
        Coupon created = couponService.createCoupon(newCoupon);
        assertNotNull(created); assertEquals("CMSNEW", created.getCode()); assertEquals(0, created.getUsedTimes());
        assertTrue(created.isPercentage()); assertFalse(created.isFreeShipping()); // Ověření flagů
        verify(couponRepository).save(any(Coupon.class));
    }

    @Test
    @DisplayName("[CMS] createCoupon úspěšně vytvoří kupón s dopravou zdarma")
    void createCoupon_FreeShippingOnly_Success() {
        Coupon newCoupon = new Coupon(); newCoupon.setCode("FREESHIPCMS"); newCoupon.setName("CMS Doprava Zdarma");
        newCoupon.setPercentage(false); newCoupon.setFreeShipping(true); // Pouze doprava
        newCoupon.setStartDate(LocalDateTime.now()); newCoupon.setActive(true);
        when(couponRepository.findByCodeIgnoreCase("FREESHIPCMS")).thenReturn(Optional.empty());
        when(couponRepository.save(any(Coupon.class))).thenAnswer(i -> i.getArgument(0));
        Coupon created = couponService.createCoupon(newCoupon);
        assertNotNull(created); assertTrue(created.isFreeShipping()); assertFalse(created.isPercentage());
        assertNull(created.getValue()); assertNull(created.getValueCZK()); assertNull(created.getValueEUR());
        verify(couponRepository).save(created);
    }

    @Test
    @DisplayName("[CMS] createCoupon úspěšně vytvoří kombinovaný kupón (5% + doprava)")
    void createCoupon_Combined_Success() {
        Coupon newCoupon = new Coupon(); newCoupon.setCode("KOMBO"); newCoupon.setName("Kombinovaná Sleva");
        newCoupon.setPercentage(true); newCoupon.setValue(new BigDecimal("5.00")); // 5%
        newCoupon.setFreeShipping(true); // + Doprava zdarma
        newCoupon.setStartDate(LocalDateTime.now()); newCoupon.setActive(true);
        when(couponRepository.findByCodeIgnoreCase("KOMBO")).thenReturn(Optional.empty());
        when(couponRepository.save(any(Coupon.class))).thenAnswer(i -> { Coupon saved = i.getArgument(0); saved.setId(11L); return saved; });
        Coupon created = couponService.createCoupon(newCoupon);
        assertNotNull(created); assertTrue(created.isPercentage()); assertTrue(created.isFreeShipping());
        assertEquals(0, new BigDecimal("5.00").compareTo(created.getValue()));
        assertNull(created.getValueCZK()); assertNull(created.getValueEUR());
        verify(couponRepository).save(created);
    }


    @Test
    @DisplayName("[CMS] updateCoupon úspěšně aktualizuje kupón")
    void updateCoupon_Success() {
        Long id = 1L; Coupon couponData = new Coupon(); couponData.setCode("SLEVA10EDIT"); couponData.setName("Upravený Název");
        couponData.setPercentage(false); couponData.setFreeShipping(true); couponData.setActive(false);
        when(couponRepository.findById(id)).thenReturn(Optional.of(couponPercent));
        when(couponRepository.findByCodeIgnoreCase("SLEVA10EDIT")).thenReturn(Optional.empty());
        when(couponRepository.save(any(Coupon.class))).thenAnswer(i -> i.getArgument(0));
        Coupon updated = couponService.updateCoupon(id, couponData);
        assertNotNull(updated); assertEquals("SLEVA10EDIT", updated.getCode()); assertFalse(updated.isPercentage());
        assertTrue(updated.isFreeShipping()); assertFalse(updated.isActive()); verify(couponRepository).save(updated);
    }

    @Test
    @DisplayName("[CMS] updateCoupon vyhodí EntityNotFoundException")
    void updateCoupon_NotFound_ThrowsException() { Long id = 99L; Coupon couponData = new Coupon(); couponData.setCode("ANY"); couponData.setName("ANY"); couponData.setPercentage(false); when(couponRepository.findById(id)).thenReturn(Optional.empty()); assertThrows(EntityNotFoundException.class, () -> couponService.updateCoupon(id, couponData)); verify(couponRepository, never()).save(any()); }

    @Test
    @DisplayName("[CMS] deactivateCoupon nastaví active na false")
    void deactivateCoupon_SetsInactive() { Long id = 1L; when(couponRepository.findById(id)).thenReturn(Optional.of(couponPercent)); when(couponRepository.save(any(Coupon.class))).thenReturn(couponPercent); couponService.deactivateCoupon(id); assertFalse(couponPercent.isActive()); verify(couponRepository).save(couponPercent); }

    @Test
    @DisplayName("[CMS] deactivateCoupon pro již neaktivní nic neudělá")
    void deactivateCoupon_AlreadyInactive_DoesNothing() { Long id = 1L; couponPercent.setActive(false); when(couponRepository.findById(id)).thenReturn(Optional.of(couponPercent)); couponService.deactivateCoupon(id); assertFalse(couponPercent.isActive()); verify(couponRepository).findById(id); verify(couponRepository, never()).save(any()); }

    @Test
    @DisplayName("[CMS Validation] vyhodí chybu, pokud není sleva ani doprava zdarma")
    void validation_ThrowsIfNoValueAndNoShipping() {
        Coupon invalidCoupon = new Coupon(); invalidCoupon.setCode("NIC"); invalidCoupon.setName("Neplatný");
        invalidCoupon.setPercentage(false); invalidCoupon.setFreeShipping(false); // Není ani sleva, ani doprava
        invalidCoupon.setValueCZK(null); invalidCoupon.setValueEUR(null); invalidCoupon.setValue(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> couponService.createCoupon(invalidCoupon));
        assertTrue(ex.getMessage().contains("musí mít definovanou"));
    }

    @Test
    @DisplayName("[CMS Validation] vyhodí chybu pro procentuální s neplatnou hodnotou")
    void validation_ThrowsIfPercentageAndInvalidValue() {
        Coupon invalidCoupon = new Coupon(); invalidCoupon.setCode("CHYBA"); invalidCoupon.setName("Chybný");
        invalidCoupon.setPercentage(true); invalidCoupon.setFreeShipping(false); // Není doprava
        invalidCoupon.setValue(new BigDecimal("-5.00")); // Neplatná hodnota
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> couponService.createCoupon(invalidCoupon));
        assertTrue(ex.getMessage().contains("Procentuální hodnota"));
    }

    @Test
    @DisplayName("[CMS Validation] vyhodí chybu pro fixní bez hodnoty")
    void validation_ThrowsIfFixedAndNoValue() {
        Coupon invalidCoupon = new Coupon(); invalidCoupon.setCode("CHYBAFIX"); invalidCoupon.setName("Chybný Fix");
        invalidCoupon.setPercentage(false); // Fixní
        invalidCoupon.setFreeShipping(false); // Není doprava
        invalidCoupon.setValueCZK(null); // Žádná hodnota
        invalidCoupon.setValueEUR(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> couponService.createCoupon(invalidCoupon));
        assertTrue(ex.getMessage().contains("musí mít definovanou"));
    }

    @Test
    @DisplayName("[CMS Validation] projde pro 'pouze doprava zdarma'")
    void validation_PassesForFreeShippingOnly() {
        Coupon validCoupon = new Coupon(); validCoupon.setCode("SHIPONLY"); validCoupon.setName("Doprava");
        validCoupon.setPercentage(false); validCoupon.setFreeShipping(true);
        validCoupon.setValue(null); validCoupon.setValueCZK(null); validCoupon.setValueEUR(null);
        validCoupon.setStartDate(LocalDateTime.now()); validCoupon.setActive(true);
        when(couponRepository.findByCodeIgnoreCase("SHIPONLY")).thenReturn(Optional.empty());
        when(couponRepository.save(any(Coupon.class))).thenAnswer(i -> i.getArgument(0));
        assertDoesNotThrow(() -> couponService.createCoupon(validCoupon));
        verify(couponRepository).save(any(Coupon.class));
    }

    @Test
    @DisplayName("[CMS Validation] projde pro kombinaci procenta 0 a dopravy zdarma")
    void validation_PassesForZeroPercentAndFreeShipping() {
        Coupon validCoupon = new Coupon(); validCoupon.setCode("KOMBOZERO"); validCoupon.setName("Doprava + 0%");
        validCoupon.setPercentage(true); validCoupon.setValue(BigDecimal.ZERO); // Nulová sleva
        validCoupon.setFreeShipping(true); // Doprava zdarma
        validCoupon.setStartDate(LocalDateTime.now()); validCoupon.setActive(true);
        when(couponRepository.findByCodeIgnoreCase("KOMBOZERO")).thenReturn(Optional.empty());
        when(couponRepository.save(any(Coupon.class))).thenAnswer(i -> i.getArgument(0));
        assertDoesNotThrow(() -> couponService.createCoupon(validCoupon));
        verify(couponRepository).save(any(Coupon.class));
    }

}