// Soubor: src/main/resources/static/js/product-configurator.js
// Verze: 4.0 - Přijímá data přes init funkci

// --- Proměnné (definované uvnitř scope skriptu) ---
let formElement, priceDisplay, priceContainer, totalSpinner, errorDiv, submitButton;
let breakdownContainer, breakdownBasePrice, breakdownDesignRow, breakdownDesignPrice, breakdownGlazeRow, breakdownGlazePrice;
let breakdownRoofColorRow, breakdownRoofColorPrice, breakdownAddonsContainer, breakdownAddonsList, breakdownLoading;
let lengthSlider, widthSlider, heightSlider, lengthValueDisplay, widthValueDisplay, heightValueDisplay;
let lengthHiddenInput, widthHiddenInput, heightHiddenInput; // Pro skrytá pole rozměrů
let designSelect, glazeSelect, roofColorSelect, addonSelects, addonOptionElements;
let calculationTimeout;
let config = { // Objekt pro uložení konfigurace předané z HTML
    calculatePriceUrl: null,
    currency: 'CZK',
    csrfToken: null,
    csrfHeaderName: null,
    productId: null,
    configuratorData: null
};

const DEBOUNCE_DELAY = 450;
const PRICE_SCALE = 2;

// --- Pomocné funkce (bez změny) ---

function updateRangeValueDisplay(sliderElement, displayElement) {
    if (sliderElement && displayElement) {
        const valueCm = parseFloat(sliderElement.value);
        if (!isNaN(valueCm)) {
            const step = parseFloat(sliderElement.step) || 1;
            const decimals = (step % 1 === 0) ? 0 : (step < 0.1 ? 2 : 1);
            displayElement.textContent = valueCm.toFixed(decimals);

            const hiddenInputId = sliderElement.id + 'Input';
            const hiddenInput = document.getElementById(hiddenInputId);
            if (hiddenInput) {
                hiddenInput.value = valueCm.toFixed(2);
            }
        } else {
            displayElement.textContent = '???';
        }
    }
}

function formatCurrency(amount, currencyCode) {
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
    const errorMsg = message || 'Při výpočtu ceny došlo k chybě.';
    console.error(`[ERROR] displayCalculationError: ${errorMsg}`);
    if (priceDisplay) priceDisplay.innerHTML = `<span class="text-danger">Chyba</span>`;
    if (breakdownBasePrice) breakdownBasePrice.textContent = "-";
    hideElement(breakdownDesignRow);
    hideElement(breakdownGlazeRow);
    hideElement(breakdownRoofColorRow);
    hideElement(breakdownAddonsContainer);
    hideElement(breakdownLoading);
    if (priceContainer) priceContainer.classList.remove('price-loading');
    if (errorDiv) { errorDiv.textContent = errorMsg; errorDiv.style.display = 'block'; }
    if (submitButton) submitButton.disabled = true;
}

function showElement(element) {
    if (element) element.style.display = '';
}

function hideElement(element) {
    if (element) element.style.display = 'none';
}

function calculateSingleAddonPrice(optionElement, currentDimensions, currency) {
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

        const lengthCm = currentDimensions.length;
        const widthCm = currentDimensions.width;
        const heightCm = currentDimensions.height;

        if (isNaN(lengthCm) || isNaN(widthCm) || isNaN(heightCm)) {
            console.warn("Některý z rozměrů není platné číslo pro výpočet ceny doplňku.");
            return null;
        }

        switch (pricingType) {
            case 'PER_CM_WIDTH': price = (widthCm > 0) ? unitPrice * widthCm : null; break;
            case 'PER_CM_LENGTH': price = (lengthCm > 0) ? unitPrice * lengthCm : null; break;
            case 'PER_CM_HEIGHT': price = (heightCm > 0) ? unitPrice * heightCm : null; break;
            case 'PER_SQUARE_METER':
                if (lengthCm > 0 && widthCm > 0) {
                    const lengthM = lengthCm / 100.0;
                    const widthM = widthCm / 100.0;
                    price = unitPrice * lengthM * widthM;
                } else price = null;
                break;
            default: console.warn(`Neznámý PricingType '${pricingType}'.`); return null;
        }
    }
    return price !== null ? parseFloat(price.toFixed(PRICE_SCALE)) : null;
}

function updateAddonOptionText(optionElement, currentDimensions, currency) {
    const baseNameAttr = 'data-base-name';
    let baseName = optionElement.getAttribute(baseNameAttr);
    if (baseName === null) {
        const currentText = optionElement.textContent;
        const priceMatch = currentText.match(/\s\(\+\s*.*\)$/);
        baseName = priceMatch ? currentText.substring(0, priceMatch.index) : currentText;
        optionElement.setAttribute(baseNameAttr, baseName);
    }

    const calculatedPrice = calculateSingleAddonPrice(optionElement, currentDimensions, currency);
    let priceString = "";
    if (calculatedPrice !== null && calculatedPrice > 0) {
        priceString = ` (+ ${formatCurrency(calculatedPrice, currency)})`;
    }
    optionElement.textContent = baseName + priceString;
}

function updateAllAddonOptions() {
    if (!lengthSlider || !widthSlider || !heightSlider || !addonOptionElements || !addonOptionElements.length) return;
    const currentDimensions = {
        length: parseFloat(lengthHiddenInput?.value) || 0,
        width: parseFloat(widthHiddenInput?.value) || 0,
        height: parseFloat(heightHiddenInput?.value) || 0
    };
    addonOptionElements.forEach(option => {
        if (option.value !== '0') {
            updateAddonOptionText(option, currentDimensions, config.currency);
        }
    });
}

function updatePriceUI(priceData) {
    console.log("[UI_UPDATE] Updating price UI with data:", priceData);

    if (priceData.errorMessage && priceData.errorMessage.trim() !== '') {
        displayCalculationError(priceData.errorMessage);
        return;
    }

    const totalCZK = parseFloat(priceData.totalPriceCZK);
    const totalEUR = parseFloat(priceData.totalPriceEUR);
    if (isNaN(totalCZK) || isNaN(totalEUR)) {
        displayCalculationError("Neplatná data o celkové ceně ze serveru."); return;
    }
    const displayTotal = config.currency === 'EUR' ? totalEUR : totalCZK;
    if (priceDisplay) priceDisplay.textContent = formatCurrency(displayTotal, config.currency);
    if (priceContainer) priceContainer.classList.remove('price-loading');
    if (errorDiv) errorDiv.style.display = 'none';
    if (submitButton) {
        const allRequiredSelected = Array.from(formElement.querySelectorAll('select[required], input[type="radio"][required]')).every(el => {
            if (el.type === 'radio') {
                // Pro radio buttony, zkontroluj, zda je alespoň jeden ve skupině vybrán
                const radioGroup = formElement.querySelectorAll(`input[name="${el.name}"]`);
                return Array.from(radioGroup).some(radio => radio.checked);
            } else {
                return el.value !== ''; // Pro selecty
            }
        });
        submitButton.disabled = !allRequiredSelected;
        if (!allRequiredSelected) console.warn("Submit button disabled: Not all required options selected.");
    }


    // --- Aktualizace Rozpisu Ceny ---
    if (!breakdownContainer || !breakdownBasePrice || !breakdownAddonsList) {
        console.warn("[UI_UPDATE] Breakdown elements missing."); hideElement(breakdownLoading); return;
    }
    const baseCZK = parseFloat(priceData.basePriceCZK);
    const baseEUR = parseFloat(priceData.basePriceEUR);
    const displayBase = config.currency === 'EUR' ? baseEUR : baseCZK;
    breakdownBasePrice.textContent = formatCurrency(displayBase, config.currency);

    const designCZK = parseFloat(priceData.designPriceCZK);
    const designEUR = parseFloat(priceData.designPriceEUR);
    const displayDesign = config.currency === 'EUR' ? designEUR : designCZK;
    if (!isNaN(displayDesign) && displayDesign > 0 && breakdownDesignPrice && breakdownDesignRow) {
        breakdownDesignPrice.textContent = formatCurrency(displayDesign, config.currency); showElement(breakdownDesignRow);
    } else { hideElement(breakdownDesignRow); }

    const glazeCZK = parseFloat(priceData.glazePriceCZK);
    const glazeEUR = parseFloat(priceData.glazePriceEUR);
    const displayGlaze = config.currency === 'EUR' ? glazeEUR : glazeCZK;
    if (!isNaN(displayGlaze) && displayGlaze > 0 && breakdownGlazePrice && breakdownGlazeRow) {
        breakdownGlazePrice.textContent = formatCurrency(displayGlaze, config.currency); showElement(breakdownGlazeRow);
    } else { hideElement(breakdownGlazeRow); }

    const roofCZK = parseFloat(priceData.roofColorPriceCZK);
    const roofEUR = parseFloat(priceData.roofColorPriceEUR);
    const displayRoof = config.currency === 'EUR' ? roofEUR : roofCZK;
    if (!isNaN(displayRoof) && displayRoof > 0 && breakdownRoofColorPrice && breakdownRoofColorRow) {
        breakdownRoofColorPrice.textContent = formatCurrency(displayRoof, config.currency); showElement(breakdownRoofColorRow);
    } else { hideElement(breakdownRoofColorRow); }

    const addonPrices = config.currency === 'EUR' ? priceData.addonPricesEUR : priceData.addonPricesCZK;
    breakdownAddonsList.innerHTML = '';
    let hasAddons = false;
    if (addonPrices && Object.keys(addonPrices).length > 0) {
        Object.entries(addonPrices).forEach(([name, priceStr]) => {
            const price = parseFloat(priceStr);
            if (!isNaN(price) && price > 0) {
                hasAddons = true;
                const addonRow = document.createElement('div');
                addonRow.className = 'd-flex justify-content-between mb-1';
                const nameSpan = document.createElement('span'); nameSpan.textContent = name;
                const priceSpan = document.createElement('span'); priceSpan.className = 'fw-bold';
                priceSpan.textContent = formatCurrency(price, config.currency);
                addonRow.appendChild(nameSpan); addonRow.appendChild(priceSpan);
                breakdownAddonsList.appendChild(addonRow);
            }
        });
    }
    if (hasAddons && breakdownAddonsContainer) { showElement(breakdownAddonsContainer); } else { hideElement(breakdownAddonsContainer); }

    hideElement(breakdownLoading);
    console.log("[UI_UPDATE] Price UI update complete.");
}

function handleConfigurationChange(event) {
    const targetElement = event?.target;
    if (!targetElement) return;
    console.log(`[EVENT] Config change detected on: ${targetElement.id || targetElement.name}`);
    if (priceContainer) priceContainer.classList.add('price-loading');
    if (breakdownLoading) showElement(breakdownLoading);
    if (submitButton) submitButton.disabled = true;
    if (errorDiv) errorDiv.style.display = 'none';

    if (targetElement.matches('.dimension-slider')) {
        const valueDisplayId = targetElement.id + 'Value';
        const valueDisplayElement = document.getElementById(valueDisplayId);
        updateRangeValueDisplay(targetElement, valueDisplayElement);
        updateAllAddonOptions();
    }
    clearTimeout(calculationTimeout);
    calculationTimeout = setTimeout(fetchDetailedPriceApiCall, DEBOUNCE_DELAY);
}

function fetchDetailedPriceApiCall() {
    console.log("[API_CALL] Debounce finished. Starting API call...");
    if (!lengthHiddenInput || !widthHiddenInput || !heightHiddenInput) {
        console.error("API call aborted, hidden dimension inputs missing.");
        displayCalculationError("Chyba konfigurace (hidden inputs).");
        return;
    }
    const lengthValue = lengthHiddenInput.value;
    const widthValue = widthHiddenInput.value;
    const heightValue = heightHiddenInput.value;

    if (lengthValue === undefined || widthValue === undefined || heightValue === undefined ||
        isNaN(parseFloat(lengthValue)) || isNaN(parseFloat(widthValue)) || isNaN(parseFloat(heightValue))) {
        displayCalculationError('Zvolené rozměry nejsou platné.'); return;
    }

    const selectedAddonIds = [];
    if (addonSelects) {
        addonSelects.forEach(select => {
            const val = select.value;
            if (val && val !== '0') { const id = parseInt(val, 10); if (!isNaN(id)) selectedAddonIds.push(id); }
        });
    }

    const requestPayload = {
        productId: config.productId,
        customDimensions: { length: parseFloat(lengthValue).toFixed(2), width: parseFloat(widthValue).toFixed(2), height: parseFloat(heightValue).toFixed(2) },
        selectedDesignId: designSelect ? parseInt(designSelect.value, 10) || null : null,
        selectedGlazeId: glazeSelect ? parseInt(glazeSelect.value, 10) || null : null,
        selectedRoofColorId: roofColorSelect ? parseInt(roofColorSelect.value, 10) || null : null,
        selectedAddonIds: selectedAddonIds
    };
    console.log("[LOG] API Request Payload:", requestPayload);

    console.log(`[API_CALL] Sending POST request to: ${config.calculatePriceUrl}`);
    fetch(config.calculatePriceUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Accept': 'application/json', ...(config.csrfHeaderName && config.csrfToken && { [config.csrfHeaderName]: config.csrfToken }) },
        body: JSON.stringify(requestPayload)
    })
        .then(response => {
            console.log(`[API_CALL] Response status: ${response.status}`);
            if (!response.ok) { return response.json().then(err => { throw new Error(err.errorMessage || `Chyba serveru (${response.status})`); }).catch(() => { throw new Error(`Chyba serveru (${response.status})`); }); }
            return response.json();
        })
        .then(data => { console.log("[API_CALL] Received data:", data); updatePriceUI(data); })
        .catch(error => { console.error('[API_CALL] Fetch/API error:', error); displayCalculationError(error.message || 'Neznámá chyba při komunikaci.'); });
}

// --- Funkce pro přípravu dat formuláře PŘED odesláním ---
function prepareFormDataBeforeSubmit() {
    console.log("[FORM_SUBMIT] Preparing form data...");
    let hiddenAddonContainer = document.getElementById('hidden-selected-addons');
    if (!hiddenAddonContainer) {
        hiddenAddonContainer = document.createElement('div');
        hiddenAddonContainer.id = 'hidden-selected-addons';
        hiddenAddonContainer.style.display = 'none';
        if (formElement) {
            formElement.appendChild(hiddenAddonContainer);
        } else {
            console.error("Cannot append hidden addon container, form not found!");
            return; // Nelze pokračovat bez formuláře
        }
    }
    hiddenAddonContainer.innerHTML = ''; // Vyčistit stará pole

    if (addonSelects) {
        addonSelects.forEach(select => {
            const selectedValue = select.value;
            if (selectedValue && selectedValue !== '0') {
                const addonId = parseInt(selectedValue, 10);
                if (!isNaN(addonId)) {
                    const hiddenInput = document.createElement('input');
                    hiddenInput.type = 'hidden';
                    hiddenInput.name = 'selectedAddonIds';
                    hiddenInput.value = addonId;
                    hiddenAddonContainer.appendChild(hiddenInput);
                }
            }
        });
    }
    console.log("[FORM_SUBMIT] Form data prepared.");
}

// --- INICIALIZAČNÍ FUNKCE ---
function initializeConfigurator(initData) {
    console.log("Initializing Configurator with data:", initData);

    // Uložíme předaná data do globálních proměnných skriptu
    config = { ...config, ...initData }; // Sloučení s defaultními hodnotami

    // Znovu cacheujeme elementy (pro jistotu, kdyby DOMContentLoaded nebylo spolehlivé)
    formElement = document.querySelector(config.formSelector || '#custom-product-form');
    // ... (znovu cacheování všech ostatních elementů jako v původním DOMContentLoaded) ...
    productIdInput = formElement ? formElement.querySelector('input[name="productId"]') : null;
    priceDisplay = document.getElementById('calculatedPrice');
    priceContainer = document.getElementById('price-summary-section');
    totalSpinner = priceContainer ? priceContainer.querySelector('.price-loading-spinner') : null;
    errorDiv = document.getElementById('customPriceErrorDisplay');
    submitButton = formElement ? formElement.querySelector('button[type="submit"]') : null;
    breakdownContainer = document.getElementById('price-breakdown');
    breakdownBasePrice = document.getElementById('breakdown-base-price');
    breakdownDesignRow = document.getElementById('breakdown-design-row');
    breakdownDesignPrice = document.getElementById('breakdown-design-price');
    breakdownGlazeRow = document.getElementById('breakdown-glaze-row');
    breakdownGlazePrice = document.getElementById('breakdown-glaze-price');
    breakdownRoofColorRow = document.getElementById('breakdown-roof-color-row');
    breakdownRoofColorPrice = document.getElementById('breakdown-roof-color-price');
    breakdownAddonsContainer = document.getElementById('breakdown-addons-container');
    breakdownAddonsList = document.getElementById('breakdown-addons-list');
    breakdownLoading = document.getElementById('breakdown-loading');
    lengthSlider = document.getElementById('length');
    widthSlider = document.getElementById('width');
    heightSlider = document.getElementById('height');
    lengthValueDisplay = document.getElementById('lengthValue');
    widthValueDisplay = document.getElementById('widthValue');
    heightValueDisplay = document.getElementById('heightValue');
    lengthHiddenInput = document.getElementById('lengthInput');
    widthHiddenInput = document.getElementById('widthInput');
    heightHiddenInput = document.getElementById('heightInput');
    designSelect = document.getElementById('designSelect');
    glazeSelect = document.getElementById('glazeSelect');
    roofColorSelect = document.getElementById('roofColorSelect');
    addonSelects = document.querySelectorAll('select[data-is-addon-category-select="true"]');
    addonOptionElements = document.querySelectorAll('select[data-is-addon-category-select="true"] option[data-addon-id]');
    // --- Konec Cacheování ---

    if (!formElement) {
        console.error("[INIT] Form element not found! Aborting initialization.");
        return;
    }

    // Připojení event listenerů ke VŠEM relevantním vstupům
    const configInputs = formElement.querySelectorAll('.config-input');
    if (configInputs.length > 0) {
        configInputs.forEach(input => {
            const eventType = input.matches('.dimension-slider') ? 'input' : 'change';
            input.removeEventListener(eventType, handleConfigurationChange); // Odstranit starý listener pro jistotu
            input.addEventListener(eventType, handleConfigurationChange);
        });
        console.log(`[INIT] Attached listeners to ${configInputs.length} config inputs.`);
    } else {
        console.warn("[INIT] No elements with class 'config-input' found inside the form.");
    }


    // Nastavení počátečního stavu
    if (lengthSlider && lengthValueDisplay) updateRangeValueDisplay(lengthSlider, lengthValueDisplay);
    if (widthSlider && widthValueDisplay) updateRangeValueDisplay(widthSlider, widthValueDisplay);
    if (heightSlider && heightValueDisplay) updateRangeValueDisplay(heightSlider, heightValueDisplay);
    updateAllAddonOptions();
    fetchDetailedPriceApiCall(); // Zavoláme API pro načtení počáteční ceny

    console.log("[INIT] Configurator initialization complete.");
}
