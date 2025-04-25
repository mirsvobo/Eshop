package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.model.Coupon;
import org.example.eshop.model.Customer;
import org.example.eshop.repository.CouponRepository;
import org.example.eshop.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.example.eshop.config.PriceConstants.*;

@Service
public class CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponService.class);

    private static final Locale CZECH_LOCALE = Locale.forLanguageTag("cs-CZ");
    private static final Locale EURO_LOCALE = Locale.forLanguageTag("sk-SK");

    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private OrderRepository orderRepository;

    // --- Metody pro čtení ---
    @Transactional(readOnly = true)
    public Optional<Coupon> findById(Long id) {
        log.debug("Fetching coupon by ID: {}", id);
        return couponRepository.findById(id);
    }

    @Cacheable(value = "couponByCode", key = "T(String).valueOf(#code).trim().toUpperCase()", unless = "#result == null or !#result.isPresent()")
    @Transactional(readOnly = true)
    public Optional<Coupon> findByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }
        String trimmedCode = code.trim().toUpperCase();
        log.debug("Finding coupon by code (case-insensitive): '{}'", trimmedCode);
        return couponRepository.findByCodeIgnoreCase(trimmedCode);
    }

    // --- Metody pro validaci a aplikaci ---
    @Transactional(readOnly = true)
    public boolean isCouponGenerallyValid(Coupon coupon) {
        if (coupon == null) {
            log.warn("isCouponGenerallyValid called with null coupon.");
            return false;
        }
        if (!coupon.isActive()) {
            log.debug("Coupon '{}' invalid: inactive.", coupon.getCode());
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStartDate() != null && now.isBefore(coupon.getStartDate())) {
            log.debug("Coupon '{}' invalid: not yet valid (from {}).", coupon.getCode(), coupon.getStartDate());
            return false;
        }
        if (coupon.getExpirationDate() != null && now.isAfter(coupon.getExpirationDate())) {
            log.debug("Coupon '{}' invalid: expired (to {}).", coupon.getCode(), coupon.getExpirationDate());
            return false;
        }
        if (coupon.getUsageLimit() != null && coupon.getUsageLimit() > 0
                && coupon.getUsedTimes() >= coupon.getUsageLimit()) {
            log.debug("Coupon '{}' invalid: total usage limit ({}) reached (used: {}).", coupon.getCode(), coupon.getUsageLimit(), coupon.getUsedTimes());
            return false;
        }
        log.debug("Coupon '{}' passed general validity checks.", coupon.getCode());
        return true;
    }

    public BigDecimal calculateDiscountAmount(BigDecimal subTotalWithoutTax, Coupon coupon, String currency) {
        if (coupon == null || subTotalWithoutTax == null || subTotalWithoutTax.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        // Kupóny "pouze doprava zdarma" neposkytují slevu na ceně zboží
        if (coupon.isFreeShippingOnly()) {
            log.debug("Coupon '{}' is free shipping only. Returning zero discount amount.", coupon.getCode());
            return BigDecimal.ZERO;
        }

        BigDecimal discountAmount = BigDecimal.ZERO;
        if (coupon.isPercentage()) {
            if (coupon.getValue() != null && coupon.getValue().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal percentage = coupon.getValue().divide(new BigDecimal("100"), CALCULATION_SCALE, ROUNDING_MODE);
                discountAmount = subTotalWithoutTax.multiply(percentage);
            } else {
                log.warn("Percentage coupon '{}' has invalid value: {}. Applying zero discount.", coupon.getCode(), coupon.getValue());
            }
        } else { // Pevná částka
            BigDecimal fixedValue = EURO_CURRENCY.equals(currency) ? coupon.getValueEUR() : coupon.getValueCZK();
            if (fixedValue != null && fixedValue.compareTo(BigDecimal.ZERO) > 0) {
                discountAmount = fixedValue; // Sleva je fixní hodnota
            } else {
                // Toto by nemělo nastat, pokud není freeShippingOnly a validace projde, ale pro jistotu
                log.warn("Fixed amount coupon '{}' has no valid value for currency '{}' and is not freeShippingOnly. Applying zero discount.", coupon.getCode(), currency);
            }
        }

        // Sleva nemůže být větší než mezisoučet
        discountAmount = discountAmount.min(subTotalWithoutTax);
        discountAmount = discountAmount.setScale(PRICE_SCALE, ROUNDING_MODE);
        log.debug("Calculated discount amount for coupon '{}': {} {}", coupon.getCode(), discountAmount, currency);
        return discountAmount;
    }


    @Transactional(readOnly = true)
    public boolean checkMinimumOrderValue(Coupon coupon, BigDecimal orderSubtotal, String currency) {
        if (coupon == null || orderSubtotal == null) {
            log.warn("checkMinimumOrderValue called with null coupon or subtotal.");
            return false;
        }
        // Zde byla chyba, mělo být EUR
        BigDecimal minimumValue = coupon.getMinimumOrderValueCZK();
        // OPRAVA: Správné pole pro EUR
        minimumValue = EURO_CURRENCY.equals(currency)
                ? coupon.getMinimumOrderValueEUR()
                : coupon.getMinimumOrderValueCZK();


        if (minimumValue == null || minimumValue.compareTo(BigDecimal.ZERO) <= 0) {
            return true; // No minimum value set.
        }
        boolean ok = orderSubtotal.compareTo(minimumValue) >= 0;
        if (!ok) {
            log.debug("Order subtotal {} {} is below minimum {} {} required for coupon '{}'",
                    orderSubtotal.setScale(PRICE_SCALE, ROUNDING_MODE), currency,
                    minimumValue.setScale(PRICE_SCALE, ROUNDING_MODE), currency,
                    coupon.getCode());
        }
        return ok;
    }

    public String getMinimumValueString(Coupon coupon, String currency) {
        if (coupon == null) return "";
        BigDecimal minimumValue = EURO_CURRENCY.equals(currency) ? coupon.getMinimumOrderValueEUR() : coupon.getMinimumOrderValueCZK();
        if (minimumValue == null || minimumValue.compareTo(BigDecimal.ZERO) <= 0) return "";
        try {
            Locale locale = EURO_CURRENCY.equals(currency) ? EURO_LOCALE : CZECH_LOCALE;
            NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(locale);
            return currencyFormatter.format(minimumValue);
        } catch (Exception e) {
            log.error("Error formatting minimum value {} for currency {}", minimumValue, currency, e);
            return minimumValue.setScale(PRICE_SCALE, ROUNDING_MODE).toPlainString() + " " + currency;
        }
    }

    @Transactional(readOnly = true)
    public boolean checkCustomerUsageLimit(Customer customer, Coupon coupon) {
        if (coupon == null || coupon.getUsageLimitPerCustomer() == null || coupon.getUsageLimitPerCustomer() <= 0) {
            return true; // No limit per customer.
        }
        if (customer == null || customer.getId() == null || customer.isGuest()) {
            log.warn("Checking customer usage limit for coupon '{}' on a guest or customer without ID. Allowing usage.", coupon.getCode());
            return true;
        }
        long customerUsageCount = orderRepository.countByCustomerIdAndAppliedCouponId(customer.getId(), coupon.getId());
        boolean allowed = customerUsageCount < coupon.getUsageLimitPerCustomer();
        if (!allowed) {
            log.debug("Coupon '{}' invalid for customer {}: usage limit per customer ({}) reached (count: {}).",
                    coupon.getCode(), customer.getId(), coupon.getUsageLimitPerCustomer(), customerUsageCount);
        }
        return allowed;
    }

    @Transactional
    public void markCouponAsUsed(Coupon coupon) {
        if (coupon == null || coupon.getId() == null) {
            log.warn("Attempted to mark a null or transient coupon as used.");
            return;
        }
        couponRepository.findById(coupon.getId()).ifPresentOrElse(couponToUpdate -> {
            couponToUpdate.setUsedTimes(couponToUpdate.getUsedTimes() + 1);
            couponRepository.save(couponToUpdate);
            log.info("Incremented usage count for coupon '{}' (ID: {}). New count: {}",
                    couponToUpdate.getCode(), couponToUpdate.getId(), couponToUpdate.getUsedTimes());
        }, () -> {
            log.error("Could not mark coupon as used, not found with ID: {}", coupon.getId());
        });
    }


    // --- Metody pro CMS ---
    @Transactional(readOnly = true)
    public List<Coupon> getAllCoupons() {
        log.debug("Fetching all coupons for CMS");
        return couponRepository.findAll(Sort.by("code"));
    }

    @Transactional
    public Coupon createCoupon(Coupon coupon) {
        log.info("Creating new coupon via CMS: Code='{}', isPercentage='{}', freeShipping='{}'",
                coupon.getCode(), coupon.isPercentage(), coupon.isFreeShipping());
        coupon.setCode(coupon.getCode() != null ? coupon.getCode().trim().toUpperCase() : null);
        validateCouponData(coupon, null); // Validace

        couponRepository.findByCodeIgnoreCase(coupon.getCode()).ifPresent(c -> {
            throw new IllegalArgumentException("Kupón s kódem '" + coupon.getCode() + "' již existuje.");
        });

        coupon.setUsedTimes(0);
        // Model má defaultně active=true, zde není třeba nastavovat

        Coupon savedCoupon = couponRepository.save(coupon);
        log.info("Coupon '{}' (ID: {}) created successfully via CMS.", savedCoupon.getCode(), savedCoupon.getId());
        return savedCoupon;
    }

    @Caching(
            put = {@CachePut(value = "couponByCode", key = "#result.code", condition = "#result != null")},
            evict = {@CacheEvict(value = "couponByCode", key = "#existingCoupon.code",
                    condition = "#existingCoupon != null and #couponData.code != null and !#existingCoupon.code.equalsIgnoreCase(#couponData.code.trim().toUpperCase())",
                    beforeInvocation = true)
            })
    @Transactional
    public Coupon updateCoupon(Long id, Coupon couponData) {
        log.info("Updating coupon with ID: {} via CMS", id);

        Coupon existingCoupon = couponRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Coupon with ID " + id + " not found."));

        couponData.setCode(couponData.getCode() != null ? couponData.getCode().trim().toUpperCase() : null);
        validateCouponData(couponData, id); // Validace PŘED uložením

        if (!existingCoupon.getCode().equalsIgnoreCase(couponData.getCode())) {
            couponRepository.findByCodeIgnoreCase(couponData.getCode())
                    .filter(found -> !found.getId().equals(id))
                    .ifPresent(found -> {
                        throw new IllegalArgumentException("Kupón s kódem '" + couponData.getCode() + "' již existuje.");
                    });
            existingCoupon.setCode(couponData.getCode());
        }

        // Update polí
        existingCoupon.setName(couponData.getName());
        existingCoupon.setDescription(couponData.getDescription());
        existingCoupon.setPercentage(couponData.isPercentage());
        existingCoupon.setFreeShipping(couponData.isFreeShipping());
        // Hodnoty jsou normalizovány ve validateCouponData
        existingCoupon.setValue(couponData.getValue());
        existingCoupon.setValueCZK(couponData.getValueCZK());
        existingCoupon.setValueEUR(couponData.getValueEUR());
        existingCoupon.setMinimumOrderValueCZK(couponData.getMinimumOrderValueCZK());
        existingCoupon.setMinimumOrderValueEUR(couponData.getMinimumOrderValueEUR());
        existingCoupon.setStartDate(couponData.getStartDate());
        existingCoupon.setExpirationDate(couponData.getExpirationDate());
        existingCoupon.setUsageLimit(couponData.getUsageLimit());
        existingCoupon.setUsageLimitPerCustomer(couponData.getUsageLimitPerCustomer());
        existingCoupon.setActive(couponData.isActive());
        // usedTimes se neaktualizuje

        Coupon updatedCoupon = couponRepository.save(existingCoupon);
        log.info("Coupon '{}' (ID: {}) updated successfully via CMS.", updatedCoupon.getCode(), updatedCoupon.getId());
        return updatedCoupon;
    }

    @CacheEvict(value = "couponByCode", key = "#couponToDeactivate.code", condition = "#couponToDeactivate != null")
    @Transactional
    public void deactivateCoupon(Long id) {
        log.warn("Attempting to deactivate coupon with ID: {} via CMS", id);
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Coupon with ID " + id + " not found."));

        if (coupon.isActive()) {
            coupon.setActive(false);
            couponRepository.save(coupon);
            log.info("Coupon '{}' (ID: {}) successfully deactivated via CMS.", coupon.getCode(), id);
        } else {
            log.info("Coupon '{}' (ID: {}) is already inactive.", coupon.getCode(), id);
        }
    }

    /**
     * Validates coupon data before saving (create or update).
     * Throws IllegalArgumentException for validation errors.
     * Normalizes values based on isPercentage and freeShipping flags.
     */
    private void validateCouponData(Coupon coupon, Long idBeingUpdated) {
        if (coupon == null) throw new IllegalArgumentException("Coupon data cannot be null.");
        if (!StringUtils.hasText(coupon.getCode())) throw new IllegalArgumentException("Kód kupónu nesmí být prázdný.");
        if (!StringUtils.hasText(coupon.getName()))
            throw new IllegalArgumentException("Název kupónu nesmí být prázdný.");

        coupon.setCode(coupon.getCode().trim().toUpperCase());

        boolean hasValueDiscount = false;

        // 1. Validace procentuální slevy
        if (coupon.isPercentage()) {
            if (coupon.getValue() == null || coupon.getValue().signum() < 0 || coupon.getValue().compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("Procentuální hodnota (pole 'value') musí být mezi 0.00 a 100.00.");
            }
            // Normalizace - může být 0, pokud je freeShipping
            if (coupon.getValue().compareTo(BigDecimal.ZERO) > 0) {
                hasValueDiscount = true;
            }
            // Normalizace - vynulovat fixní hodnoty
            coupon.setValueCZK(null);
            coupon.setValueEUR(null);
        }
        // 2. Validace fixní slevy
        else {
            boolean hasCzValue = coupon.getValueCZK() != null && coupon.getValueCZK().compareTo(BigDecimal.ZERO) > 0;
            boolean hasEuValue = coupon.getValueEUR() != null && coupon.getValueEUR().compareTo(BigDecimal.ZERO) > 0;
            if (coupon.getValueCZK() != null && coupon.getValueCZK().signum() < 0)
                throw new IllegalArgumentException("Pevná částka CZK nesmí být záporná.");
            if (coupon.getValueEUR() != null && coupon.getValueEUR().signum() < 0)
                throw new IllegalArgumentException("Pevná částka EUR nesmí být záporná.");

            if (hasCzValue || hasEuValue) {
                hasValueDiscount = true;
            }
            // Normalizace - vynulovat procento a nepoužité fixní ceny
            coupon.setValue(null);
            if (coupon.getValueCZK() != null && coupon.getValueCZK().compareTo(BigDecimal.ZERO) <= 0)
                coupon.setValueCZK(null);
            if (coupon.getValueEUR() != null && coupon.getValueEUR().compareTo(BigDecimal.ZERO) <= 0)
                coupon.setValueEUR(null);
        }

        // 3. Kontrola kombinace hodnoty a freeShipping
        if (!hasValueDiscount && !coupon.isFreeShipping()) {
            throw new IllegalArgumentException("Kupón musí mít definovanou kladnou slevu (procentuální nebo fixní) NEBO musí mít zaškrtnutou dopravu zdarma.");
        }
        // Pokud je zaškrtnuta doprava zdarma a hodnota slevy je 0, je to OK (jen doprava zdarma).
        if (coupon.isFreeShipping() && !hasValueDiscount) {
            log.debug("Coupon {} configured for free shipping only.", coupon.getCode());
            // Explicitně vynulujeme hodnoty pro jistotu
            coupon.setValue(null);
            coupon.setValueCZK(null);
            coupon.setValueEUR(null);
            coupon.setPercentage(false); // "Free shipping only" je technicky typ "fixní slevy" s nulovou hodnotou
        }


        // Normalizace minimálních hodnot
        if (coupon.getMinimumOrderValueCZK() != null) {
            if (coupon.getMinimumOrderValueCZK().signum() < 0)
                throw new IllegalArgumentException("Minimální hodnota CZK nesmí být záporná.");
            if (coupon.getMinimumOrderValueCZK().compareTo(BigDecimal.ZERO) == 0) coupon.setMinimumOrderValueCZK(null);
        }
        if (coupon.getMinimumOrderValueEUR() != null) {
            if (coupon.getMinimumOrderValueEUR().signum() < 0)
                throw new IllegalArgumentException("Minimální hodnota EUR nesmí být záporná.");
            if (coupon.getMinimumOrderValueEUR().compareTo(BigDecimal.ZERO) == 0) coupon.setMinimumOrderValueEUR(null);
        }

        // Validace datumů
        if (coupon.getStartDate() != null && coupon.getExpirationDate() != null && coupon.getStartDate().isAfter(coupon.getExpirationDate())) {
            throw new IllegalArgumentException("Datum 'Platnost od' nesmí být po datu 'Platnost do'.");
        }

        // Validace a normalizace limitů
        if (coupon.getUsageLimit() != null) {
            if (coupon.getUsageLimit() < 0)
                throw new IllegalArgumentException("Celkový limit použití nesmí být záporný.");
            if (coupon.getUsageLimit() == 0) coupon.setUsageLimit(null);
        }
        if (coupon.getUsageLimitPerCustomer() != null) {
            if (coupon.getUsageLimitPerCustomer() < 0)
                throw new IllegalArgumentException("Limit použití na zákazníka nesmí být záporný.");
            if (coupon.getUsageLimitPerCustomer() == 0) coupon.setUsageLimitPerCustomer(null);
        }

        // Validace limitu vs. aktuální počet použití při UPDATE
        if (idBeingUpdated != null && coupon.getUsageLimit() != null) {
            couponRepository.findById(idBeingUpdated).ifPresent(existing -> {
                if (coupon.getUsageLimit() < existing.getUsedTimes()) {
                    throw new IllegalArgumentException("Celkový limit použití (" + coupon.getUsageLimit() + ") nesmí být nižší než aktuální počet použití (" + existing.getUsedTimes() + ").");
                }
            });
        }
    }
}