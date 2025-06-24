function formatCurrency(t, e, n = 2) {
    if (null === t || void 0 === t || isNaN(t)) return "---";
    const o = parseFloat(t.toFixed(n));
    return o.toLocaleString("cs-CZ", {
        minimumFractionDigits: n,
        maximumFractionDigits: n
    }).replace(/\s/g, " ") + " " + e
}

function safeParseFloat(value) {
    if (value === null || value === undefined) return null;
    const cleanedValue = String(value).replace(',', '.').replace(/[^\d.-]/g, '');
    const parsed = parseFloat(cleanedValue);
    const result = isNaN(parsed) ? null : parsed;
    return result;
}

document.addEventListener("DOMContentLoaded", function () {
    const checkoutForm = document.getElementById("checkout-form");
    if (!checkoutForm) {
        console.error("JS Error: Checkout form #checkout-form not found! Script cannot initialize.");
        return
    }
    const dataset = checkoutForm.dataset;
    console.log("JS: Checkout script starting (v28 - Shipping Discount Class Toggle Final)...");
    const isUserLoggedIn = dataset.isUserLoggedIn === "true";
    const csrfToken = dataset.csrfToken || null;
    const csrfHeaderName = dataset.csrfHeaderName || null;
    const calculateShippingUrl = dataset.calculateShippingUrl || "/pokladna/calculate-shipping";
    const currencySymbol = dataset.currencySymbol || "Kč";
    let shippingOK = dataset.initialShippingValid === "true";
    const initialCartEmpty = dataset.initialCartEmpty === "true";
    const initialTotalItemVat = safeParseFloat(dataset.initialTotalItemVat || "0");
    console.log("JS: Initial values - isUserLoggedIn:", isUserLoggedIn, "shippingOK:", shippingOK, "cartEmpty:", initialCartEmpty);
    console.log("JS: Initial prices - ItemVAT:", initialTotalItemVat);
    console.log("JS: Config - csrfHeader:", csrfHeaderName, "shippingUrl:", calculateShippingUrl, "symbol:", currencySymbol);

    const phonePrefixEl = document.getElementById("phonePrefix");
    const phoneNumberPartEl = document.getElementById("phoneNumberPart");
    const hiddenPhoneEl = document.getElementById("phone");
    const deliveryPhonePrefixEl = document.getElementById("deliveryPhonePrefix");
    const deliveryPhoneNumberPartEl = document.getElementById("deliveryPhoneNumberPart");
    const hiddenDeliveryPhoneEl = document.getElementById("deliveryPhone");
    const calculateShippingBtn = document.getElementById("calculate-shipping-btn");
    const addressTriggerElements = document.querySelectorAll(".address-trigger");
    const useInvoiceAddressCheckbox = document.getElementById("useInvoiceAddressAsDelivery");
    const deliveryAddressFieldsContainer = document.querySelector(".delivery-address-fields");
    const submitOrderButton = document.getElementById("submit-order-button");
    const recalculateShippingNoticeArea = document.getElementById("recalculate-shipping-notice-area");
    const shippingCostDisplayEl = document.getElementById('shipping-cost-display');
    const shippingCostValueEl = document.getElementById('shipping-cost-value');
    const shippingCostErrorEl = document.getElementById('shipping-cost-error');
    const summaryOriginalShippingCostEl = document.getElementById('summary-original-shipping-cost');
    const summaryShippingTaxEl = document.getElementById('summary-shipping-tax');
    const shippingDiscountRow = document.getElementById('summary-shipping-discount-row');
    const shippingDiscountValueEl = document.getElementById('summary-shipping-discount-value');
    const vatBreakdownShippingEl = document.getElementById('vat-breakdown-shipping');
    const summaryTotalVatEl = document.getElementById('summary-total-vat');
    const summaryOriginalTotalPriceEl = document.getElementById('summary-original-total-price');
    const summaryRoundingRowEl = document.getElementById('summary-rounding-row');
    const summaryRoundingDifferenceEl = document.getElementById('summary-rounding-difference');
    const summaryTotalPriceEl = document.getElementById('summary-total-price');
    const hiddenShippingCostNoTaxEl = document.getElementById('hiddenShippingCostNoTax');
    const hiddenShippingTaxEl = document.getElementById('hiddenShippingTax');
    const shippingErrorAlert = document.getElementById('shipping-error-alert');
    const shippingErrorText = document.getElementById('shipping-error-text');

    function updateSubmitButtonState() {
        console.log("JS: updateSubmitButtonState. Shipping OK:", shippingOK);
        if (!submitOrderButton || !recalculateShippingNoticeArea) {
            console.error("JS Error: updateSubmitButtonState - missing required elements (#submit-order-button or #recalculate-shipping-notice-area)!");
            return
        }
        const cartIsEmpty = initialCartEmpty;
        const shouldDisableButton = cartIsEmpty || !shippingOK;
        submitOrderButton.disabled = shouldDisableButton;
        console.log("JS: updateSubmitButtonState - Button disabled:", shouldDisableButton);
        const recalculateMsg = "Pro odeslání objednávky je nutné nejprve spočítat dopravu.";
        if (!cartIsEmpty && !shippingOK) {
            recalculateShippingNoticeArea.textContent = recalculateMsg;
            recalculateShippingNoticeArea.classList.remove("d-none");
            console.log("JS: updateSubmitButtonState - Recalculate notice shown:", recalculateMsg)
        } else {
            recalculateShippingNoticeArea.classList.add("d-none");
            console.log("JS: updateSubmitButtonState - Recalculate notice hidden.")
        }
    }

    function updateSummary(ajaxData) {
        console.log("JS: updateSummary with AJAX data (v28):", ajaxData);
        if (shippingCostDisplayEl) shippingCostDisplayEl.classList.remove('error', 'calculating');
        if (shippingCostErrorEl) shippingCostErrorEl.textContent = "";
        if (shippingErrorAlert) shippingErrorAlert.classList.add('d-none');
        shippingOK = false;

        const currentCurrencySymbol = checkoutForm.dataset.currencySymbol || "Kč";

        if (ajaxData && ajaxData.errorMessage) {
            console.error("JS Error: Shipping API error:", ajaxData.errorMessage);
            const errorMsg = ajaxData.errorMessage || "Neznámá chyba výpočtu dopravy.";
            if (shippingCostDisplayEl && shippingCostErrorEl) {
                shippingCostDisplayEl.classList.add('error');
                shippingCostErrorEl.textContent = errorMsg
            }
            if (summaryOriginalShippingCostEl) summaryOriginalShippingCostEl.innerHTML = `<span class="text-danger recalculate-shipping-notice">${errorMsg}</span>`;
            if (summaryTotalPriceEl) summaryTotalPriceEl.innerHTML = `<span class="text-warning fw-normal recalculate-shipping-notice">(Nutno přepočítat dopravu)</span>`;
            if (summaryOriginalTotalPriceEl) summaryOriginalTotalPriceEl.textContent = "---";
            if (summaryRoundingRowEl) summaryRoundingRowEl.classList.add('d-none');
            if (summaryTotalVatEl) summaryTotalVatEl.textContent = formatCurrency(initialTotalItemVat, currentCurrencySymbol);
            if (hiddenShippingCostNoTaxEl) hiddenShippingCostNoTaxEl.value = "";
            if (hiddenShippingTaxEl) hiddenShippingTaxEl.value = "";
            if (vatBreakdownShippingEl) vatBreakdownShippingEl.classList.add('d-none');

            if (shippingDiscountRow && shippingDiscountValueEl) {
                shippingDiscountValueEl.textContent = "";
                shippingDiscountRow.classList.add('d-none'); // Skrýt pomocí třídy
                console.log("JS: Shipping calculation error, ensuring shipping discount row is hidden using class.");
            }
            if (shippingErrorText && shippingErrorAlert) {
                shippingErrorText.textContent = errorMsg;
                shippingErrorAlert.classList.remove('d-none')
            }
        } else if (ajaxData && ajaxData.shippingCostNoTax !== undefined && ajaxData.shippingCostNoTax !== null && ajaxData.totalPrice !== null) {
            console.log("JS Info: Processing successful shipping data.");
            shippingOK = true;
            const finalShippingCostNoTax = safeParseFloat(ajaxData.shippingCostNoTax);
            const finalShippingTax = safeParseFloat(ajaxData.shippingTax);
            const preciseOrderTotal = safeParseFloat(ajaxData.totalPrice);
            const originalShippingCostNoTax = safeParseFloat(ajaxData.originalShippingCostNoTax);
            const shippingDiscountFromResponse = ajaxData.shippingDiscountAmount ? safeParseFloat(ajaxData.shippingDiscountAmount) : null;
            const totalVATWithShipping = safeParseFloat(ajaxData.totalVatWithShipping);
            const roundedOrderTotalForDisplay = Math.floor(preciseOrderTotal);
            const roundingDifference = preciseOrderTotal - roundedOrderTotalForDisplay;

            console.log(`JS Debug: Prices from AJAX - finalShipCostNoTax=${finalShippingCostNoTax}, finalShipTax=${finalShippingTax}, preciseOrderTotal=${preciseOrderTotal}, roundedOrderTotal=${roundedOrderTotalForDisplay}, origShipCost=${originalShippingCostNoTax}, shipDiscount=${shippingDiscountFromResponse}, totalVAT=${totalVATWithShipping}, roundingDiff=${roundingDifference}`);

            if (shippingCostDisplayEl && shippingCostValueEl && shippingCostErrorEl) {
                shippingCostValueEl.textContent = formatCurrency(originalShippingCostNoTax, currentCurrencySymbol) + " (bez DPH)";
                shippingCostErrorEl.textContent = ""
            }
            if (summaryOriginalShippingCostEl) {
                summaryOriginalShippingCostEl.textContent = formatCurrency(originalShippingCostNoTax, currentCurrencySymbol)
            }

            // --- START ÚPRAVA ZOBRAZENÍ SLEVY NA DOPRAVU ---
            if (shippingDiscountRow && shippingDiscountValueEl) {
                const shouldShowDiscount = shippingDiscountFromResponse !== null && shippingDiscountFromResponse > 0;
                shippingDiscountValueEl.textContent = shouldShowDiscount ? ("- " + formatCurrency(shippingDiscountFromResponse, currentCurrencySymbol)) : "";
                // Použijeme classList.toggle pro přidání/odebrání 'd-none'
                // Druhý argument toggle: true = přidá třídu (skryje), false = odebere třídu (zobrazí)
                shippingDiscountRow.classList.toggle('d-none', !shouldShowDiscount);
                console.log(shouldShowDiscount ? "JS: Shipping discount SHOWN (class method):" : "JS: Shipping discount HIDDEN (class method):", shippingDiscountFromResponse);
            } else {
                console.warn("JS Warn: Shipping discount display elements not found.");
            }
            // --- KONEC ÚPRAVY ZOBRAZENÍ SLEVY NA DOPRAVU ---

            if (vatBreakdownShippingEl && summaryShippingTaxEl) {
                const hasShippingTax = finalShippingTax !== null && finalShippingTax > 0;
                summaryShippingTaxEl.textContent = hasShippingTax ? formatCurrency(finalShippingTax, currentCurrencySymbol) : "";
                vatBreakdownShippingEl.classList.toggle('d-none', !hasShippingTax);
            }

            if (summaryTotalVatEl) {
                summaryTotalVatEl.textContent = formatCurrency(totalVATWithShipping, currentCurrencySymbol)
            }
            if (summaryOriginalTotalPriceEl) {
                summaryOriginalTotalPriceEl.textContent = formatCurrency(preciseOrderTotal, currentCurrencySymbol, 2)
            }
            if (summaryRoundingRowEl && summaryRoundingDifferenceEl) {
                const hasRounding = roundingDifference !== null && Math.abs(roundingDifference.toFixed(2)) > 0.001;
                summaryRoundingDifferenceEl.textContent = hasRounding ? ((roundingDifference > 0 ? "- " : "+ ") + formatCurrency(Math.abs(roundingDifference), currentCurrencySymbol, 2)) : "";
                summaryRoundingRowEl.classList.toggle('d-none', !hasRounding);
            }
            if (summaryTotalPriceEl) {
                summaryTotalPriceEl.innerHTML = "";
                const spanTotalPrice = document.createElement('span');
                spanTotalPrice.textContent = formatCurrency(roundedOrderTotalForDisplay, currentCurrencySymbol, 0);
                summaryTotalPriceEl.appendChild(spanTotalPrice);
                summaryTotalPriceEl.classList.remove('text-warning', 'fw-normal', 'recalculate-shipping-notice');
                summaryTotalPriceEl.classList.add('fw-bold')
            }
            if (hiddenShippingCostNoTaxEl) {
                hiddenShippingCostNoTaxEl.value = (finalShippingCostNoTax !== null) ? finalShippingCostNoTax.toFixed(2) : ""
            }
            if (hiddenShippingTaxEl) {
                hiddenShippingTaxEl.value = (finalShippingTax !== null) ? finalShippingTax.toFixed(2) : ""
            }
            console.log("JS Info: Summary updated successfully from AJAX.")
        } else {
            console.error("JS Error: Invalid/incomplete data received from shipping API:", ajaxData);
            if (summaryOriginalShippingCostEl) summaryOriginalShippingCostEl.innerHTML = `<span class="text-danger recalculate-shipping-notice">(Chyba dat)</span>`;
            if (summaryTotalPriceEl) summaryTotalPriceEl.innerHTML = `<span class="text-warning fw-normal recalculate-shipping-notice">(Chyba dat)</span>`;
            if (summaryOriginalTotalPriceEl) summaryOriginalTotalPriceEl.textContent = "---";
            if (summaryRoundingRowEl) summaryRoundingRowEl.classList.add('d-none');
            if (vatBreakdownShippingEl) vatBreakdownShippingEl.classList.add('d-none');
            if (summaryTotalVatEl && initialTotalItemVat !== null) summaryTotalVatEl.textContent = formatCurrency(initialTotalItemVat, currentCurrencySymbol);

            if (shippingDiscountRow && shippingDiscountValueEl) {
                shippingDiscountValueEl.textContent = "";
                shippingDiscountRow.classList.add('d-none');
                console.log("JS: Invalid API data, ensuring shipping discount row is hidden using class.");
            }
        }
        updateSubmitButtonState()
    }

    function resetShippingDisplay() {
        console.log("JS: resetShippingDisplay called.");
        shippingOK = false;
        if (shippingCostDisplayEl) shippingCostDisplayEl.classList.remove('error', 'calculating');
        if (shippingCostValueEl) shippingCostValueEl.textContent = "";
        if (shippingCostErrorEl) shippingCostErrorEl.innerHTML = `<span class="text-warning recalculate-shipping-notice">(Nutno přepočítat)</span>`;
        if (summaryOriginalShippingCostEl) summaryOriginalShippingCostEl.innerHTML = `<span class="text-warning recalculate-shipping-notice">(Nutno přepočítat)</span>`;
        if (summaryShippingTaxEl) summaryShippingTaxEl.textContent = "---";

        if (shippingDiscountRow && shippingDiscountValueEl) {
            shippingDiscountValueEl.textContent = "";
            shippingDiscountRow.classList.add('d-none'); // Použití třídy pro skrytí
            console.log("JS: Resetting shipping display, ensuring shipping discount row is hidden using class.");
        }

        if (vatBreakdownShippingEl) vatBreakdownShippingEl.classList.add('d-none');
        const currentCurrencySymbol = checkoutForm.dataset.currencySymbol || "Kč";
        const currentInitialTotalItemVat = safeParseFloat(checkoutForm.dataset.initialTotalItemVat || "0");
        if (summaryTotalVatEl && currentInitialTotalItemVat !== null) summaryTotalVatEl.textContent = formatCurrency(currentInitialTotalItemVat, currentCurrencySymbol);
        if (summaryOriginalTotalPriceEl) summaryOriginalTotalPriceEl.textContent = "---";
        if (summaryRoundingRowEl) summaryRoundingRowEl.classList.add('d-none');
        if (summaryTotalPriceEl) summaryTotalPriceEl.innerHTML = `<span class="text-warning fw-normal recalculate-shipping-notice">(Nutno přepočítat dopravu)</span>`;
        if (hiddenShippingCostNoTaxEl) hiddenShippingCostNoTaxEl.value = "";
        if (hiddenShippingTaxEl) hiddenShippingTaxEl.value = "";
        updateSubmitButtonState()
    }

    function toggleDeliveryFields(checkbox, deliveryFieldsContainer) {
        console.log("JS: toggleDeliveryFields. Checked:", checkbox?.checked);
        if (checkbox && deliveryFieldsContainer) {
            deliveryFieldsContainer.classList.toggle("hidden", checkbox.checked);
            resetShippingDisplay()
        } else {
            console.warn("JS Warn: toggleDeliveryFields - elements (#useInvoiceAddressAsDelivery or .delivery-address-fields) not found.")
        }
    }

    function updateCombinedPhone(prefixEl, numberPartEl, hiddenEl, isInitialCall = false) {
        if (!prefixEl || !numberPartEl || !hiddenEl) {
            console.warn("JS Warn: Missing elements for updating combined phone.");
            return
        }
        const prefix = prefixEl.value;
        const numberPart = numberPartEl.value;
        const cleanedNumberPart = numberPart.replace(/\D/g, '');
        const feedbackEl = numberPartEl.nextElementSibling;
        if (cleanedNumberPart.length > 0 || !isInitialCall) {
            hiddenEl.value = prefix + cleanedNumberPart;
            console.log(`JS: Updated hidden input #${hiddenEl.id} to: ${prefix+cleanedNumberPart}`)
        } else if (isInitialCall && hiddenEl.value && hiddenEl.value.startsWith(prefix)) {
            console.log(`JS: Initial call and number part is empty. Keeping hidden input #${hiddenEl.id} value from DTO: ${hiddenEl.value}`)
        } else if (isInitialCall) {
            hiddenEl.value = prefix;
            console.log(`JS: Initial call, number empty, prefix mismatch or hiddenEl empty. Setting hidden input #${hiddenEl.id} to prefix: ${prefix}`)
        }
        if (!isInitialCall) {
            numberPartEl.classList.remove("is-invalid");
            if (feedbackEl && feedbackEl.classList.contains("invalid-feedback")) {
                feedbackEl.textContent = ""
            }
        }
        if (!isInitialCall && numberPart.trim().length > 0) {
            let minLen = 9;
            let maxLen = 9;
            if (cleanedNumberPart.length > 0 && (cleanedNumberPart.length < minLen || cleanedNumberPart.length > maxLen)) {
                numberPartEl.classList.add("is-invalid");
                if (feedbackEl && feedbackEl.classList.contains("invalid-feedback")) {
                    feedbackEl.textContent = `Zadejte prosím platné ${minLen}místné číslo.`
                }
            }
        }
    }

    if (calculateShippingBtn) {
        calculateShippingBtn.addEventListener("click", function (event) {
            event.preventDefault();
            console.log("JS: Calculate Shipping Button clicked.");
            const useInvoiceAddr = useInvoiceAddressCheckbox ? useInvoiceAddressCheckbox.checked : true;
            const addressData = {};
            if (useInvoiceAddr) {
                addressData.street = document.getElementById("invoiceStreet")?.value || "";
                addressData.city = document.getElementById("invoiceCity")?.value || "";
                addressData.zipCode = document.getElementById("invoiceZipCode")?.value || "";
                addressData.country = document.getElementById("invoiceCountry")?.value || ""
            } else {
                addressData.street = document.getElementById("deliveryStreet")?.value || "";
                addressData.city = document.getElementById("deliveryCity")?.value || "";
                addressData.zipCode = document.getElementById("deliveryZipCode")?.value || "";
                addressData.country = document.getElementById("deliveryCountry")?.value || ""
            }
            if (!addressData.street || !addressData.city || !addressData.zipCode || !addressData.country) {
                console.warn("JS: Address data incomplete for shipping calculation.");
                updateSummary({
                    errorMessage: "Prosím, vyplňte kompletní " + (useInvoiceAddr ? "fakturační" : "dodací") + " adresu."
                });
                updateSubmitButtonState();
                return
            }
            console.log("JS: Sending shipping calculation request with address:", addressData);
            const spinner = calculateShippingBtn.querySelector(".spinner-border");
            calculateShippingBtn.disabled = true;
            if (spinner) spinner.classList.remove("d-none");
            if (shippingCostDisplayEl) shippingCostDisplayEl.classList.add('calculating');
            if (shippingCostErrorEl) shippingCostErrorEl.textContent = "(Počítám...)";

            const currentCsrfToken = checkoutForm.dataset.csrfToken;
            const currentCsrfHeaderName = checkoutForm.dataset.csrfHeaderName;

            fetch(calculateShippingUrl, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Accept": "application/json",
                    ...(currentCsrfHeaderName && currentCsrfToken && {
                        [currentCsrfHeaderName]: currentCsrfToken
                    }),
                },
                body: JSON.stringify(addressData),
            }).then(response => {
                if (!response.ok) {
                    return response.json().then(errData => {
                        throw new Error(errData.errorMessage || `Chyba serveru: ${response.status}`)
                    }).catch(() => {
                        throw new Error(`Chyba serveru: ${response.status}`)
                    })
                }
                return response.json()
            }).then(data => {
                updateSummary(data)
            }).catch(error => {
                console.error("JS Error: Shipping calculation failed:", error);
                updateSummary({
                    errorMessage: error.message || "Chyba výpočtu dopravy."
                })
            }).finally(() => {
                calculateShippingBtn.disabled = false;
                if (spinner) spinner.classList.add("d-none");
                if (shippingCostDisplayEl) shippingCostDisplayEl.classList.remove('calculating');
                console.log("JS: Shipping calculation AJAX finished.")
            })
        });
        console.log("JS: Event listener for shipping calculation button attached.")
    } else {
        console.warn("JS Warn: Calculate shipping button #calculate-shipping-btn not found!")
    }

    if (addressTriggerElements.length > 0) {
        addressTriggerElements.forEach(el => {
            el.addEventListener("input", resetShippingDisplay);
            el.addEventListener("change", resetShippingDisplay)
        });
        console.log("JS: Event listeners attached to address trigger elements.")
    } else {
        console.warn("JS Warn: No address trigger elements found. Add class 'address-trigger' to address inputs.")
    }

    if (useInvoiceAddressCheckbox) {
        useInvoiceAddressCheckbox.addEventListener("change", function () {
            toggleDeliveryFields(this, deliveryAddressFieldsContainer)
        });
        toggleDeliveryFields(useInvoiceAddressCheckbox, deliveryAddressFieldsContainer);
        console.log("JS: Event listener for delivery address toggle attached.")
    } else {
        console.warn("JS Warn: 'Use invoice address' checkbox #useInvoiceAddressAsDelivery not found.")
    }

    if (phonePrefixEl && phoneNumberPartEl && hiddenPhoneEl) {
        phonePrefixEl.addEventListener("change", () => updateCombinedPhone(phonePrefixEl, phoneNumberPartEl, hiddenPhoneEl, false));
        phoneNumberPartEl.addEventListener("input", () => updateCombinedPhone(phonePrefixEl, phoneNumberPartEl, hiddenPhoneEl, false));
        updateCombinedPhone(phonePrefixEl, phoneNumberPartEl, hiddenPhoneEl, true);
        console.log("JS: Listeners and initial setup for main phone attached.")
    } else {
        console.warn("JS Warn: Elements for main phone listener missing.")
    }

    if (deliveryPhonePrefixEl && deliveryPhoneNumberPartEl && hiddenDeliveryPhoneEl) {
        deliveryPhonePrefixEl.addEventListener("change", () => updateCombinedPhone(deliveryPhonePrefixEl, deliveryPhoneNumberPartEl, hiddenDeliveryPhoneEl, false));
        deliveryPhoneNumberPartEl.addEventListener("input", () => updateCombinedPhone(deliveryPhonePrefixEl, deliveryPhoneNumberPartEl, hiddenDeliveryPhoneEl, false));
        updateCombinedPhone(deliveryPhonePrefixEl, deliveryPhoneNumberPartEl, hiddenDeliveryPhoneEl, true);
        console.log("JS: Listeners and initial setup for delivery phone attached.")
    } else {
        console.warn("JS Warn: Elements for delivery phone listener missing.")
    }

    const generalErrorsSummary = document.getElementById("form-validation-errors-summary");
    const hasValidationErrors = document.body.querySelector(".is-invalid") !== null;
    if (generalErrorsSummary && hasValidationErrors) {
        const firstInvalidField = checkoutForm.querySelector(".is-invalid");
        if (firstInvalidField) {
            console.log("JS: Validation errors detected, scrolling to:", firstInvalidField);
            firstInvalidField.scrollIntoView({
                behavior: "smooth",
                block: "center"
            });
            setTimeout(() => {
                try {
                    if (typeof firstInvalidField.focus === 'function') {
                        firstInvalidField.focus({
                            preventScroll: true
                        });
                        console.log("JS: Focused first invalid field.")
                    } else {
                        console.log("JS: First invalid element not focusable.")
                    }
                } catch (focusError) {
                    console.error("Error focusing element:", focusError)
                }
            }, 300)
        } else {
            console.log("JS: General validation error, but no .is-invalid field found.")
        }
    } else {
        console.log("JS: No validation errors on load.")
    }

    console.log("JS: Setting initial button state.");
    updateSubmitButtonState();
    if (!shippingOK && !initialCartEmpty) {
        console.log("JS Info: Initial shipping invalid & cart not empty -> resetting display.");
        resetShippingDisplay()
    } else {
        console.log("JS Info: Initial shipping OK or cart empty.")
    }
    console.log("JS: Checkout page initialization complete (v28 - Shipping Discount Class Toggle Final).")
});