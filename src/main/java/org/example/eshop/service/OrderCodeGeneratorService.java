package org.example.eshop.service;

import jakarta.annotation.PostConstruct;
import org.example.eshop.model.Order;
import org.example.eshop.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Pro @PostConstruct s DB

import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderCodeGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(OrderCodeGeneratorService.class);
    private static final long INITIAL_COUNTER_VALUE = 205L; // Poslední použité číslo

    @Autowired
    private OrderRepository orderRepository;

    private AtomicLong orderCounter;

    @PostConstruct
    @Transactional(readOnly = true) // Transakce pro čtení z DB
    protected void initializeCounter() {
        log.info("Initializing order code counter...");
        // Najdeme objednávku s nejvyšším ID pro zjištění maxima
        Page<Order> latestOrderPage = orderRepository.findAll(
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "id"))
        );

        long maxExistingId = 0L;
        if (latestOrderPage.hasContent()) {
            maxExistingId = latestOrderPage.getContent().get(0).getId();
            log.info("Found maximum existing order ID: {}", maxExistingId);
        } else {
            log.info("No existing orders found in database.");
        }

        // Inicializujeme čítač na vyšší hodnotu z (poslední známé vs. maximum z DB)
        long startValue = Math.max(INITIAL_COUNTER_VALUE, maxExistingId);
        this.orderCounter = new AtomicLong(startValue);
        log.info("Order code counter initialized to: {}", startValue);
    }

    /**
     * Získá další unikátní číselný kód objednávky.
     * @return Další číslo v pořadí jako String.
     */
    public String getNextOrderCode() {
        if (orderCounter == null) {
            // Pojistka, pokud by PostConstruct z nějakého důvodu selhal před prvním použitím
            log.error("Order counter not initialized! Falling back to emergency initialization.");
            initializeCounter();
            if(orderCounter == null){ // Pokud ani nouzová inicializace nepomohla
                throw new IllegalStateException("Order counter could not be initialized.");
            }
        }
        long nextCode = orderCounter.incrementAndGet();
        log.debug("Generated next order code: {}", nextCode);
        return String.valueOf(nextCode);
    }
}