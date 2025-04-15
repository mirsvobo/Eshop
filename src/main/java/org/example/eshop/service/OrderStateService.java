package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.model.OrderState;
import org.example.eshop.repository.OrderRepository; // Pro kontrolu závislostí
import org.example.eshop.repository.OrderStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

import java.util.List;
import java.util.Optional;

@Service
public class OrderStateService {

    private static final Logger log = LoggerFactory.getLogger(OrderStateService.class);

    @Autowired private OrderStateRepository orderStateRepository;
    @Autowired private OrderRepository orderRepository; // Pro kontrolu počtu objednávek ve stavu
    @Autowired private EmailService emailService; // Pro vymazání cache

    /**
     * Vrátí všechny definované stavy objednávek, seřazené podle displayOrder.
     * Pro použití v CMS (např. v select boxu).
     * @return Seznam OrderState.
     */
    @Cacheable("sortedOrderStates")
    @Transactional(readOnly = true)
    public List<OrderState> getAllOrderStatesSorted(){
        log.debug("Fetching all order states sorted by displayOrder");
        return orderStateRepository.findAllByOrderByDisplayOrderAsc(); // Použití metody z repo
    }

    /**
     * Najde stav objednávky podle jeho ID.
     * @param id ID stavu.
     * @return Optional obsahující OrderState, pokud existuje.
     */
    @Transactional(readOnly = true)
    public Optional<OrderState> getOrderStateById(Long id){
        log.debug("Fetching order state by ID: {}", id);
        return orderStateRepository.findById(id);
    }

    /**
     * Najde stav objednávky podle jeho kódu (case-insensitive).
     * @param code Kód stavu (např. "NEW", "SHIPPED").
     * @return Optional obsahující OrderState, pokud existuje.
     */
    @Transactional(readOnly = true)
    public Optional<OrderState> findByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }
        log.debug("Fetching order state by code: {}", code);
        return orderStateRepository.findByCodeIgnoreCase(code.trim());
    }


    /**
     * Vytvoří nový stav objednávky.
     * @param orderState Objekt stavu k vytvoření.
     * @return Uložený stav.
     */
    @Caching(evict = {
            @CacheEvict(value = "sortedOrderStates", allEntries = true),
            // Pokud by OrderState měl vlastní detail cache podle ID
            // @CacheEvict(value = "orderStateDetails", key = "#result.id", condition="#result != null")
    })
    @Transactional
    public OrderState createOrderState(OrderState orderState){
        if (!StringUtils.hasText(orderState.getCode()) || !StringUtils.hasText(orderState.getName())) {
            throw new IllegalArgumentException("OrderState code and name cannot be empty.");
        }
        String code = orderState.getCode().trim().toUpperCase(); // Uložit kód velkými písmeny
        orderState.setCode(code);
        log.info("Creating new order state: Name='{}', Code='{}'", orderState.getName(), code);

        // Validace: unikátnost kódu
        if (orderStateRepository.findByCodeIgnoreCase(code).isPresent()) {
            throw new IllegalArgumentException("OrderState with code '" + code + "' already exists.");
        }
        // Nastavit výchozí pořadí, pokud není zadáno?
        if (orderState.getDisplayOrder() == null) {
            orderState.setDisplayOrder(0);
        }

        OrderState savedState = orderStateRepository.save(orderState);
        emailService.clearConfigCache(); // Vymazat cache konfigurace emailů
        return savedState;
    }

    /**
     * Aktualizuje existující stav objednávky.
     * @param id ID stavu k aktualizaci.
     * @param orderStateData Objekt s novými daty.
     * @return Optional s aktualizovaným stavem.
     */
    // Návratový typ Optional<Object> obsahující Optional<OrderState>
    @Caching(evict = {
            @CacheEvict(value = "sortedOrderStates", allEntries = true)
            // @CacheEvict(value = "orderStateDetails", key = "#id") // Invalidujeme podle ID
    })
    @Transactional
    public Object updateOrderState(Long id, OrderState orderStateData){
        log.info("Updating order state with ID: {}", id);
        if (!StringUtils.hasText(orderStateData.getCode()) || !StringUtils.hasText(orderStateData.getName())) {
            throw new IllegalArgumentException("OrderState code and name cannot be empty.");
        }
        String newCode = orderStateData.getCode().trim().toUpperCase();

        return orderStateRepository.findById(id)
                .map(existingState -> {
                    // Zkontrolovat unikátnost kódu, pokud se mění
                    if (!existingState.getCode().equalsIgnoreCase(newCode)) {
                        orderStateRepository.findByCodeIgnoreCase(newCode)
                                .filter(found -> !found.getId().equals(id)) // Zkontrolovat, zda nalezený není ten samý
                                .ifPresent(found -> {
                                    throw new IllegalArgumentException("OrderState with code '" + newCode + "' already exists.");
                                });
                        existingState.setCode(newCode);
                    }
                    // Aktualizovat ostatní pole
                    existingState.setName(orderStateData.getName());
                    existingState.setDescription(orderStateData.getDescription());
                    existingState.setDisplayOrder(orderStateData.getDisplayOrder() != null ? orderStateData.getDisplayOrder() : 0);
                    existingState.setFinalState(orderStateData.isFinalState());

                    OrderState updatedState = orderStateRepository.save(existingState);
                    emailService.clearConfigCache(); // Vymazat cache konfigurace emailů
                    log.info("Order state {} (ID: {}) updated successfully.", updatedState.getName(), updatedState.getId());
                    return Optional.of(updatedState);
                });
    }

    /**
     * Smaže stav objednávky podle ID.
     * POZOR: Povoleno pouze pokud žádné objednávky nejsou v tomto stavu.
     * @param id ID stavu ke smazání.
     * @throws EntityNotFoundException pokud stav neexistuje.
     * @throws IllegalStateException pokud existují objednávky v tomto stavu.
     */
    @Caching(evict = {
            @CacheEvict(value = "sortedOrderStates", allEntries = true)
            // @CacheEvict(value = "orderStateDetails", key = "#id")
    })
    @Transactional
    public void deleteOrderStateById(Long id){
        log.warn("Attempting to delete order state with ID: {}", id);
        OrderState state = getOrderStateById(id)
                .orElseThrow(() -> new EntityNotFoundException("OrderState with id " + id + " not found for deletion."));

        // Kontrola: Existují objednávky v tomto stavu?
        long orderCount = orderRepository.countByStateOfOrderId(id);
        if (orderCount > 0) {
            log.error("Cannot delete order state '{}' (ID: {}) because it is currently assigned to {} order(s).", state.getName(), id, orderCount);
            throw new IllegalStateException("Cannot delete order state '" + state.getName() + "' as it is currently in use by " + orderCount + " orders.");
        }

        // Pokud nejsou žádné objednávky, můžeme smazat
        try {
            orderStateRepository.deleteById(id);
            emailService.clearConfigCache(); // Vymazat cache konfigurace emailů
            log.info("Successfully deleted order state {} (ID: {})", state.getName(), id);
        } catch (DataIntegrityViolationException e) {
            // Pojistka, kdyby countByStateOfOrderId selhal nebo došlo k race condition
            log.error("Cannot delete order state '{}' (ID: {}) due to unexpected data integrity violation.", state.getName(), id, e);
            throw new IllegalStateException("Cannot delete order state '" + state.getName() + "' due to existing references.", e);
        }
    }
}