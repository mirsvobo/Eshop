// Soubor: src/main/resources/static/js/product-configurator.js
// Verze: 1.9 - Odstraněna kontrola existence elementů v calculatePriceApiCall + logika pro select atributy

document.addEventListener('DOMContentLoaded', function() {
    console.log("[LOG] Configurator script starting (v1.9)...");

    const formElement = document.getElementById('add-to-cart-form');
    if (!formElement) {
        console.error("[ERROR] Form element #add-to-cart-form not found! Aborting script.");
        return;
    }
    console.log("[LOG] Form element found.");

    // --- Cache pro elementy (PŘESUNUTO NAHORU) ---
    console.log("[LOG] Caching UI elements...");
    const priceDisplay = document.getElementById('product-price-display');
    const spinner = document.getElementById('price-spinner');
    const errorDiv = document.getElementById('customPriceErrorDisplay');
    const submitButton = document.getElementById('add-to-cart-btn');
    const quantityInput = document.getElementById('quantity');
    const lengthSlider = document.getElementById('customLength');
    const widthSlider = document.getElementById('customWidth');
    const heightSlider = document.getElementById('customHeight');
    const lengthValueDisplay = document.getElementById('customLengthValueDisplay');
    const widthValueDisplay = document.getElementById('customWidthValueDisplay');
    const heightValueDisplay = document.getElementById('customHeightValueDisplay');
    const designSelect = document.getElementById('designSelect');       // Pro <select>
    const glazeSelect = document.getElementById('glazeSelect');         // Pro <select>
    const roofColorSelect = document.getElementById('roofColorSelect'); // Pro <select>
    const hasDividerCheckbox = document.getElementById('customHasDivider');
    const hasGutterCheckbox = document.getElementById('customHasGutter');
    const hasShedCheckbox = document.getElementById('customHasGardenShed');
    console.log("[LOG] UI elements cached.");
    // Zkontrolujeme, jestli se našly klíčové elementy pro cenu
    if (!priceDisplay || !spinner || !errorDiv || !submitButton) {
        console.error("[ERROR] One or more core UI elements for price display/control not found during init!");
        // Můžeme zde zobrazit nějakou obecnou chybu uživateli, pokud prvky chybí
    }
    if (!lengthSlider || !widthSlider || !heightSlider || !lengthValueDisplay || !widthValueDisplay || !heightValueDisplay) {
        console.warn("[WARN] One or more dimension slider/display elements not found.");
    }
    if (!designSelect || !glazeSelect || !roofColorSelect) {
        console.warn("[WARN] One or more attribute select elements not found.");
    }


    // --- Načtení dat z data-* atributů ---
    const productJsData = { id: formElement.dataset.productId ? parseInt(formElement.dataset.productId, 10) : null };
    let initialCzkValue = formElement.dataset.initialCzk;
    let initialEurValue = formElement.dataset.initialEur;
    console.log(`[LOG] Raw initial prices from data attributes: CZK=${initialCzkValue}, EUR=${initialEurValue}`);
    let currentUnitPriceCZK = (initialCzkValue && !isNaN(parseFloat(initialCzkValue))) ? parseFloat(initialCzkValue) : 0.0;
    let currentUnitPriceEUR = (initialEurValue && !isNaN(parseFloat(initialEurValue))) ? parseFloat(initialEurValue) : 0.0;
    const initialErrorFromServer = formElement.dataset.initialError || null;
    const calculatePriceUrl = formElement.dataset.calculateUrl || '/api/product/calculate-price';
    const currencySymbol = formElement.dataset.currencySymbol || 'Kč';
    const currentGlobalCurrency = currencySymbol === '€' ? 'EUR' : 'CZK'; // Odvození kódu měny
    console.log(`[LOG] Parsed Data: productID=${productJsData.id}, initialUnitCZK=${currentUnitPriceCZK}, initialUnitEUR=${currentUnitPriceEUR}, initialError=${initialErrorFromServer}, apiURL=${calculatePriceUrl}, currencySymbol=${currencySymbol}, globalCurrency=${currentGlobalCurrency}`);

    if (!productJsData || !productJsData.id) {
        console.error("[ERROR] Product ID not found or invalid in data attributes.");
        displayCalculationError('Chyba konfigurace stránky (ID produktu).');
        return;
    }

    // --- Globální proměnné ---
    let calculationTimeout;
    const DEBOUNCE_DELAY = 400; // ms

    // --- Funkce pro UI ---
    function changeMainImage(thumbElement) {
        // console.log("[LOG] changeMainImage called for element:", thumbElement);
        if (!thumbElement) return;
        const imageUrl = thumbElement.getAttribute('data-img-url');
        const mainImage = document.getElementById('mainProductImage');
        if (imageUrl && mainImage) {
            // console.log(`[LOG] Setting main image src to: ${imageUrl}`);
            mainImage.src = imageUrl;
            document.querySelectorAll('.product-thumbnails img').forEach(img => img.classList.remove('active'));
            thumbElement.classList.add('active');
        } else {
            console.warn("[WARN] Could not change main image. Image URL or main image element missing.");
        }
    }
    window.changeMainImage = changeMainImage;

    function updateRangeValueDisplay(sliderElement, displayElement) {
        if (sliderElement && displayElement) {
            const valueCm = parseFloat(sliderElement.value);
            if (!isNaN(valueCm)) {
                const formattedValue = valueCm.toFixed(1).replace('.', ',');
                displayElement.textContent = formattedValue;
            } else {
                console.warn(`[WARN] Invalid value from slider ${sliderElement.id}: ${sliderElement.value}`);
                displayElement.textContent = '???,?';
            }
        }
    }

    function formatCurrency(amount, currencyCode) {
        if (amount == null || isNaN(amount)) return "---";
        const options = { style: 'currency', currency: currencyCode, minimumFractionDigits: 2, maximumFractionDigits: 2 };
        const locale = currencyCode === 'EUR' ? 'sk-SK' : 'cs-CZ';
        try { return new Intl.NumberFormat(locale, options).format(amount); }
        catch (e) { console.error("[ERROR] Error formatting currency:", { amount, currencyCode, locale }, e); const fixedAmount = amount.toFixed(2).replace('.', ','); return `${fixedAmount} ${currencyCode === 'EUR' ? '€' : 'Kč'}`; }
    }

    function getSelectedFixedAddonsPrice(currency) {
        // console.log(`[LOG] Calculating fixed addons price for currency: ${currency}`);
        let totalAddonPrice = 0;
        document.querySelectorAll('.fixed-addon-checkbox:checked').forEach(checkbox => {
            const addonId = checkbox.value;
            const priceInputId = currency === 'EUR' ? `addon_price_eur_${addonId}` : `addon_price_czk_${addonId}`;
            const priceInput = document.getElementById(priceInputId);
            if (priceInput?.value) {
                const price = parseFloat(priceInput.value);
                if (!isNaN(price) && price > 0) { totalAddonPrice += price; }
                else { /* console.warn(`[WARN] Invalid or zero price for addon ${addonId} (${currency}): ${priceInput.value}`); */ }
            } else { /* console.warn(`[WARN] Price input not found for addon ${addonId} (${currency}): ${priceInputId}`); */ }
        });
        // console.log(`[LOG] Total fixed addons price (${currency}): ${totalAddonPrice.toFixed(2)}`);
        return totalAddonPrice;
    }

    function getSelectedAttributeSurcharge(currency) {
        let totalSurcharge = 0;
        const selects = [designSelect, glazeSelect, roofColorSelect];
        // console.log(`[LOG] Calculating attribute surcharge for currency: ${currency}`);

        selects.forEach(select => {
            if (select && select.value && select.selectedIndex >= 0) {
                const selectedOption = select.options[select.selectedIndex];
                if (selectedOption) {
                    const priceAttr = currency === 'EUR' ? 'data-price-eur' : 'data-price-czk';
                    const price = parseFloat(selectedOption.getAttribute(priceAttr) || '0');
                    if (!isNaN(price) && price > 0) {
                        totalSurcharge += price;
                        // console.log(`[LOG] Surcharge added for ${select.id} (${currency}): ${price.toFixed(2)}`);
                    }
                }
            }
        });
        console.log(`[LOG] Total attribute surcharge (${currency}): ${totalSurcharge.toFixed(2)}`);
        return totalSurcharge;
    }

    function updateDisplayPrice() {
        console.log("[LOG] updateDisplayPrice called");
        if (!priceDisplay) { console.error("[ERROR] Price display element not found!"); return; }

        const quantity = quantityInput ? (parseInt(quantityInput.value, 10) || 1) : 1;
        const addonsPriceCZK = getSelectedFixedAddonsPrice('CZK');
        const addonsPriceEUR = getSelectedFixedAddonsPrice('EUR');
        const attributeSurchargeCZK = getSelectedAttributeSurcharge('CZK');
        const attributeSurchargeEUR = getSelectedAttributeSurcharge('EUR');

        const unitCZK = !isNaN(currentUnitPriceCZK) ? currentUnitPriceCZK : 0;
        const unitEUR = !isNaN(currentUnitPriceEUR) ? currentUnitPriceEUR : 0;

        const finalUnitCZK = unitCZK + attributeSurchargeCZK + addonsPriceCZK;
        const finalUnitEUR = unitEUR + attributeSurchargeEUR + addonsPriceEUR;

        const totalCZK = quantity * finalUnitCZK;
        const totalEUR = quantity * finalUnitEUR;

        console.log(`[LOG] updateDisplayPrice - unitCZK=${unitCZK.toFixed(2)}, attrSurCZK=${attributeSurchargeCZK.toFixed(2)}, addonsCZK=${addonsPriceCZK.toFixed(2)}, finalUnitCZK=${finalUnitCZK.toFixed(2)}, quantity=${quantity}, totalCZK=${totalCZK.toFixed(2)}`);

        if (isNaN(totalCZK) || isNaN(totalEUR)) {
            console.error("[ERROR] NaN detected in total price calculation.");
            displayCalculationError('Při výpočtu ceny došlo k interní chybě.');
        } else {
            const czkHtml = formatCurrency(totalCZK, 'CZK');
            const eurHtml = formatCurrency(totalEUR, 'EUR');
            priceDisplay.innerHTML = `${czkHtml} <span class="price-eur">/ ${eurHtml}</span>`;
            if (spinner) spinner.classList.add('d-none');
            if (errorDiv) errorDiv.style.display = 'none';
            priceDisplay.classList.remove('calculating');
            if (submitButton) submitButton.disabled = false;
            console.log("[LOG] Price display updated successfully.");
        }
    }

    function displayCalculationError(message) {
        const errorMsg = message || 'Při výpočtu ceny došlo k chybě.';
        console.error(`[ERROR] displayCalculationError: ${errorMsg}`);
        currentUnitPriceCZK = 0; currentUnitPriceEUR = 0;
        console.log("[LOG] Unit prices reset to 0 due to error.");
        if (priceDisplay) { priceDisplay.innerHTML = `<span class="text-danger">Chyba výpočtu</span>`; priceDisplay.classList.remove('calculating'); }
        if (spinner) spinner.classList.add('d-none');
        if (errorDiv) { errorDiv.textContent = errorMsg; errorDiv.style.display = 'block'; }
        if (submitButton) submitButton.disabled = true;
    }

    // --- Kalkulace ceny - volání API ---
    function handleCustomConfigChange(event) {
        const targetId = event?.target?.id || 'unknown';
        console.log(`[EVENT] Configuration changed by element: ${targetId}. Scheduling API call...`);
        if (event && event.target && event.target.matches('.dimension-slider')) {
            const displayId = event.target.id + 'ValueDisplay';
            const displayElement = document.getElementById(displayId);
            updateRangeValueDisplay(event.target, displayElement);
        }
        if (priceDisplay) priceDisplay.classList.add('calculating');
        if (spinner) spinner.classList.remove('d-none');
        if (submitButton) submitButton.disabled = true;
        if (errorDiv) errorDiv.style.display = 'none';
        clearTimeout(calculationTimeout);
        calculationTimeout = setTimeout(calculatePriceApiCall, DEBOUNCE_DELAY);
    }

    function calculatePriceApiCall() {
        console.log("[API_CALL] Debounce timeout finished. Starting API call to calculate unit price...");
        // ODSTRANĚNA KONTROLA ELEMENTŮ ZDE

        const lengthValue = lengthSlider?.value;
        const widthValue = widthSlider?.value;
        const heightValue = heightSlider?.value;

        const requestPayload = {
            productId: productJsData.id,
            customDimensions: {
                length: lengthValue ? Number(lengthValue).toFixed(2) : null,
                width: widthValue ? Number(widthValue).toFixed(2) : null,
                height: heightValue ? Number(heightValue).toFixed(2) : null
            },
            // Atributy (design, lazura, barva) se už neposílají
            customHasDivider: hasDividerCheckbox?.checked || false,
            customHasGutter: hasGutterCheckbox?.checked || false,
            customHasGardenShed: hasShedCheckbox?.checked || false
        };
        console.log("[LOG] Constructed request payload:", requestPayload);

        const dims = requestPayload.customDimensions;
        if (dims.length === null || dims.width === null || dims.height === null || isNaN(parseFloat(dims.length)) || isNaN(parseFloat(dims.width)) || isNaN(parseFloat(dims.height))) {
            console.error("[ERROR] Invalid dimension values found in payload:", dims);
            displayCalculationError('Zvolené rozměry nejsou platné.');
            return;
        }

        console.log(`[API_CALL] Sending POST request to: ${calculatePriceUrl}`);
        fetch(calculatePriceUrl, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            body: JSON.stringify(requestPayload)
        })
            .then(response => {
                console.log(`[API_CALL] Received response status: ${response.status}`);
                if (!response.ok) {
                    return response.json().then(err => { throw err; }).catch(() => { throw { status: response.status, message: `Chyba serveru (${response.status})` }; });
                }
                return response.json();
            })
            .then(data => {
                console.log("[API_CALL] Received data:", data);
                if (data.errorMessage) {
                    console.warn("[API_CALL] API returned error message:", data.errorMessage);
                    throw { message: data.errorMessage };
                }
                const czk = parseFloat(data.priceCZK);
                const eur = parseFloat(data.priceEUR);
                console.log(`[LOG] Parsed unit prices from API: CZK=${czk}, EUR=${eur}`);
                currentUnitPriceCZK = !isNaN(czk) ? czk : 0;
                currentUnitPriceEUR = !isNaN(eur) ? eur : 0;
                console.log(`[LOG] Stored global unit prices: CZK=${currentUnitPriceCZK}, EUR=${currentUnitPriceEUR}`);
                console.log("[LOG] Calling updateDisplayPrice after successful API call.");
                updateDisplayPrice(); // Zde se přičtou i příplatky atributů
            })
            .catch(error => {
                console.error('[API_CALL] Fetch error or API error:', error);
                const message = error.errorMessage || error.message || 'Neznámá chyba při výpočtu ceny.';
                displayCalculationError(message);
            });
    }

    // Funkce volaná při změně množství, addonů NEBO VÝBĚRU ATRIBUTU
    function handlePriceInfluencingChange(event) {
        const targetId = event?.target?.id || 'unknown';
        console.log(`[EVENT] Price influencing change detected (Quantity/Addon/Attribute Select): ${targetId}. Updating display price...`);
        updateDisplayPrice(); // Jen přepočítá zobrazenou cenu
    }

    // --- Inicializace Event Listenerů ---
    console.log("[LOG] Attaching event listeners...");
    // Slidery a checkboxy volitelných prvků spouští API call
    document.querySelectorAll('.dimension-slider, .custom-feature-checkbox').forEach(input => {
        const eventType = input.matches('.dimension-slider') ? 'input' : 'change';
        console.log(`[LOG] Attaching '${eventType}' listener to config element: ${input.id}`);
        input.addEventListener(eventType, handleCustomConfigChange);
        if (input.matches('.dimension-slider')) {
            const displayId = input.id + 'ValueDisplay';
            const displayElement = document.getElementById(displayId);
            updateRangeValueDisplay(input, displayElement);
        }
    });

    // Selecty atributů a checkboxy addonů aktualizují jen zobrazenou cenu
    // Přidána třída 'config-input-select' k selectům v HTML
    document.querySelectorAll('.config-input-select, .fixed-addon-checkbox').forEach(input => {
        console.log(`[LOG] Attaching 'change' listener to price influencing element: ${input.id}`);
        input.addEventListener('change', handlePriceInfluencingChange);
    });

    // Množství aktualizuje jen zobrazenou cenu
    if (quantityInput) {
        console.log("[LOG] Attaching 'change' and 'input' listeners to quantity input.");
        quantityInput.addEventListener('change', handlePriceInfluencingChange);
        quantityInput.addEventListener('input', handlePriceInfluencingChange);
    } else { console.warn("[WARN] Quantity input not found!"); }

    // --- Nastavení počáteční ceny ---
    console.log("[LOG] Setting initial price state...");
    if (priceDisplay) {
        if (initialErrorFromServer && initialErrorFromServer !== 'null' && initialErrorFromServer !== '') {
            console.warn("[WARN] Initial error received from server:", initialErrorFromServer);
            displayCalculationError(initialErrorFromServer);
        } else {
            console.log("[LOG] Initial state: No server error. Displaying initial price or calculating...");
            updateDisplayPrice(); // Zobrazí cenu na základě aktuálních hodnot (včetně příp. počátečních příplatků)
            if (!(currentUnitPriceCZK > 0 || currentUnitPriceEUR > 0)) {
                console.log("[LOG] Initial unit prices are zero or invalid. Triggering initial API call...");
                calculatePriceApiCall();
            } else if (submitButton) {
                submitButton.disabled = false;
                console.log("[LOG] Valid initial price detected. Submit button enabled.");
            }
        }
    } else {
        console.error("[ERROR] Price display element not found during init! Submit button disabled.");
        if(submitButton) submitButton.disabled = true;
    }

    console.log("[LOG] Configurator initialization complete.");
}); // End DOMContentLoaded