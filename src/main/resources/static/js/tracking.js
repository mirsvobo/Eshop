// Soubor: /js/tracking.js
// Verze v5 - Vylepšené logování a kontroly

class TrackingService {
    constructor(options = {}) {
        this.logPrefix = '[TrackingService V5]';
        this.debugMode = options.debugMode || true; // true pro detailní logy
        this.sessionTrackedEvents = new Set();

        this._googleAdsId = options.googleAdsId || 'AW-17046857865';
        this._adsConversionLabelViewItem = options.adsConversionLabelViewItem || '5icqCLa-n8EaEInRycA_';
        this._adsConversionLabelAddToCart = options.adsConversionLabelAddToCart || 'KT_JCK2-n8EaEInRycA_';
        this._adsConversionLabelBeginCheckout = options.adsConversionLabelBeginCheckout || 'YVBgCLC-n8EaEInRycA_';
        this._adsConversionLabelPurchase = options.adsConversionLabelPurchase || 'I-9yCKq-n8EaEInRycA_';
        this._adsConversionLabelContact = options.adsConversionLabelContact || 'QfVhCLO-n8EaEInRycA_';

        window.dataLayer = window.dataLayer || [];

        if (this.debugMode) {
            console.log(`${this.logPrefix} INSTANCE CREATED. Debug mode ENABLED. Options:`, JSON.parse(JSON.stringify(options)));
        } else {
            console.log(`${this.logPrefix} INSTANCE CREATED. Debug mode DISABLED.`);
        }
    }

    _isConsentGivenFor(type) {
        // Prioritizujeme debugMode z instance, pokud je nastaven
        const currentDebugMode = typeof this.debugMode === 'boolean' ? this.debugMode : !!(window.trackingService && window.trackingService.debugMode);

        if (typeof window.CookieConsent === 'undefined' || typeof window.CookieConsent.getUserPreferences !== 'function') {
            if (currentDebugMode) console.warn(`${this.logPrefix} _isConsentGivenFor - CookieConsent utility not available. Assuming NO consent for '${type}'.`);
            return false;
        }
        const preferences = window.CookieConsent.getUserPreferences();
        if (currentDebugMode) console.log(`${this.logPrefix} _isConsentGivenFor - User Preferences:`, JSON.stringify(preferences));

        if (preferences && preferences.acceptedCategories && Array.isArray(preferences.acceptedCategories)) {
            const given = preferences.acceptedCategories.includes(type);
            if (currentDebugMode) console.log(`${this.logPrefix} Consent check for '${type}': ${given}`);
            return given;
        }
        if (currentDebugMode) console.log(`${this.logPrefix} Consent check for '${type}': No acceptedCategories array found. Defaulting to false.`);
        return false;
    }

    _isEventTracked(eventKey) {
        const tracked = this.sessionTrackedEvents.has(eventKey);
        if (tracked && this.debugMode) {
            console.log(`${this.logPrefix} Event '${eventKey}' already tracked in this session.`);
        }
        return tracked;
    }

    _markEventAsTracked(eventKey) {
        this.sessionTrackedEvents.add(eventKey);
        if (this.debugMode) {
            console.log(`${this.logPrefix} Marking event '${eventKey}' as tracked.`);
        }
    }

    _pushToDataLayer(payload) {
        try {
            window.dataLayer.push({ ...payload }); // Posíláme kopii, abychom předešli nechtěným modifikacím
            if (this.debugMode) console.log(`${this.logPrefix} PUSHED TO DATALAYER:`, JSON.parse(JSON.stringify(payload)));
        } catch (error) {
            console.error(`${this.logPrefix} Error pushing to dataLayer:`, error, 'Payload:', JSON.parse(JSON.stringify(payload)));
        }
    }

    _addGoogleAdsData(payload, conversionLabel) {
        if (this._isConsentGivenFor('marketing') && this._googleAdsId && conversionLabel) {
            payload.event_callback = function() { // Pro Google Ads je lepší použít event_callback
                if (typeof gtag === 'function') {
                    gtag('event', 'conversion', {
                        'send_to': `${this._googleAdsId}/${conversionLabel}`,
                        'value': payload.ecommerce ? (payload.ecommerce.value || 0.0) : (payload.value || 0.0),
                        'currency': payload.ecommerce ? (payload.ecommerce.currency || 'CZK') : (payload.currency || 'CZK'),
                        'transaction_id': payload.ecommerce ? payload.ecommerce.transaction_id : undefined
                    });
                    if (this.debugMode) console.log(`${this.logPrefix} Google Ads gtag conversion sent for label: ${conversionLabel}`);
                } else {
                    if (this.debugMode) console.warn(`${this.logPrefix} gtag function not found for Google Ads conversion callback.`);
                }
            }.bind(this); // Nutné pro správný 'this' context
            if (this.debugMode) console.log(`${this.logPrefix} Added Google Ads data (Label: ${conversionLabel}) using event_callback.`);
        } else {
            if (this.debugMode) console.log(`${this.logPrefix} Google Ads data skipped (no consent/ID/label: ${conversionLabel}).`);
        }
    }

    _prepareEcommerceData(currency, value, items = [], couponCode = null) {
        const ecommerceData = {
            currency: (currency || 'CZK').toUpperCase(),
            value: value != null ? parseFloat(parseFloat(value).toFixed(2)) : 0.00,
            items: items.map(item => ({
                item_id: item.item_id || `product_${item.productId || item.id || 'unknown'}`,
                item_name: item.item_name || 'Neznámý produkt',
                item_brand: item.item_brand || 'Dřevníky Kolář',
                item_category: item.item_category || 'Nezarazeno', // Může být 'Dřevníky' nebo 'Dřevníky Na Míru'
                item_variant: item.item_variant || undefined, // Pokud máte varianty
                price: item.price != null ? parseFloat(parseFloat(item.price).toFixed(2)) : 0.00,
                quantity: parseInt(item.quantity) || 1
            }))
        };
        if (couponCode) {
            ecommerceData.coupon = couponCode;
        }
        return ecommerceData;
    }

    trackViewItem(viewItemData) {
        const logPrefix = `${this.logPrefix}[view_item]:`;
        if (this.debugMode) console.log(`${logPrefix} CALLED. Consent analytics: ${this._isConsentGivenFor('analytics')}, marketing: ${this._isConsentGivenFor('marketing')}`);

        if (!this._isConsentGivenFor('analytics') && !this._isConsentGivenFor('marketing')) {
            if (this.debugMode) console.log(`${logPrefix} Analytics & Marketing consent not given. Skipping.`);
            return;
        }
        if (!viewItemData || !viewItemData.item_id) { // Používáme item_id dle GA4 schématu
            console.warn(`${logPrefix} Insufficient data for view_item. 'item_id' is required. Data:`, viewItemData);
            return;
        }

        const eventKey = `view_item_${viewItemData.item_id}`;
        if (this._isEventTracked(eventKey)) {
            if (this.debugMode) console.log(`${logPrefix} Event '${eventKey}' already tracked. Skipping.`);
            return;
        }

        if (this.debugMode) console.log(`${logPrefix} Processing data:`, viewItemData);

        const ecommerceData = this._prepareEcommerceData(
            viewItemData.currency,
            viewItemData.price,
            [viewItemData]
        );

        const dataLayerPayload = {
            'event': 'view_item',
            'ecommerce': ecommerceData
        };

        this._addGoogleAdsData(dataLayerPayload, this._adsConversionLabelViewItem);
        this._markEventAsTracked(eventKey);
        this._pushToDataLayer(dataLayerPayload);
        console.log(`${logPrefix} FINISHED for item ${viewItemData.item_id}.`);
    }

    trackAddToCart(addToCartData) {
        const logPrefix = `${this.logPrefix}[add_to_cart]:`;
        if (this.debugMode) console.log(`${logPrefix} CALLED. Consent analytics: ${this._isConsentGivenFor('analytics')}, marketing: ${this._isConsentGivenFor('marketing')}`);

        if (!this._isConsentGivenFor('analytics') && !this._isConsentGivenFor('marketing')) {
            if (this.debugMode) console.log(`${logPrefix} Analytics & Marketing consent not given. Skipping.`);
            return;
        }
        if (!addToCartData || !addToCartData.item_id || !addToCartData.quantity) {
            console.warn(`${logPrefix} Insufficient data for add_to_cart. 'item_id' and 'quantity' are required. Data:`, addToCartData);
            return;
        }

        if (this.debugMode) console.log(`${logPrefix} Processing data:`, addToCartData);

        const itemPrice = parseFloat(addToCartData.price || 0);
        const itemQuantity = parseInt(addToCartData.quantity || 1);

        const ecommerceData = this._prepareEcommerceData(
            addToCartData.currency,
            itemPrice * itemQuantity,
            [addToCartData]
        );

        const dataLayerPayload = {
            'event': 'add_to_cart',
            'ecommerce': ecommerceData
        };
        this._addGoogleAdsData(dataLayerPayload, this._adsConversionLabelAddToCart);
        this._pushToDataLayer(dataLayerPayload);
        console.log(`${logPrefix} FINISHED for item ${addToCartData.item_id}.`);
    }

    trackBeginCheckout(checkoutData) {
        const logPrefix = `${this.logPrefix}[begin_checkout]:`;
        if (this.debugMode) console.log(`${logPrefix} CALLED. Consent analytics: ${this._isConsentGivenFor('analytics')}, marketing: ${this._isConsentGivenFor('marketing')}`);

        if (!this._isConsentGivenFor('analytics') && !this._isConsentGivenFor('marketing')) {
            if (this.debugMode) console.log(`${logPrefix} Analytics & Marketing consent not given. Skipping.`);
            return;
        }
        if (!checkoutData || !checkoutData.items || checkoutData.items.length === 0) {
            console.warn(`${logPrefix} Insufficient data for begin_checkout. 'items' array is required. Data:`, checkoutData);
            return;
        }

        const eventKey = `begin_checkout_session`;
        if (this._isEventTracked(eventKey)) {
            if (this.debugMode) console.log(`${logPrefix} Event '${eventKey}' already tracked. Skipping.`);
            return;
        }

        if (this.debugMode) console.log(`${logPrefix} Processing data:`, checkoutData);

        const ecommerceData = this._prepareEcommerceData(
            checkoutData.currency,
            checkoutData.value,
            checkoutData.items,
            checkoutData.coupon
        );

        const dataLayerPayload = {
            'event': 'begin_checkout',
            'ecommerce': ecommerceData
        };
        this._addGoogleAdsData(dataLayerPayload, this._adsConversionLabelBeginCheckout);
        this._markEventAsTracked(eventKey);
        this._pushToDataLayer(dataLayerPayload);
        console.log(`${logPrefix} FINISHED.`);
    }

    trackPurchase(purchaseData) {
        const logPrefix = `${this.logPrefix}[purchase]:`;
        if (this.debugMode) console.log(`${logPrefix} CALLED. Consent analytics: ${this._isConsentGivenFor('analytics')}, marketing: ${this._isConsentGivenFor('marketing')}`);

        if (!this._isConsentGivenFor('analytics') && !this._isConsentGivenFor('marketing')) {
            if (this.debugMode) console.log(`${logPrefix} Analytics & Marketing consent not given. Skipping.`);
            return;
        }
        if (!purchaseData || !purchaseData.transaction_id) {
            console.warn(`${logPrefix} Insufficient data for purchase. 'transaction_id' is required. Data:`, purchaseData);
            return;
        }

        const eventKey = `purchase_${purchaseData.transaction_id}`;
        if (this._isEventTracked(eventKey)) {
            if (this.debugMode) console.log(`${logPrefix} Event '${eventKey}' already tracked. Skipping.`);
            return;
        }

        if (this.debugMode) console.log(`${logPrefix} Processing ecommerce data:`, purchaseData);

        const dataLayerPayload = {
            'event': 'purchase',
            'ecommerce': purchaseData // Předpokládáme, že purchaseData je již celý ecommerce objekt
        };

        this._addGoogleAdsData(dataLayerPayload, this._adsConversionLabelPurchase);
        this._markEventAsTracked(eventKey);
        this._pushToDataLayer(dataLayerPayload);
        console.log(`${logPrefix} FINISHED for TX ID ${purchaseData.transaction_id}.`);
    }

    trackContact(contactMethod, contactValue = '') {
        const logPrefix = `${this.logPrefix}[contact_click]:`;
        if (this.debugMode) console.log(`${logPrefix} CALLED. Consent marketing: ${this._isConsentGivenFor('marketing')}`);

        if (!this._isConsentGivenFor('marketing')) {
            if (this.debugMode) console.log(`${logPrefix} Marketing consent not given. Skipping.`);
            return;
        }
        if (!contactMethod) {
            console.warn(`${logPrefix} Contact method is required.`);
            return;
        }

        if (this.debugMode) console.log(`${logPrefix} Tracking contact method: ${contactMethod}, Value: ${contactValue}`);

        const dataLayerPayload = {
            'event': 'contact_click',
            'contact_method': contactMethod,
            'contact_value': contactValue,
        };
        this._addGoogleAdsData(dataLayerPayload, this._adsConversionLabelContact);
        this._pushToDataLayer(dataLayerPayload);
        console.log(`${logPrefix} FINISHED.`);
    }

    initBaseScripts() {
        const logPrefix = `${this.logPrefix}[initBaseScripts]:`;
        if (this.debugMode) console.log(`${logPrefix} Base scripts initialization attempt (no specific scripts in this version).`);
    }
}
// Konec souboru tracking.js