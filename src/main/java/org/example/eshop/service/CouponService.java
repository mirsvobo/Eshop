package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.model.Coupon;
import org.example.eshop.model.Customer;
import org.example.eshop.repository.CouponRepository;
import org.example.eshop.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat; // Import for formatting
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale; // Import for currency formatting
import java.util.Optional;

@Service
public class CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponService.class);
    // Assuming PriceConstants are defined elsewhere or use local constants
    private static final int PRICE_SCALE = 2;
    private static final int CALCULATION_SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final String DEFAULT_CURRENCY = "CZK";
    private static final String EURO_CURRENCY = "EUR";

    // Define Locales for formatting
    private static final Locale CZECH_LOCALE = new Locale("cs", "CZ");
    private static final Locale EURO_LOCALE = Locale.GERMANY; // Or another Eurozone locale like Locale.FRANCE

    @Autowired private CouponRepository couponRepository;
    @Autowired private OrderRepository orderRepository; // Assuming OrderRepository is correctly injected

    @Transactional(readOnly = true)
    public List<Coupon> getAllCoupons() { return couponRepository.findAll(); }

    @Transactional(readOnly = true)
    public Optional<Coupon> getCouponById(Long id) { return couponRepository.findById(id); }

    @Transactional(readOnly = true)
    public Optional<Coupon> findByCode(String code) {
        if (code == null || code.trim().isEmpty()) return Optional.empty();
        return couponRepository.findByCodeIgnoreCase(code.trim());
    }

    /**
     * Checks general validity of a coupon (active, dates, overall usage limit).
     * @param coupon The coupon to check.
     * @return true if generally valid, false otherwise.
     */
    public boolean isCouponGenerallyValid(Coupon coupon) {
        if (coupon == null) {
            log.warn("isCouponGenerallyValid called with null coupon.");
            return false;
        }
        if (!coupon.isActive()) {
            log.debug("Coupon '{}' is inactive.", coupon.getCode());
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean dateValid = (coupon.getStartDate() == null || !now.isBefore(coupon.getStartDate())) &&
                (coupon.getExpirationDate() == null || !now.isAfter(coupon.getExpirationDate()));
        if (!dateValid) {
            log.debug("Coupon '{}' is outside its valid date range (Start: {}, End: {}).",
                    coupon.getCode(), coupon.getStartDate(), coupon.getExpirationDate());
            return false;
        }
        boolean usageLimitOk = coupon.getUsageLimit() == null || coupon.getUsedTimes() < coupon.getUsageLimit();
        if (!usageLimitOk) {
            log.debug("Coupon '{}' reached its overall usage limit (Used: {}, Limit: {}).",
                    coupon.getCode(), coupon.getUsedTimes(), coupon.getUsageLimit());
            return false;
        }
        log.trace("Coupon '{}' passed general validity checks.", coupon.getCode());
        return true;
    }

    /**
     * Calculates the discount amount for a given price and coupon in the specified currency.
     * @param price The price (subtotal) to apply the discount to.
     * @param coupon The coupon object.
     * @param currency The currency code ("CZK" or "EUR").
     * @return The calculated discount amount, scaled appropriately. Returns ZERO if inputs are invalid.
     */
    public BigDecimal calculateDiscountAmount(BigDecimal price, Coupon coupon, String currency) {
        if (price == null || coupon == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal discountAmount = BigDecimal.ZERO; // Initialize to zero

        if (coupon.isPercentage()) {
            if (coupon.getValue() != null && coupon.getValue().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal discountFactor = coupon.getValue().divide(BigDecimal.valueOf(100), CALCULATION_SCALE, ROUNDING_MODE);
                discountAmount = price.multiply(discountFactor);
            } else {
                log.warn("Percentage coupon '{}' has null or zero value. Applying zero discount.", coupon.getCode());
            }
        } else {
            BigDecimal fixedValue = EURO_CURRENCY.equals(currency) ? coupon.getValueEUR() : coupon.getValueCZK();
            if (fixedValue != null && fixedValue.compareTo(BigDecimal.ZERO) > 0) {
                discountAmount = fixedValue;
                // Ensure fixed discount doesn't exceed the price it's applied to
                if (discountAmount.compareTo(price) > 0) {
                    log.debug("Fixed discount {} for coupon '{}' exceeds price {}. Capping discount at price.", discountAmount, coupon.getCode(), price);
                    discountAmount = price;
                }
            } else {
                log.warn("Fixed amount coupon '{}' has no valid value defined for currency '{}'. Applying zero discount.", coupon.getCode(), currency);
            }
        }
        // Scale the final discount amount
        return discountAmount.setScale(PRICE_SCALE, ROUNDING_MODE);
    }

    /**
     * Checks if the order subtotal meets the minimum value requirement for the coupon in the specified currency.
     * @param coupon The coupon object.
     * @param orderSubtotal The subtotal of the order (before discount).
     * @param currency The currency code ("CZK" or "EUR").
     * @return true if the minimum value is met or not set, false otherwise.
     */
    public boolean checkMinimumOrderValue(Coupon coupon, BigDecimal orderSubtotal, String currency) {
        if (coupon == null || orderSubtotal == null) return false; // Invalid input

        BigDecimal minimumValue = EURO_CURRENCY.equals(currency) ? coupon.getMinimumOrderValueEUR() : coupon.getMinimumOrderValueCZK();

        // If no minimum value is set for the currency, the check passes
        if (minimumValue == null || minimumValue.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }

        boolean ok = orderSubtotal.compareTo(minimumValue) >= 0;
        if (!ok) {
            log.warn("Order subtotal {} {} is below minimum {} {} required for coupon '{}'",
                    orderSubtotal.setScale(PRICE_SCALE, ROUNDING_MODE), currency,
                    minimumValue.setScale(PRICE_SCALE, ROUNDING_MODE), currency,
                    coupon.getCode());
        }
        return ok;
    }

    /**
     * Gets the minimum order value required for the coupon as a formatted string.
     * @param coupon The coupon object.
     * @param currency The currency code ("CZK" or "EUR").
     * @return Formatted string (e.g., "1 000,00 Kč", "50,00 €") or an empty string if no minimum is set.
     */
    public String getMinimumValueString(Coupon coupon, String currency) {
        if (coupon == null) return "";

        BigDecimal minimumValue = EURO_CURRENCY.equals(currency) ? coupon.getMinimumOrderValueEUR() : coupon.getMinimumOrderValueCZK();

        if (minimumValue == null || minimumValue.compareTo(BigDecimal.ZERO) <= 0) {
            return ""; // No minimum value set
        }

        try {
            Locale locale = EURO_CURRENCY.equals(currency) ? EURO_LOCALE : CZECH_LOCALE;
            NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(locale);
            // Set scale for formatting if needed (NumberFormat usually handles it based on locale)
            // currencyFormatter.setMinimumFractionDigits(PRICE_SCALE);
            // currencyFormatter.setMaximumFractionDigits(PRICE_SCALE);
            return currencyFormatter.format(minimumValue);
        } catch (Exception e) {
            log.error("Error formatting minimum value {} for currency {}", minimumValue, currency, e);
            // Fallback to simple string representation
            return minimumValue.setScale(PRICE_SCALE, ROUNDING_MODE).toPlainString() + " " + currency;
        }
    }


    /**
     * Increments the usage count for a given coupon.
     * @param coupon The coupon that was used.
     */
    @Transactional
    public void markCouponAsUsed(Coupon coupon) {
        if (coupon != null && coupon.getId() != null && coupon.getId() > 0){
            // Fetch the latest state from DB to avoid race conditions/stale data
            couponRepository.findById(coupon.getId()).ifPresentOrElse(freshCoupon -> {
                freshCoupon.setUsedTimes(freshCoupon.getUsedTimes() + 1);
                couponRepository.save(freshCoupon);
                log.info("Incremented usage count for coupon '{}' (ID: {}) to {}.", freshCoupon.getCode(), freshCoupon.getId(), freshCoupon.getUsedTimes());
            }, () -> log.error("Could not mark coupon as used, not found with ID: {}", coupon.getId()));
        } else {
            log.warn("Attempted to mark a null or transient coupon as used.");
        }
    }

    /**
     * Checks if a specific customer has reached the usage limit for a coupon.
     * @param customer The customer object.
     * @param coupon The coupon object.
     * @return true if the limit is not reached or not set, false otherwise.
     */
    @Transactional(readOnly = true)
    public boolean checkCustomerUsageLimit(Customer customer, Coupon coupon) {
        // Pass if no customer, no coupon, or no per-customer limit set
        if (customer == null || coupon == null || coupon.getUsageLimitPerCustomer() == null || coupon.getUsageLimitPerCustomer() <= 0) {
            return true;
        }
        // Count orders by this customer where this specific coupon was applied
        // Ensure OrderRepository is available and has the necessary method
        long usageCount = orderRepository.countByCustomerAndAppliedCoupon(customer, coupon);
        /* Alternative if countBy... method doesn't exist:
         long usageCount = orderRepository.findByCustomerOrderByOrderDateDesc(customer).stream()
                 .filter(order -> order.getAppliedCoupon() != null && order.getAppliedCoupon().getId().equals(coupon.getId())) // Compare Long IDs
                 .count();
        */

        boolean limitOk = usageCount < coupon.getUsageLimitPerCustomer();
        if (!limitOk) {
            log.warn("Customer {} (ID: {}) usage limit ({}) reached for coupon '{}'. Usage count: {}",
                    customer.getEmail(), customer.getId(), coupon.getUsageLimitPerCustomer(), coupon.getCode(), usageCount);
        } else {
            log.debug("Customer {} (ID: {}) usage count for coupon '{}' is {} (limit: {})",
                    customer.getEmail(), customer.getId(), coupon.getCode(), usageCount, coupon.getUsageLimitPerCustomer());
        }
        return limitOk;
    }

    // --- CRUD Operations ---
    @Transactional
    public Coupon createCoupon(Coupon coupon) {
        if (!StringUtils.hasText(coupon.getCode())) {
            throw new IllegalArgumentException("Coupon code cannot be empty.");
        }
        String trimmedCode = coupon.getCode().trim();
        couponRepository.findByCodeIgnoreCase(trimmedCode).ifPresent(existing -> {
            throw new IllegalArgumentException("Coupon with code '" + trimmedCode + "' already exists.");
        });
        // TODO: Add more validation (dates, values CZK/EUR consistency)
        log.info("Creating new coupon: {}", trimmedCode);
        coupon.setCode(trimmedCode);
        coupon.setUsedTimes(0); // Ensure usedTimes starts at 0
        return couponRepository.save(coupon);
    }

    @Transactional
    public void deleteCoupon(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Coupon with id " + id + " not found for deletion."));
        // Instead of deleting, mark as inactive for history
        coupon.setActive(false);
        couponRepository.save(coupon);
        log.warn("Coupon {} (ID: {}) marked as inactive.", coupon.getCode(), id);
        // Or delete permanently: couponRepository.delete(coupon);
    }

    @Transactional
    public Optional<Coupon> updateCoupon(Long id, Coupon couponData) {
        log.info("Updating coupon ID: {}", id);
        return couponRepository.findById(id).map(existingCoupon -> {
            // Validate uniqueness of code if changed
            if (StringUtils.hasText(couponData.getCode()) && !existingCoupon.getCode().equalsIgnoreCase(couponData.getCode().trim())) {
                String newCode = couponData.getCode().trim();
                couponRepository.findByCodeIgnoreCase(newCode)
                        .filter(found -> !found.getId().equals(id)) // Ensure it's not the same coupon
                        .ifPresent(existing -> { throw new IllegalArgumentException("Coupon code '" + newCode + "' is already used by another coupon."); });
                existingCoupon.setCode(newCode);
            }

            // Update fields from couponData
            existingCoupon.setName(couponData.getName());
            existingCoupon.setDescription(couponData.getDescription());
            existingCoupon.setPercentage(couponData.isPercentage());

            // Handle value based on percentage flag
            if (couponData.isPercentage()) {
                existingCoupon.setValue(couponData.getValue()); // Store percentage value
                existingCoupon.setValueCZK(null); // Clear fixed values
                existingCoupon.setValueEUR(null);
            } else {
                existingCoupon.setValue(null); // Clear percentage value
                existingCoupon.setValueCZK(couponData.getValueCZK()); // Store fixed CZK
                existingCoupon.setValueEUR(couponData.getValueEUR()); // Store fixed EUR
            }

            existingCoupon.setStartDate(couponData.getStartDate());
            existingCoupon.setExpirationDate(couponData.getExpirationDate());
            existingCoupon.setUsageLimit(couponData.getUsageLimit());
            existingCoupon.setUsageLimitPerCustomer(couponData.getUsageLimitPerCustomer());
            existingCoupon.setMinimumOrderValueCZK(couponData.getMinimumOrderValueCZK());
            existingCoupon.setMinimumOrderValueEUR(couponData.getMinimumOrderValueEUR());
            existingCoupon.setActive(couponData.isActive());
            // Note: usedTimes should generally not be updated manually here

            Coupon updatedCoupon = couponRepository.save(existingCoupon);
            log.info("Coupon {} (ID: {}) updated successfully.", updatedCoupon.getCode(), updatedCoupon.getId());
            return updatedCoupon;
        });
    }
}
