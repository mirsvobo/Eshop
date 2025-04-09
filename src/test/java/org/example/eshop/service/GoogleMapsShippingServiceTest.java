// Soubor: src/test/java/org/example/eshop/service/GoogleMapsShippingServiceTest.java
package org.example.eshop.service;

// Zbytečné importy pro DistanceMatrixApiRequest a MockedStatic odstraněny
import com.google.maps.GeoApiContext;
import com.google.maps.model.*;
import org.example.eshop.config.PriceConstants;
import org.example.eshop.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleMapsShippingServiceTest implements PriceConstants {

    @Mock
    private GeoApiContext geoApiContext;

    @Spy // Stále používáme Spy
    @InjectMocks
    private GoogleMapsShippingService shippingServiceSpy;

    private Order testOrder;
    private LatLng originLatLng;

    // Ceny pro testování
    private final BigDecimal fixedCZK = new BigDecimal("500.00");
    private final BigDecimal perKmCZK = new BigDecimal("15.00");
    private final BigDecimal fixedEUR = new BigDecimal("20.00");
    private final BigDecimal perKmEUR = new BigDecimal("0.60");
    private final BigDecimal taxRate = new BigDecimal("0.21");


    @BeforeEach
    void setUp() {
        // Setup zůstává stejný
        ReflectionTestUtils.setField(shippingServiceSpy, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(shippingServiceSpy, "originLatitude", 50.035003);
        ReflectionTestUtils.setField(shippingServiceSpy, "originLongitude", 15.491800);
        ReflectionTestUtils.setField(shippingServiceSpy, "fixedPriceCZK", fixedCZK);
        ReflectionTestUtils.setField(shippingServiceSpy, "perKmPriceCZK", perKmCZK);
        ReflectionTestUtils.setField(shippingServiceSpy, "fixedPriceEUR", fixedEUR);
        ReflectionTestUtils.setField(shippingServiceSpy, "perKmPriceEUR", perKmEUR);

        originLatLng = new LatLng(50.035003, 15.491800);
        ReflectionTestUtils.setField(shippingServiceSpy, "geoApiContext", geoApiContext);
        ReflectionTestUtils.setField(shippingServiceSpy, "originLatLng", originLatLng);

        testOrder = new Order();
        testOrder.setOrderCode("SHIP-TEST-1");
        testOrder.setDeliveryStreet("Testovací Ulice 10");
        testOrder.setDeliveryCity("Praha");
        testOrder.setDeliveryZipCode("11000");
        testOrder.setDeliveryCountry("Česká republika");
    }

    // Pomocná metoda pro vytvoření mock DistanceMatrix odpovědi (zůstává stejná)
    private DistanceMatrix createMockDistanceMatrix(DistanceMatrixElementStatus status, long distanceInMeters) {
        DistanceMatrix result = new DistanceMatrix(new String[]{"Origin"}, new String[]{"Destination"}, new DistanceMatrixRow[1]);
        result.rows[0] = new DistanceMatrixRow();
        result.rows[0].elements = new DistanceMatrixElement[1];
        result.rows[0].elements[0] = new DistanceMatrixElement();
        result.rows[0].elements[0].status = status;
        if (status == DistanceMatrixElementStatus.OK) {
            result.rows[0].elements[0].distance = new Distance();
            result.rows[0].elements[0].distance.inMeters = distanceInMeters;
        }
        return result;
    }

    // ODSTRANILI JSME metody mockApiChainSetup a setupMockApiChainToThrow


    @Test
    @DisplayName("[calculateShippingCost] Úspěšný výpočet ceny dopravy (CZK)")
    void calculateShippingCost_Success_CZK() throws Exception {
        // --- Příprava ---
        long distanceMeters = 150000; // 150 km
        BigDecimal distanceKm = new BigDecimal("150.0000");
        testOrder.setCurrency("CZK");
        String expectedDestination = "Testovací Ulice 10, Praha, 11000, Česká republika";

        // Vytvoříme mock odpovědi
        DistanceMatrix mockResponse = createMockDistanceMatrix(DistanceMatrixElementStatus.OK, distanceMeters);

        // Mockujeme POUZE naši novou metodu, aby vrátila připravenou odpověď
        doReturn(mockResponse).when(shippingServiceSpy).getDistanceMatrixResult(originLatLng, expectedDestination);

        // --- Provedení ---
        BigDecimal calculatedCost = shippingServiceSpy.calculateShippingCost(testOrder, "CZK");

        // --- Ověření ---
        BigDecimal expectedCost = fixedCZK.add(distanceKm.multiply(perKmCZK)).setScale(PRICE_SCALE, ROUNDING_MODE);
        assertNotNull(calculatedCost);
        assertEquals(0, expectedCost.compareTo(calculatedCost));

        // Ověření, že byla volána naše obalovací metoda se správnými argumenty
        verify(shippingServiceSpy).getDistanceMatrixResult(originLatLng, expectedDestination);
    }

    @Test
    @DisplayName("[calculateShippingCost] Úspěšný výpočet ceny dopravy (EUR)")
    void calculateShippingCost_Success_EUR() throws Exception {
        // --- Příprava ---
        long distanceMeters = 85500; // 85.5 km
        BigDecimal distanceKm = new BigDecimal("85.5000");
        testOrder.setCurrency("EUR");
        testOrder.setDeliveryCity("Brno");
        String expectedDestination = "Testovací Ulice 10, Brno, 11000, Česká republika";

        DistanceMatrix mockResponse = createMockDistanceMatrix(DistanceMatrixElementStatus.OK, distanceMeters);
        doReturn(mockResponse).when(shippingServiceSpy).getDistanceMatrixResult(originLatLng, expectedDestination);


        // --- Provedení ---
        BigDecimal calculatedCost = shippingServiceSpy.calculateShippingCost(testOrder, "EUR");

        // --- Ověření ---
        BigDecimal expectedCost = fixedEUR.add(distanceKm.multiply(perKmEUR)).setScale(PRICE_SCALE, ROUNDING_MODE);
        assertNotNull(calculatedCost);
        assertEquals(0, expectedCost.compareTo(calculatedCost));

        verify(shippingServiceSpy).getDistanceMatrixResult(originLatLng, expectedDestination);
    }


    @Test
    @DisplayName("[calculateShippingCost] API vrátí chybu (ZERO_RESULTS)")
    void calculateShippingCost_ApiReturnsZeroResults() throws Exception {
        // --- Příprava ---
        testOrder.setCurrency("CZK");
        String expectedDestination = "Testovací Ulice 10, Praha, 11000, Česká republika";
        DistanceMatrix mockErrorResponse = createMockDistanceMatrix(DistanceMatrixElementStatus.ZERO_RESULTS, 0);
        doReturn(mockErrorResponse).when(shippingServiceSpy).getDistanceMatrixResult(originLatLng, expectedDestination);


        // --- Provedení ---
        BigDecimal calculatedCost = shippingServiceSpy.calculateShippingCost(testOrder, "CZK");

        // --- Ověření ---
        assertEquals(0, fixedCZK.compareTo(calculatedCost)); // Fallback cena
        verify(shippingServiceSpy).getDistanceMatrixResult(originLatLng, expectedDestination); // Ověříme, že metoda byla volána
    }

    @Test
    @DisplayName("[calculateShippingCost] API volání selže (simulovaná Exception)")
    void calculateShippingCost_ApiThrowsException() throws Exception {
        // --- Příprava ---
        testOrder.setCurrency("EUR");
        String expectedDestination = "Testovací Ulice 10, Praha, 11000, Česká republika";

        // Mockujeme naši metodu, aby hodila výjimku
        doThrow(new RuntimeException("Simulated API communication error"))
                .when(shippingServiceSpy).getDistanceMatrixResult(originLatLng, expectedDestination);

        // --- Provedení ---
        BigDecimal calculatedCost = shippingServiceSpy.calculateShippingCost(testOrder, "EUR");

        // --- Ověření ---
        assertEquals(0, fixedEUR.compareTo(calculatedCost)); // Fallback cena
        verify(shippingServiceSpy).getDistanceMatrixResult(originLatLng, expectedDestination);
    }

    @Test
    @DisplayName("[calculateShippingCost] Neúplná adresa v objednávce vede k fallback ceně")
    void calculateShippingCost_IncompleteAddress() throws Exception {
        // --- Příprava ---
        // Nastavíme všechny klíčové části adresy na null, aby buildFullAddress vrátil prázdný řetězec
        testOrder.setDeliveryStreet(null);
        testOrder.setDeliveryCity(null);
        testOrder.setDeliveryZipCode(null);
        testOrder.setDeliveryCountry(null); // I zemi pro jistotu

        testOrder.setCurrency("CZK");

        // --- Provedení ---
        BigDecimal calculatedCost = shippingServiceSpy.calculateShippingCost(testOrder, "CZK");

        // --- Ověření ---
        // Očekáváme fallback cenu (fixní částku)
        assertEquals(0, fixedCZK.compareTo(calculatedCost));
        // Ověříme, že naše obalovací metoda getDistanceMatrixResult NEBYLA volána
        verify(shippingServiceSpy, never()).getDistanceMatrixResult(any(LatLng.class), anyString());
    }

    @Test
    @DisplayName("[calculateShippingCost] Google API kontext není inicializován")
    void calculateShippingCost_ContextNotInitialized() throws Exception {
        ReflectionTestUtils.setField(shippingServiceSpy, "geoApiContext", null);
        testOrder.setCurrency("CZK");
        BigDecimal calculatedCost = shippingServiceSpy.calculateShippingCost(testOrder, "CZK");
        assertEquals(0, new BigDecimal("3000.00").compareTo(calculatedCost)); // Fallback cena
        verify(shippingServiceSpy, never()).getDistanceMatrixResult(any(LatLng.class), anyString());
    }

    @Test
    @DisplayName("[getShippingTaxRate] Vrátí správnou sazbu DPH")
    void getShippingTaxRate_ReturnsCorrectRate() {
        assertEquals(0, taxRate.compareTo(shippingServiceSpy.getShippingTaxRate()));
    }
}