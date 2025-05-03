// Soubor: src/main/resources/static/js/cookie-config.js
console.log("DEBUG: cookie-config.js script loaded.");

/**
 * Základní konfigurace pro CookieConsent v3
 */
CookieConsent.run({
    guiOptions: {
        consentModal: { layout: "box", position: "bottom right", equalWeightButtons: true, flipButtons: false },
        preferencesModal: { layout: "box", position: "right", equalWeightButtons: true, flipButtons: false }
    },
    categories: {
        necessary: { readOnly: true, enabled: true },
        analytics: {
            enabled: false, // Defaultně zakázané
            autoClear: { cookies: [ { name: /^_ga/ }, { name: '_ga' } ] }
        },
        marketing: {
            enabled: false, // Defaultně zakázané
            autoClear: { cookies: [ { name: '_gcl_au' }, { name: '_fbp' } ] }
            // Zde můžeme později přidat services pro manuální ovládání skriptů,
            // ale spoléháme teď na data-cookiecategory atributy
        }
    },
    language: {
        default: "cs",
        translations: {
            cs: {
                consentModal: {
                    title: "Používáme cookies 🍪",
                    description: "Tyto webové stránky používají nezbytné cookies k zajištění správného fungování a sledovací/marketingové cookies k pochopení toho, jak s nimi komunikujete. Tyto budou nastaveny až po vašem souhlasu. <a href='/gdpr' target='_blank' class='cc__link'>Více informací</a>", // ODKAZ NA ZÁSADY
                    acceptAllBtn: "Přijmout vše",
                    acceptNecessaryBtn: "Odmítnout vše",
                    showPreferencesBtn: "Spravovat předvolby",
                },
                preferencesModal: {
                    title: "Nastavení souhlasu s cookies",
                    acceptAllBtn: "Přijmout vše",
                    acceptNecessaryBtn: "Odmítnout vše",
                    savePreferencesBtn: "Uložit nastavení",
                    closeIconLabel: "Zavřít",
                    serviceCounterLabel: "Služba|Služby",
                    sections: [
                        {
                            title: "Použití cookies",
                            description: "Na našem webu používáme cookies, abychom Vám poskytli co nejrelevantnější služby a funkce. Některé jsou nezbytné, zatímco jiné nám pomáhají zlepšovat tento web a váš zážitek (analytické a marketingové cookies)."
                        },
                        {
                            title: "Nezbytné cookies <span class=\"pm__badge\">Vždy povolené</span>",
                            description: "Tyto cookies jsou nezbytné pro základní funkčnost webu a nelze je vypnout. Zahrnují například cookies pro správu session nebo nákupního košíku.",
                            linkedCategory: "necessary"
                        },
                        {
                            title: "Analytické cookies",
                            description: "Tyto cookies nám pomáhají pochopit, jak návštěvníci používají náš web, abychom jej mohli vylepšovat. Sbírají anonymizovaná data.",
                            linkedCategory: "analytics",
                        },
                        {
                            title: "Marketingové cookies",
                            description: "Tyto cookies se používají ke sledování návštěvníků napříč webovými stránkami. Záměrem je zobrazovat reklamy, které jsou relevantní a poutavé pro jednotlivé uživatele.",
                            linkedCategory: "marketing"
                        },
                        {
                            title: "Více informací",
                            description: "Pro více informací o tom, jak používáme cookies, si prosím přečtěte naše <a href='/gdpr' target='_blank' class='cc__link'>Zásady používání cookies</a>." // ODKAZ NA ZÁSADY
                        }
                    ]
                }
            }
        }
    }, // Konec objektu language

    // --- Callback funkce pro inicializaci našeho trackingu ---
    onFirstConsent: function({categories}){
        console.log("CookieConsent onFirstConsent triggered. Accepted categories:", categories);
        if (typeof initializeTrackingAfterConsent === 'function') {
            initializeTrackingAfterConsent();
        } else {
            console.warn("Function initializeTrackingAfterConsent not found during onFirstConsent.");
            // Případně zkusit zavolat přímo TrackingService, pokud je to bezpečné
            // if(typeof TrackingService?.initBaseScripts === 'function') TrackingService.initBaseScripts();
        }
    },
    onConsent: function({categories}){
        console.log("CookieConsent onConsent triggered. Accepted categories:", categories);
        if (typeof initializeTrackingAfterConsent === 'function') {
            initializeTrackingAfterConsent();
        } else {
            console.warn("Function initializeTrackingAfterConsent not found during onConsent.");
        }
    },
    onChange: function({categories}){
        console.log("CookieConsent onChange triggered. Accepted categories:", categories);
        if (typeof initializeTrackingAfterConsent === 'function') {
            initializeTrackingAfterConsent(); // Znovu inicializujeme/zkontrolujeme trackery
            console.log("Consent changed, base tracking scripts state re-evaluated.");
        } else {
            console.warn("Function initializeTrackingAfterConsent not found during onChange.");
        }
    }
});