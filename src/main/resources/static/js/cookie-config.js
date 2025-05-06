const consentModeMap = {
    necessary: {}, // Nezbytné nemění Google Consent Mode
    analytics: {
        analytics_storage: 'granted'
    },
    marketing: {
        ad_storage: 'granted',
        ad_user_data: 'granted',
        ad_personalization: 'granted'
    }
};

/**
 * Aktualizuje stav souhlasu v Google Consent Mode v2.
 * @param {string[]|undefined} acceptedCategories Pole názvů přijatých kategorií nebo undefined/null.
 */
function updateGoogleConsent(acceptedCategories) {
    console.log("[updateGoogleConsent] Funkce volána s acceptedCategories:", JSON.stringify(acceptedCategories), "(Typ:", typeof acceptedCategories, ")");

    const consentUpdate = {
        analytics_storage: 'denied',
        ad_storage: 'denied',
        ad_user_data: 'denied',
        ad_personalization: 'denied'
    };

    if (acceptedCategories && Array.isArray(acceptedCategories)) {
        console.log("[updateGoogleConsent] AcceptedCategories je platné pole. Zpracovávám...");
        acceptedCategories.forEach(category => {
            console.log(`[updateGoogleConsent] Zpracovávám kategorii: ${category}`);
            if (consentModeMap[category]) {
                Object.assign(consentUpdate, consentModeMap[category]);
                console.log(`[updateGoogleConsent] Kategorie ${category} mapována na:`, consentModeMap[category]);
            } else {
                console.log(`[updateGoogleConsent] Kategorie ${category} nemá mapování v consentModeMap.`);
            }
        });
    } else {
        console.warn("[updateGoogleConsent] acceptedCategories není platné pole, nastavuji vše na 'denied'.");
    }

    console.log("[updateGoogleConsent] Finální stav pro Google Consent Mode:", consentUpdate);
    if (typeof gtag === 'function') {
        gtag('consent', 'update', consentUpdate);
        console.log("[updateGoogleConsent] Příkaz gtag('consent', 'update', ...) odeslán.");
    } else {
        console.warn("[updateGoogleConsent] Funkce gtag nebyla nalezena při pokusu o update souhlasu!");
    }

    // Inicializace ostatních skriptů (Sklik, Heureka) - volání TrackingService
    if (typeof TrackingService !== 'undefined' && typeof TrackingService.initBaseScripts === 'function') {
        console.log("[updateGoogleConsent] Volám TrackingService.initBaseScripts IHNED po aktualizaci souhlasu.");
        // === ZMĚNA: Odebráno setTimeout ===
        TrackingService.initBaseScripts();
        // === KONEC ZMĚNY ===
    } else {
        console.error("[updateGoogleConsent] TrackingService nebo initBaseScripts nejsou definovány!");
        // Zde už nemá smysl zkoušet znovu, protože by to mělo být definováno díky defer
    }
}

// Konfigurace a spuštění CookieConsent knihovny
CookieConsent.run({
    guiOptions: {
        consentModal: { layout: "box", position: "bottom right", equalWeightButtons: true, flipButtons: false },
        preferencesModal: { layout: "box", position: "right", equalWeightButtons: true, flipButtons: false }
    },
    categories: {
        necessary: { readOnly: true, enabled: true },
        analytics: { enabled: false, autoClear: { cookies: [{ name: /^_ga/ }, { name: '_ga' }] } },
        marketing: { enabled: false, autoClear: { cookies: [{ name: '_gcl_au' }, { name: '_fbp' }] } }
    },
    language: {
        default: "cs",
        translations: {
            cs: { /* ... texty zůstávají stejné ... */
                consentModal:{title:"Používáme cookies 🍪",description:"Tyto webové stránky používají nezbytné cookies k zajištění správného fungování a sledovací/marketingové cookies k pochopení toho, jak s nimi komunikujete (včetně personalizace reklam). Tyto budou nastaveny až po vašem souhlasu. <a href='/gdpr' target='_blank' class='cc__link'>Více informací</a>",acceptAllBtn:"Přijmout vše",acceptNecessaryBtn:"Odmítnout vše",showPreferencesBtn:"Spravovat předvolby",},preferencesModal:{title:"Nastavení souhlasu s cookies",acceptAllBtn:"Přijmout vše",acceptNecessaryBtn:"Odmítnout vše",savePreferencesBtn:"Uložit nastavení",closeIconLabel:"Zavřít",serviceCounterLabel:"Služba|Služby",sections:[{title:"Použití cookies",description:"Na našem webu používáme cookies, abychom Vám poskytli co nejrelevantnější služby a funkce. Některé jsou nezbytné, zatímco jiné nám pomáhají zlepšovat tento web a váš zážitek (analytické a marketingové cookies)."},{title:"Nezbytné cookies <span class=\"pm__badge\">Vždy povolené</span>",description:"Tyto cookies jsou nezbytné pro základní funkčnost webu a nelze je vypnout. Zahrnují například cookies pro správu session nebo nákupního košíku.",linkedCategory:"necessary"},{title:"Analytické cookies",description:"Tyto cookies nám pomáhají pochopit, jak návštěvníci používají náš web, abychom jej mohli vylepšovat. Sbírají anonymizovaná data.",linkedCategory:"analytics",},{title:"Marketingové cookies",description:"Tyto cookies se používají ke sledování návštěvníků napříč webovými stránkami. Záměrem je zobrazovat reklamy, které jsou relevantní a poutavé pro jednotlivé uživatele, a měřit jejich účinnost.",linkedCategory:"marketing"},{title:"Více informací",description:"Pro více informací o tom, jak používáme cookies, si prosím přečtěte naše <a href='/gdpr' target='_blank' class='cc__link'>Zásady používání cookies</a>."}]}
            }
        }
    },
    onFirstConsent: function({ categories }) {
        console.log("CookieConsent onFirstConsent triggered.");
        const preferences = CookieConsent.getUserPreferences();
        console.log("CookieConsent preferences [onFirstConsent]:", preferences);
        updateGoogleConsent(preferences.acceptedCategories);
    },
    onConsent: function({ categories }) {
        console.log("CookieConsent onConsent triggered.");
        const preferences = CookieConsent.getUserPreferences();
        console.log("CookieConsent preferences [onConsent]:", preferences);
        updateGoogleConsent(preferences.acceptedCategories);
    },
    onChange: function({ categories }) {
        console.log("CookieConsent onChange triggered.");
        const preferences = CookieConsent.getUserPreferences();
        console.log("CookieConsent preferences [onChange]:", preferences);
        updateGoogleConsent(preferences.acceptedCategories);
    }
});