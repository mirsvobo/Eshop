// Soubor: src/main/resources/static/js/product-configurator.js
// Verze: 3.0 - Implementace detailního rozpisu ceny

document.addEventListener('DOMContentLoaded', function() {
    console.log("[LOG] Configurator script starting (v3.0 - Price Breakdown)...");

    // --- Formulář a ID produktu ---
    const formElement = document.getElementById('custom-product-form');
    if (!formElement) {
        console.error("[ERROR] Form element '#custom-product-form' not found! Aborting script.");
        return;
    }
    console.log("[LOG] Form element found.");

    // --- Cache pro elementy ---
    console.log("[LOG] Caching UI elements...");
    // Celková cena
    const priceDisplay = document.getElementById('calculatedPrice');
    const priceContainer = document.getElementById('price-summary-section');
    const totalSpinner = priceContainer ? priceContainer.querySelector('.price-loading-spinner') : null;
    const errorDiv = document.getElementById('customPriceErrorDisplay');
    const submitButton = formElement.querySelector('button[type="submit"]');
    const quantityInput = document.getElementById('quantity');

    // Rozpis ceny
    const breakdownContainer = document.getElementById('price-breakdown');
    const breakdownBasePrice = document.getElementById('breakdown-base-price');
    const breakdownDesignRow = document.getElementById('breakdown-design-row');
    const breakdownDesignPrice = document.getElementById('breakdown-design-price');
    const breakdownGlazeRow = document.getElementById('breakdown-glaze-row');
    const breakdownGlazePrice = document.getElementById('breakdown-glaze-price');
    const breakdownRoofColorRow = document.getElementById('breakdown-roof-color-row');
    const breakdownRoofColorPrice = document.getElementById('breakdown-roof-color-price');
    const breakdownAddonsContainer = document.getElementById('breakdown-addons-container');
    const breakdownAddonsList = document.getElementById('breakdown-addons-list');
    const breakdownLoading = document.getElementById('breakdown-loading'); // Div pro načítání rozpisu

    // Posuvníky a jejich hodnoty
    const lengthSlider = document.getElementById('length');
    const widthSlider = document.getElementById('width');
    const heightSlider = document.getElementById('height');
    const lengthValueDisplay = document.getElementById('lengthValue');
    const widthValueDisplay = document.getElementById('widthValue');
    const heightValueDisplay = document.getElementById('heightValue');

    // Selecty pro atributy a doplňky
    const designSelect = document.getElementById('designSelect');
    const glazeSelect = document.getElementById('glazeSelect');
    const roofColorSelect = document.getElementById('roofColorSelect');
    const addonSelects = document.querySelectorAll('select[data-is-addon-category-select="true"]');
    const addonOptionElements = document.querySelectorAll('option[data-addon-id]'); // Používá se pro update textu

    console.log("[LOG] UI elements cached.");
    // Základní kontroly
    if (!priceDisplay || !priceContainer || !totalSpinner || !breakdownContainer || !breakdownBasePrice) {
        console.error("[ERROR] Core price display/breakdown elements missing!");
        return;
    }
    if (!lengthSlider || !widthSlider || !heightSlider) {
        console.warn("[WARN] Dimension sliders missing, custom configuration might not work.");
        // Pokud slidery nejsou, nemá smysl volat API - můžeme script ukončit nebo zakázat submit
        // displayCalculationError("Chyba konfigurace stránky (chybí posuvníky).");
        // return;
    }

    // --- Načtení dat z globálních proměnných (definovaných v HTML) ---
    const productIdInput = formElement.querySelector('input[name="productId"]');
    const productJsData = { id: productIdInput ? parseInt(productIdInput.value, 10) : null };

    // URL API a měna (musí být definovány v HTML <script> bloku!)
    if (typeof calculatePriceUrl === 'undefined' || typeof currency === 'undefined') {
        console.error("[ERROR] Globální proměnné 'calculatePriceUrl' nebo 'currency' nejsou definovány v HTML!");
        displayCalculationError("Chyba konfigurace stránky (API).");
        return;
    }
    const finalCalculatePriceUrl = calculatePriceUrl;
    const finalCurrency = currency;

    console.log(`[LOG] Initial Data: productID=${productJsData.id}, apiURL=${finalCalculatePriceUrl}, globalCurrency=${finalCurrency}`);

    if (!productJsData || !productJsData.id) {
        console.error("[ERROR] Product ID not found or invalid.");
        displayCalculationError('Chyba konfigurace stránky (ID produktu).');
        return;
    }

    // --- Globální proměnné ---
    let calculationTimeout;
    const DEBOUNCE_DELAY = 450;
    const PRICE_SCALE = 2;

    // --- Funkce pro UI ---

    function updateRangeValueDisplay(sliderElement, displayElement) {
        // ... (zůstává stejná jako v předchozí verzi) ...
        if (sliderElement && displayElement) {
            const valueCm = parseFloat(sliderElement.value);
            if (!isNaN(valueCm)) {
                const step = parseFloat(sliderElement.step) || 1;
                const decimals = (step % 1 === 0) ? 0 : 1;
                displayElement.textContent = valueCm.toFixed(decimals);
            } else {
                displayElement.textContent = '???';
            }
        }
    }

    function formatCurrency(amount, currencyCode) {
        // ... (zůstává stejná jako v předchozí verzi) ...
        if (amount == null || isNaN(amount)) return "N/A";
        const options = { style: 'currency', currency: currencyCode, minimumFractionDigits: 2, maximumFractionDigits: 2 };
        const locale = currencyCode === 'EUR' ? 'sk-SK' : 'cs-CZ';
        try {
            return new Intl.NumberFormat(locale, options).format(amount).replace(/\s/g, ' ');
        } catch (e) {
            const fixedAmount = amount.toFixed(2).replace('.', ',');
            return `${fixedAmount} ${currencyCode === 'EUR' ? '€' : 'Kč'}`;
        }
    }

    function displayCalculationError(message) {
        // ... (zůstává stejná, jen upravíme reset) ...
        const errorMsg = message || 'Při výpočtu ceny došlo k chybě.';
        console.error(`[ERROR] displayCalculationError: ${errorMsg}`);
        // Reset celkové ceny a rozpisu
        if (priceDisplay) priceDisplay.innerHTML = `<span class="text-danger">Chyba</span>`;
        if (breakdownBasePrice) breakdownBasePrice.textContent = "-";
        hideElement(breakdownDesignRow);
        hideElement(breakdownGlazeRow);
        hideElement(breakdownRoofColorRow);
        hideElement(breakdownAddonsContainer);
        hideElement(breakdownLoading);
        // Skrytí spinneru a zobrazení chyby
        if (priceContainer) priceContainer.classList.remove('price-loading');
        if (errorDiv) { errorDiv.textContent = errorMsg; errorDiv.style.display = 'block'; }
        if (submitButton) submitButton.disabled = true;
    }

    function showElement(element) {
        if (element) element.style.display = ''; // Reset na výchozí (obvykle block nebo flex)
    }

    function hideElement(element) {
        if (element) element.style.display = 'none';
    }

    // --- Funkce pro dynamické ceny v <option> ---

    function calculateSingleAddonPrice(optionElement, currentDimensions, currency) {
        // ... (zůstává stejná jako v předchozí verzi) ...
        const pricingType = optionElement.dataset.pricingType;
        const priceData = optionElement.dataset;
        let price = null;
        let unitPrice = 0;

        if (pricingType === 'FIXED') {
            price = parseFloat(currency === 'EUR' ? priceData.priceEur : priceData.priceCzk);
            if (isNaN(price) || price < 0) return null;
        } else {
            unitPrice = parseFloat(currency === 'EUR' ? priceData.pricePerUnitEur : priceData.pricePerUnitCzk);
            if (isNaN(unitPrice) || unitPrice <= 0) return null;
        }

        switch (pricingType) {
            case 'FIXED': break;
            case 'PER_CM_WIDTH':
                if (currentDimensions.width > 0) price = unitPrice * currentDimensions.width; else return null;
                break;
            case 'PER_CM_LENGTH':
                if (currentDimensions.length > 0) price = unitPrice * currentDimensions.length; else return null;
                break;
            case 'PER_CM_HEIGHT':
                if (currentDimensions.height > 0) price = unitPrice * currentDimensions.height; else return null;
                break;
            case 'PER_SQUARE_METER':
                if (currentDimensions.length > 0 && currentDimensions.width > 0) {
                    const lengthM = currentDimensions.length / 100.0;
                    const widthM = currentDimensions.width / 100.0;
                    price = unitPrice * lengthM * widthM;
                } else return null;
                break;
            default: return null;
        }
        return price !== null ? parseFloat(price.toFixed(PRICE_SCALE)) : null;
    }

    function updateAddonOptionText(optionElement, currentDimensions, currency) {
        // ... (zůstává stejná jako v předchozí verzi) ...
        const baseName = optionElement.textContent.split(' (+')[0];
        const calculatedPrice = calculateSingleAddonPrice(optionElement, currentDimensions, currency);
        let priceString = "";
        if (calculatedPrice !== null && calculatedPrice > 0) {
            priceString = ` (+ ${formatCurrency(calculatedPrice, currency)})`;
        }
        optionElement.textContent = baseName + priceString;
    }

    function updateAllAddonOptions() {
        // ... (zůstává stejná jako v předchozí verzi) ...
        if (!lengthSlider || !widthSlider || !heightSlider || !addonOptionElements.length) return;
        const currentDimensions = {
            length: parseFloat(lengthSlider.value) || 0,
            width: parseFloat(widthSlider.value) || 0,
            height: parseFloat(heightSlider.value) || 0
        };
        addonOptionElements.forEach(option => {
            updateAddonOptionText(option, currentDimensions, finalCurrency);
        });
        // console.log("Texty cen doplňků aktualizovány."); // Můžeme logovat méně
    }

    // --- Funkce pro aktualizaci UI rozpisu a celkové ceny ---

    /**
     * Aktualizuje HTML elementy pro rozpis a celkovou cenu na základě dat z API.
     * @param {object} priceData - Objekt CustomPriceResponseDto z API.
     */
    function updatePriceUI(priceData) {
        console.log("[UI_UPDATE] Updating price UI with data:", priceData);

        if (priceData.errorMessage) {
            displayCalculationError(priceData.errorMessage);
            return;
        }

        // --- Aktualizace Celkové Ceny ---
        const totalCZK = parseFloat(priceData.totalPriceCZK);
        const totalEUR = parseFloat(priceData.totalPriceEUR);

        if (isNaN(totalCZK) || isNaN(totalEUR)) {
            console.error("[UI_UPDATE] Invalid total price data received:", priceData);
            displayCalculationError("Neplatná data o celkové ceně ze serveru.");
            return;
        }

        const displayTotal = finalCurrency === 'EUR' ? totalEUR : totalCZK;
        if (priceDisplay) {
            priceDisplay.textContent = formatCurrency(displayTotal, finalCurrency);
        }
        if (priceContainer) priceContainer.classList.remove('price-loading'); // Skryje celkový spinner
        if (errorDiv) errorDiv.style.display = 'none';
        if (submitButton) submitButton.disabled = false;

        // --- Aktualizace Rozpisu Ceny ---
        if (!breakdownContainer || !breakdownBasePrice || !breakdownAddonsList) {
            console.warn("[UI_UPDATE] Breakdown elements missing, skipping breakdown update.");
            hideElement(breakdownLoading);
            return; // Nemůžeme aktualizovat rozpis
        }

        // Základní cena
        const baseCZK = parseFloat(priceData.basePriceCZK);
        const baseEUR = parseFloat(priceData.basePriceEUR);
        const displayBase = finalCurrency === 'EUR' ? baseEUR : baseCZK;
        breakdownBasePrice.textContent = formatCurrency(displayBase, finalCurrency);

        // Design
        const designCZK = parseFloat(priceData.designPriceCZK);
        const designEUR = parseFloat(priceData.designPriceEUR);
        const displayDesign = finalCurrency === 'EUR' ? designEUR : designCZK;
        if (displayDesign > 0) {
            breakdownDesignPrice.textContent = formatCurrency(displayDesign, finalCurrency);
            showElement(breakdownDesignRow);
        } else {
            hideElement(breakdownDesignRow);
        }

        // Lazura
        const glazeCZK = parseFloat(priceData.glazePriceCZK);
        const glazeEUR = parseFloat(priceData.glazePriceEUR);
        const displayGlaze = finalCurrency === 'EUR' ? glazeEUR : glazeCZK;
        if (displayGlaze > 0) {
            breakdownGlazePrice.textContent = formatCurrency(displayGlaze, finalCurrency);
            showElement(breakdownGlazeRow);
        } else {
            hideElement(breakdownGlazeRow);
        }

        // Střecha
        const roofCZK = parseFloat(priceData.roofColorPriceCZK);
        const roofEUR = parseFloat(priceData.roofColorPriceEUR);
        const displayRoof = finalCurrency === 'EUR' ? roofEUR : roofCZK;
        if (displayRoof > 0) {
            breakdownRoofColorPrice.textContent = formatCurrency(displayRoof, finalCurrency);
            showElement(breakdownRoofColorRow);
        } else {
            hideElement(breakdownRoofColorRow);
        }

        // Doplňky
        const addonPrices = finalCurrency === 'EUR' ? priceData.addonPricesEUR : priceData.addonPricesCZK;
        breakdownAddonsList.innerHTML = ''; // Vyčistit předchozí doplňky
        let hasAddons = false;
        if (addonPrices && Object.keys(addonPrices).length > 0) {
            Object.entries(addonPrices).forEach(([name, priceStr]) => {
                const price = parseFloat(priceStr);
                if (!isNaN(price) && price > 0) { // Zobrazujeme jen doplňky s cenou > 0
                    hasAddons = true;
                    const addonRow = document.createElement('div');
                    addonRow.className = 'd-flex justify-content-between mb-1';
                    const nameSpan = document.createElement('span');
                    nameSpan.textContent = name;
                    const priceSpan = document.createElement('span');
                    priceSpan.className = 'fw-bold';
                    priceSpan.textContent = formatCurrency(price, finalCurrency);
                    addonRow.appendChild(nameSpan);
                    addonRow.appendChild(priceSpan);
                    breakdownAddonsList.appendChild(addonRow);
                }
            });
        }

        // Zobrazit/skrýt kontejner doplňků
        if (hasAddons) {
            showElement(breakdownAddonsContainer);
        } else {
            hideElement(breakdownAddonsContainer);
        }

        // Skryjeme spinner pro rozpis
        hideElement(breakdownLoading);

        console.log("[UI_UPDATE] Price UI update complete.");
    }


    // --- Kalkulace Detailní Ceny - volání API ---

    /**
     * Zpracuje změnu v konfiguraci.
     */
    function handleConfigurationChange(event) {
        const targetElement = event?.target;
        if (!targetElement) return;

        console.log(`[EVENT] Config change detected on: ${targetElement.id || targetElement.name}`);

        // Zobrazíme oba spinnery (pro celkovou cenu i rozpis)
        if (priceContainer) priceContainer.classList.add('price-loading');
        if (breakdownLoading) showElement(breakdownLoading);
        if (submitButton) submitButton.disabled = true;
        if (errorDiv) errorDiv.style.display = 'none';

        // Pokud se změnily rozměry, aktualizujeme text cen doplňků v selectech
        if (targetElement.matches('.dimension-slider')) {
            const valueDisplayId = targetElement.id + 'Value';
            const valueDisplayElement = document.getElementById(valueDisplayId);
            updateRangeValueDisplay(targetElement, valueDisplayElement);
            updateAllAddonOptions();
        }

        // Vždy zavoláme API po jakékoli změně s debounce
        clearTimeout(calculationTimeout);
        calculationTimeout = setTimeout(fetchDetailedPriceApiCall, DEBOUNCE_DELAY);
    }

    /**
     * Zavolá API pro výpočet DETAILNÍ ceny včetně rozpisu.
     */
    function fetchDetailedPriceApiCall() {
        console.log("[API_CALL] Debounce finished. Starting API call to fetch DETAILED price...");
        if (!lengthSlider || !widthSlider || !heightSlider) {
            console.error("API call aborted, sliders missing.");
            displayCalculationError("Chyba konfigurace (posuvníky).");
            return;
        }

        const lengthValue = lengthSlider.value;
        const widthValue = widthSlider.value;
        const heightValue = heightSlider.value;

        if (isNaN(parseFloat(lengthValue)) || isNaN(parseFloat(widthValue)) || isNaN(parseFloat(heightValue))) {
            console.error("[ERROR] Invalid non-numeric dimension values:", { lengthValue, widthValue, heightValue });
            displayCalculationError('Zvolené rozměry nejsou platné.');
            return;
        }

        // --- Sestavení payloadu ---
        const selectedAddonIds = [];
        addonSelects.forEach(select => {
            const val = select.value;
            if (val && val !== '0') { // Přidáme jen ID, které není '0' (volba "Ne")
                const id = parseInt(val, 10);
                if (!isNaN(id)) selectedAddonIds.push(id);
            }
        });

        const requestPayload = {
            productId: productJsData.id,
            customDimensions: {
                length: parseFloat(lengthValue).toFixed(2),
                width: parseFloat(widthValue).toFixed(2),
                height: parseFloat(heightValue).toFixed(2)
            },
            // Přidáme ID vybraných atributů (pokud selecty existují)
            selectedDesignId: designSelect ? parseInt(designSelect.value, 10) || null : null,
            selectedGlazeId: glazeSelect ? parseInt(glazeSelect.value, 10) || null : null,
            selectedRoofColorId: roofColorSelect ? parseInt(roofColorSelect.value, 10) || null : null,
            selectedAddonIds: selectedAddonIds
        };
        console.log("[LOG] Constructed request payload for DETAILED price:", requestPayload);

        // --- Volání API ---
        console.log(`[API_CALL] Sending POST request to: ${finalCalculatePriceUrl}`);
        fetch(finalCalculatePriceUrl, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                // CSRF token, pokud je potřeba
                // [csrfHeaderName]: csrfToken
            },
            body: JSON.stringify(requestPayload)
        })
            .then(response => {
                console.log(`[API_CALL] Received response status: ${response.status}`);
                if (!response.ok) {
                    return response.json().then(err => {
                        throw new Error(err.errorMessage || `Chyba serveru (${response.status})`);
                    }).catch(() => { throw new Error(`Chyba serveru (${response.status})`); });
                }
                return response.json();
            })
            .then(data => {
                console.log("[API_CALL] Received DETAILED price data:", data);
                // Aktualizujeme celé UI (celkovou cenu i rozpis)
                updatePriceUI(data);
            })
            .catch(error => {
                console.error('[API_CALL] Fetch error or API error:', error);
                displayCalculationError(error.message || 'Neznámá chyba při komunikaci se serverem.');
            });
    }

    // --- Funkce pro přípravu dat formuláře ---
    function prepareFormDataBeforeSubmit(event) {
        // ... (zůstává stejná jako v předchozí verzi) ...
        console.log("[FORM_SUBMIT] Preparing form data...");
        addonSelects.forEach(select => {
            if (select.value === '0') {
                select.disabled = true;
            } else {
                select.disabled = false;
            }
        });
        console.log("[FORM_SUBMIT] Form data prepared. Submitting...");
    }

    // --- Inicializace Event Listenerů ---
    console.log("[LOG] Attaching event listeners...");
    // Všechny vstupy, které ovlivňují cenu, budou spouštět handleConfigurationChange
    const configInputs = [lengthSlider, widthSlider, heightSlider, designSelect, glazeSelect, roofColorSelect, quantityInput, ...addonSelects];
    configInputs.forEach(input => {
        if (input) { // Zkontrolujeme, jestli element existuje
            const eventType = input.matches('.dimension-slider') ? 'input' : 'change';
            console.log(`[LOG] Attaching '${eventType}' listener to config element: ${input.id || input.name}`);
            input.addEventListener(eventType, handleConfigurationChange);
        }
    });

    formElement.addEventListener('submit', prepareFormDataBeforeSubmit);

    // --- Nastavení počátečního stavu ---
    console.log("[LOG] Setting initial state...");
    // 1. Aktualizovat zobrazení hodnot posuvníků
    if (lengthSlider && lengthValueDisplay) updateRangeValueDisplay(lengthSlider, lengthValueDisplay);
    if (widthSlider && widthValueDisplay) updateRangeValueDisplay(widthSlider, widthValueDisplay);
    if (heightSlider && heightValueDisplay) updateRangeValueDisplay(heightSlider, heightValueDisplay);

    // 2. Aktualizovat texty cen u VŠECH doplňků na základě výchozích rozměrů
    updateAllAddonOptions();

    // 3. Zavolat API pro načtení počátečního detailního rozpisu a ceny
    console.log("[LOG] Triggering initial API call to fetch detailed price.");
    fetchDetailedPriceApiCall(); // Tato funkce nyní zobrazí i spinnery a aktualizuje UI

    console.log("[LOG] Configurator initialization complete (v3.0).");

});