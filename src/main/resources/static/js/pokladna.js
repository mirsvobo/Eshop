function formatCurrency(value, symbol, decimalPlaces = 2) {
    if (value === null || typeof value === 'undefined' || isNaN(value)) {
        return '---';
    }
    const roundedValue = parseFloat(value.toFixed(decimalPlaces));
    // Používáme české locale pro formátování, nahradíme pevnou mezeru za normální pro konzistenci
    return roundedValue.toLocaleString('cs-CZ', { minimumFractionDigits: decimalPlaces, maximumFractionDigits: decimalPlaces }).replace(/\s/g, ' ') + ' ' + symbol;
}

function safeParseFloat(value) {
    if (value === null || typeof value === 'undefined') return null;
    // Odstraníme vše kromě číslic, tečky a mínusu, čárku nahradíme tečkou
    const cleanedValue = String(value).replace(',', '.').replace(/[^\d.-]/g, '');
    const parsed = parseFloat(cleanedValue);
    const result = isNaN(parsed) ? null : parsed;
    // console.log(`safeParseFloat: Input='${value}', Cleaned='${cleanedValue}', Parsed=${parsed}, Result=${result}`); // Debug log
    return result;
}

document.addEventListener('DOMContentLoaded', function() {
    const checkoutForm = document.getElementById('checkout-form');
    if (!checkoutForm) {
        console.error("JS Error: Checkout form #checkout-form not found! Script cannot initialize.");
        return;
    }
    const dataset = checkoutForm.dataset;
    console.log("JS: Checkout script starting (v25 - Full with Fix)..."); // Aktualizovaná verze

    // Načtení dat z datasetu formuláře
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

    // Logování načtených hodnot
    console.log("JS: Initial values - isUserLoggedIn:", isUserLoggedIn, "shippingOK:", shippingCalculatedSuccessfully, "cartEmpty:", initialCartIsEmptyState);
    console.log("JS: Initial prices - Subtotal:", initialSubtotal, "CouponDiscount:", initialCouponDiscount, "ItemVAT:", initialTotalItemVat);
    console.log("JS: Config - csrfHeader:", csrfHeaderName, "shippingUrl:", calculateShippingUrl, "symbol:", currencySymbol);

    // Selektory pro UI elementy
    const phonePrefixSelect = document.getElementById('phonePrefix');
    const phoneNumberInput = document.getElementById('phoneNumberPart');
    const hiddenPhoneInput = document.getElementById('phone');
    const deliveryPhonePrefixSelect = document.getElementById('deliveryPhonePrefix');
    const deliveryPhoneNumberInput = document.getElementById('deliveryPhoneNumberPart');
    const hiddenDeliveryPhoneInput = document.getElementById('deliveryPhone');
    const calculateShippingBtn = document.getElementById('calculate-shipping-btn');
    const addressTriggers = document.querySelectorAll('.address-trigger'); // Přidejte třídu 'address-trigger' relevantním polím adresy v HTML
    const useInvoiceCheckbox = document.getElementById('useInvoiceAddressAsDelivery');
    const deliveryFieldsDiv = document.querySelector('.delivery-address-fields');
    const submitButton = document.getElementById('submit-order-button');
    const noticeArea = document.getElementById('recalculate-shipping-notice-area');
    const costDisplay = document.getElementById('shipping-cost-display');
    const costValueSpan = document.getElementById('shipping-cost-value');
    const costErrorSpan = document.getElementById('shipping-cost-error');
    const summaryOriginalShippingCostEl = document.getElementById('summary-original-shipping-cost');
    const summaryShippingTaxEl = document.getElementById('summary-shipping-tax');
    const summaryShippingDiscountRowEl = document.getElementById('summary-shipping-discount-row');
    const summaryShippingDiscountValueEl = document.getElementById('summary-shipping-discount-value');
    const vatBreakdownShippingDiv = document.getElementById('vat-breakdown-shipping');
    const summaryTotalVatEl = document.getElementById('summary-total-vat');
    const summaryOriginalTotalPriceEl = document.getElementById('summary-original-total-price');
    const summaryRoundingRowEl = document.getElementById('summary-rounding-row');
    const summaryRoundingDifferenceEl = document.getElementById('summary-rounding-difference');
    const summaryTotalPriceEl = document.getElementById('summary-total-price');
    const hiddenCostNoTaxEl = document.getElementById('hiddenShippingCostNoTax');
    const hiddenTaxEl = document.getElementById('hiddenShippingTax');
    const errorAlertEl = document.getElementById('shipping-error-alert');
    const errorAlertTextEl = document.getElementById('shipping-error-text');

    // --- FUNKCE ---

    // Aktualizuje stav odesílacího tlačítka a upozornění na nutnost přepočtu
    function updateSubmitButtonState() {
        console.log("JS: updateSubmitButtonState. Shipping OK:", shippingCalculatedSuccessfully);
        if (!submitButton || !noticeArea) {
            console.error("JS Error: updateSubmitButtonState - missing required elements (#submit-order-button or #recalculate-shipping-notice-area)!");
            return;
        }
        const cartIsEmpty = initialCartIsEmptyState;
        const isDisabled = cartIsEmpty || !shippingCalculatedSuccessfully;
        submitButton.disabled = isDisabled;
        console.log("JS: updateSubmitButtonState - Button disabled:", isDisabled);

        if (!cartIsEmpty && !shippingCalculatedSuccessfully) {
            const noticeText = 'Pro odeslání objednávky je nutné nejprve spočítat dopravu.';
            noticeArea.textContent = noticeText;
            noticeArea.classList.remove('d-none');
            console.log("JS: updateSubmitButtonState - Recalculate notice shown:", noticeText);
        } else {
            noticeArea.classList.add('d-none');
            console.log("JS: updateSubmitButtonState - Recalculate notice hidden.");
        }
    }

    // Aktualizuje zobrazení souhrnu na základě dat z AJAX odpovědi
    function updateSummary(data) {
        console.log("JS: updateSummary with AJAX data:", data);

        // Reset UI prvků
        if (costDisplay) costDisplay.classList.remove('error', 'calculating');
        if (costErrorSpan) costErrorSpan.textContent = '';
        if (errorAlertEl) errorAlertEl.classList.add('d-none');
        shippingCalculatedSuccessfully = false; // Defaultně false, přepíše se při úspěchu

        if (data && data.errorMessage) {
            console.error("JS Error: Shipping API error:", data.errorMessage);
            const errorText = data.errorMessage || 'Neznámá chyba výpočtu dopravy.';
            // Zobrazit chybu v sekci dopravy
            if (costDisplay && costErrorSpan) { costDisplay.classList.add('error'); costErrorSpan.textContent = errorText; }
            // Zobrazit chybu v souhrnu
            if (summaryOriginalShippingCostEl) summaryOriginalShippingCostEl.innerHTML = `<span class="text-danger recalculate-shipping-notice">${errorText}</span>`;
            if (summaryOriginalTotalPriceEl) summaryOriginalTotalPriceEl.textContent = '---';
            if (summaryRoundingRowEl) summaryRoundingRowEl.classList.add('d-none');
            if (summaryTotalPriceEl) { summaryTotalPriceEl.innerHTML = `<span class="text-warning fw-normal recalculate-shipping-notice">(Nutno přepočítat dopravu)</span>`; }
            if (summaryTotalVatEl) summaryTotalVatEl.textContent = formatCurrency(initialTotalItemVat, currencySymbol); // Vrátit DPH jen ze zboží
            if (hiddenCostNoTaxEl) hiddenCostNoTaxEl.value = '';
            if (hiddenTaxEl) hiddenTaxEl.value = '';
            if (vatBreakdownShippingDiv) vatBreakdownShippingDiv.style.display = 'none';
            if (summaryShippingDiscountRowEl) summaryShippingDiscountRowEl.style.display = 'none';
            // Zobrazit hlavní alert
            if (errorAlertTextEl) errorAlertTextEl.textContent = errorText;
            if (errorAlertEl) errorAlertEl.classList.remove('d-none');

        } else if (data && typeof data.shippingCostNoTax !== 'undefined' && data.shippingCostNoTax !== null && data.totalPrice !== null) {
            console.log("JS Info: Processing successful shipping data.");
            shippingCalculatedSuccessfully = true;

            // Bezpečné parsování hodnot z odpovědi
            const costNoTax = safeParseFloat(data.shippingCostNoTax); // Finální cena dopravy bez DPH
            const tax = safeParseFloat(data.shippingTax);             // Finální DPH z dopravy
            const preciseTotalFromApi = safeParseFloat(data.totalPrice); // Přesná celková cena PŘED finálním zaokrouhlením
            const roundedTotalToDisplay = Math.floor(preciseTotalFromApi); // Finální zaokrouhlená cena pro zobrazení
            const originalCostNoTax = safeParseFloat(data.originalShippingCostNoTax); // Původní cena dopravy bez DPH
            const shippingDiscount = safeParseFloat(data.shippingDiscountAmount);     // Výše slevy na dopravu
            const totalVatWithShipping = safeParseFloat(data.totalVatWithShipping);  // Celkové DPH (zboží + finální doprava)
            const calculatedOriginalTotal = preciseTotalFromApi; // Původní PŘESNÁ cena
            const calculatedRoundingDifference = calculatedOriginalTotal - roundedTotalToDisplay; // Rozdíl zaokrouhlení

            console.log(`JS Debug: Prices - costNoTax=${costNoTax}, tax=${tax}, preciseTotal=${preciseTotalFromApi}, roundedTotal=${roundedTotalToDisplay}, origCost=${originalCostNoTax}, shipDiscount=${shippingDiscount}, totalVAT=${totalVatWithShipping}, origTotalCalc=${calculatedOriginalTotal}, roundingDiff=${calculatedRoundingDifference}`);

            // Aktualizace zobrazení ceny dopravy
            if (costDisplay && costValueSpan && costErrorSpan) {
                costValueSpan.textContent = formatCurrency(costNoTax, currencySymbol) + ' (bez DPH)';
                costErrorSpan.textContent = '';
            } else { console.warn("JS Warn: #shipping-cost-display elements not found."); }

            // Aktualizace souhrnu
            if (summaryOriginalShippingCostEl) {
                summaryOriginalShippingCostEl.textContent = formatCurrency(originalCostNoTax, currencySymbol);
            } else { console.warn("JS Warn: #summary-original-shipping-cost not found."); }

            if (summaryShippingDiscountRowEl && summaryShippingDiscountValueEl) {
                if (shippingDiscount !== null && shippingDiscount > 0) {
                    summaryShippingDiscountValueEl.textContent = '- ' + formatCurrency(shippingDiscount, currencySymbol);
                    summaryShippingDiscountRowEl.style.display = 'flex'; // Použít flex pro zachování layoutu
                } else {
                    summaryShippingDiscountRowEl.style.display = 'none';
                }
            } else { console.warn("JS Warn: Shipping discount elements not found."); }

            if (vatBreakdownShippingDiv && summaryShippingTaxEl) {
                if (tax !== null && tax > 0) {
                    summaryShippingTaxEl.textContent = formatCurrency(tax, currencySymbol);
                    vatBreakdownShippingDiv.style.display = 'block'; // Zobrazit DPH z dopravy
                } else {
                    vatBreakdownShippingDiv.style.display = 'none'; // Skrýt DPH z dopravy
                }
            } else { console.warn("JS Warn: Shipping tax breakdown elements not found."); }

            if (summaryTotalVatEl) {
                summaryTotalVatEl.textContent = formatCurrency(totalVatWithShipping, currencySymbol);
            } else { console.warn("JS Warn: #summary-total-vat not found."); }

            if (summaryOriginalTotalPriceEl) {
                // Zobrazíme původní PŘESNOU cenu
                summaryOriginalTotalPriceEl.textContent = formatCurrency(calculatedOriginalTotal, currencySymbol, 2);
            } else { console.warn("JS Warn: #summary-original-total-price not found."); }

            if (summaryRoundingRowEl && summaryRoundingDifferenceEl) {
                // Zobrazíme zaokrouhlení, pokud je rozdíl větší než malá tolerance
                if (calculatedRoundingDifference !== null && Math.abs(calculatedRoundingDifference.toFixed(2)) > 0.001) {
                    summaryRoundingDifferenceEl.textContent = '- ' + formatCurrency(calculatedRoundingDifference, currencySymbol, 2);
                    summaryRoundingRowEl.classList.remove('d-none');
                } else {
                    summaryRoundingDifferenceEl.textContent = '';
                    summaryRoundingRowEl.classList.add('d-none');
                }
            } else { console.warn("JS Warn: Rounding display elements not found."); }

            if (summaryTotalPriceEl) {
                // Zobrazíme finální ZAOKROUHLENOU cenu
                summaryTotalPriceEl.innerHTML = ''; // Vyčistit předchozí obsah
                const priceSpan = document.createElement('span');
                priceSpan.textContent = formatCurrency(roundedTotalToDisplay, currencySymbol, 0); // 0 des. míst
                summaryTotalPriceEl.appendChild(priceSpan);
                // Odstranit varovné třídy a přidat tučné písmo
                summaryTotalPriceEl.classList.remove('text-warning', 'fw-normal', 'recalculate-shipping-notice');
                summaryTotalPriceEl.classList.add('fw-bold');
            } else { console.warn("JS Warn: #summary-total-price not found."); }

            // Aktualizace skrytých polí pro odeslání formuláře
            if (hiddenCostNoTaxEl) { hiddenCostNoTaxEl.value = costNoTax !== null ? costNoTax.toFixed(2) : ''; } else { console.warn("JS Warn: #hiddenShippingCostNoTax not found."); }
            if (hiddenTaxEl) { hiddenTaxEl.value = tax !== null ? tax.toFixed(2) : ''; } else { console.warn("JS Warn: #hiddenShippingTax not found."); }

            console.log("JS Info: Summary updated successfully from AJAX.");

        } else {
            // Neúplná nebo nevalidní data z API
            console.error("JS Error: Invalid/incomplete data received from shipping API:", data);
            if (summaryOriginalShippingCostEl) summaryOriginalShippingCostEl.innerHTML = `<span class="text-danger recalculate-shipping-notice">(Chyba dat)</span>`;
            if (summaryOriginalTotalPriceEl) summaryOriginalTotalPriceEl.textContent = '---';
            if (summaryRoundingRowEl) summaryRoundingRowEl.classList.add('d-none');
            if (vatBreakdownShippingDiv) vatBreakdownShippingDiv.style.display = 'none';
            if (summaryTotalVatEl) summaryTotalVatEl.textContent = '---';
            if (summaryTotalPriceEl) summaryTotalPriceEl.innerHTML = `<span class="text-warning fw-normal recalculate-shipping-notice">(Chyba dat)</span>`;
        }
        updateSubmitButtonState(); // Aktualizovat stav odesílacího tlačítka
    }

    // Resetuje zobrazení dopravy a celkové ceny (např. po změně adresy)
    function resetShippingDisplay() {
        console.log("JS: resetShippingDisplay called.");

        shippingCalculatedSuccessfully = false; // Označit, že doprava není spočítaná

        // Reset zobrazení ceny dopravy
        if (costDisplay) costDisplay.classList.remove('error', 'calculating');
        if (costValueSpan) costValueSpan.textContent = '';
        if (costErrorSpan) costErrorSpan.innerHTML = `<span class="text-warning recalculate-shipping-notice">(Nutno přepočítat)</span>`;

        // Reset souhrnu
        if (summaryOriginalShippingCostEl) summaryOriginalShippingCostEl.innerHTML = `<span class="text-warning recalculate-shipping-notice">(Nutno přepočítat)</span>`;
        if (summaryShippingTaxEl) summaryShippingTaxEl.textContent = '---';
        if (summaryShippingDiscountRowEl) summaryShippingDiscountRowEl.style.display = 'none'; // Skrýt slevu na dopravu
        if (vatBreakdownShippingDiv) vatBreakdownShippingDiv.style.display = 'none'; // Skrýt DPH z dopravy
        // Vrátit DPH jen ze zboží
        if (summaryTotalVatEl) summaryTotalVatEl.textContent = formatCurrency(initialTotalItemVat, currencySymbol);
        if (summaryOriginalTotalPriceEl) summaryOriginalTotalPriceEl.textContent = '---'; // Reset původní ceny
        if (summaryRoundingRowEl) summaryRoundingRowEl.classList.add('d-none'); // Skrýt zaokrouhlení
        if (summaryTotalPriceEl) {
            summaryTotalPriceEl.innerHTML = `<span class="text-warning fw-normal recalculate-shipping-notice">(Nutno přepočítat dopravu)</span>`; // Zobrazit varování
        }
        // Vymazat skrytá pole
        if (hiddenCostNoTaxEl) hiddenCostNoTaxEl.value = '';
        if (hiddenTaxEl) hiddenTaxEl.value = '';

        updateSubmitButtonState(); // Aktualizovat stav odesílacího tlačítka
    }

    // Přepíná zobrazení polí pro dodací adresu
    function toggleDeliveryFields(checkbox, deliveryDiv) {
        console.log("JS: toggleDeliveryFields. Checked:", checkbox?.checked);
        if (checkbox && deliveryDiv) {
            deliveryDiv.classList.toggle('hidden', checkbox.checked); // Třída 'hidden' by měla mít styl display: none !important;
            resetShippingDisplay(); // Resetovat dopravu při každé změně
        } else { console.warn("JS Warn: toggleDeliveryFields - elements (#useInvoiceAddressAsDelivery or .delivery-address-fields) not found."); }
    }

    // Spojí předvolbu a číslo, aktualizuje skryté pole a provede základní frontend validaci délky
    function updateCombinedPhone(prefixSelect, numberInput, hiddenInput, isInitialCall = false) {
        if (!prefixSelect || !numberInput || !hiddenInput) {
            console.warn("JS Warn: Missing elements for updating combined phone.");
            return;
        }

        const prefix = prefixSelect.value;
        const numberPartRaw = numberInput.value;
        const numberPartClean = numberPartRaw.replace(/\D/g, ''); // Odstraní vše kromě číslic
        const errorDisplay = numberInput.nextElementSibling; // Předpokládáme, že invalid-feedback je hned za inputem

        // Aktualizujeme skryté pole, jen když je co uložit nebo když to není první volání
        if (numberPartClean.length > 0 || !isInitialCall) {
            const combined = prefix + numberPartClean;
            hiddenInput.value = combined;
            console.log(`JS: Updated hidden input #${hiddenInput.id} to: ${combined}`);
        } else if (isInitialCall && hiddenInput.value.startsWith(prefix)) {
            console.log(`JS: Initial call and number part is empty. Keeping hidden input #${hiddenInput.id} value from DTO: ${hiddenInput.value}`);
        } else if (isInitialCall) {
            // Pokud je to první volání, číslo je prázdné a prefix neodpovídá, nastavíme jen prefix
            hiddenInput.value = prefix;
            console.log(`JS: Initial call, number empty, prefix mismatch. Setting hidden input #${hiddenInput.id} to prefix: ${prefix}`);
        }


        // Reset chybového stavu POUZE pokud to není první volání
        if (!isInitialCall) {
            numberInput.classList.remove('is-invalid');
            if (errorDisplay && errorDisplay.classList.contains('invalid-feedback')) {
                errorDisplay.textContent = ''; // Vymažeme text chyby
            }
        }

        // Frontend Kontrola Délky - jen pokud uživatel něco zadal (ne při startu, ne pro prázdný input)
        if (!isInitialCall && numberPartRaw.trim().length > 0) {
            let minLength = 9;
            let maxLength = 9; // Pro CZ/SK

            if (numberPartClean.length > 0 && (numberPartClean.length < minLength || numberPartClean.length > maxLength)) {
                numberInput.classList.add('is-invalid');
                if (errorDisplay && errorDisplay.classList.contains('invalid-feedback')) {
                    errorDisplay.textContent = `Zadejte prosím platné ${minLength}místné číslo.`; // Nastavíme text chyby
                }
            }
        }
    }

    // --- EVENT LISTENERY ---

    // Listener pro tlačítko výpočtu dopravy
    if (calculateShippingBtn) {
        calculateShippingBtn.addEventListener('click', function(event) {
            event.preventDefault();
            console.log("JS: Calculate Shipping Button clicked.");

            const useInvoice = useInvoiceCheckbox ? useInvoiceCheckbox.checked : true;
            const addressData = {};

            if (useInvoice) {
                addressData.street = document.getElementById('invoiceStreet')?.value || '';
                addressData.city = document.getElementById('invoiceCity')?.value || '';
                addressData.zipCode = document.getElementById('invoiceZipCode')?.value || '';
                addressData.country = document.getElementById('invoiceCountry')?.value || '';
            } else {
                addressData.street = document.getElementById('deliveryStreet')?.value || '';
                addressData.city = document.getElementById('deliveryCity')?.value || '';
                addressData.zipCode = document.getElementById('deliveryZipCode')?.value || '';
                addressData.country = document.getElementById('deliveryCountry')?.value || '';
            }

            if (!addressData.street || !addressData.city || !addressData.zipCode || !addressData.country) {
                console.warn("JS: Address data incomplete for shipping calculation.");
                updateSummary({ errorMessage: 'Prosím, vyplňte kompletní ' + (useInvoice ? 'fakturační' : 'dodací') + ' adresu.' });
                updateSubmitButtonState();
                return;
            }

            console.log("JS: Sending shipping calculation request with address:", addressData);

            const btnSpinner = calculateShippingBtn.querySelector('.spinner-border');
            calculateShippingBtn.disabled = true;
            if (btnSpinner) btnSpinner.classList.remove('d-none');
            if (costDisplay) costDisplay.classList.add('calculating');
            if (costErrorSpan) costErrorSpan.textContent = '(Počítám...)';


            fetch(calculateShippingUrl, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                    ...(csrfHeaderName && csrfToken && { [csrfHeaderName]: csrfToken })
                },
                body: JSON.stringify(addressData)
            })
                .then(response => {
                    if (!response.ok) {
                        return response.json().then(err => {
                            throw new Error(err.errorMessage || `Chyba serveru: ${response.status}`);
                        }).catch(() => {
                            throw new Error(`Chyba serveru: ${response.status}`);
                        });
                    }
                    return response.json();
                })
                .then(data => {
                    updateSummary(data);
                })
                .catch(error => {
                    console.error('JS Error: Shipping calculation failed:', error);
                    updateSummary({ errorMessage: error.message || 'Chyba výpočtu dopravy.' });
                })
                .finally(() => {
                    calculateShippingBtn.disabled = false;
                    if (btnSpinner) btnSpinner.classList.add('d-none');
                    if (costDisplay) costDisplay.classList.remove('calculating');
                    console.log("JS: Shipping calculation AJAX finished.");
                });
        });
        console.log("JS: Event listener for shipping calculation button attached.");
    } else {
        console.warn("JS Warn: Calculate shipping button #calculate-shipping-btn not found!");
    }

    // Listener pro změnu adresy (na všech relevantních polích)
    if (addressTriggers.length > 0) {
        addressTriggers.forEach(trigger => {
            trigger.addEventListener('input', resetShippingDisplay);
            trigger.addEventListener('change', resetShippingDisplay); // Pro selecty atd.
        });
        console.log("JS: Event listeners attached to address trigger elements.");
    } else {
        console.warn("JS Warn: No address trigger elements found. Add class 'address-trigger' to address inputs.");
    }

    // Listener pro checkbox dodací adresy
    if (useInvoiceCheckbox) {
        useInvoiceCheckbox.addEventListener('change', function() {
            toggleDeliveryFields(this, deliveryFieldsDiv);
        });
        // Zavoláme pro inicializaci správného zobrazení
        toggleDeliveryFields(useInvoiceCheckbox, deliveryFieldsDiv);
        console.log("JS: Event listener for delivery address toggle attached.");
    } else {
        console.warn("JS Warn: 'Use invoice address' checkbox #useInvoiceAddressAsDelivery not found.");
    }

    // Listenery pro telefony
    if (phonePrefixSelect && phoneNumberInput && hiddenPhoneInput) {
        phonePrefixSelect.addEventListener('change', () => updateCombinedPhone(phonePrefixSelect, phoneNumberInput, hiddenPhoneInput, false));
        phoneNumberInput.addEventListener('input', () => updateCombinedPhone(phonePrefixSelect, phoneNumberInput, hiddenPhoneInput, false));
        // Prvotní nastavení
        updateCombinedPhone(phonePrefixSelect, phoneNumberInput, hiddenPhoneInput, true);
        console.log("JS: Listeners and initial setup for main phone attached.");
    } else { console.warn("JS Warn: Elements for main phone listener missing."); }

    if (deliveryPhonePrefixSelect && deliveryPhoneNumberInput && hiddenDeliveryPhoneInput) {
        deliveryPhonePrefixSelect.addEventListener('change', () => updateCombinedPhone(deliveryPhonePrefixSelect, deliveryPhoneNumberInput, hiddenDeliveryPhoneInput, false));
        deliveryPhoneNumberInput.addEventListener('input', () => updateCombinedPhone(deliveryPhonePrefixSelect, deliveryPhoneNumberInput, hiddenDeliveryPhoneInput, false));
        // Prvotní nastavení
        updateCombinedPhone(deliveryPhonePrefixSelect, deliveryPhoneNumberInput, hiddenDeliveryPhoneInput, true);
        console.log("JS: Listeners and initial setup for delivery phone attached.");
    } else { console.warn("JS Warn: Elements for delivery phone listener missing."); }

    // --- SCROLL & FOCUS K CHYBĚ ---
    const validationErrorAlert = document.getElementById('form-validation-errors-summary');
    const formHasErrors = document.body.querySelector('.is-invalid') !== null;
    if (validationErrorAlert && formHasErrors) {
        const firstInvalidField = document.querySelector('#checkout-form .is-invalid');
        if (firstInvalidField) {
            console.log("JS: Validation errors detected, scrolling to:", firstInvalidField);
            firstInvalidField.scrollIntoView({ behavior: 'smooth', block: 'center' });
            setTimeout(() => {
                if (typeof firstInvalidField.focus === 'function') {
                    firstInvalidField.focus({ preventScroll: true });
                    console.log("JS: Focused first invalid field.");
                } else { console.log("JS: First invalid element not focusable."); }
            }, 300);
        } else { console.log("JS: General validation error, but no .is-invalid field found."); }
    } else { console.log("JS: No validation errors on load."); }

    // --- INICIALIZACE ---
    console.log("JS: Setting initial button state.");
    updateSubmitButtonState(); // Nastavit počáteční stav tlačítka
    // Resetovat zobrazení dopravy, pokud nebyla platná při načtení stránky a košík není prázdný
    if (!shippingCalculatedSuccessfully && !initialCartIsEmptyState) {
        console.log("JS Info: Initial shipping invalid & cart not empty -> resetting display.");
        resetShippingDisplay();
    } else { console.log("JS Info: Initial shipping OK or cart empty."); }

    console.log("JS: Checkout page initialization complete (v25 - Full with Fix).");
});