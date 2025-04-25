// src/main/java/org/example/eshop/service/OrderCodeGeneratorService.java
package org.example.eshop.service;

import jakarta.annotation.PostConstruct;
import org.example.eshop.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderCodeGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(OrderCodeGeneratorService.class);
    // Definujeme MINIMÁLNÍ hodnotu, od které má čítač začít, pokud je DB prázdná nebo max. kód je nižší
    private static final long MINIMUM_START_COUNTER_VALUE = 205L;

    @Autowired
    private OrderRepository orderRepository;

    private AtomicLong orderCounter;

    @PostConstruct
    @Transactional(readOnly = true) // Transakce pro čtení z DB
    protected void initializeCounter() {
        log.info("Initializing order code counter...");

        // *** ZMĚNA: Hledáme maximální číselný orderCode ***
        Optional<String> maxOrderCodeOpt = orderRepository.findMaxNumericOrderCode(); // Voláme novou metodu repo

        long maxExistingCode = 0L;
        if (maxOrderCodeOpt.isPresent()) {
            try {
                // Pokusíme se převést nalezený kód na číslo
                maxExistingCode = Long.parseLong(maxOrderCodeOpt.get());
                log.info("Found maximum existing numeric order code: {}", maxExistingCode);
            } catch (NumberFormatException e) {
                // Pokud kód není číslo, logujeme varování a začneme od minima
                log.warn("Could not parse maximum order code '{}' as number. Starting from minimum value.", maxOrderCodeOpt.get());
                maxExistingCode = 0L; // Reset na 0, aby se použilo MINIMUM_START_COUNTER_VALUE
            }
        } else {
            log.info("No existing numeric-like order codes found in database.");
        }
        // *** KONEC ZMĚNY ***

        // Inicializujeme čítač na vyšší hodnotu z (MINIMUM_START vs. maximum z DB)
        long startValue = Math.max(MINIMUM_START_COUNTER_VALUE, maxExistingCode);
        this.orderCounter = new AtomicLong(startValue);
        log.info("Order code counter initialized to: {}", startValue);
    }

    /**
     * Získá další unikátní číselný kód objednávky.
     *
     * @return Další číslo v pořadí jako String.
     */
    public String getNextOrderCode() {
        if (orderCounter == null) {
            // Pojistka, pokud by PostConstruct z nějakého důvodu selhal před prvním použitím
            log.error("Order counter not initialized! Falling back to emergency initialization.");
            initializeCounter(); // Zkusíme znovu inicializovat
            if (orderCounter == null) { // Pokud ani nouzová inicializace nepomohla
                throw new IllegalStateException("Order counter could not be initialized.");
            }
        }
        // Inkrementujeme AŽ PO získání aktuální hodnoty pro generování
        long nextCode = orderCounter.incrementAndGet();
        log.debug("Generated next order code: {}", nextCode);
        return String.valueOf(nextCode);
    }
}