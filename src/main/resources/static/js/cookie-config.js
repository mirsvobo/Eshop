// Soubor: /js/cookie-config.js
// Verze v5 - Spolehlivější inicializace TrackingService a logování

const consentModeMap = {
    necessary: {},
    analytics: { analytics_storage: 'granted' },
    marketing: { ad_storage: 'granted', ad_user_data: 'granted', ad_personalization: 'granted' }
};

// Pomocná funkce pro získání debug módu, i když trackingService ještě není plně inicializována
function getDebugModeFlag() {
    if (window.trackingService && typeof window.trackingService.debugMode === 'boolean') {
        return window.trackingService.debugMode;
    }
    // Pokud je debugMode v možnostech instance TrackingService, a ta existuje
    if (window.trackingService && window.trackingService._googleAdsId) { // Předpoklad, že _googleAdsId je vždy přítomen
        const tsInstance = window.trackingService;
        if (typeof tsInstance.debugMode === 'boolean') return tsInstance.debugMode;
    }
    // Fallback, pokud nic jiného není k dispozici
    // Můžete nastavit defaultní hodnotu nebo se pokusit číst z globální proměnné
    // if (typeof window.GLOBAL_DEBUG_MODE === 'boolean') return window.GLOBAL_DEBUG_MODE;
    return true; // Default na false, pokud není jinak specifikováno
}


function updateGoogleConsent(acceptedCategories) {
    const isDebugEnabled = getDebugModeFlag();
    let consentProcessedDispatched = false;

    if (isDebugEnabled) {
        console.log("[cookie-config.js updateGoogleConsent V5] Voláno s acceptedCategories:", JSON.stringify(acceptedCategories));
    }

    const consentUpdate = {
        analytics_storage: 'denied',
        ad_storage: 'denied',
        ad_user_data: 'denied',
        ad_personalization: 'denied'
    };

    if (acceptedCategories && Array.isArray(acceptedCategories)) {
        acceptedCategories.forEach(category => {
            if (consentModeMap[category]) {
                Object.assign(consentUpdate, consentModeMap[category]);
            }
        });
    }

    if (isDebugEnabled) console.log("[cookie-config.js updateGoogleConsent V5] Finální stav pro Google Consent Mode:", consentUpdate);

    const dispatchAndInitialize = (status, errorMsg = null) => {
        // Inicializace TrackingService PRVNÍ, aby byl debugMode dostupný co nejdříve
        if (typeof TrackingService !== 'undefined') {
            if (!window.trackingService) {
                const options = {
                    debugMode: false, // Nastavte ZDE true/false dle potřeby, nebo předejte zvenku
                    googleAdsId: 'AW-17046857865',
                    adsConversionLabelViewItem: '5icqCLa-n8EaEInRycA_',
                    adsConversionLabelAddToCart: 'KT_JCK2-n8EaEInRycA_',
                    adsConversionLabelBeginCheckout: 'YVBgCLC-n8EaEInRycA_',
                    adsConversionLabelPurchase: 'I-9yCKq-n8EaEInRycA_',
                    adsConversionLabelContact: 'QfVhCLO-n8EaEInRycA_'
                };
                try {
                    window.trackingService = new TrackingService(options);
                    // Nyní můžeme bezpečněji přistupovat k this.debugMode v TrackingService
                    if (window.trackingService.debugMode) console.log('[cookie-config.js V5] TrackingService instance vytvořena.');
                    window.isTrackingServiceReady = true;
                    if (window.trackingService.initBaseScripts) {
                        window.trackingService.initBaseScripts();
                    }
                } catch (e) {
                    console.error('[cookie-config.js V5] CHYBA PŘI INSTANCIACI TrackingService:', e);
                    window.isTrackingServiceReady = false;
                }
            } else {
                if (getDebugModeFlag()) console.log('[cookie-config.js V5] TrackingService již byla inicializována.');
            }
        } else {
            console.error('[cookie-config.js V5] Třída TrackingService není definována!');
            window.isTrackingServiceReady = false;
        }

        // Odeslání události consentProcessed
        if (!consentProcessedDispatched) {
            consentProcessedDispatched = true;
            setTimeout(() => {
                const detail = { categories: acceptedCategories, consentState: consentUpdate, status: status };
                if (errorMsg) detail.error = errorMsg;
                if (getDebugModeFlag()) console.log(`[cookie-config.js V5] Vysílám událost 'consentProcessed' (status: ${status}). Detail:`, detail);
                document.dispatchEvent(new CustomEvent('consentProcessed', { detail: detail }));

                if (typeof window.attemptToTriggerTrackingActions === 'function') {
                    window.attemptToTriggerTrackingActions('cookie-config.js after consent dispatch');
                }
            }, 50);
        }
    };

    if (typeof gtag === 'function') {
        try {
            gtag('consent', 'update', consentUpdate);
            if (getDebugModeFlag()) console.log("[cookie-config.js updateGoogleConsent V5] gtag('consent', 'update', ...) odeslán.");
            dispatchAndInitialize('success');
        } catch (error) {
            console.error("[cookie-config.js updateGoogleConsent V5] Chyba při volání gtag:", error);
            dispatchAndInitialize('error', 'gtag call failed');
        }
    } else {
        console.warn("[cookie-config.js updateGoogleConsent V5] Funkce gtag nebyla nalezena!");
        dispatchAndInitialize('error', 'gtag not found');
    }
}

if (typeof CookieConsent !== 'undefined') {
    CookieConsent.run({
        guiOptions: {
            consentModal: { layout: "box", position: "bottom right", equalWeightButtons: true, flipButtons: false },
            preferencesModal: { layout: "box", position: "right", equalWeightButtons: true, flipButtons: false }
        },
        categories: {
            necessary: { readOnly: true, enabled: true },
            analytics: { enabled: false, autoClear: { cookies: [{ name: /^_ga/ }, { name: '_gid' }, { name: '_gat_UA-' }, { name: '_gat_gtag_' }, { name: /^_dc_gtm_UA-/ }] } },
            marketing: { enabled: false, autoClear: { cookies: [{ name: '_gcl_au' }, { name: '_fbp' }, { name: 'IDE' }, { name: /_gac_.*/}, { name: '_uetmsclkid' }, { name: '_uetvid' }] } }
        },
        language: { /* ... (texty zůstávají stejné) ... */
            default: "cs",
            translations: { cs: {
                    consentModal:{title:"Používáme cookies 🍪",description:"Tyto webové stránky používají nezbytné cookies k zajištění správného fungování a sledovací/marketingové cookies k pochopení toho, jak s nimi komunikujete (včetně personalizace reklam). Tyto budou nastaveny až po vašem souhlasu. <a href='/gdpr' target='_blank' class='cc__link'>Více informací</a>",acceptAllBtn:"Přijmout vše",acceptNecessaryBtn:"Odmítnout vše",showPreferencesBtn:"Spravovat předvolby",},preferencesModal:{title:"Nastavení souhlasu s cookies",acceptAllBtn:"Přijmout vše",acceptNecessaryBtn:"Odmítnout vše",savePreferencesBtn:"Uložit nastavení",closeIconLabel:"Zavřít",serviceCounterLabel:"Služba|Služby",sections:[{title:"Použití cookies",description:"Na našem webu používáme cookies, abychom Vám poskytli co nejrelevantnější služby a funkce. Některé jsou nezbytné, zatímco jiné nám pomáhají zlepšovat tento web a váš zážitek (analytické a marketingové cookies)."},{title:"Nezbytné cookies <span class=\"pm__badge\">Vždy povolené</span>",description:"Tyto cookies jsou nezbytné pro základní funkčnost webu a nelze je vypnout. Zahrnují například cookies pro správu session nebo nákupního košíku.",linkedCategory:"necessary"},{title:"Analytické cookies",description:"Tyto cookies nám pomáhají pochopit, jak návštěvníci používají náš web, abychom jej mohli vylepšovat. Sbírají anonymizovaná data (např. Google Analytics).",linkedCategory:"analytics",},{title:"Marketingové cookies",description:"Tyto cookies se používají ke sledování návštěvníků napříč webovými stránkami. Záměrem je zobrazovat reklamy, které jsou relevantní (např. Google Ads, Sklik retargeting) a měřit jejich účinnost.",linkedCategory:"marketing"},{title:"Více informací",description:"Pro více informací o tom, jak používáme cookies, si prosím přečtěte naše <a href='/gdpr' target='_blank' class='cc__link'>Zásady používání cookies</a>."}]}
                }}
        },
        onFirstConsent: function({ categories }) {
            if (getDebugModeFlag()) console.log("CookieConsent onFirstConsent triggered (V5).");
            updateGoogleConsent(CookieConsent.getUserPreferences().acceptedCategories);
        },
        onConsent: function({ categories }) {
            if (getDebugModeFlag()) console.log("CookieConsent onConsent triggered (V5).");
            updateGoogleConsent(CookieConsent.getUserPreferences().acceptedCategories);
        },
        onChange: function({ categories }) {
            if (getDebugModeFlag()) console.log("CookieConsent onChange triggered (V5).");
            updateGoogleConsent(CookieConsent.getUserPreferences().acceptedCategories);
        }
    });
} else {
    console.error("[cookie-config.js V5] Knihovna CookieConsent nebyla nalezena.");
    setTimeout(() => {
        console.warn("[cookie-config.js V5] Vysílám událost 'consentProcessed' (CookieConsent NENALEZEN).");
        // I když CookieConsent není, inicializujeme TrackingService a consentProcessed událost
        if (typeof TrackingService !== 'undefined' && !window.trackingService) {
            const options = { debugMode: false, googleAdsId: 'AW-17046857865'};
            try {
                window.trackingService = new TrackingService(options);
                if(window.trackingService.debugMode) console.log('[cookie-config.js V5] TrackingService instance vytvořena (fallback - CookieConsent not found).');
                window.isTrackingServiceReady = true;
                if (window.trackingService.initBaseScripts) window.trackingService.initBaseScripts();
            } catch (e) {
                console.error('[cookie-config.js V5] CHYBA PŘI INSTANCIACI TrackingService (fallback):', e);
                window.isTrackingServiceReady = false;
            }
        }
        // Odešleme událost consentProcessed i v tomto fallback scénáři
        document.dispatchEvent(new CustomEvent('consentProcessed', { detail: { categories: null, consentState: null, status: 'error', error: 'CookieConsent not found' } }));
        // A explicitně zavoláme pokus o spuštění fronty
        if (typeof window.attemptToTriggerTrackingActions === 'function') {
            window.attemptToTriggerTrackingActions('cookie-config.js fallback after synthetic consentProcessed');
        }

    }, 50);
}
// --- Konec souboru cookie-config.js ---