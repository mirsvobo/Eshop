package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.model.OrderItem;
import org.example.eshop.repository.OrderItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Služba pro základní operace s OrderItem.
 * POZNÁMKA: Většina logiky pro OrderItem je řešena v rámci OrderService.
 * Tato služba je primárně pro administrativní účely (např. zobrazení v CMS) nebo reporting.
 * Přímá modifikace OrderItem přes tuto službu se obecně nedoporučuje kvůli riziku narušení konzistence objednávky.
 */
@Service
public class OrderItemService {

    private static final Logger log = LoggerFactory.getLogger(OrderItemService.class);

    @Autowired
    private OrderItemRepository orderItemRepository;

    // --- Metody pro čtení ---

    /**
     * Vrátí všechny položky všech objednávek (používat opatrně, může být velmi mnoho dat).
     * Vhodné pro specifické reporty.
     *
     * @return Seznam všech OrderItem.
     */
    @Transactional(readOnly = true)
    public List<OrderItem> getAllOrderItems() {
        log.debug("Fetching all order items");
        return orderItemRepository.findAll();
    }

    /**
     * Najde všechny položky pro konkrétní ID objednávky.
     *
     * @param orderId ID objednávky.
     * @return Seznam položek dané objednávky.
     */
    @Transactional(readOnly = true)
    public List<OrderItem> getOrderItemsByOrderId(Long orderId) {
        log.debug("Fetching order items for order ID: {}", orderId);
        // Použijeme metodu přidanou do OrderItemRepository
        return orderItemRepository.findByOrderId(orderId);
    }


    /**
     * Najde položku objednávky podle jejího ID.
     *
     * @param orderItemId ID položky.
     * @return Optional obsahující OrderItem, pokud existuje.
     */
    @Transactional(readOnly = true)
    public Optional<OrderItem> getOrderItemById(Long orderItemId) {
        log.debug("Fetching order item by ID: {}", orderItemId);
        return orderItemRepository.findById(orderItemId);
    }

    // --- Metody pro Modifikaci (VELMI NEDOPORUČENÉ PRO BĚŽNÉ POUŽITÍ) ---
    // Tyto metody by měly být používány pouze v krajních případech administrátorem
    // s plným vědomím důsledků na integritu dat objednávky.

    /**
     * Vytvoří novou položku objednávky.
     * VAROVÁNÍ: Nepoužívat přímo! Položky by měly být vytvářeny pouze přes OrderService.createOrder.
     * Tato metoda nezahrnuje žádné výpočty cen, daní, slev atd. a nenaváže správně součty v Order!
     *
     * @param orderItem Objekt položky (musí mít nastavenou vazbu na Order).
     * @return Uložená položka.
     * @throws UnsupportedOperationException Vždy hodí výjimku, aby se zabránilo použití.
     */
    @Transactional
    public OrderItem createOrderItem(OrderItem orderItem) {
        log.error("CRITICAL: Direct creation of OrderItem attempted via OrderItemService! Operation blocked.");
        throw new UnsupportedOperationException("Direct creation of OrderItems via OrderItemService is forbidden due to data integrity risks. Use OrderService.createOrder instead.");
        // Původní kód (nebezpečný):
        // if (orderItem.getOrder() == null) {
        //     throw new IllegalArgumentException("OrderItem must be associated with an Order.");
        // }
        // return orderItemRepository.save(orderItem);
    }

    /**
     * Aktualizuje položku objednávky.
     * VAROVÁNÍ: Používat extrémně opatrně! Může narušit konzistenci objednávky (ceny, součty).
     * Povoleno pouze pro úpravu polí, která nemají vliv na finance (např. interní poznámka).
     *
     * @param id            ID položky k aktualizaci.
     * @param orderItemData Objekt s novými daty.
     * @return Optional s aktualizovanou položkou.
     * @throws UnsupportedOperationException Pokud se pokusí změnit kritická pole.
     */
    @Transactional
    public Object updateOrderItem(Long id, OrderItem orderItemData) {
        log.warn("Direct update of OrderItem with ID: {} attempted via OrderItemService. This might break order consistency.", id);
        return orderItemRepository.findById(id)
                .map(existingItem -> {
                    // TODO: Definovat, která pole je BEZPEČNÉ aktualizovat přímo (pokud nějaká jsou).
                    // Například POUZE interní poznámka:
                    // existingItem.setInternalNote(orderItemData.getInternalNote());

                    // Kontrola, zda se nemění kritická data (ceny, množství, produkt, vazba na objednávku atd.)
                    if (orderItemData.getCount() != null && !orderItemData.getCount().equals(existingItem.getCount()) ||
                            orderItemData.getUnitPriceWithoutTax() != null && orderItemData.getUnitPriceWithoutTax().compareTo(existingItem.getUnitPriceWithoutTax()) != 0 ||
                            // ... další kontroly pro ceny, daně, produkt, variantu ...
                            orderItemData.getOrder() != null && !orderItemData.getOrder().getId().equals(existingItem.getOrder().getId())) {
                        log.error("CRITICAL: Attempting unsafe direct update on critical fields of OrderItem ID: {}. Update blocked.", id);
                        throw new UnsupportedOperationException("Direct update of critical OrderItem fields (quantity, prices, product, order link, etc.) is not allowed via OrderItemService.");
                    }

                    log.info("Updating non-critical fields for OrderItem ID: {} (ensure only safe fields are updated here!)", id);
                    // Pokud projdou kontroly, uložit (ale pouze pokud se aktualizují bezpečná pole)
                    // return Optional.of(orderItemRepository.save(existingItem));
                    log.warn("Direct update of OrderItem ID {} performed via OrderItemService, but only non-critical fields should be affected.", id);
                    return Optional.of(existingItem); // Vrátit existující, pokud žádné bezpečné pole není definováno
                });
    }

    /**
     * Smaže položku objednávky.
     * VAROVÁNÍ: Extrémně nebezpečné! Naruší součty v objednávce a datovou integritu.
     * Tato operace by měla být zakázána. Položky by se měly maximálně označit jako stornované.
     *
     * @param id ID položky ke smazání.
     * @throws UnsupportedOperationException Vždy hodí výjimku, aby se zabránilo použití.
     */
    @Transactional
    public void deleteOrderItem(Long id) {
        log.error("CRITICAL: Direct deletion of OrderItem with ID: {} attempted via OrderItemService! Operation blocked.", id);
        if (!orderItemRepository.existsById(id)) {
            throw new EntityNotFoundException("OrderItem with id " + id + " not found for deletion attempt.");
        }
        throw new UnsupportedOperationException("Direct deletion of OrderItems is forbidden due to severe data integrity risks.");
        // Fyzické smazání (nebezpečné):
        // orderItemRepository.deleteById(id);
    }
}