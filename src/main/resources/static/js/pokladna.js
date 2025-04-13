// --- JavaScript (Verze 22 - Oprava zobr. dopravy a zaokrouhlení) ---

// --- Pomocné funkce (bez změny) ---
function formatCurrency(value, symbol, decimalPlaces = 2) {
    if (value === null || typeof value === 'undefined' || isNaN(value)) {
        return '---';
    }
    const roundedValue = parseFloat(value.toFixed(decimalPlaces));
    return roundedValue.toLocaleString('cs-CZ', { minimumFractionDigits: decimalPlaces, maximumFractionDigits: decimalPlaces }) + ' ' + symbol;
}

function safeParseFloat(value) {
    if (value === null || typeof value === 'undefined') return null;
    const cleanedValue = String(value).replace(',', '.').replace(/[^\d.-]/g, '');
    const parsed = parseFloat(cleanedValue);
    const result = isNaN(parsed) ? null : parsed;
    return result;
}

// --- Hlavní logika po načtení DOM ---
document.addEventListener('DOMContentLoaded', function() {
    const checkoutForm = document.getElementById('checkout-form');
    if (!checkoutForm) {
        console.error("JS Error: Checkout form #checkout-form not found! Script cannot initialize.");
        return;
    }
    const dataset = checkoutForm.dataset;

    console.log("JS: Checkout script starting (v22 - UI Fix)...");

    // --- Globální proměnné a konstanty (načtené z data-* atributů) ---
    const isUserLoggedIn = dataset.isUserLoggedIn === 'true';
    const csrfToken = dataset.csrfToken || null;
    const csrfHeaderName = dataset.csrfHeaderName || null;
    const calculateShippingUrl = dataset.calculateShippingUrl || '/pokladna/calculate-shipping';
    const currencySymbol = dataset.currencySymbol || 'Kč';
    let shippingCalculatedSuccessfully = dataset.initialShippingValid === 'true';
    const initialCartIsEmptyState = dataset.initialCartEmpty === 'true';
    const initialSubtotal = safeParseFloat(dataset.initialSubtotal || '0');
    const initialCouponDiscount = safeParseFloat(dataset.initialCouponDiscount || '0');
    const initialTotalItemVat = safeParseFloat(dataset.initialTotalItemVat || '0');

    console.log("JS: Initial values from data - isUserLoggedIn:", isUserLoggedIn, "shippingCalculatedSuccessfully:", shippingCalculatedSuccessfully, "cartIsEmpty:", initialCartIsEmptyState);
    console.log("JS: Initial prices from data - Subtotal:", initialSubtotal, "CouponDiscount:", initialCouponDiscount, "ItemVAT:", initialTotalItemVat);
    console.log("JS: Config - csrfToken:", csrfToken ? '***' : null, "csrfHeader:", csrfHeaderName, "shippingUrl:", calculateShippingUrl, "symbol:", currencySymbol);


    // --- Funkce pro aktualizaci UI ---
    function updateSubmitButtonState() {
        console.log("JS: updateSubmitButtonState called. Current shippingCalculatedSuccessfully:", shippingCalculatedSuccessfully);
        const submitButton = document.getElementById('submit-order-button');
        const noticeArea = document.getElementById('recalculate-shipping-notice-area');
        const cartIsEmpty = initialCartIsEmptyState;

        if (!submitButton || !noticeArea) {
            console.error("JS Error in updateSubmitButtonState: elements #submit-order-button or #recalculate-shipping-notice-area not found!");
            return;
        }

        // Tlačítko je deaktivováno, pokud košík je prázdný NEBO doprava NENÍ úspěšně vypočtena
        const isDisabled = cartIsEmpty || !shippingCalculatedSuccessfully;
        submitButton.disabled = isDisabled;
        console.log("JS: updateSubmitButtonState - Button disabled:", isDisabled);

        // Zobrazit hlášku o nutnosti přepočtu jen pokud není košík prázdný a doprava není validní
        if (!cartIsEmpty && !shippingCalculatedSuccessfully) {
            const errorSpan = document.querySelector('#summary-total-price .recalculate-shipping-notice span');
            const noticeText = errorSpan ? errorSpan.textContent.replace(/[()]/g,'') : 'Pro odeslání objednávky je nutné nejprve spočítat dopravu.';
            noticeArea.textContent = noticeText;
            noticeArea.classList.remove('d-none');
            console.log("JS: updateSubmitButtonState - Recalculate notice shown:", noticeText);
        } else {
            noticeArea.classList.add('d-none');
            console.log("JS: updateSubmitButtonState - Recalculate notice hidden.");
        }
    }

    // Funkce pro aktualizaci souhrnu po AJAX volání
    function updateSummary(data) {
        console.log("JS: updateSummary called with AJAX data:", data);
        // Získání elementů souhrnu
        const summaryOriginalTotalPriceEl = document.getElementById('summary-original-total-price');
        const summaryRoundingRowEl = document.getElementById('summary-rounding-row');
        const summaryRoundingDifferenceEl = document.getElementById('summary-rounding-difference');
        const summaryTotalPriceEl = document.getElementById('summary-total-price');
        const costDisplay = document.getElementById('shipping-cost-display');
        const costValueSpan = document.getElementById('shipping-cost-value');
        const costErrorSpan = document.getElementById('shipping-cost-error');
        const summaryOriginalShippingCostEl = document.getElementById('summary-original-shipping-cost');
        const summaryShippingTaxEl = document.getElementById('summary-shipping-tax');
        const summaryShippingDiscountRowEl = document.getElementById('summary-shipping-discount-row');
        const summaryShippingDiscountValueEl = document.getElementById('summary-shipping-discount-value');
        const vatBreakdownShippingDiv = document.getElementById('vat-breakdown-shipping');
        const summaryTotalVatEl = document.getElementById('summary-total-vat');
        const hiddenCostNoTaxEl = document.getElementById('hiddenShippingCostNoTax');
        const hiddenTaxEl = document.getElementById('hiddenShippingTax');
        const errorAlertEl = document.getElementById('shipping-error-alert');
        const errorAlertTextEl = document.getElementById('shipping-error-text');

        // Vyčistit předchozí stavy
        if (costDisplay) costDisplay.classList.remove('error', 'calculating');
        if (costErrorSpan) costErrorSpan.textContent = '';
        if (errorAlertEl) errorAlertEl.classList.add('d-none');
        shippingCalculatedSuccessfully = false; // Reset

        if (data && data.errorMessage) {
            // --- Zpracování chyby z API ---
            console.error("JS Error: Shipping calculation API returned error:", data.errorMessage);
            const errorText = data.errorMessage || 'Neznámá chyba výpočtu dopravy.';
            if (costDisplay && costErrorSpan) { costDisplay.classList.add('error'); costErrorSpan.textContent = errorText; }
            // Reset souhrnu
            if (summaryOriginalShippingCostEl) summaryOriginalShippingCostEl.innerHTML = `<span class="text-danger recalculate-shipping-notice">${errorText}</span>`;
            if (summaryOriginalTotalPriceEl) summaryOriginalTotalPriceEl.textContent = '---';
            if (summaryRoundingRowEl) summaryRoundingRowEl.classList.add('d-none'); // Skrýt
            if (summaryTotalPriceEl) { summaryTotalPriceEl.innerHTML = `<span class="text-warning fw-normal recalculate-shipping-notice">(Nutno přepočítat dopravu)</span>`; }
            if (summaryTotalVatEl) summaryTotalVatEl.textContent = formatCurrency(initialTotalItemVat, currencySymbol);
            if (hiddenCostNoTaxEl) hiddenCostNoTaxEl.value = '';
            if (hiddenTaxEl) hiddenTaxEl.value = '';
            if (vatBreakdownShippingDiv) vatBreakdownShippingDiv.style.display = 'none'; // Skrýt
            if(summaryShippingDiscountRowEl) summaryShippingDiscountRowEl.style.display = 'none';
            if (errorAlertTextEl) errorAlertTextEl.textContent = errorText;
            if (errorAlertEl) errorAlertEl.classList.remove('d-none');

        } else if (data && typeof data.shippingCostNoTax !== 'undefined' && data.shippingCostNoTax !== null && data.totalPrice !== null) {
            // --- Zpracování úspěšné odpovědi z API ---
            console.log("JS Info: Processing successful shipping calculation data.");
            shippingCalculatedSuccessfully = true;
            const costNoTax = safeParseFloat(data.shippingCostNoTax);
            const tax = safeParseFloat(data.shippingTax);
            // API vrací totalPrice jako zaokrouhlenou hodnotu na 2 místa, pro finální zobrazení potřebujeme zaokrouhlit dolů na 0
            const preciseTotalFromApi = safeParseFloat(data.totalPrice); // Toto je cena před finálním zaokrouhlením dolů
            const roundedTotalToDisplay = Math.floor(preciseTotalFromApi); // Zaokrouhlení dolů na celé číslo pro finální zobrazení

            const originalCostNoTax = safeParseFloat(data.originalShippingCostNoTax);
            const shippingDiscount = safeParseFloat(data.shippingDiscountAmount);
            const totalVatWithShipping = safeParseFloat(data.totalVatWithShipping);

            const subtotalAfterDiscount = initialSubtotal - initialCouponDiscount;
            // Odhad původní celkové ceny PŘED zaokrouhlením (přesnější než v JS)
            const calculatedOriginalTotal = preciseTotalFromApi; // Použijeme přesnější hodnotu z API
            // Rozdíl mezi PŘESNOU cenou z API a cenou ZAOKROUHLENOU DOLŮ NA CELÉ ČÍSLO
            const calculatedRoundingDifference = calculatedOriginalTotal - roundedTotalToDisplay;
            console.log(`JS Debug: calculatedOriginalTotal=${calculatedOriginalTotal}, roundedTotalToDisplay=${roundedTotalToDisplay}, calculatedRoundingDifference=${calculatedRoundingDifference}`);

            // --- ZAČÁTEK AKTUALIZACE UI ---
            // Vstupní pole dopravy (zůstává)
            if (costDisplay && costValueSpan && costErrorSpan) { costValueSpan.textContent = formatCurrency(costNoTax, data.currencySymbol) + ' (bez DPH)'; costErrorSpan.textContent = ''; }

            // Doprava v SOUHRNU
            if (summaryOriginalShippingCostEl) {
                summaryOriginalShippingCostEl.textContent = formatCurrency(originalCostNoTax, data.currencySymbol);
                console.log("JS Debug: Updated #summary-original-shipping-cost to:", summaryOriginalShippingCostEl.textContent);
            } else { console.warn("JS Warn: #summary-original-shipping-cost not found."); }

            // Sleva na dopravu (zůstává)
            if (summaryShippingDiscountRowEl && summaryShippingDiscountValueEl) { if (shippingDiscount !== null && shippingDiscount > 0) { /*...*/ } else { /*...*/ } }

            // Rozpis DPH z dopravy v SOUHRNU
            if (vatBreakdownShippingDiv && summaryShippingTaxEl) {
                if (tax !== null && tax > 0) {
                    summaryShippingTaxEl.textContent = formatCurrency(tax, data.currencySymbol);
                    vatBreakdownShippingDiv.style.display = 'block'; // Zobrazit
                    console.log("JS Debug: Updated and showing #summary-shipping-tax:", summaryShippingTaxEl.textContent);
                } else {
                    vatBreakdownShippingDiv.style.display = 'none'; // Skrýt
                    console.log("JS Debug: Hiding shipping tax breakdown.");
                }
            } else { console.warn("JS Warn: Shipping tax breakdown elements not found."); }

            // Celkové DPH v SOUHRNU
            if (summaryTotalVatEl) {
                summaryTotalVatEl.textContent = formatCurrency(totalVatWithShipping, data.currencySymbol);
                console.log("JS Debug: Updated #summary-total-vat to:", summaryTotalVatEl.textContent);
            } else { console.warn("JS Warn: #summary-total-vat not found."); }

            // Mezisoučet celkem (s DPH) v SOUHRNU
            if (summaryOriginalTotalPriceEl) {
                summaryOriginalTotalPriceEl.textContent = formatCurrency(calculatedOriginalTotal, data.currencySymbol, 2); // Zobrazíme PŘESNOU cenu před zaokrouhlením
                console.log("JS Debug: Updated #summary-original-total-price to:", summaryOriginalTotalPriceEl.textContent);
            } else { console.warn("JS Warn: #summary-original-total-price not found."); }

            // Řádek a hodnota zaokrouhlení v SOUHRNU
            if (summaryRoundingRowEl && summaryRoundingDifferenceEl) {
                // Použijeme novou podmínku s .toFixed(2) a > 0.00
                if (calculatedRoundingDifference !== null && Math.abs(calculatedRoundingDifference.toFixed(2)) > 0.00) {
                    summaryRoundingDifferenceEl.textContent = '- ' + formatCurrency(calculatedRoundingDifference, data.currencySymbol, 2);
                    summaryRoundingRowEl.classList.remove('d-none'); // Zobrazit řádek
                    console.log("JS Debug: Showing rounding difference:", calculatedRoundingDifference);
                } else {
                    summaryRoundingDifferenceEl.textContent = ''; // Vymazat text
                    summaryRoundingRowEl.classList.add('d-none'); // Skrýt řádek
                    console.log("JS Debug: Hiding rounding difference (Difference <= 0.00 after rounding):", calculatedRoundingDifference);
                }
            } else { console.warn("JS Warn: Rounding display elements (#summary-rounding-row / #summary-rounding-difference) not found."); }

            // Finální cena k úhradě v SOUHRNU
            if(summaryTotalPriceEl){
                summaryTotalPriceEl.innerHTML = '';
                const priceSpan = document.createElement('span');
                priceSpan.textContent = formatCurrency(roundedTotalToDisplay, data.currencySymbol, 0); // Použijeme dolů zaokrouhlenou hodnotu
                summaryTotalPriceEl.appendChild(priceSpan);
                summaryTotalPriceEl.classList.remove('text-warning', 'fw-normal', 'recalculate-shipping-notice');
                summaryTotalPriceEl.classList.add('fw-bold');
                console.log("JS Debug: Displaying final rounded total:", roundedTotalToDisplay);
            } else { console.warn("JS Warn: #summary-total-price not found."); }

            // Skrytá pole (zůstává)
            if (hiddenCostNoTaxEl) { hiddenCostNoTaxEl.value = costNoTax !== null ? costNoTax.toFixed(2) : ''; } else { console.warn("JS Warn: #hiddenShippingCostNoTax not found."); }
            if (hiddenTaxEl) { hiddenTaxEl.value = tax !== null ? tax.toFixed(2) : ''; } else { console.warn("JS Warn: #hiddenShippingTax not found."); }

            console.log("JS Info: Summary updated successfully from AJAX.");
            // --- KONEC AKTUALIZACE UI ---

        } else {
            // --- Neplatná data z API ---
            console.error("JS Error: Invalid or incomplete numeric data received from shipping calculation API:", data);
            if (summaryOriginalShippingCostEl) summaryOriginalShippingCostEl.innerHTML = `<span class="text-danger recalculate-shipping-notice">(Chyba dat)</span>`;
            if (summaryOriginalTotalPriceEl) summaryOriginalTotalPriceEl.textContent = '---';
            if (summaryRoundingRowEl) summaryRoundingRowEl.classList.add('d-none');
            if (vatBreakdownShippingDiv) vatBreakdownShippingDiv.style.display = 'none';
            if (summaryTotalVatEl) summaryTotalVatEl.textContent = '---';
            if (summaryTotalPriceEl) summaryTotalPriceEl.innerHTML = `<span class="text-warning fw-normal recalculate-shipping-notice">(Chyba dat)</span>`;
        }
        // Aktualizace stavu tlačítka a hlášky vždy
        updateSubmitButtonState();
    }

    // Funkce pro reset zobrazení dopravy (zůstává stejná jako v předchozí odpovědi)
    function resetShippingDisplay() {
        console.log("JS: resetShippingDisplay called due to address change.");
        // Získání elementů
        const summaryTotalPriceEl = document.getElementById('summary-total-price');
        const costDisplay = document.getElementById('shipping-cost-display');
        const costValueSpan = document.getElementById('shipping-cost-value');
        const costErrorSpan = document.getElementById('shipping-cost-error');
        const summaryOriginalShippingCostEl = document.getElementById('summary-original-shipping-cost');
        const summaryShippingTaxEl = document.getElementById('summary-shipping-tax');
        const summaryShippingDiscountRowEl = document.getElementById('summary-shipping-discount-row');
        const vatBreakdownShippingDiv = document.getElementById('vat-breakdown-shipping');
        const summaryTotalVatEl = document.getElementById('summary-total-vat');
        const summaryOriginalTotalPriceEl = document.getElementById('summary-original-total-price');
        const summaryRoundingRowEl = document.getElementById('summary-rounding-row');
        const hiddenCostNoTaxEl = document.getElementById('hiddenShippingCostNoTax');
        const hiddenTaxEl = document.getElementById('hiddenShippingTax');

        shippingCalculatedSuccessfully = false;

        // Reset UI
        if (costDisplay) costDisplay.classList.remove('error', 'calculating');
        if (costValueSpan) costValueSpan.textContent = '';
        if (costErrorSpan) costErrorSpan.innerHTML = `<span class="text-warning recalculate-shipping-notice">(Nutno přepočítat)</span>`;
        if (summaryOriginalShippingCostEl) summaryOriginalShippingCostEl.innerHTML = `<span class="text-warning recalculate-shipping-notice">(Nutno přepočítat)</span>`;
        if (summaryShippingTaxEl) summaryShippingTaxEl.textContent = '---';
        if (summaryShippingDiscountRowEl) summaryShippingDiscountRowEl.style.display = 'none';
        if (vatBreakdownShippingDiv) vatBreakdownShippingDiv.style.display = 'none';
        if (summaryTotalVatEl) summaryTotalVatEl.textContent = formatCurrency(initialTotalItemVat, currencySymbol);
        if (summaryOriginalTotalPriceEl) summaryOriginalTotalPriceEl.textContent = '---';
        if (summaryRoundingRowEl) summaryRoundingRowEl.classList.add('d-none'); // Skrýt
        if (summaryTotalPriceEl) { summaryTotalPriceEl.innerHTML = `<span class="text-warning fw-normal recalculate-shipping-notice">(Nutno přepočítat dopravu)</span>`; }
        if (hiddenCostNoTaxEl) hiddenCostNoTaxEl.value = '';
        if (hiddenTaxEl) hiddenTaxEl.value = '';

        updateSubmitButtonState();
    }

    // Funkce pro skrytí/zobrazení dodací adresy (zůstává stejná)
    function toggleDeliveryFields(checkbox, deliveryDiv) {
        console.log("JS: toggleDeliveryFields called. Checkbox checked:", checkbox?.checked);
        if (checkbox && deliveryDiv) {
            deliveryDiv.classList.toggle('hidden', checkbox.checked);
            resetShippingDisplay();
        } else {
            console.warn("JS Warn: toggleDeliveryFields - checkbox or deliveryDiv not found.");
        }
    }

    // --- Navěšení Event Listenerů (zůstává stejné) ---
    console.log("JS: Attaching DOMContentLoaded listeners...");
    const calculateShippingBtn = document.getElementById('calculate-shipping-btn');
    const addressTriggers = document.querySelectorAll('.address-trigger');
    const useInvoiceCheckbox = document.getElementById('useInvoiceAddressAsDelivery');
    const deliveryFieldsDiv = document.querySelector('.delivery-address-fields');

    if (calculateShippingBtn) {
        console.log("JS: Button #calculate-shipping-btn found. Attaching listener...");
        calculateShippingBtn.addEventListener('click', function(e) {
            console.log("JS: Button #calculate-shipping-btn CLICKED!");
            e.preventDefault();
            console.log("JS: Default event prevented.");
            // --- AJAX Volání ---
            const btn = this;
            const btnSpinner = btn.querySelector('.spinner-border');
            const btnTextNode = Array.from(btn.childNodes).find(node => node.nodeType === Node.TEXT_NODE && node.textContent.trim().length > 0);
            const originalBtnText = btnTextNode ? btnTextNode.textContent.trim() : 'Spočítat dopravu dle adresy';
            console.log("JS: Disabling button and showing spinner.");
            btn.disabled = true;
            if(btnSpinner) btnSpinner.style.display = 'inline-block';
            if(btnTextNode) btnTextNode.textContent = ' Počítám...';
            const costDisplay = document.getElementById('shipping-cost-display');
            if (costDisplay) costDisplay.classList.add('calculating');
            const submitButton = document.getElementById('submit-order-button');
            if (submitButton) submitButton.disabled = true;
            const errorAlert = document.getElementById('shipping-error-alert');
            if (errorAlert) errorAlert.classList.add('d-none');
            console.log("JS: Collecting address data...");
            const addressData = { street: '', city: '', zipCode: '', country: '' };
            const useInvoiceAddr = useInvoiceCheckbox ? useInvoiceCheckbox.checked : true;
            const streetId = useInvoiceAddr ? 'invoiceStreet' : 'deliveryStreet';
            const cityId = useInvoiceAddr ? 'invoiceCity' : 'deliveryCity';
            const zipId = useInvoiceAddr ? 'invoiceZipCode' : 'deliveryZipCode';
            const countryId = useInvoiceAddr ? 'invoiceCountry' : 'deliveryCountry';
            addressData.street = document.getElementById(streetId)?.value || '';
            addressData.city = document.getElementById(cityId)?.value || '';
            addressData.zipCode = document.getElementById(zipId)?.value || '';
            addressData.country = document.getElementById(countryId)?.value || '';
            console.log(`JS: Collected address data (using invoice: ${useInvoiceAddr}):`, addressData);
            console.log("JS: Performing frontend address validation...");
            if (!addressData.street || !addressData.city || !addressData.zipCode || !addressData.country) {
                const errorMsg = `Pro výpočet dopravy vyplňte kompletní ${useInvoiceAddr ? 'fakturační' : 'dodací'} adresu (Ulice, Město, PSČ, Země).`;
                console.warn("JS: Frontend address validation failed:", errorMsg);
                const errorAlertText = document.getElementById('shipping-error-text');
                if (errorAlertText) errorAlertText.textContent = errorMsg;
                if (errorAlert) errorAlert.classList.remove('d-none');
                btn.disabled = false;
                if(btnSpinner) btnSpinner.style.display = 'none';
                if(btnTextNode) btnTextNode.textContent = originalBtnText;
                if (costDisplay) costDisplay.classList.remove('calculating');
                updateSubmitButtonState();
                console.log("JS: Exiting click handler due to validation error.");
                return;
            }
            console.log("JS: Frontend validation passed.");
            const headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
            if (csrfToken && csrfHeaderName) { headers[csrfHeaderName] = csrfToken; }
            console.log("JS: Initiating fetch request to:", calculateShippingUrl, " Headers:", headers);
            fetch(calculateShippingUrl, { method: 'POST', headers: headers, body: JSON.stringify(addressData) })
                .then(response => {
                    console.log("JS Fetch: Received response status:", response.status);
                    if (!response.ok) { return response.json().then(errData => { throw errData; }).catch(() => { throw new Error(`Chyba serveru (${response.status})`); }); }
                    return response.json();
                })
                .then(data => { console.log("JS Fetch: Received successful data:", data); updateSummary(data); })
                .catch(errorData => { console.error('JS Fetch: Error during fetch:', errorData); updateSummary({ errorMessage: errorData?.errorMessage || errorData?.message || 'Chyba komunikace se serverem.' }); })
                .finally(() => {
                    console.log("JS Fetch: Finished. Resetting button state.");
                    btn.disabled = false;
                    if(btnSpinner) btnSpinner.style.display = 'none';
                    if(btnTextNode) btnTextNode.textContent = originalBtnText;
                    if (costDisplay) costDisplay.classList.remove('calculating');
                    // updateSubmitButtonState() se volá uvnitř updateSummary
                });
            console.log("JS: Fetch request initiated.");
        });
        console.log("JS: Click listener attached successfully to #calculate-shipping-btn.");
    } else { console.error("JS Error: Button #calculate-shipping-btn not found! Listener not attached."); }

    if (addressTriggers.length > 0) {
        console.log("JS: Attaching 'change' listeners to address trigger elements.");
        addressTriggers.forEach(el => { el.addEventListener('change', resetShippingDisplay); });
        console.log("JS: Change listeners attached to address triggers.");
    } else { console.warn("JS Warning: No address trigger elements found."); }

    if (useInvoiceCheckbox && deliveryFieldsDiv) {
        console.log("JS: Initializing delivery fields visibility based on checkbox state.");
        toggleDeliveryFields(useInvoiceCheckbox, deliveryFieldsDiv);
        useInvoiceCheckbox.addEventListener('change', () => toggleDeliveryFields(useInvoiceCheckbox, deliveryFieldsDiv));
        console.log("JS: Change listener attached to delivery toggle checkbox.");
    } else { console.warn("JS Warn: Checkbox/Div for delivery address toggle not found."); }

    // --- Nastavení počátečního stavu (zůstává stejné) ---
    console.log("JS: Setting initial button state.");
    updateSubmitButtonState();
    if (!shippingCalculatedSuccessfully && !initialCartIsEmptyState) {
        console.log("JS Info: Initial shipping is invalid and cart not empty -> calling resetShippingDisplay().");
        resetShippingDisplay();
    } else {
        console.log("JS Info: Initial shipping is valid or cart is empty. No initial reset needed.");
    }

    console.log("JS: Checkout page initialization complete (v22 - UI Fix).");
}); // Konec DOMContentLoaded