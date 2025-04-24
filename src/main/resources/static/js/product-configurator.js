// // Soubor: src/main/resources/static/js/product-configurator.js
// // Verze: 2.1 - Přidána dynamická aktualizace cen doplňků v <option>
//
document.addEventListener('DOMContentLoaded', function() {
    console.log("[LOG] Configurator script starting (v2.1)...");

    // --- Formulář a ID produktu ---
    // POZNÁMKA: Váš původní kód načítal data z `formElement = document.getElementById('add-to-cart-form');`
    // ale HTML kód pro detail produktu (produkt-detail-custom.html), který jsme procházeli,
    // má formulář s ID 'custom-product-form'. Ověřte, které ID je správné a případně upravte zde:
    const formElement = document.getElementById('custom-product-form'); // Používám ID z produkt-detail-custom.html
    if (!formElement) {
        // Zkusíme i staré ID pro jistotu
        const fallbackFormElement = document.getElementById('add-to-cart-form');
        if (fallbackFormElement) {
            formElement = fallbackFormElement;
            console.warn("[WARN] Formulář nalezen pod starým ID 'add-to-cart-form'. Doporučeno sjednotit na 'custom-product-form'.");
        } else {
            console.error("[ERROR] Form element '#custom-product-form' ani '#add-to-cart-form' not found! Aborting script.");
            // Možná zobrazit chybu uživateli zde?
            return;
        }
    }
    console.log("[LOG] Form element found with ID:", formElement.id);


    // --- Cache pro elementy ---
    console.log("[LOG] Caching UI elements...");
    // POZNÁMKA: Ověřte ID elementů podle vašeho aktuálního HTML (produkt-detail-custom.html)
    const priceDisplay = document.getElementById('calculatedPrice'); // ID z produkt-detail-custom.html pro zobrazení ceny
    const priceContainer = document.getElementById('price-summary-section'); // Kontejner ceny pro zobrazení spinneru
    const spinner = priceContainer ? priceContainer.querySelector('.price-loading-spinner') : null; // Spinner uvnitř kontejneru
    const errorDiv = document.getElementById('customPriceErrorDisplay'); // Předpokládáme, že existuje element pro chyby
    const submitButton = formElement.querySelector('button[type="submit"]'); // Tlačítko odeslání uvnitř formuláře
    const quantityInput = document.getElementById('quantity');

    // POSUVNÍKY - Používáme ID z produkt-detail-custom.html
    const lengthSlider = document.getElementById('length');
    const widthSlider = document.getElementById('width');
    const heightSlider = document.getElementById('height');
    // ZOBRAZENÍ HODNOT POSUVNÍKŮ - Používáme ID z produkt-detail-custom.html
    const lengthValueDisplay = document.getElementById('lengthValue');
    const widthValueDisplay = document.getElementById('widthValue');
    const heightValueDisplay = document.getElementById('heightValue');

    // SELECTY PRO ATRIBUTY (pokud existují i u custom produktu - Váš kód je měl)
    const designSelect = document.getElementById('designSelect'); // Pokud existuje
    const glazeSelect = document.getElementById('glazeSelect');   // Pokud existuje
    const roofColorSelect = document.getElementById('roofColorSelect'); // Pokud existuje

    // SELECTY PRO DOPLŇKY - Používáme atribut z produkt-detail-custom.html
    const addonSelects = document.querySelectorAll('select[data-is-addon-category-select="true"]'); // Selektor pro selecty kategorií doplňků
    // ELEMENTY <OPTION> PRO DOPLŇKY - Selektor podle atributu přidaného v minulém kroku
    const addonOptionElements = document.querySelectorAll('option[data-addon-id]');

    // Element pro skrytá pole (pokud používáte - Váš kód ho měl)
    const hiddenAddonsContainer = document.getElementById('hidden-selected-addons');

    console.log("[LOG] UI elements cached.");
    // Kontrola klíčových prvků pro konfigurátor
    if (!lengthSlider || !widthSlider || !heightSlider || !priceDisplay || !submitButton || addonSelects.length === 0) {
        console.warn("[WARN] One or more core configurator UI elements (sliders, price display, submit button, addon selects) might be missing!");
    }
    if (!priceContainer || !spinner) {
        console.warn("[WARN] Price container or spinner element not found for loading indication.");
    }


    // --- Načtení dat z data-* atributů (z elementu #calculatedPrice) ---
    // Získání ID produktu z formuláře (předpoklad)
    const productIdInput = formElement.querySelector('input[name="productId"]');
    const productJsData = { id: productIdInput ? parseInt(productIdInput.value, 10) : null };

    // Získání počátečních cen a měny z elementu #calculatedPrice
    let initialCzkValue = priceDisplay ? priceDisplay.getAttribute('data-initial-price-czk') : null;
    let initialEurValue = priceDisplay ? priceDisplay.getAttribute('data-initial-price-eur') : null;
    // Získání URL pro API a měny z globálních JS proměnných definovaných v HTML (lepší než data-* na formuláři)
    // Předpokládáme, že proměnné 'calculatePriceUrl' a 'currency' jsou definovány v <script> bloku v HTML
    // const calculatePriceUrl = calculatePriceUrl; // Definováno v HTML
    // const currency = currency; // Definováno v HTML
    // Pokud nejsou definovány globálně, je třeba je získat jinak (např. z data-* atributu na formuláři nebo specifickém elementu)
    // Následující řádky jsou POUZE ZÁLOŽNÍ, pokud globální proměnné neexistují:
    const fallbackCalculateUrl = formElement.dataset.calculateUrl || '/api/product/calculate-price';
    const fallbackCurrency = formElement.dataset.currencySymbol === '€' ? 'EUR' : 'CZK';
    const finalCalculatePriceUrl = typeof calculatePriceUrl !== 'undefined' ? calculatePriceUrl : fallbackCalculateUrl;
    const finalCurrency = typeof currency !== 'undefined' ? currency : fallbackCurrency;


    console.log(`[LOG] Raw initial prices from data attributes: CZK=${initialCzkValue}, EUR=${initialEurValue}`);
    let currentUnitPriceCZK = (initialCzkValue && !isNaN(parseFloat(initialCzkValue))) ? parseFloat(initialCzkValue) : 0.0;
    let currentUnitPriceEUR = (initialEurValue && !isNaN(parseFloat(initialEurValue))) ? parseFloat(initialEurValue) : 0.0;
    const initialErrorFromServer = priceDisplay ? priceDisplay.dataset.initialError || null : null; // Pokud existuje data-initial-error

    console.log(`[LOG] Parsed Data: productID=${productJsData.id}, initialUnitCZK=${currentUnitPriceCZK}, initialUnitEUR=${currentUnitPriceEUR}, initialError=${initialErrorFromServer}, apiURL=${finalCalculatePriceUrl}, globalCurrency=${finalCurrency}`);

    if (!productJsData || !productJsData.id) {
        console.error("[ERROR] Product ID not found or invalid.");
        displayCalculationError('Chyba konfigurace stránky (ID produktu).');
        return;
    }

    // --- Globální proměnné ---
    let calculationTimeout;
    const DEBOUNCE_DELAY = 450; // ms - mírně zvýšeno
    const PRICE_SCALE = 2; // Počet desetinných míst pro ceny

    // --- Funkce pro UI ---

    /**
     * Aktualizuje zobrazenou hodnotu vedle posuvníku.
     */
    function updateRangeValueDisplay(sliderElement, displayElement) {
        if (sliderElement && displayElement) {
            const valueCm = parseFloat(sliderElement.value);
            if (!isNaN(valueCm)) {
                const step = parseFloat(sliderElement.step) || 1;
                // Zobrazujeme na nula desetinných míst, pokud je krok celé číslo
                const decimals = (step % 1 === 0) ? 0 : 1;
                displayElement.textContent = valueCm.toFixed(decimals); // Jen číslo, " cm" je v HTML
            } else {
                console.warn(`[WARN] Invalid value from slider ${sliderElement.id}: ${sliderElement.value}`);
                displayElement.textContent = '???';
            }
        }
    }

    /**
     * Funkce pro formátování ceny
     * @param {number} amount - Částka k formátování
     * @param {string} currencyCode - 'CZK' nebo 'EUR'
     * @returns {string} Formátovaná cena (např. "1 234,50 Kč")
     */
    function formatCurrency(amount, currencyCode) {
        if (amount == null || isNaN(amount)) return "N/A";
        const options = { style: 'currency', currency: currencyCode, minimumFractionDigits: 2, maximumFractionDigits: 2 };
        const locale = currencyCode === 'EUR' ? 'sk-SK' : 'cs-CZ';
        try {
            // Nahrazení nezlomitelné mezery běžnou mezerou
            return new Intl.NumberFormat(locale, options).format(amount).replace(/\s/g, ' ');
        } catch (e) {
            console.error("Chyba formátování měny:", e);
            const fixedAmount = amount.toFixed(2).replace('.', ',');
            return `${fixedAmount} ${currencyCode === 'EUR' ? '€' : 'Kč'}`;
        }
    }

    /**
     * Zobrazí chybu při výpočtu ceny.
     */
    function displayCalculationError(message) {
        const errorMsg = message || 'Při výpočtu ceny došlo k chybě.';
        console.error(`[ERROR] displayCalculationError: ${errorMsg}`);
        currentUnitPriceCZK = 0; currentUnitPriceEUR = 0;
        console.log("[LOG] Unit prices reset to 0 due to error.");
        if (priceDisplay) {
            priceDisplay.innerHTML = `<span class="text-danger">Chyba</span>`;
            priceDisplay.closest('.price-summary')?.classList.remove('price-loading'); // Odebrání třídy pro načítání
        }
        // Zobrazení chyby v dedikovaném divu, pokud existuje
        if (errorDiv) { errorDiv.textContent = errorMsg; errorDiv.style.display = 'block'; }
        if (submitButton) submitButton.disabled = true;
    }

    // --- Funkce pro výpočet ceny doplňků (pro celkový souhrn) ---

    // Tato funkce počítá CELKOVOU cenu VYBRANÝCH doplňků
    function getSelectedAddonsPrice(currency) {
        // console.log(`[LOG] Calculating SELECTED addons price for currency: ${currency}`);
        let totalAddonPrice = 0;

        addonSelects.forEach(select => {
            // Bereme jen vybranou option, která není "Ne" (hodnota '0')
            if (select.value && select.value !== '0' && select.selectedIndex > 0) {
                const selectedOption = select.options[select.selectedIndex];
                if (selectedOption) {
                    // Vypočítáme cenu tohoto JEDNOHO vybraného doplňku
                    const dimensions = {
                        length: parseFloat(lengthSlider?.value) || 0,
                        width: parseFloat(widthSlider?.value) || 0,
                        height: parseFloat(heightSlider?.value) || 0
                    };
                    const addonPrice = calculateSingleAddonPrice(selectedOption, dimensions, currency);

                    if (addonPrice !== null) {
                        totalAddonPrice += addonPrice;
                        console.log(`[LOG] Addon price added (${currency}): ${addonPrice.toFixed(2)} for ${select.id}`);
                    }
                }
            }
        });
        // console.log(`[LOG] Total SELECTED addons price (${currency}): ${totalAddonPrice.toFixed(2)}`);
        return parseFloat(totalAddonPrice.toFixed(PRICE_SCALE)); // Zaokrouhlení finální sumy
    }

    // --- NOVÉ FUNKCE pro dynamické ceny v <option> ---

    /**
     * Vypočítá a vrátí cenu JEDNOHO doplňku na základě jeho typu a aktuálních rozměrů.
     * Používá se jak pro zobrazení v <option>, tak pro výpočet celkové ceny.
     * @param {Element} optionElement - <option> element doplňku
     * @param {object} currentDimensions - Objekt s aktuálními rozměry { length: number, width: number, height: number }
     * @param {string} currency - 'CZK' nebo 'EUR'
     * @returns {number|null} Vypočítaná cena nebo null, pokud nemá být zobrazena/počítána
     */
    function calculateSingleAddonPrice(optionElement, currentDimensions, currency) {
        const pricingType = optionElement.dataset.pricingType;
        const priceData = optionElement.dataset; // Přístup ke všem data-* atributům

        let price = null;
        let unitPrice = 0;

        // Získání správné jednotkové nebo fixní ceny podle měny
        if (pricingType === 'FIXED') {
            price = parseFloat(currency === 'EUR' ? priceData.priceEur : priceData.priceCzk);
            // Pro zobrazení chceme i nulovou cenu (pokud je fixní a 0), ale pro výpočet sumy ne
            if (isNaN(price) || price < 0) return null; // Ignorovat neplatné nebo záporné fixní ceny
        } else {
            unitPrice = parseFloat(currency === 'EUR' ? priceData.pricePerUnitEur : priceData.pricePerUnitCzk);
            if (isNaN(unitPrice) || unitPrice <= 0) return null; // Nelze počítat s neplatnou nebo nulovou jednotkovou cenou
        }

        // Výpočet podle typu ceny
        switch (pricingType) {
            case 'FIXED':
                // Cena je již načtena výše
                break;
            case 'PER_CM_WIDTH':
                if (currentDimensions.width > 0) {
                    price = unitPrice * currentDimensions.width;
                } else return null;
                break;
            case 'PER_CM_LENGTH':
                if (currentDimensions.length > 0) {
                    price = unitPrice * currentDimensions.length;
                } else return null;
                break;
            case 'PER_CM_HEIGHT':
                if (currentDimensions.height > 0) {
                    price = unitPrice * currentDimensions.height;
                } else return null;
                break;
            case 'PER_SQUARE_METER':
                if (currentDimensions.length > 0 && currentDimensions.width > 0) {
                    const lengthM = currentDimensions.length / 100.0;
                    const widthM = currentDimensions.width / 100.0;
                    price = unitPrice * lengthM * widthM;
                } else return null;
                break;
            default:
                return null; // Neznámý nebo nepodporovaný typ pro výpočet
        }

        // Vracíme cenu zaokrouhlenou na 2 des. místa
        return price !== null ? parseFloat(price.toFixed(PRICE_SCALE)) : null;
    }

    /**
     * Aktualizuje text jednoho <option> doplňku.
     * @param {Element} optionElement - <option> element doplňku
     * @param {object} currentDimensions - Aktuální rozměry
     * @param {string} currency - Aktuální měna
     */
    function updateAddonOptionText(optionElement, currentDimensions, currency) {
        // Název doplňku bereme z původního textu (předpokládáme, že th:text nastavil jen název)
        const baseName = optionElement.textContent.split(' (+')[0]; // Odstraní starou cenu

        const calculatedPrice = calculateSingleAddonPrice(optionElement, currentDimensions, currency);

        let priceString = "";
        // Zobrazujeme cenu jen pokud je větší než nula
        if (calculatedPrice !== null && calculatedPrice > 0) {
            priceString = ` (+ ${formatCurrency(calculatedPrice, currency)})`;
        }

        optionElement.textContent = baseName + priceString;
    }

    /**
     * Projde všechny doplňky a aktualizuje jejich texty s cenami.
     */
    function updateAllAddonOptions() {
        if (!lengthSlider || !widthSlider || !heightSlider || !addonOptionElements.length) {
            // console.warn("Chybí posuvníky nebo elementy doplňků pro aktualizaci cen.");
            return; // Tichý návrat, pokud elementy chybí
        }

        const currentDimensions = {
            length: parseFloat(lengthSlider.value) || 0,
            width: parseFloat(widthSlider.value) || 0,
            height: parseFloat(heightSlider.value) || 0
        };

        addonOptionElements.forEach(option => {
            // 'finalCurrency' je globální proměnná s aktuální měnou
            updateAddonOptionText(option, currentDimensions, finalCurrency);
        });
        console.log("Texty cen doplňků aktualizovány.");
    }


    // --- Funkce pro výpočet a zobrazení CELKOVÉ ceny ---

    /**
     * Aktualizuje zobrazení celkové ceny produktu.
     */
    function updateDisplayPrice() {
        console.log("[LOG] updateDisplayPrice called");
        if (!priceDisplay || !priceContainer) { console.error("[ERROR] Price display or container element not found!"); return; }

        const quantity = quantityInput ? (parseInt(quantityInput.value, 10) || 1) : 1;

        // Použijeme stávající funkci pro výpočet ceny VYBRANÝCH doplňků
        const addonsPriceCZK = getSelectedAddonsPrice('CZK');
        const addonsPriceEUR = getSelectedAddonsPrice('EUR');

        // Základní cena z API (proměnné currentUnitPriceCZK/EUR se aktualizují po API volání)
        const unitCZK = !isNaN(currentUnitPriceCZK) ? currentUnitPriceCZK : 0;
        const unitEUR = !isNaN(currentUnitPriceEUR) ? currentUnitPriceEUR : 0;

        // Příplatky za atributy (Design, Glazura, Střecha) - pokud existují
        // Tyto selecty nemusí být v custom konfigurátoru, pokud ano, odkomentujte
        // const attributeSurchargeCZK = getSelectedAttributeSurcharge('CZK');
        // const attributeSurchargeEUR = getSelectedAttributeSurcharge('EUR');
        const attributeSurchargeCZK = 0; // Placeholder, pokud nejsou
        const attributeSurchargeEUR = 0; // Placeholder, pokud nejsou


        // Finální cena za kus = základ + atributy + VYBRANÉ doplňky
        const finalUnitCZK = unitCZK + attributeSurchargeCZK + addonsPriceCZK;
        const finalUnitEUR = unitEUR + attributeSurchargeEUR + addonsPriceEUR;

        // Celková cena = cena za kus * množství
        const totalCZK = quantity * finalUnitCZK;
        const totalEUR = quantity * finalUnitEUR;

        console.log(`[LOG] updateDisplayPrice - baseUnitCZK=${unitCZK.toFixed(2)}, attrSurCZK=${attributeSurchargeCZK.toFixed(2)}, addonsCZK=${addonsPriceCZK.toFixed(2)}, finalUnitCZK=${finalUnitCZK.toFixed(2)}, quantity=${quantity}, totalCZK=${totalCZK.toFixed(2)}`);

        if (isNaN(totalCZK) || isNaN(totalEUR)) {
            console.error("[ERROR] NaN detected in total price calculation.");
            displayCalculationError('Při výpočtu ceny došlo k interní chybě.');
        } else {
            // Zobrazení ceny podle aktuální měny
            const displayPrice = finalCurrency === 'EUR' ? totalEUR : totalCZK;
            priceDisplay.textContent = formatCurrency(displayPrice, finalCurrency); // Pouze aktuální měna
            priceDisplay.closest('.price-summary')?.classList.remove('price-loading'); // Skryje spinner
            if (errorDiv) errorDiv.style.display = 'none'; // Skryje případnou chybu
            if (submitButton) submitButton.disabled = false;
            console.log("[LOG] Total price display updated successfully.");
        }
    }


    // --- Kalkulace ZÁKLADNÍ ceny - volání API ---

    /**
     * Zpracuje změnu v konfiguraci (posuvník, select doplňku, množství).
     */
    function handleConfigurationChange(event) {
        const targetElement = event?.target;
        if (!targetElement) return;

        const isDimensionSlider = targetElement.matches('#length, #width, #height');
        const isAddonSelect = targetElement.matches('select[data-is-addon-category-select="true"]');
        const isQuantityInput = targetElement.matches('#quantity');
        // const isAttributeSelect = targetElement.matches('#designSelect, #glazeSelect, #roofColorSelect'); // Pokud existují

        console.log(`[EVENT] Config change detected on: ${targetElement.id || targetElement.name}`);

        let requiresApiCall = false;

        if (isDimensionSlider) {
            requiresApiCall = true;
            // Aktualizace zobrazení hodnoty posuvníku
            const valueDisplayId = targetElement.id + 'Value'; // Např. 'lengthValue'
            const valueDisplayElement = document.getElementById(valueDisplayId);
            updateRangeValueDisplay(targetElement, valueDisplayElement);
            // Také aktualizujeme texty cen doplňků ihned při změně rozměru
            updateAllAddonOptions();
        }

        // Změna vybraného doplňku nebo množství nevolá API pro základní cenu,
        // ale vyžaduje přepočet celkové ceny (updateDisplayPrice).
        // Změna atributu (pokud existuje) také nevolá API.

        if (priceContainer) priceContainer.classList.add('price-loading'); // Zobrazit spinner
        if (submitButton) submitButton.disabled = true;
        if (errorDiv) errorDiv.style.display = 'none';

        clearTimeout(calculationTimeout);

        if (requiresApiCall) {
            console.log("[LOG] Change requires API call (dimensions changed). Debouncing...");
            calculationTimeout = setTimeout(calculateBasePriceApiCall, DEBOUNCE_DELAY);
        } else {
            console.log("[LOG] Change does NOT require API call. Updating total display price directly...");
            // Přepočet a zobrazení celkové ceny s malým zpožděním pro plynulost
            calculationTimeout = setTimeout(updateDisplayPrice, 50);
        }
    }

    /**
     * Zavolá API pro výpočet ZÁKLADNÍ ceny na základě rozměrů.
     */
    function calculateBasePriceApiCall() {
        console.log("[API_CALL] Debounce timeout finished. Starting API call to calculate BASE unit price...");
        if (!lengthSlider || !widthSlider || !heightSlider) {
            console.error("API call aborted, sliders missing.");
            displayCalculationError("Chyba konfigurace (posuvníky).");
            return;
        }

        const lengthValue = lengthSlider.value;
        const widthValue = widthSlider.value;
        const heightValue = heightSlider.value;

        // Kontrola platnosti číselných hodnot
        if (isNaN(parseFloat(lengthValue)) || isNaN(parseFloat(widthValue)) || isNaN(parseFloat(heightValue))) {
            console.error("[ERROR] Invalid non-numeric dimension values:", { lengthValue, widthValue, heightValue });
            displayCalculationError('Zvolené rozměry nejsou platné.');
            return;
        }

        const requestPayload = {
            productId: productJsData.id,
            // Posíláme hodnoty jako BigDecimal (stringy) podle DTO
            customDimensions: {
                length: parseFloat(lengthValue).toFixed(2),
                width: parseFloat(widthValue).toFixed(2),
                height: parseFloat(heightValue).toFixed(2)
            },
            // Tyto flagy už backend nepoužívá pro výpočet základní ceny
            customHasDivider: false,
            customHasGutter: false,
            customHasGardenShed: false
        };
        console.log("[LOG] Constructed request payload for BASE price:", requestPayload);

        // Použití finální URL proměnné
        console.log(`[API_CALL] Sending POST request to: ${finalCalculatePriceUrl}`);
        fetch(finalCalculatePriceUrl, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                // Přidání CSRF tokenu, pokud je potřeba (váš kód ho měl)
                // [csrfHeaderName]: csrfToken // Předpokládá, že csrfHeaderName a csrfToken jsou definovány
            },
            body: JSON.stringify(requestPayload)
        })
            .then(response => {
                console.log(`[API_CALL] Received response status: ${response.status}`);
                if (!response.ok) {
                    // Pokusíme se získat chybovou zprávu z těla odpovědi
                    return response.json().then(err => {
                        console.error("[API_CALL] Server returned error:", err);
                        throw new Error(err.errorMessage || `Chyba serveru (${response.status})`);
                    }).catch((parseError) => {
                        console.error("[API_CALL] Error parsing error response or server error without JSON body:", parseError);
                        // Obecná chyba, pokud tělo neobsahuje JSON nebo parsování selže
                        throw new Error(`Chyba serveru (${response.status})`);
                    });
                }
                return response.json();
            })
            .then(data => {
                console.log("[API_CALL] Received BASE price data:", data);
                if (data.errorMessage) {
                    console.warn("[API_CALL] API returned error message:", data.errorMessage);
                    throw new Error(data.errorMessage); // Vyvoláme chybu, aby ji zachytil .catch
                }
                const czk = parseFloat(data.priceCZK);
                const eur = parseFloat(data.priceEUR);

                if (isNaN(czk) || isNaN(eur)) {
                    console.error("[API_CALL] Invalid price data received from API:", data);
                    throw new Error("Neplatná data o ceně ze serveru.");
                }

                console.log(`[LOG] Parsed BASE unit prices from API: CZK=${czk}, EUR=${eur}`);
                currentUnitPriceCZK = czk; // Aktualizace globální proměnné
                currentUnitPriceEUR = eur; // Aktualizace globální proměnné
                console.log(`[LOG] Stored global BASE unit prices: CZK=${currentUnitPriceCZK}, EUR=${currentUnitPriceEUR}`);

                // Po úspěšném získání základní ceny PŘEPOČÍTÁME A ZOBRAZÍME CELKOVOU cenu
                console.log("[LOG] Calling updateDisplayPrice after successful API call.");
                updateDisplayPrice();
            })
            .catch(error => {
                console.error('[API_CALL] Fetch error or API error:', error);
                displayCalculationError(error.message || 'Neznámá chyba při komunikaci se serverem.');
            });
    }

    // --- Funkce pro přípravu dat formuláře ---
    // Funkce prepareAddonsForSubmission z vašeho kódu zde může zůstat,
    // pokud používáte skrytá pole pro odeslání doplňků.
    // Pokud ne, můžete ji odstranit.
    // Zde je verze, která odesílá data podle struktury selectů v produkt-detail-custom.html
    function prepareFormDataBeforeSubmit(event) {
        console.log("[FORM_SUBMIT] Preparing form data...");
        // Projdeme všechny selecty pro kategorie doplňků
        addonSelects.forEach(select => {
            const selectedValue = select.value;
            // Pokud je vybrána volba "Ne" (hodnota '0'), nechceme tento select odeslat,
            // protože backend by mohl očekávat ID doplňku. Disablujeme ho.
            if (selectedValue === '0') {
                select.disabled = true;
                console.log(`[FORM_SUBMIT] Disabling select ${select.name} (value is 0)`);
            } else {
                // Ujistíme se, že je povolený, pokud má vybraný doplněk
                select.disabled = false;
                // Název selectu (např. 'selectedAddonInCategory_Střecha') je nastaven v HTML a měl by být OK.
            }
        });
        console.log("[FORM_SUBMIT] Form data prepared. Submitting...");
        // Formulář se nyní odešle
    }

    // --- Inicializace Event Listenerů ---
    console.log("[LOG] Attaching event listeners...");

    // Listener pro všechny prvky ovlivňující konfiguraci
    // Použijeme společnou třídu 'config-input', kterou přidáte relevantním prvkům v HTML
    // (posuvníky, selecty doplňků, input množství)
    formElement.querySelectorAll('.dimension-slider, select[data-is-addon-category-select="true"], #quantity').forEach(input => {
        const eventType = input.matches('.dimension-slider') ? 'input' : 'change';
        console.log(`[LOG] Attaching '${eventType}' listener to config element: ${input.id || input.name}`);
        input.addEventListener(eventType, handleConfigurationChange);
    });

    // Případné listenery pro atributy (Design, Glazura, Střecha), pokud existují
    // const attributeSelects = [designSelect, glazeSelect, roofColorSelect];
    // attributeSelects.forEach(select => {
    //     if (select) {
    //         select.addEventListener('change', handleConfigurationChange);
    //         console.log(`[LOG] Attaching 'change' listener to attribute select: ${select.id}`);
    //     }
    // });

    // Inicializace zobrazení hodnot posuvníků
    if (lengthSlider && lengthValueDisplay) updateRangeValueDisplay(lengthSlider, lengthValueDisplay);
    if (widthSlider && widthValueDisplay) updateRangeValueDisplay(widthSlider, widthValueDisplay);
    if (heightSlider && heightValueDisplay) updateRangeValueDisplay(heightSlider, heightValueDisplay);

    // Listener pro odeslání formuláře - připraví data (např. disabluje nevybrané selecty)
    formElement.addEventListener('submit', prepareFormDataBeforeSubmit);


    // --- Nastavení počátečního stavu ---
    console.log("[LOG] Setting initial state...");

    // 1. Aktualizovat texty cen u VŠECH doplňků na základě výchozích rozměrů
    updateAllAddonOptions();

    // 2. Vypočítat a zobrazit CELKOVOU cenu na základě výchozí konfigurace
    // (výchozí rozměry, výchozí vybrané doplňky - obvykle "Ne")
    // Pokud výchozí základní cena (currentUnitPriceCZK/EUR) je 0,
    // znamená to, že musíme zavolat API pro její získání.
    if (currentUnitPriceCZK <= 0 && currentUnitPriceEUR <= 0 && lengthSlider) {
        console.log("[LOG] Initial base unit prices are zero or invalid. Triggering initial API call...");
        // Zavoláme API pro základní cenu s výchozími rozměry
        // Spinner by se měl zobrazit v handleConfigurationChange nebo calculateBasePriceApiCall
        calculateBasePriceApiCall(); // API zavolá updateDisplayPrice po úspěchu
    } else {
        // Pokud máme nenulovou počáteční základní cenu (což by nemělo nastat, pokud API počítá vždy),
        // nebo pokud stránka není konfigurovatelná (nemá posuvníky),
        // rovnou zobrazíme celkovou cenu.
        console.log("[LOG] Using pre-loaded base price or non-configurable product. Updating total price display.");
        updateDisplayPrice();
    }


    // Pokud existoval počáteční error ze serveru
    if (initialErrorFromServer && initialErrorFromServer !== 'null' && initialErrorFromServer !== '') {
        console.warn("[WARN] Initial error received from server:", initialErrorFromServer);
        displayCalculationError(initialErrorFromServer);
    }

    console.log("[LOG] Configurator initialization complete (v2.1).");

}); // End DOMContentLoaded