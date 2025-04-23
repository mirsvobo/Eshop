// // Soubor: src/main/resources/static/js/product-configurator.js
// // Verze: 2.0 - Podpora pro seskupené doplňky a různé typy cenotvorby
//
// document.addEventListener('DOMContentLoaded', function() {
//     console.log("[LOG] Configurator script starting (v2.0)...");
//
//     const formElement = document.getElementById('add-to-cart-form');
//     if (!formElement) {
//         console.error("[ERROR] Form element #add-to-cart-form not found! Aborting script.");
//         return;
//     }
//     console.log("[LOG] Form element found.");
//
//     // --- Cache pro elementy ---
//     console.log("[LOG] Caching UI elements...");
//     const priceDisplay = document.getElementById('product-price-display');
//     const spinner = document.getElementById('price-spinner');
//     const errorDiv = document.getElementById('customPriceErrorDisplay');
//     const submitButton = document.getElementById('add-to-cart-btn');
//     const quantityInput = document.getElementById('quantity');
//     const lengthSlider = document.getElementById('customLength');
//     const widthSlider = document.getElementById('customWidth');
//     const heightSlider = document.getElementById('customHeight');
//     const lengthValueDisplay = document.getElementById('customLengthValueDisplay');
//     const widthValueDisplay = document.getElementById('customWidthValueDisplay');
//     const heightValueDisplay = document.getElementById('customHeightValueDisplay');
//     const designSelect = document.getElementById('designSelect');
//     const glazeSelect = document.getElementById('glazeSelect');
//     const roofColorSelect = document.getElementById('roofColorSelect');
//     // Element pro skrytá pole vybraných doplňků
//     const hiddenAddonsContainer = document.getElementById('hidden-selected-addons');
//
//     console.log("[LOG] UI elements cached.");
//     // Kontrola klíčových prvků
//     if (!priceDisplay || !spinner || !errorDiv || !submitButton || !hiddenAddonsContainer) {
//         console.error("[ERROR] One or more core UI elements for price/control/hidden fields not found during init!");
//     }
//     // ... (další kontroly prvků) ...
//
//     // --- Načtení dat z data-* atributů ---
//     const productJsData = { id: formElement.dataset.productId ? parseInt(formElement.dataset.productId, 10) : null };
//     let initialCzkValue = formElement.dataset.initialCzk;
//     let initialEurValue = formElement.dataset.initialEur;
//     console.log(`[LOG] Raw initial prices from data attributes: CZK=${initialCzkValue}, EUR=${initialEurValue}`);
//     let currentUnitPriceCZK = (initialCzkValue && !isNaN(parseFloat(initialCzkValue))) ? parseFloat(initialCzkValue) : 0.0;
//     let currentUnitPriceEUR = (initialEurValue && !isNaN(parseFloat(initialEurValue))) ? parseFloat(initialEurValue) : 0.0;
//     const initialErrorFromServer = formElement.dataset.initialError || null;
//     const calculatePriceUrl = formElement.dataset.calculateUrl || '/api/product/calculate-price';
//     const currencySymbol = formElement.dataset.currencySymbol || 'Kč';
//     const currentGlobalCurrency = currencySymbol === '€' ? 'EUR' : 'CZK';
//     console.log(`[LOG] Parsed Data: productID=${productJsData.id}, initialUnitCZK=${currentUnitPriceCZK}, initialUnitEUR=${currentUnitPriceEUR}, initialError=${initialErrorFromServer}, apiURL=${calculatePriceUrl}, currencySymbol=${currencySymbol}, globalCurrency=${currentGlobalCurrency}`);
//
//     if (!productJsData || !productJsData.id) {
//         console.error("[ERROR] Product ID not found or invalid in data attributes.");
//         displayCalculationError('Chyba konfigurace stránky (ID produktu).');
//         return;
//     }
//
//     // --- Globální proměnné ---
//     let calculationTimeout;
//     const DEBOUNCE_DELAY = 400; // ms
//
//     // --- Funkce pro UI ---
//     // ... (changeMainImage, updateRangeValueDisplay, formatCurrency - zůstávají stejné) ...
//     function updateRangeValueDisplay(sliderElement, displayElement) {
//         if (sliderElement && displayElement) {
//             const valueCm = parseFloat(sliderElement.value);
//             if (!isNaN(valueCm)) {
//                 // Použijeme toFixed(0) pro celá čísla, pokud krok je celé číslo, jinak toFixed(1)
//                 const step = parseFloat(sliderElement.step) || 1;
//                 const decimals = (step % 1 === 0) ? 0 : 1;
//                 const formattedValue = valueCm.toFixed(decimals).replace('.', ',');
//                 displayElement.textContent = formattedValue;
//             } else {
//                 console.warn(`[WARN] Invalid value from slider ${sliderElement.id}: ${sliderElement.value}`);
//                 displayElement.textContent = '???';
//             }
//         }
//     }
//
//     function formatCurrency(amount, currencyCode) {
//         if (amount == null || isNaN(amount)) return "---";
//         const options = { style: 'currency', currency: currencyCode, minimumFractionDigits: 2, maximumFractionDigits: 2 };
//         const locale = currencyCode === 'EUR' ? 'sk-SK' : 'cs-CZ';
//         try { return new Intl.NumberFormat(locale, options).format(amount); }
//         catch (e) { console.error("[ERROR] Error formatting currency:", { amount, currencyCode, locale }, e); const fixedAmount = amount.toFixed(2).replace('.', ','); return `${fixedAmount} ${currencyCode === 'EUR' ? '€' : 'Kč'}`; }
//     }
//
//     // --- Funkce pro výpočet ceny ---
//
//     function getSelectedAttributeSurcharge(currency) {
//         let totalSurcharge = 0;
//         const selects = [designSelect, glazeSelect, roofColorSelect];
//         // console.log(`[LOG] Calculating attribute surcharge for currency: ${currency}`);
//         selects.forEach(select => {
//             if (select && select.value && select.selectedIndex >= 0) {
//                 const selectedOption = select.options[select.selectedIndex];
//                 if (selectedOption) {
//                     const priceAttr = currency === 'EUR' ? 'data-price-eur' : 'data-price-czk';
//                     const price = parseFloat(selectedOption.getAttribute(priceAttr) || '0');
//                     if (!isNaN(price) && price > 0) {
//                         totalSurcharge += price;
//                     }
//                 }
//             }
//         });
//         console.log(`[LOG] Total attribute surcharge (${currency}): ${totalSurcharge.toFixed(2)}`);
//         return totalSurcharge;
//     }
//
//     // NOVÁ FUNKCE pro výpočet ceny doplňků
//     function getSelectedAddonsPrice(currency) {
//         console.log(`[LOG] Calculating addons price for currency: ${currency}`);
//         let totalAddonPrice = 0;
//         const addonSelects = document.querySelectorAll('.addon-category-select');
//
//         addonSelects.forEach(select => {
//             if (select.value && select.selectedIndex > 0) { // Index > 0 to skip '-- Bez doplňku --'
//                 const selectedOption = select.options[select.selectedIndex];
//                 if (selectedOption) {
//                     const pricingType = selectedOption.getAttribute('data-pricing-type');
//                     const priceAttr = currency === 'EUR' ? 'data-price-eur' : 'data-price-czk';
//                     const pricePerUnitAttr = currency === 'EUR' ? 'data-price-per-unit-eur' : 'data-price-per-unit-czk';
//
//                     if (pricingType === 'FIXED') {
//                         const price = parseFloat(selectedOption.getAttribute(priceAttr) || '0');
//                         if (!isNaN(price)) {
//                             totalAddonPrice += price;
//                             console.log(`[LOG] Addon FIXED price added (${currency}): ${price.toFixed(2)} for ${select.id}`);
//                         }
//                     } else if (pricingType.startsWith('PER_CM_')) {
//                         const pricePerUnit = parseFloat(selectedOption.getAttribute(pricePerUnitAttr) || '0');
//                         if (!isNaN(pricePerUnit)) {
//                             let dimensionValue = 0;
//                             if (pricingType === 'PER_CM_WIDTH' && widthSlider) {
//                                 dimensionValue = parseFloat(widthSlider.value) || 0;
//                             } else if (pricingType === 'PER_CM_LENGTH' && lengthSlider) {
//                                 dimensionValue = parseFloat(lengthSlider.value) || 0;
//                             } else if (pricingType === 'PER_CM_HEIGHT' && heightSlider) {
//                                 dimensionValue = parseFloat(heightSlider.value) || 0;
//                             }
//                             if (dimensionValue > 0) {
//                                 const addonDimPrice = dimensionValue * pricePerUnit;
//                                 totalAddonPrice += addonDimPrice;
//                                 console.log(`[LOG] Addon DIMENSIONAL price added (${currency}): ${addonDimPrice.toFixed(2)} (${dimensionValue} * ${pricePerUnit}) for ${select.id}`);
//                             }
//                         }
//                     } else if (pricingType === 'PER_SQUARE_METER') {
//                         const pricePerUnit = parseFloat(selectedOption.getAttribute(pricePerUnitAttr) || '0');
//                         if (!isNaN(pricePerUnit) && lengthSlider && widthSlider) {
//                             const lengthCm = parseFloat(lengthSlider.value) || 0;
//                             const widthCm = parseFloat(widthSlider.value) || 0;
//                             if(lengthCm > 0 && widthCm > 0) {
//                                 const areaM2 = (lengthCm / 100.0) * (widthCm / 100.0); // Convert cm to m
//                                 const addonAreaPrice = areaM2 * pricePerUnit;
//                                 totalAddonPrice += addonAreaPrice;
//                                 console.log(`[LOG] Addon AREA price added (${currency}): ${addonAreaPrice.toFixed(4)} (${areaM2.toFixed(4)}m2 * ${pricePerUnit}) for ${select.id}`);
//                             }
//                         }
//                     }
//                     // Add other pricing types here if needed
//                 }
//             }
//         });
//         console.log(`[LOG] Total addons price (${currency}): ${totalAddonPrice.toFixed(2)}`);
//         // Round final addon price to 2 decimal places for currency
//         return parseFloat(totalAddonPrice.toFixed(PRICE_SCALE));
//     }
//
//
//     function updateDisplayPrice() {
//         console.log("[LOG] updateDisplayPrice called");
//         if (!priceDisplay) { console.error("[ERROR] Price display element not found!"); return; }
//
//         const quantity = quantityInput ? (parseInt(quantityInput.value, 10) || 1) : 1;
//
//         // Získáme příplatky za atributy a NOVĚ za doplňky
//         const attributeSurchargeCZK = getSelectedAttributeSurcharge('CZK');
//         const attributeSurchargeEUR = getSelectedAttributeSurcharge('EUR');
//         const addonsPriceCZK = getSelectedAddonsPrice('CZK'); // Použití nové funkce
//         const addonsPriceEUR = getSelectedAddonsPrice('EUR'); // Použití nové funkce
//
//         // Základní cena z API (nebo počáteční)
//         const unitCZK = !isNaN(currentUnitPriceCZK) ? currentUnitPriceCZK : 0;
//         const unitEUR = !isNaN(currentUnitPriceEUR) ? currentUnitPriceEUR : 0;
//
//         // Finální cena za kus = základ + atributy + doplňky
//         const finalUnitCZK = unitCZK + attributeSurchargeCZK + addonsPriceCZK;
//         const finalUnitEUR = unitEUR + attributeSurchargeEUR + addonsPriceEUR;
//
//         // Celková cena = cena za kus * množství
//         const totalCZK = quantity * finalUnitCZK;
//         const totalEUR = quantity * finalUnitEUR;
//
//         console.log(`[LOG] updateDisplayPrice - baseUnitCZK=${unitCZK.toFixed(2)}, attrSurCZK=${attributeSurchargeCZK.toFixed(2)}, addonsCZK=${addonsPriceCZK.toFixed(2)}, finalUnitCZK=${finalUnitCZK.toFixed(2)}, quantity=${quantity}, totalCZK=${totalCZK.toFixed(2)}`);
//
//         if (isNaN(totalCZK) || isNaN(totalEUR)) {
//             console.error("[ERROR] NaN detected in total price calculation.");
//             displayCalculationError('Při výpočtu ceny došlo k interní chybě.');
//         } else {
//             const czkHtml = formatCurrency(totalCZK, 'CZK');
//             const eurHtml = formatCurrency(totalEUR, 'EUR');
//             priceDisplay.innerHTML = `<span id="price-value">${czkHtml}</span> <span class="price-eur">/ ${eurHtml}</span>`;
//             if (spinner) spinner.classList.add('d-none');
//             if (errorDiv) errorDiv.style.display = 'none';
//             priceDisplay.classList.remove('calculating');
//             if (submitButton) submitButton.disabled = false;
//             console.log("[LOG] Price display updated successfully.");
//         }
//     }
//
//     function displayCalculationError(message) {
//         const errorMsg = message || 'Při výpočtu ceny došlo k chybě.';
//         console.error(`[ERROR] displayCalculationError: ${errorMsg}`);
//         currentUnitPriceCZK = 0; currentUnitPriceEUR = 0;
//         console.log("[LOG] Unit prices reset to 0 due to error.");
//         if (priceDisplay) { priceDisplay.innerHTML = `<span class="text-danger">Chyba výpočtu</span>`; priceDisplay.classList.remove('calculating'); }
//         if (spinner) spinner.classList.add('d-none');
//         if (errorDiv) { errorDiv.textContent = errorMsg; errorDiv.style.display = 'block'; }
//         if (submitButton) submitButton.disabled = true;
//     }
//
//     // --- Kalkulace ZÁKLADNÍ ceny - volání API ---
//     function handleCustomConfigChange(event) {
//         const targetId = event?.target?.id || 'unknown';
//         console.log(`[EVENT] Configuration changed by element: ${targetId}. Scheduling API call or display update...`);
//
//         let requiresApiCall = false;
//         if (event && event.target) {
//             // Dimension sliders always trigger API call
//             if (event.target.matches('.dimension-slider')) {
//                 requiresApiCall = true;
//                 const displayId = event.target.id + 'ValueDisplay';
//                 const displayElement = document.getElementById(displayId);
//                 updateRangeValueDisplay(event.target, displayElement);
//             }
//             // Addon selects with dimensional pricing trigger API call (or full recalculation)
//             else if (event.target.matches('.addon-category-select')) {
//                 const selectedOption = event.target.options[event.target.selectedIndex];
//                 const pricingType = selectedOption?.getAttribute('data-pricing-type');
//                 if (pricingType && pricingType !== 'FIXED' && pricingType !== 'NONE') {
//                     // Dimensional addons affect price based on dimensions,
//                     // but the base price from API doesn't change.
//                     // We just need to update the displayed price.
//                     // Let's keep it simple: ALL addon changes just update the display.
//                     // requiresApiCall = true; // Potentially, if base price depended on addons
//                 }
//             }
//         }
//
//         if (priceDisplay) priceDisplay.classList.add('calculating');
//         if (spinner) spinner.classList.remove('d-none'); // Hide spinner initially for non-API updates
//         if (submitButton) submitButton.disabled = true;
//         if (errorDiv) errorDiv.style.display = 'none';
//
//         clearTimeout(calculationTimeout);
//
//         if (requiresApiCall) {
//             console.log("[LOG] Change requires API call (dimensions changed). Debouncing...");
//             if (spinner) spinner.classList.remove('d-none'); // Show spinner for API call
//             calculationTimeout = setTimeout(calculatePriceApiCall, DEBOUNCE_DELAY);
//         } else {
//             console.log("[LOG] Change does NOT require API call (attribute/addon/quantity). Updating display directly...");
//             // Update display immediately or with minimal debounce
//             calculationTimeout = setTimeout(updateDisplayPrice, 50); // Small delay for responsiveness
//         }
//     }
//
//     function calculatePriceApiCall() {
//         console.log("[API_CALL] Debounce timeout finished. Starting API call to calculate BASE unit price...");
//
//         const lengthValue = lengthSlider?.value;
//         const widthValue = widthSlider?.value;
//         const heightValue = heightSlider?.value;
//
//         const requestPayload = {
//             productId: productJsData.id,
//             customDimensions: {
//                 length: lengthValue ? Number(lengthValue).toFixed(2) : null,
//                 width: widthValue ? Number(widthValue).toFixed(2) : null,
//                 height: heightValue ? Number(heightValue).toFixed(2) : null
//             },
//             // Boolean flags for specific features are no longer sent/used by backend for base price
//             // customHasDivider: false, // Example: Not needed anymore
//             // customHasGutter: false,
//             // customHasGardenShed: false
//         };
//         console.log("[LOG] Constructed request payload for BASE price:", requestPayload);
//
//         const dims = requestPayload.customDimensions;
//         if (dims.length === null || dims.width === null || dims.height === null || isNaN(parseFloat(dims.length)) || isNaN(parseFloat(dims.width)) || isNaN(parseFloat(dims.height))) {
//             console.error("[ERROR] Invalid dimension values found in payload:", dims);
//             displayCalculationError('Zvolené rozměry nejsou platné.');
//             return;
//         }
//
//         console.log(`[API_CALL] Sending POST request to: ${calculatePriceUrl}`);
//         fetch(calculatePriceUrl, {
//             method: 'POST',
//             headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
//             body: JSON.stringify(requestPayload)
//         })
//             .then(response => {
//                 console.log(`[API_CALL] Received response status: ${response.status}`);
//                 if (!response.ok) {
//                     return response.json().then(err => { throw err; }).catch(() => { throw { status: response.status, message: `Chyba serveru (${response.status})` }; });
//                 }
//                 return response.json();
//             })
//             .then(data => {
//                 console.log("[API_CALL] Received BASE price data:", data);
//                 if (data.errorMessage) {
//                     console.warn("[API_CALL] API returned error message:", data.errorMessage);
//                     throw { message: data.errorMessage };
//                 }
//                 const czk = parseFloat(data.priceCZK);
//                 const eur = parseFloat(data.priceEUR);
//                 console.log(`[LOG] Parsed BASE unit prices from API: CZK=${czk}, EUR=${eur}`);
//                 currentUnitPriceCZK = !isNaN(czk) ? czk : 0;
//                 currentUnitPriceEUR = !isNaN(eur) ? eur : 0;
//                 console.log(`[LOG] Stored global BASE unit prices: CZK=${currentUnitPriceCZK}, EUR=${currentUnitPriceEUR}`);
//                 console.log("[LOG] Calling updateDisplayPrice after successful API call to add addons/attributes.");
//                 updateDisplayPrice(); // Přepočítá celkovou cenu včetně doplňků a atributů
//             })
//             .catch(error => {
//                 console.error('[API_CALL] Fetch error or API error:', error);
//                 const message = error.errorMessage || error.message || 'Neznámá chyba při výpočtu základní ceny.';
//                 displayCalculationError(message);
//             });
//     }
//
//     // --- Funkce pro přípravu dat formuláře ---
//     function prepareAddonsForSubmission() {
//         if (!hiddenAddonsContainer) return;
//         console.log("[LOG] Preparing selected addons for form submission...");
//         hiddenAddonsContainer.innerHTML = ''; // Clear previous hidden inputs
//         const addonSelects = document.querySelectorAll('.addon-category-select');
//         let index = 0;
//
//         addonSelects.forEach(select => {
//             if (select.value && select.selectedIndex > 0) { // Only selected addons (not 'none')
//                 const addonId = select.value;
//                 // Create hidden input for the addon ID
//                 const input = document.createElement('input');
//                 input.type = 'hidden';
//                 // Name format matches expected CartItemDto.selectedAddons list structure
//                 input.name = `selectedAddons[${index}].addonId`;
//                 input.value = addonId;
//                 hiddenAddonsContainer.appendChild(input);
//
//                 // Add quantity (assuming always 1 for select-based addons)
//                 const qtyInput = document.createElement('input');
//                 qtyInput.type = 'hidden';
//                 qtyInput.name = `selectedAddons[${index}].quantity`;
//                 qtyInput.value = 1; // Default to 1 for selected addons
//                 hiddenAddonsContainer.appendChild(qtyInput);
//
//                 console.log(`[LOG] Added hidden input for addonId=${addonId}, index=${index}`);
//                 index++;
//             }
//         });
//         console.log(`[LOG] Finished preparing ${index} selected addons.`);
//     }
//
//     // --- Inicializace Event Listenerů ---
//     console.log("[LOG] Attaching event listeners...");
//
//     // Všechny prvky ovlivňující cenu (slidery, selecty atributů, selecty doplňků, množství)
//     // nyní volají handleCustomConfigChange, která rozhodne, zda volat API nebo jen update displaye.
//     // Přidána třída 'config-input' ke všem relevantním prvkům v HTML.
//     document.querySelectorAll('.config-input').forEach(input => {
//         const eventType = input.matches('.dimension-slider') ? 'input' : 'change';
//         console.log(`[LOG] Attaching '${eventType}' listener to config element: ${input.id || input.name || 'addonSelect'} using class '.config-input'`);
//         input.addEventListener(eventType, handleCustomConfigChange);
//
//         // Initial display update for sliders
//         if (input.matches('.dimension-slider')) {
//             const displayId = input.id + 'ValueDisplay';
//             const displayElement = document.getElementById(displayId);
//             updateRangeValueDisplay(input, displayElement);
//         }
//     });
//
//     // Listener pro odeslání formuláře - naplní skryté inputy doplňků
//     formElement.addEventListener('submit', prepareAddonsForSubmission);
//
//     // --- Nastavení počáteční ceny ---
//     console.log("[LOG] Setting initial price state...");
//     if (priceDisplay) {
//         if (initialErrorFromServer && initialErrorFromServer !== 'null' && initialErrorFromServer !== '') {
//             console.warn("[WARN] Initial error received from server:", initialErrorFromServer);
//             displayCalculationError(initialErrorFromServer);
//         } else {
//             console.log("[LOG] Initial state: No server error. Displaying initial price or calculating...");
//             // Spustíme první výpočet pro zobrazení ceny s výchozími hodnotami doplňků/atributů
//             updateDisplayPrice();
//             // Pokud je základní cena 0 (což by měla být po úpravě backendu), zavoláme API pro její načtení
//             if (!(currentUnitPriceCZK > 0 || currentUnitPriceEUR > 0)) {
//                 console.log("[LOG] Initial base unit prices are zero or invalid. Triggering initial API call...");
//                 // Delay API call slightly to allow UI to render?
//                 setTimeout(calculatePriceApiCall, 100);
//             } else {
//                 // Pokud jsme měli nenulovou počáteční cenu z backendu (což už by nemělo nastat)
//                 console.log("[LOG] Non-zero initial base price detected (unexpected?). Enabling submit button.");
//                 if (submitButton) submitButton.disabled = false;
//             }
//         }
//     } else {
//         console.error("[ERROR] Price display element not found during init! Submit button disabled.");
//         if(submitButton) submitButton.disabled = true;
//     }
//
//     console.log("[LOG] Configurator initialization complete (v2.0).");
// }); // End DOMContentLoaded