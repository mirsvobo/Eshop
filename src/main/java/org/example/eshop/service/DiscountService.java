package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.config.PriceConstants; // Import konstant
import org.example.eshop.model.Discount;
import org.example.eshop.model.Product;
import org.example.eshop.repository.DiscountRepository;
import org.example.eshop.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort; // Přidán import pro Sort
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils; // Přidán import pro StringUtils

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DiscountService implements PriceConstants {

    private static final Logger log = LoggerFactory.getLogger(DiscountService.class);

    @Autowired private DiscountRepository discountRepository;
    @Autowired private ProductRepository productRepository; // Pro načítání produktů

    // --- Čtecí metody (Upraveno a doplněno) ---
    @Transactional(readOnly = true)
    public List<Discount> getAllDiscounts() {
        log.debug("Fetching all discounts for CMS list");
        // Můžeme přidat řazení, např. podle data vytvoření nebo názvu
        return discountRepository.findAll(Sort.by("name"));
    }

    @Transactional(readOnly = true)
    public Optional<Discount> getDiscountById(Long id) {
        log.debug("Fetching discount by ID: {}", id);
        return discountRepository.findById(id);
    }

    /**
     * Najde aktuálně aktivní slevy aplikovatelné na specifický produkt.
     * Kontroluje aktivitu slevy a její časovou platnost.
     * Zohledňuje slevy přiřazené přímo k produktu A slevy bez přiřazených produktů (globální).
     * @param product Produkt, pro který se hledají slevy.
     * @return Seznam aktivních a platných slev pro daný produkt.
     */
    @Transactional(readOnly = true)
    public List<Discount> findActiveDiscountsForProduct(Product product) {
        if (product == null) return Collections.emptyList();
        LocalDateTime now = LocalDateTime.now();
        Long productId = product.getId();

        // Optimalizovanější přístup (příklad - vyžaduje úpravu repository):
        // return discountRepository.findActiveApplicableDiscountsForProduct(productId, now);

        // Původní (méně efektivní) přístup:
        List<Discount> potentialDiscounts = discountRepository.findAll().stream()
                .filter(Discount::isActive)
                .filter(d -> d.getValidFrom() != null && d.getValidTo() != null)
                .filter(d -> !now.isBefore(d.getValidFrom()) && !now.isAfter(d.getValidTo()))
                .toList();

        return potentialDiscounts.stream()
                .filter(discount -> CollectionUtils.isEmpty(discount.getProducts()) ||
                        discount.getProducts().stream().anyMatch(p -> p.getId().equals(productId)))
                .collect(Collectors.toList());
    }

    /**
     * Aplikuje nejlepší dostupnou procentuální slevu na danou cenu.
     * @param price Původní cena (bez DPH).
     * @param product Produkt, pro který hledáme slevy.
     * @return Cena po aplikaci nejlepší procentuální slevy, nebo původní cena.
     */
    @Transactional(readOnly = true)
    public BigDecimal applyBestPercentageDiscount(BigDecimal price, Product product) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0 || product == null) return price;
        List<Discount> activeDiscounts = findActiveDiscountsForProduct(product);

        Optional<Discount> bestPercentageDiscount = activeDiscounts.stream()
                .filter(Discount::isPercentage)
                .filter(d -> d.getValue() != null && d.getValue().compareTo(BigDecimal.ZERO) > 0)
                .max(Comparator.comparing(Discount::getValue));

        if (bestPercentageDiscount.isPresent()) {
            Discount discount = bestPercentageDiscount.get();
            log.debug("Applying discount '{}' ({}%) to price {} for product '{}'", discount.getName(), discount.getValue(), price, product.getName());
            BigDecimal discountMultiplier = BigDecimal.ONE.subtract(discount.getValue().divide(new BigDecimal("100"), CALCULATION_SCALE, ROUNDING_MODE));
            BigDecimal discountedPrice = price.multiply(discountMultiplier).setScale(PRICE_SCALE, ROUNDING_MODE);
            return discountedPrice.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : discountedPrice;
        } else {
            return price;
        }
    }

    /**
     * Aplikuje nejlepší dostupnou slevu pevnou částkou pro danou měnu.
     * @param price Cena před slevou.
     * @param product Produkt.
     * @param currency Měna ("CZK" nebo "EUR").
     * @return Cena po slevě.
     */
    @Transactional(readOnly = true)
    public BigDecimal applyBestFixedDiscount(BigDecimal price, Product product, String currency) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0 || product == null) return price;
        List<Discount> activeDiscounts = findActiveDiscountsForProduct(product);

        Optional<Discount> bestFixedDiscount = activeDiscounts.stream()
                .filter(d -> !d.isPercentage())
                .filter(d -> {
                    BigDecimal value = EURO_CURRENCY.equals(currency) ? d.getValueEUR() : d.getValueCZK();
                    return value != null && value.compareTo(BigDecimal.ZERO) > 0;
                })
                .max(Comparator.comparing(d -> EURO_CURRENCY.equals(currency) ? Optional.ofNullable(d.getValueEUR()).orElse(BigDecimal.ZERO) : Optional.ofNullable(d.getValueCZK()).orElse(BigDecimal.ZERO)));


        if (bestFixedDiscount.isPresent()) {
            Discount discount = bestFixedDiscount.get();
            BigDecimal discountValue = EURO_CURRENCY.equals(currency) ? discount.getValueEUR() : discount.getValueCZK();
            if (discountValue == null) return price; // Pojistka
            log.debug("Applying fixed discount '{}' ({} {}) to price {} {} for product '{}'", discount.getName(), discountValue, currency, price, currency, product.getName());
            BigDecimal discountedPrice = price.subtract(discountValue);
            discountedPrice = discountedPrice.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : discountedPrice;
            return discountedPrice.setScale(PRICE_SCALE, ROUNDING_MODE);
        } else {
            return price;
        }
        // TODO: Rozhodnout, jak kombinovat procentuální a fixní slevy. Nyní se aplikují nezávisle.
    }

    // --- CRUD operace (pro CMS) ---
    @Transactional
    public Discount createDiscount(Discount discount, Set<Long> productIds) {
        log.info("Creating new discount: {}", discount.getName());
        validateDiscount(discount); // Validace dat slevy

        // Zpracování přiřazených produktů
        Set<Product> products = loadAndAssignProducts(productIds);
        discount.setProducts(products);
        discount.setActive(true); // Nová sleva je defaultně aktivní

        Discount savedDiscount = discountRepository.save(discount);
        log.info("Discount {} created successfully with ID: {}", savedDiscount.getName(), savedDiscount.getId());
        return savedDiscount;
    }

    @Transactional
    public Object updateDiscount(Long id, Discount discountData, Set<Long> productIds) {
        log.info("Updating discount ID: {}", id);
        validateDiscount(discountData); // Validace dat z formuláře

        return discountRepository.findById(id).map(existingDiscount -> {
            // Aktualizace základních polí
            existingDiscount.setName(discountData.getName());
            existingDiscount.setDescription(discountData.getDescription());
            existingDiscount.setPercentage(discountData.isPercentage());
            existingDiscount.setValue(discountData.getValue());
            existingDiscount.setValueCZK(discountData.getValueCZK());
            existingDiscount.setValueEUR(discountData.getValueEUR());
            existingDiscount.setValidFrom(discountData.getValidFrom());
            existingDiscount.setValidTo(discountData.getValidTo());
            existingDiscount.setActive(discountData.isActive());

            // Aktualizace přiřazených produktů
            Set<Product> productsToAssign = loadAndAssignProducts(productIds);

            // Odebrání produktů, které už nemají být přiřazeny
            existingDiscount.getProducts().removeIf(product -> !productsToAssign.contains(product));

            // Přidání nových produktů
            productsToAssign.forEach(product -> {
                if (!existingDiscount.getProducts().contains(product)) {
                    existingDiscount.getProducts().add(product);
                }
            });

            Discount updatedDiscount = discountRepository.save(existingDiscount);
            log.info("Discount {} (ID: {}) updated successfully.", updatedDiscount.getName(), updatedDiscount.getId());
            return Optional.of(updatedDiscount); // Vrátíme Optional<Discount>
        });
    }

    @Transactional
    public void deleteDiscount(Long id) {
        log.warn("Attempting to deactivate (soft delete) discount with ID: {}", id);
        Discount discount = discountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Discount with id " + id + " not found for deletion."));
        if (discount.isActive()) {
            discount.setActive(false);
            discountRepository.save(discount);
            log.info("Discount {} (ID: {}) marked as inactive.", discount.getName(), id);
        } else {
            log.info("Discount {} (ID: {}) is already inactive.", discount.getName(), id);
        }
        // Tvrdé smazání nedoporučeno kvůli možné historii
    }

    // --- Pomocné metody ---
    private void validateDiscount(Discount discount) {
        if (discount == null) throw new IllegalArgumentException("Discount data cannot be null.");
        if (!StringUtils.hasText(discount.getName())) throw new IllegalArgumentException("Discount name cannot be empty.");
        validateDiscountDates(discount);
        validateDiscountValues(discount);
    }

    private void validateDiscountDates(Discount discount) {
        if (discount.getValidFrom() == null || discount.getValidTo() == null) throw new IllegalArgumentException("Discount validity dates (From, To) cannot be null.");
        if (discount.getValidTo().isBefore(discount.getValidFrom())) throw new IllegalArgumentException("'Valid To' date cannot be before 'Valid From' date.");
    }

    private Set<Product> loadAndAssignProducts(Set<Long> productIds) {
        if (CollectionUtils.isEmpty(productIds)) return new HashSet<>();
        List<Product> foundProducts = productRepository.findAllById(productIds);
        if (foundProducts.size() != productIds.size()) {
            Set<Long> foundIds = foundProducts.stream().map(Product::getId).collect(Collectors.toSet());
            productIds.removeAll(foundIds);
            log.warn("Some products were not found when assigning to discount: IDs {}", productIds);
            // Můžeme zde vyhodit výjimku nebo jen logovat podle potřeby
            // throw new EntityNotFoundException("One or more products not found: " + productIds);
        }
        return new HashSet<>(foundProducts);
    }

    private void validateDiscountValues(Discount discount) {
        if (discount.isPercentage()) {
            if (discount.getValue() == null || discount.getValue().compareTo(BigDecimal.ZERO) <= 0 || discount.getValue().compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("Percentage discount value must be > 0 and <= 100.");
            }
            // Normalizace - vynulovat fixní hodnoty
            discount.setValueCZK(null);
            discount.setValueEUR(null);
        } else {
            boolean czkValid = discount.getValueCZK() != null && discount.getValueCZK().compareTo(BigDecimal.ZERO) > 0;
            boolean eurValid = discount.getValueEUR() != null && discount.getValueEUR().compareTo(BigDecimal.ZERO) > 0;
            if (!czkValid && !eurValid) {
                throw new IllegalArgumentException("Fixed discount must have a positive value for at least one currency (CZK or EUR).");
            }
            // Kontrola záporných hodnot
            if (discount.getValueCZK() != null && discount.getValueCZK().signum() < 0) throw new IllegalArgumentException("Fixed amount CZK cannot be negative.");
            if (discount.getValueEUR() != null && discount.getValueEUR().signum() < 0) throw new IllegalArgumentException("Fixed amount EUR cannot be negative.");

            // Normalizace - vynulovat procento a nepoužité/nulové fixní ceny
            discount.setValue(null);
            if (!czkValid) discount.setValueCZK(null);
            if (!eurValid) discount.setValueEUR(null);
        }
    }
}