package org.example.eshop.service;

import lombok.Getter;
import org.example.eshop.model.CartItem;
import org.example.eshop.model.Coupon;
import org.example.eshop.config.PriceConstants; // Ensure this is imported
import org.example.eshop.model.TaxRate; // Import TaxRate if needed, otherwise use BigDecimal for rate
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Component
@SessionScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
@Getter // Keep Lombok Getter
public class Cart implements Serializable, PriceConstants { // Implement PriceConstants

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(Cart.class);

    // Use LinkedHashMap to maintain insertion order
    private final Map<String, CartItem> items = new LinkedHashMap<>();

    private Coupon appliedCoupon; // Applied valid coupon object
    private String appliedCouponCode; // Last attempted coupon code

    // --- Existing Methods (addItem, removeItem, updateQuantity, etc.) ---
    public void addItem(CartItem newItem) {
        if (newItem == null || newItem.getProductId() == null || newItem.getCartItemId() == null) {
            log.warn("Attempted to add invalid CartItem (null or missing ID). Cart hash: {}", this.hashCode());
            return;
        }
        String itemId = newItem.getCartItemId();
        log.debug("addItem called for cart hash: {}. Item ID: {}, Product ID: {}", this.hashCode(), itemId, newItem.getProductId());
        if (items.containsKey(itemId)) {
            CartItem existingItem = items.get(itemId);
            int newQuantity = existingItem.getQuantity() + newItem.getQuantity();
            existingItem.setQuantity(newQuantity);
            existingItem.setUnitPriceCZK(newItem.getUnitPriceCZK());
            existingItem.setUnitPriceEUR(newItem.getUnitPriceEUR());
            existingItem.setTaxRatePercent(newItem.getTaxRatePercent());
            log.debug("Increased quantity for cart item ID: {} to {}. Cart hash: {}", itemId, newQuantity, this.hashCode());
        } else {
            items.put(itemId, newItem);
            log.debug("Added new cart item ID: {}. Cart hash: {}", itemId, this.hashCode());
        }
        if (log.isDebugEnabled()) {
            log.debug("Cart items map after addItem (hash: {}): {}", this.hashCode(), items);
        }
    }
    public void removeItem(String cartItemId) {
        log.debug("removeItem called for cart hash: {}. Item ID: {}", this.hashCode(), cartItemId);
        if (items.remove(cartItemId) != null) {
            log.debug("Removed cart item ID: {}. Cart hash: {}", cartItemId, this.hashCode());
        } else {
            log.warn("Attempted to remove non-existent cart item ID: {}. Cart hash: {}", cartItemId, this.hashCode());
        }
        if (log.isDebugEnabled()) {
            log.debug("Cart items map after removeItem (hash: {}): {}", this.hashCode(), items);
        }
    }
    public void updateQuantity(String cartItemId, int quantity) {
        log.debug("updateQuantity called for cart hash: {}. Item ID: {}, New Quantity: {}", this.hashCode(), cartItemId, quantity);
        CartItem item = items.get(cartItemId);
        if (item != null) {
            if (quantity > 0) {
                item.setQuantity(quantity);
                log.debug("Updated quantity for cart item ID: {} to {}. Cart hash: {}", cartItemId, quantity, this.hashCode());
            } else {
                log.debug("Quantity <= 0 for item ID {}, removing item. Cart hash: {}", cartItemId, this.hashCode());
                items.remove(cartItemId);
            }
        } else {
            log.warn("Attempted to update quantity for non-existent cart item ID: {}. Cart hash: {}", cartItemId, this.hashCode());
        }
        if (log.isDebugEnabled()) {
            log.debug("Cart items map after updateQuantity (hash: {}): {}", this.hashCode(), items);
        }
    }
    public List<CartItem> getItemsList() {
        log.trace("getItemsList called for cart hash: {}. Returning {} items.", this.hashCode(), items.size());
        return Collections.unmodifiableList(new ArrayList<>(items.values()));
    }
    public Map<String, CartItem> getItems() {
        return Collections.unmodifiableMap(items); // Return unmodifiable map
    }
    public int getItemCount() {
        log.trace("getItemCount called for cart hash: {}. Count: {}", this.hashCode(), items.size());
        return items.size();
    }
    public int getTotalQuantity() {
        return items.values().stream().mapToInt(CartItem::getQuantity).sum();
    }
    public void clearCart() {
        log.info("clearCart called for cart hash: {}. Clearing items and coupon.", this.hashCode());
        items.clear();
        appliedCoupon = null;
        appliedCouponCode = null;
    }
    public void applyCoupon(Coupon coupon, String code) {
        this.appliedCoupon = coupon;
        this.appliedCouponCode = code;
        log.info("Applied coupon code '{}' to cart hash: {}.", code, this.hashCode());
    }
    public void setAttemptedCouponCode(String code) {
        this.appliedCouponCode = code;
        if (this.appliedCoupon != null && (code == null || !code.equalsIgnoreCase(this.appliedCoupon.getCode()))) {
            this.appliedCoupon = null;
            log.info("Removed previously applied valid coupon from cart hash: {} due to new attempt with code '{}'.", this.hashCode(), code);
        }
    }
    public void removeCoupon() {
        if (this.appliedCoupon != null || this.appliedCouponCode != null) {
            this.appliedCoupon = null;
            this.appliedCouponCode = null;
            log.info("Removed coupon from cart hash: {}.", this.hashCode());
        }
    }
    public boolean hasItems() {
        boolean empty = items.isEmpty();
        log.trace("hasItems called for cart hash: {}. Is empty: {}", this.hashCode(), empty);
        return !empty;
    }
    // --- END of Existing Methods ---


    // --- NEW/MODIFIED Calculation Methods ---

    /**
     * Calculates the subtotal of all items in the cart for the given currency (before VAT and discounts).
     * @param currency Currency code ("CZK" or "EUR").
     * @return Calculated subtotal.
     */
    public BigDecimal calculateSubtotal(String currency) {
        return items.values().stream()
                .map(item -> item.getTotalLinePriceWithoutTax(currency)) // Use method from CartItem
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(PRICE_SCALE, ROUNDING_MODE);
    }

    /**
     * Calculates the total discount amount based on the applied coupon and subtotal.
     * Uses the correct methods from the Coupon model (isPercentage, getValue, getValueCZK, getValueEUR).
     * @param currency Currency code ("CZK" or "EUR").
     * @return Discount amount, never null.
     */
    public BigDecimal calculateDiscountAmount(String currency) {
        if (appliedCoupon == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal subtotal = calculateSubtotal(currency); // Base for discount calculation (price without tax)
        BigDecimal discount = BigDecimal.ZERO;

        // Check coupon type and calculate discount using correct methods
        if (appliedCoupon.isPercentage()) {
            if (appliedCoupon.getValue() != null && appliedCoupon.getValue().compareTo(BigDecimal.ZERO) > 0) {
                int calcScale = (this instanceof PriceConstants) ? CALCULATION_SCALE : 4;
                BigDecimal discountFactor = appliedCoupon.getValue().divide(BigDecimal.valueOf(100), calcScale, ROUNDING_MODE);
                discount = subtotal.multiply(discountFactor);
            } else {
                log.warn("Percentage coupon '{}' has null or zero value. Applying zero discount.", appliedCoupon.getCode());
            }
        } else {
            BigDecimal fixedValue = null;
            if (EURO_CURRENCY.equals(currency)) {
                fixedValue = appliedCoupon.getValueEUR();
            } else { // Default to CZK
                fixedValue = appliedCoupon.getValueCZK();
            }

            if (fixedValue != null && fixedValue.compareTo(BigDecimal.ZERO) > 0) {
                discount = fixedValue.max(BigDecimal.ZERO);
            } else {
                log.warn("Fixed amount coupon '{}' has no valid value defined for currency '{}'. Applying zero discount.", appliedCoupon.getCode(), currency);
            }
        }

        // Ensure discount doesn't exceed the subtotal and scale correctly
        return discount.min(subtotal).setScale(PRICE_SCALE, ROUNDING_MODE);
    }


    /**
     * Calculates the total price excluding VAT, after applying discounts, but before shipping.
     * @param currency Currency code ("CZK" or "EUR").
     * @return Total price without VAT, after discount. Never null.
     */
    public BigDecimal calculateTotalPriceWithoutTaxAfterDiscount(String currency) {
        BigDecimal subtotal = calculateSubtotal(currency);
        BigDecimal discount = calculateDiscountAmount(currency);
        return subtotal.subtract(discount).max(BigDecimal.ZERO).setScale(PRICE_SCALE, ROUNDING_MODE);
    }

    /**
     * Calculates the total VAT amount for all items in the cart.
     * Includes DEBUG logging.
     * @param currency Currency code ("CZK" or "EUR").
     * @return Total VAT amount for items, never null.
     */
    public BigDecimal calculateTotalVatAmount(String currency) {
        log.trace("DEBUG_VAT (Cart {}): Starting calculateTotalVatAmount for currency {}", this.hashCode(), currency);
        BigDecimal totalVat = items.values().stream()
                .map(item -> item.getVatAmount(currency)) // Use method from CartItem
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal finalTotalVat = totalVat.setScale(PRICE_SCALE, ROUNDING_MODE);
        log.trace("DEBUG_VAT (Cart {}): Calculated total VAT = {}", this.hashCode(), finalTotalVat);
        return finalTotalVat;
    }

    /**
     * Calculates the breakdown of VAT amounts per tax rate for all items in the cart.
     * Includes DEBUG logging.
     * @param currency Currency code ("CZK" or "EUR").
     * @return Map where key is the TaxRate percentage (BigDecimal) and value is the total VAT amount (BigDecimal) for that rate. Sorted by rate.
     */
    public Map<BigDecimal, BigDecimal> calculateVatBreakdown(String currency) {
        Map<BigDecimal, BigDecimal> vatBreakdown = new HashMap<>();
        log.trace("DEBUG_VAT (Cart {}): Starting calculateVatBreakdown for currency {}", this.hashCode(), currency);

        for (CartItem item : items.values()) {
            BigDecimal itemVatAmount = item.getVatAmount(currency);
            BigDecimal taxRatePercent = item.getTaxRatePercent();
            BigDecimal rateKey = (taxRatePercent == null)
                    ? BigDecimal.ZERO.setScale(2)
                    : taxRatePercent.setScale(2, RoundingMode.HALF_UP);

            log.trace("DEBUG_VAT (Cart {}): Processing item ID {}. RateKey={}, ItemVatAmount={}",
                    this.hashCode(), item.getCartItemId(), rateKey, itemVatAmount);

            if (itemVatAmount != null) {
                BigDecimal currentTotalForRate = vatBreakdown.getOrDefault(rateKey, BigDecimal.ZERO);
                vatBreakdown.merge(rateKey, itemVatAmount, BigDecimal::add);
                log.trace("DEBUG_VAT (Cart {}): Merged item VAT. RateKey {}: {} -> {}",
                        this.hashCode(), rateKey, currentTotalForRate, vatBreakdown.get(rateKey));
            } else {
                log.warn("DEBUG_VAT (Cart {}): Item ID {} returned null VAT amount for currency {}. Skipping merge.",
                        this.hashCode(), item.getCartItemId(), currency);
            }
        }

        log.trace("DEBUG_VAT (Cart {}): Raw breakdown map before sorting/scaling: {}", this.hashCode(), vatBreakdown);

        Map<BigDecimal, BigDecimal> finalBreakdown = vatBreakdown.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().setScale(PRICE_SCALE, ROUNDING_MODE),
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        log.trace("DEBUG_VAT (Cart {}): Final breakdown map after sorting/scaling: {}", this.hashCode(), finalBreakdown);
        return finalBreakdown;
    }


    /**
     * Calculates the total price of items including VAT and after discount, but BEFORE shipping.
     * Formula: (Subtotal - Discount) + Total VAT
     * @param currency Currency code ("CZK" or "EUR").
     * @return Total price before shipping, never null.
     */
    public BigDecimal calculateTotalPriceBeforeShipping(String currency) {
        BigDecimal subtotal = calculateSubtotal(currency);
        BigDecimal discount = calculateDiscountAmount(currency);
        BigDecimal totalVat = calculateTotalVatAmount(currency); // Uses the updated method with logging

        BigDecimal total = subtotal
                .subtract(discount)
                .add(totalVat);

        BigDecimal finalTotal = total.max(BigDecimal.ZERO).setScale(PRICE_SCALE, ROUNDING_MODE);
        log.trace("DEBUG_VAT (Cart {}): Calculated TotalPriceBeforeShipping = {}", this.hashCode(), finalTotal);
        return finalTotal;
    }
}
