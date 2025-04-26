// Soubor: src/main/resources/static/js/cookie-config.js
console.log("DEBUG: cookie-config.js script loaded."); // <-- PŘIDÁNO

/**
 * Základní konfigurace pro CookieConsent v3
 * Dokumentace: https://cookieconsent.orestbida.com/reference/configuration-reference.html
 */
CookieConsent.run({
    // --- Základní nastavení ---
    guiOptions: {
        consentModal: {
            layout: "box",              // Může být box, bar, cloud
            position: "bottom right",   // Pozice boxu/baru
            equalWeightButtons: true,
            flipButtons: false
        },
        preferencesModal: {
            layout: "box",
            position: "right",
            equalWeightButtons: true,
            flipButtons: false
        }
    },

    // --- Kategorie Cookies ---
    categories: {
        necessary: {
            readOnly: true,          // Nezbytné cookies nelze odmítnout
            enabled: true            // Jsou vždy povolené
        },
        analytics: {
            enabled: false,          // Defaultně zakázané
            autoClear: {
                cookies: [           // Cookies, které se smažou, pokud uživatel souhlas odvolá
                    { name: /^_ga/ }, // Všechny Google Analytics cookies (_ga, _gid, _gat_...)
                    { name: '_ga' }
                ]
            }
        },
        marketing: {
            enabled: false,
            autoClear: {
                cookies: [
                    // Zde přijdou cookies pro Google Ads, Facebook Pixel, Sklik atd.
                    // Příklad:
                    { name: '_gcl_au' }, // Google Ads conversion linker
                    { name: '_fbp' }     // Facebook Pixel
                ]
            }
            // Můžeme přidat i 'scripts: true' pro blokování skriptů, viz níže
        }
    },

    // --- Jazyková nastavení (Čeština) ---
    language: {
        default: "cs",
        translations: {
            cs: {
                consentModal: {
                    title: "Používáme cookies 🍪",
                    description: "Tyto webové stránky používají nezbytné cookies k zajištění správného fungování a sledovací/marketingové cookies k pochopení toho, jak s nimi komunikujete. Tyto budou nastaveny až po vašem souhlasu. <a href='/zasady-cookies' target='_blank' class='cc__link'>Více informací</a>", // ODKAZ NA ZÁSADY
                    acceptAllBtn: "Přijmout vše",
                    acceptNecessaryBtn: "Odmítnout vše",
                    showPreferencesBtn: "Spravovat předvolby",
                    //footer: "<a href=\"#link\">Privacy Policy</a>\n<a href=\"#link\">Terms and conditions</a>"
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
                            // Zde můžeme později přidat konkrétní služby (např. Google Analytics)
                            // services: [ { label: 'Google Analytics', description: '...' } ]
                        },
                        {
                            title: "Marketingové cookies",
                            description: "Tyto cookies se používají ke sledování návštěvníků napříč webovými stránkami. Záměrem je zobrazovat reklamy, které jsou relevantní a poutavé pro jednotlivé uživatele.",
                            linkedCategory: "marketing"
                            // Zde můžeme později přidat konkrétní služby (Google Ads, Sklik, Heureka, FB Pixel...)
                        },
                        {
                            title: "Více informací",
                            description: "Pro více informací o tom, jak používáme cookies, si prosím přečtěte naše <a href='/zasady-cookies' target='_blank' class='cc__link'>Zásady používání cookies</a>." // ODKAZ NA ZÁSADY
                        }
                    ]
                }
            }
        }
    },

    // --- Pokročilá konfigurace (propojení s GTM atd. - přidáme později) ---
    // services: { ... } // Zde definujeme konkrétní služby (GA, Ads, ...) a jak je knihovna má ovládat
});

// Tento kód se spustí a zobrazí cookie lištu, pokud souhlas ještě nebyl udělen.