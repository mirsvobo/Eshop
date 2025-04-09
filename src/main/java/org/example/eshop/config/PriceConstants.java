package org.example.eshop.config; // Nebo jiný vhodný balíček

import java.math.RoundingMode;

/**
 * Interface obsahující sdílené konstanty pro práci s cenami a zaokrouhlováním.
 * Použití interface místo třídy zamezuje nechtěné instanciaci.
 */
public interface PriceConstants {

    /** Počet desetinných míst pro finální ceny a zobrazení (typicky 2). */
    int PRICE_SCALE = 2;

    /** Počet desetinných míst pro mezivýpočty (vyšší přesnost). */
    int CALCULATION_SCALE = 4;

    /** Režim zaokrouhlování používaný v aplikaci. */
    RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    // Zde mohou být další sdílené konstanty, např. kódy měn
    String DEFAULT_CURRENCY = "CZK";
    String EURO_CURRENCY = "EUR";
}