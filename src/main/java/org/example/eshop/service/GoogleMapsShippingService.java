package org.example.eshop.service;

import com.google.maps.DistanceMatrixApi;
import com.google.maps.GeoApiContext;
import com.google.maps.GeoApiContext.Builder; // Explicitní import pro Builder
import com.google.maps.model.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.eshop.config.PriceConstants;
import org.example.eshop.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils; // Používáme Spring StringUtils

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;

/**
 * Implementace ShippingService využívající Google Maps Distance Matrix API.
 * Obsahuje refaktorizaci pro lepší testovatelnost bez mockito-inline.
 */
@Service("googleMapsShippingService")
public class GoogleMapsShippingService implements ShippingService, PriceConstants {

    private static final Logger log = LoggerFactory.getLogger(GoogleMapsShippingService.class);

    @Value("${google.maps.api.key}")
    private String apiKey;

    @Value("${eshop.shipping.origin.latitude}")
    private double originLatitude;

    @Value("${eshop.shipping.origin.longitude}")
    private double originLongitude;

    @Value("${eshop.shipping.fixed.price.czk}")
    private BigDecimal fixedPriceCZK;
    @Value("${eshop.shipping.perkm.price.czk}")
    private BigDecimal perKmPriceCZK;
    @Value("${eshop.shipping.fixed.price.eur}")
    private BigDecimal fixedPriceEUR;
    @Value("${eshop.shipping.perkm.price.eur}")
    private BigDecimal perKmPriceEUR;

    // Sazba DPH pro dopravu - konstanta
    private static final BigDecimal SHIPPING_TAX_RATE = new BigDecimal("0.21");

    private GeoApiContext geoApiContext;
    private LatLng originLatLng; // Uchováme si LatLng objekt

    @PostConstruct
    private void init() {
        log.info("Initializing Google Maps API context...");
        try {
            // Kontrola API klíče
            if (!StringUtils.hasText(apiKey)) {
                throw new IllegalArgumentException("Google Maps API key (google.maps.api.key) is missing or empty.");
            }
            // Použití explicitního importu Builder
            geoApiContext = new GeoApiContext.Builder()
                    .apiKey(apiKey)
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .writeTimeout(5, TimeUnit.SECONDS)
                    .build();
            log.info("Google Maps API context initialized successfully.");

            // Vytvoření LatLng objektu z načtených souřadnic
            originLatLng = new LatLng(originLatitude, originLongitude);
            log.info("Origin coordinates set to: {}", originLatLng);

        } catch (IllegalArgumentException iae) {
            log.error("Configuration error during Google Maps init: {}", iae.getMessage());
            geoApiContext = null;
            originLatLng = null;
        }
        catch (Exception e) {
            log.error("Failed to initialize Google Maps API context or set origin coordinates: {}", e.getMessage(), e);
            geoApiContext = null;
            originLatLng = null;
        }
    }

    @PreDestroy
    private void destroy() {
        if (geoApiContext != null) {
            log.info("Shutting down Google Maps API context.");
            // Metoda shutdown() může vyvolat výjimku, i když je to nepravděpodobné
            try {
                geoApiContext.shutdown();
            } catch (Exception e) {
                log.warn("Error shutting down GeoApiContext: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Obalovací metoda: Provede volání Google Distance Matrix API a vrátí výsledek.
     * Protected pro umožnění mockování v testu.
     *
     * @param origin Počáteční bod (LatLng).
     * @param destinationAddress Cílová adresa jako String.
     * @return Objekt DistanceMatrix nebo null při chybě kontextu.
     * @throws Exception Může vyhodit různé výjimky z Google API klienta (ApiException, IOException, InterruptedException).
     */
    protected DistanceMatrix getDistanceMatrixResult(LatLng origin, String destinationAddress) throws Exception {
        if (geoApiContext == null) {
            log.error("GeoApiContext is not initialized! Cannot call Google API.");
            // V testu budeme mockovat, ale v reálu by zde měla být vhodná reakce
            // Můžeme vrátit null nebo vyhodit specifickou výjimku
            return null; // Nebo throw new IllegalStateException("GeoApiContext not initialized");
        }
        log.debug("Calling Google Distance Matrix API: Origin={}, Destination='{}'", origin, destinationAddress);
        // Zde je samotné volání Google API
        return DistanceMatrixApi.newRequest(geoApiContext)
                .origins(origin)
                .destinations(destinationAddress)
                .mode(TravelMode.DRIVING) // Používáme auto jako výchozí
                // .units(Unit.METRIC) // Výchozí jsou metrické jednotky
                .await(); // await() může hodit různé checked/unchecked exceptions
    }

    @Override
    public BigDecimal calculateShippingCost(Order order, String currency) {
        String orderCodeForLog = order != null ? order.getOrderCode() : "N/A";
        log.info("Calculating shipping cost for order {} in currency {}", orderCodeForLog, currency);

        // Výchozí fallback cena pro danou měnu nebo nula
        BigDecimal fallbackPrice = EURO_CURRENCY.equals(currency) ? fixedPriceEUR : fixedPriceCZK;
        BigDecimal fallbackPriceOrDefault = fallbackPrice != null ? fallbackPrice.setScale(PRICE_SCALE, ROUNDING_MODE) : BigDecimal.ZERO;

        // Základní kontroly
        if (originLatLng == null || geoApiContext == null) {
            log.error("Origin coordinates or Google Maps API context not available. Cannot calculate shipping cost for order {}.", orderCodeForLog);
            // Vracíme vysokou fallback cenu, pokud není inicializace OK
            return new BigDecimal("3000.00").setScale(PRICE_SCALE, ROUNDING_MODE);
        }
        if (order == null) {
            log.error("Order object is null. Cannot calculate shipping cost.");
            return fallbackPriceOrDefault;
        }


        String destinationAddress = buildFullAddress(order);
        if (!StringUtils.hasText(destinationAddress)) {
            log.warn("Cannot calculate shipping for order {}: Delivery address is incomplete. Returning fallback price.", order.getOrderCode());
            return fallbackPriceOrDefault;
        }

        try {
            // Zavoláme naši obalovací metodu
            DistanceMatrix matrix = getDistanceMatrixResult(originLatLng, destinationAddress);

            // Zpracování výsledku
            if (matrix != null && matrix.rows != null && matrix.rows.length > 0
                    && matrix.rows[0].elements != null && matrix.rows[0].elements.length > 0)
            {
                DistanceMatrixElement element = matrix.rows[0].elements[0];
                if (element.status == DistanceMatrixElementStatus.OK && element.distance != null) {
                    // Máme platnou vzdálenost, spočítáme cenu
                    long distanceInMeters = element.distance.inMeters;
                    BigDecimal distanceInKm = BigDecimal.valueOf(distanceInMeters)
                            .divide(BigDecimal.valueOf(1000), CALCULATION_SCALE, ROUNDING_MODE);

                    BigDecimal fixedPrice = EURO_CURRENCY.equals(currency) ? fixedPriceEUR : fixedPriceCZK;
                    BigDecimal perKmPrice = EURO_CURRENCY.equals(currency) ? perKmPriceEUR : perKmPriceCZK;

                    // Kontrola, zda jsou ceny pro danou měnu nastaveny
                    if (fixedPrice == null || perKmPrice == null) {
                        log.error("Shipping price configuration missing for currency '{}' in order {}! Returning fallback price.", currency, order.getOrderCode());
                        return fallbackPriceOrDefault;
                    }

                    BigDecimal distanceCost = distanceInKm.multiply(perKmPrice);
                    BigDecimal totalCost = fixedPrice.add(distanceCost);
                    BigDecimal finalCost = totalCost.setScale(PRICE_SCALE, ROUNDING_MODE);

                    log.info("Calculated shipping cost for order {}: {} {} (Distance: {} km)",
                            order.getOrderCode(), finalCost, currency, distanceInKm.setScale(1, ROUNDING_MODE));
                    return finalCost;

                } else {
                    // API vrátilo element, ale se stavem chyby (např. ZERO_RESULTS, NOT_FOUND)
                    log.warn("Could not calculate distance for order {}. Google API status: {}. Returning fallback price.",
                            order.getOrderCode(), element.status);
                    return fallbackPriceOrDefault;
                }
            } else {
                // API nevrátilo očekávanou strukturu odpovědi
                log.warn("Distance Matrix API returned unexpected result (null or empty rows/elements) for order {}. Returning fallback price.",
                        order.getOrderCode());
                return fallbackPriceOrDefault;
            }

        } catch (Exception e) {
            // Zachytíme jakoukoliv výjimku z getDistanceMatrixResult nebo při zpracování
            log.error("Error during shipping calculation or Google API call for order {}: {}",
                    order.getOrderCode(), e.getMessage(), e);
            return fallbackPriceOrDefault; // Fallback cena při jakékoliv chybě
        }
    }

    @Override
    public BigDecimal getShippingTaxRate() {
        // Vrací pevnou sazbu DPH pro dopravu
        return SHIPPING_TAX_RATE;
    }

    /**
     * Sestaví úplnou dodací adresu z objednávky pro Google API.
     */
    private String buildFullAddress(Order order) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(order.getDeliveryStreet())) {
            sb.append(order.getDeliveryStreet());
        }
        if (StringUtils.hasText(order.getDeliveryCity())) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(order.getDeliveryCity());
        }
        if (StringUtils.hasText(order.getDeliveryZipCode())) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(order.getDeliveryZipCode());
        }
        if (StringUtils.hasText(order.getDeliveryCountry())) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(order.getDeliveryCountry());
        }
        log.trace("Built address string for Google API: '{}'", sb.toString());
        return sb.toString(); // Není třeba odstraňovat úvodní čárku, pokud začínáme bez ní
    }
}