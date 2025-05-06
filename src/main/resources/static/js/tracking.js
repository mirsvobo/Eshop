/**
 * TrackingService Object v16_GTM_With_Labels
 * Handles sending e-commerce events via dataLayer for GTM.
 * Includes specific IDs/labels provided by the user.
 */
const TrackingService = {
    _gtmInitialized: false, // Flag to check if dataLayer is ready
    _pendingViewItemData: null,
    _pendingBeginCheckoutData: null,
    _pendingPurchaseData: null,
    _trackedEvents: {}, // Object to store keys of already tracked events on this page load

    // --- Configuration (Provided by User) ---
    _googleAdsId: '17046857865', // Google Ads Conversion ID (same for all)
    _adsConversionLabelViewItem: '5icqCLa-n8EaEInRycA_',
    _adsConversionLabelAddToCart: 'KT_JCK2-n8EaEInRycA_',
    _adsConversionLabelBeginCheckout: 'YVBgCLC-n8EaEInRycA_',
    _adsConversionLabelPurchase: 'I-9yCKq-n8EaEInRycA_',
    _adsConversionLabelContact: 'QfVhCLO-n8EaEInRycA_',

    _sklikId: 100245398,          // Sklik - Retargeting ID
    _sklikPurchaseId: 100245399,    // Sklik - Conversion ID - Purchase
    _sklikBeginCheckoutId: 100245400, // Sklik - Conversion ID - Begin Checkout

    _heurekaApiKey: '337b0666ea50d1f4fcd05d5473cfef9e43ec', // Heureka Ověřeno zákazníky API Key

    /**
     * Ensures the dataLayer array exists.
     * @returns {boolean} True if dataLayer is available.
     */
    _ensureDataLayer: function() {
        window.dataLayer = window.dataLayer || [];
        this._gtmInitialized = true; // Assume dataLayer exists => GTM can read it
        return true;
    },

    /**
     * Creates a unique key for an event to prevent duplicates during a single page lifecycle.
     * @param {string} eventName - The name of the event (e.g., 'view_item').
     * @param {string|null} contextId - A specific identifier (e.g., product ID, transaction ID) or null for generic page events.
     * @returns {string} The unique event key.
     */
    _getEventKey: function(eventName, contextId) {
        if (!contextId) {
            // For events like begin_checkout, use pathname as context to avoid multiple sends on the same page
            return `${eventName}_generic_${window.location.pathname}`;
        }
        // For item-specific or transaction-specific events
        return `${eventName}_${contextId}`;
    },

    /**
     * Called after consent changes or DOM load to process any pending tracking data.
     * Pushes data to dataLayer if it's ready.
     */
    initBaseScripts: function() {
        const logPrefix = "[TrackingService.initBaseScripts]";
        console.log(`${logPrefix} Initializing...`);
        this._ensureDataLayer(); // Ensure dataLayer exists

        // Process pending view_item
        if (this._pendingViewItemData && this._gtmInitialized) {
            console.log(`${logPrefix} Processing pending view_item data...`);
            const itemId = this._pendingViewItemData?.ecommerce?.items?.[0]?.item_id;
            const eventKey = this._getEventKey("view_item", itemId);
            if (!this._trackedEvents[eventKey]) {
                this._trackedEvents[eventKey] = true;
                window.dataLayer.push(this._pendingViewItemData);
                console.log(`${logPrefix} Pushed pending view_item to dataLayer. Key: '${eventKey}'`);
            } else { console.warn(`${logPrefix} Pending view_item event (Key: '${eventKey}') already tracked.`); }
            this._pendingViewItemData = null;
        }

        // Process pending begin_checkout
        if (this._pendingBeginCheckoutData && this._gtmInitialized) {
            console.log(`${logPrefix} Processing pending begin_checkout data...`);
            const eventKey = this._getEventKey("begin_checkout", null);
            if (!this._trackedEvents[eventKey]) {
                this._trackedEvents[eventKey] = true;
                window.dataLayer.push(this._pendingBeginCheckoutData);
                console.log(`${logPrefix} Pushed pending begin_checkout to dataLayer. Key: '${eventKey}'`);
                // Pass data also to Sklik specific dataLayer variable (if needed by GTM tag)
                window.dataLayer.push({
                    'event': 'sklik_begin_checkout', // Custom event for Sklik tag trigger
                    'sklik_conversion_id': this._sklikBeginCheckoutId,
                    'sklik_order_id': null, // No order ID at this stage
                    'sklik_value': this._pendingBeginCheckoutData.ecommerce.value // Pass value without VAT
                });
                console.log(`${logPrefix} Pushed sklik_begin_checkout event.`);
                this._pendingBeginCheckoutData = null;
            } else { console.warn(`${logPrefix} Pending begin_checkout event (Key: '${eventKey}') already tracked.`); }
            this._pendingBeginCheckoutData = null;
        }

        // Process pending purchase
        if (this._pendingPurchaseData && this._gtmInitialized) {
            console.log(`${logPrefix} Processing pending purchase data...`);
            const eventKey = this._getEventKey("purchase", this._pendingPurchaseData?.ecommerce?.transaction_id);
            if (!this._trackedEvents[eventKey]) {
                this._trackedEvents[eventKey] = true;
                window.dataLayer.push(this._pendingPurchaseData); // GA4 purchase
                console.log(`${logPrefix} Pushed pending purchase (GA4) to dataLayer. Key: '${eventKey}'`);

                // Push data for Google Ads Purchase (redundant if using GA4 import, but safe)
                window.dataLayer.push({
                    'event': 'google_ads_purchase', // Custom event for Ads tag trigger
                    'google_ads_id': this._googleAdsId,
                    'google_ads_label': this._adsConversionLabelPurchase,
                    'value': this._pendingPurchaseData.ecommerce.value_no_vat, // Value WITHOUT VAT for Ads
                    'currency': this._pendingPurchaseData.ecommerce.currency,
                    'transaction_id': this._pendingPurchaseData.ecommerce.transaction_id
                });
                console.log(`${logPrefix} Pushed google_ads_purchase event.`);

                // Push data for Sklik Purchase
                window.dataLayer.push({
                    'event': 'sklik_purchase', // Custom event for Sklik tag trigger
                    'sklik_conversion_id': this._sklikPurchaseId,
                    'sklik_order_id': this._pendingPurchaseData.ecommerce.transaction_id,
                    'sklik_value': this._pendingPurchaseData.ecommerce.value_no_vat // Value WITHOUT VAT for Sklik
                });
                console.log(`${logPrefix} Pushed sklik_purchase event.`);

                // Push data for Heureka Ověřeno
                window.dataLayer.push({
                    'event': 'heureka_purchase', // Custom event for Heureka tag trigger
                    'heureka_api_key': this._heurekaApiKey,
                    'heureka_email': this._pendingPurchaseData.ecommerce.customer_email, // Pass email
                    'heureka_order_id': this._pendingPurchaseData.ecommerce.transaction_id,
                    'heureka_items': this._pendingPurchaseData.ecommerce.heureka_items // Pass Heureka specific items
                });
                console.log(`${logPrefix} Pushed heureka_purchase event.`);

                this._pendingPurchaseData = null;
            } else { console.warn(`${logPrefix} Pending purchase event (Key: '${eventKey}') already tracked.`); }
            this._pendingPurchaseData = null;
        }
        console.log(`${logPrefix} Initialization finished.`);
    },

    /**
     * Tracks a product view event. Pushes 'view_item' to dataLayer.
     */
    trackViewItem: function(itemData) {
        const eventName = "view_item";
        const logPrefix = `[${eventName}]`;
        console.log(`${logPrefix} Received data:`, JSON.stringify(itemData));
        if (!itemData || !itemData.variantId || !itemData.name || itemData.priceNoVat == null) {
            console.warn(`${logPrefix} Missing required item data. Skipping.`); return;
        }
        const eventKey = this._getEventKey(eventName, itemData.variantId);
        if (this._trackedEvents[eventKey]) {
            console.warn(`${logPrefix} Event already tracked. Key: '${eventKey}'. Skipping.`); return;
        }

        const price = parseFloat(itemData.priceNoVat) || 0;
        const currency = itemData.currency || 'CZK';

        const dataLayerPayload = {
            event: eventName, // Standard GA4 event name
            ecommerce: {
                currency: currency,
                value: price.toFixed(2), // Value of the item viewed (without VAT)
                items: [{
                    item_id: itemData.variantId,
                    item_name: itemData.name,
                    item_brand: itemData.brand || 'Dřevníky Kolář',
                    item_category: itemData.category || 'Dřevníky',
                    price: price.toFixed(2), // Price without VAT
                    quantity: 1
                }]
            },
            // Optional: Add specific data for Google Ads tag if needed, though value/currency should suffice
            'google_ads_id': this._googleAdsId,
            'google_ads_label': this._adsConversionLabelViewItem
            // 'google_ads_value': price.toFixed(2), // Redundant if value is set in ecommerce
            // 'google_ads_currency': currency     // Redundant if currency is set in ecommerce
        };

        if (!this._ensureDataLayer()) {
            console.warn(`${logPrefix} DataLayer not ready. Storing view_item data.`);
            this._pendingViewItemData = dataLayerPayload;
            return;
        }

        this._trackedEvents[eventKey] = true;
        console.log(`${logPrefix} Marking event key '${eventKey}' as tracked.`);
        window.dataLayer.push(dataLayerPayload);
        console.log(`${logPrefix} Pushed to dataLayer:`, dataLayerPayload);
        console.log(`${logPrefix} Finished processing.`);
    },

    /**
     * Tracks adding an item to the cart. Pushes 'add_to_cart' to dataLayer.
     */
    trackAddToCart: function(itemData) {
        const eventName = "add_to_cart";
        const logPrefix = `[${eventName}]`;
        console.log(`${logPrefix} Received data:`, JSON.stringify(itemData));
        if (!itemData || !itemData.variantId || !itemData.name || itemData.price == null) {
            console.warn(`${logPrefix} Missing required item data. Skipping.`); return;
        }
        const quantity = parseInt(itemData.quantity) || 1;
        if (quantity <= 0) {
            console.warn(`${logPrefix} Invalid quantity (${quantity}). Skipping.`); return;
        }

        const eventKey = this._getEventKey(eventName, itemData.variantId + '_ts' + Date.now());

        const unitPriceNoVat = parseFloat(itemData.price) || 0;
        const itemValue = (unitPriceNoVat * quantity);
        const currency = itemData.currency || 'CZK';

        const dataLayerPayload = {
            event: eventName, // Standard GA4 event name
            ecommerce: {
                currency: currency,
                value: itemValue.toFixed(2), // Total value of items added (without VAT)
                items: [{
                    item_id: itemData.variantId,
                    item_name: itemData.name,
                    item_brand: itemData.brand || 'Dřevníky Kolář',
                    item_category: itemData.category || 'Dřevníky',
                    price: unitPriceNoVat.toFixed(2), // Unit price without VAT
                    quantity: quantity
                }]
            },
            // Optional: Add specific data for Google Ads tag
            'google_ads_id': this._googleAdsId,
            'google_ads_label': this._adsConversionLabelAddToCart
            // 'google_ads_value': itemValue.toFixed(2), // Redundant
            // 'google_ads_currency': currency         // Redundant
        };

        if (!this._ensureDataLayer()) {
            console.warn(`${logPrefix} DataLayer not ready. Cannot track add_to_cart.`);
            return;
        }

        this._trackedEvents[eventKey] = true; // Mark this specific instance
        console.log(`${logPrefix} Marking event key '${eventKey}' as tracked.`);
        window.dataLayer.push(dataLayerPayload);
        console.log(`${logPrefix} Pushed to dataLayer:`, dataLayerPayload);
        console.log(`${logPrefix} Finished processing.`);
    },

    /**
     * Tracks the start of the checkout process. Pushes 'begin_checkout' to dataLayer.
     */
    trackBeginCheckout: function(checkoutData) {
        const eventName = "begin_checkout";
        const logPrefix = `[${eventName}]`;
        console.log(`${logPrefix} Received data:`, JSON.stringify(checkoutData));
        if (!checkoutData || !Array.isArray(checkoutData.items) || checkoutData.items.length === 0 || checkoutData.value_no_vat == null) {
            console.warn(`${logPrefix} Missing or invalid checkout data. Skipping.`); return;
        }

        const eventKey = this._getEventKey(eventName, null);
        if (this._trackedEvents[eventKey]) {
            console.warn(`${logPrefix} Event already tracked this page load. Key: '${eventKey}'. Skipping.`); return;
        }

        const visitedFlag = 'checkout_visited';
        try {
            if (sessionStorage.getItem(visitedFlag)) {
                console.log(`${logPrefix} Checkout already tracked in this session (sessionStorage). Skipping.`);
                return;
            }
        } catch (e) { console.warn(`${logPrefix} sessionStorage access error:`, e); }

        const totalValueNoVat = parseFloat(checkoutData.value_no_vat) || 0;
        const currency = checkoutData.currency || 'CZK';
        const mappedItems = checkoutData.items.map(item => ({
            item_id: item.variantId,
            item_name: item.name,
            item_brand: item.brand || 'Dřevníky Kolář',
            item_category: item.category || 'Dřevníky',
            price: parseFloat(item.price).toFixed(2) || '0.00',
            quantity: parseInt(item.quantity) || 1
        }));

        const dataLayerPayload = {
            event: eventName, // Standard GA4 event name
            ecommerce: {
                currency: currency,
                value: totalValueNoVat.toFixed(2), // Total value of items without VAT
                items: mappedItems
                // 'coupon': checkoutData.coupon || undefined
            },
            // Optional: Add specific data for Google Ads & Sklik if needed directly
            'google_ads_id': this._googleAdsId,
            'google_ads_label': this._adsConversionLabelBeginCheckout,
            // 'google_ads_value': totalValueNoVat.toFixed(2), // Redundant
            // 'google_ads_currency': currency,                 // Redundant
            'sklik_conversion_id': this._sklikBeginCheckoutId // ID for Sklik Begin Checkout
            // 'sklik_value': totalValueNoVat.toFixed(2)       // Redundant if using ecommerce.value in GTM
        };

        if (!this._ensureDataLayer()) {
            console.warn(`${logPrefix} DataLayer not ready. Storing begin_checkout data.`);
            this._pendingBeginCheckoutData = dataLayerPayload;
            return;
        }

        this._trackedEvents[eventKey] = true;
        try { sessionStorage.setItem(visitedFlag, 'true'); } catch (e) { console.error(`${logPrefix} sessionStorage setItem error:`, e); }
        console.log(`${logPrefix} Marking event key '${eventKey}' as tracked.`);
        window.dataLayer.push(dataLayerPayload);
        console.log(`${logPrefix} Pushed to dataLayer:`, dataLayerPayload);
        console.log(`${logPrefix} Finished processing.`);
    },

    /**
     * Tracks a completed purchase. Pushes 'purchase' to dataLayer and specific events for Ads/Sklik/Heureka.
     */
    trackPurchase: function(orderData) {
        const eventName = "purchase";
        const logPrefix = `[${eventName}]`;
        console.log(`${logPrefix} Received data:`, JSON.stringify(orderData));
        if (!orderData || !orderData.transaction_id || !Array.isArray(orderData.items) || orderData.items.length === 0 || orderData.value == null) {
            console.warn(`${logPrefix} Missing required order data. Skipping.`); return;
        }

        const eventKey = this._getEventKey(eventName, orderData.transaction_id);
        if (this._trackedEvents[eventKey]) {
            console.warn(`${logPrefix} Event already tracked. Key: '${eventKey}'. Skipping.`); return;
        }

        const totalValueWithVat = parseFloat(orderData.value) || 0;
        const taxWithVat = parseFloat(orderData.tax) || 0;
        const shippingWithVat = parseFloat(orderData.shipping) || 0;
        const currency = orderData.currency || 'CZK';

        // Items for GA4 (prices WITHOUT VAT)
        const gaItems = orderData.items.map(item => ({
            item_id: item.item_id,
            item_name: item.item_name,
            item_brand: item.item_brand || 'Dřevníky Kolář',
            item_category: item.item_category || 'Dřevníky',
            price: parseFloat(item.price).toFixed(2) || '0.00', // Unit price NO VAT
            quantity: parseInt(item.quantity) || 1
        }));

        // Value WITHOUT VAT for Ads and Sklik
        const itemsValueNoVat = parseFloat(orderData.value_no_vat) || 0;
        const shippingNoVat = parseFloat(orderData.shipping_no_vat) || 0;
        const totalValueNoVat = itemsValueNoVat + shippingNoVat;

        // Heureka specific items (prices WITH VAT)
        const heurekaItems = orderData.heureka_items || [];

        const dataLayerPayload = {
            // Standard GA4 Purchase Event
            event: eventName,
            ecommerce: {
                transaction_id: orderData.transaction_id,
                value: totalValueWithVat.toFixed(2), // Total value WITH VAT
                currency: currency,
                tax: taxWithVat.toFixed(2),
                shipping: shippingWithVat.toFixed(2),
                items: gaItems
                // 'coupon': orderData.coupon || undefined
            },
            // Specific data for other platforms (can be picked up by GTM)
            'google_ads_id': this._googleAdsId,
            'google_ads_label': this._adsConversionLabelPurchase,
            'google_ads_value': totalValueNoVat.toFixed(2), // Value WITHOUT VAT for Ads

            'sklik_conversion_id': this._sklikPurchaseId,
            'sklik_order_id': orderData.transaction_id,
            'sklik_value': totalValueNoVat.toFixed(2), // Value WITHOUT VAT for Sklik

            'heureka_api_key': this._heurekaApiKey,
            'heureka_email': orderData.customer_email, // Customer email is needed
            'heureka_order_id': orderData.transaction_id,
            'heureka_items_dl': heurekaItems // Pass Heureka items separately for clarity
        };

        if (!this._ensureDataLayer()) {
            console.warn(`${logPrefix} DataLayer not ready. Storing purchase data.`);
            this._pendingPurchaseData = dataLayerPayload;
            return;
        }

        this._trackedEvents[eventKey] = true; // Mark as tracked *before* pushing
        console.log(`${logPrefix} Marking event key '${eventKey}' as tracked.`);
        window.dataLayer.push(dataLayerPayload);
        console.log(`${logPrefix} Pushed to dataLayer:`, dataLayerPayload);
        console.log(`${logPrefix} Finished processing.`);
    },

    /**
     * Tracks a click on a contact link. Pushes 'contact_click' to dataLayer.
     */
    trackContactClick: function(contactType) {
        const eventName = "contact_click";
        const logPrefix = `[${eventName}]`;
        console.log(`${logPrefix} Tracking contact click. Type: ${contactType}`);

        const contactValue = 10.00; // Example fixed value for contact conversion


        const dataLayerPayload = {
            event: eventName,
            event_category: 'Contact',
            event_label: contactType,
            // Optional: Add specific data for Google Ads tag
            'google_ads_id': this._googleAdsId,
            'google_ads_label': this._adsConversionLabelContact,
            'google_ads_value': contactValue.toFixed(2),
            'currency': 'CZK'
        };


        if (!this._ensureDataLayer()) {
            console.warn(`${logPrefix} DataLayer not ready. Cannot track contact click.`);
            return;
        }

        window.dataLayer.push(dataLayerPayload);
        console.log(`${logPrefix} Pushed to dataLayer:`, dataLayerPayload);
        console.log(`${logPrefix} Finished processing.`);
    }
};

// Event Listener for contact clicks (No changes needed here)
document.addEventListener("click", function(event) {
    const contactLink = event.target.closest("a.track-contact-click");
    if (contactLink) {
        const href = contactLink.getAttribute("href");
        let contactType = "unknown";
        if (href?.startsWith("tel:")) { contactType = "phone"; }
        else if (href?.startsWith("mailto:")) { contactType = "email"; }
        console.log(`[Contact Click Listener] Detected click. Type: ${contactType}, Href: ${href}`);
        if (typeof TrackingService !== 'undefined' && typeof TrackingService.trackContactClick === 'function') {
            TrackingService.trackContactClick(contactType);
        } else { console.warn("[Contact Click Listener] TrackingService not ready."); }
    }
});

// Initial check on DOMContentLoaded
document.addEventListener('DOMContentLoaded', () => {
    console.log("[TrackingService Load] DOMContentLoaded. Calling initBaseScripts (first check).");
    if (typeof TrackingService !== 'undefined') {
        TrackingService.initBaseScripts();
    } else {
        console.error("[TrackingService Load] TrackingService not defined on DOMContentLoaded!");
    }
});