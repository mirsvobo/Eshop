// Soubor: src/main/resources/static/js/homepage.js
// Verze 3 - Instance carouselu se získává uvnitř listeneru

document.addEventListener('DOMContentLoaded', function () {
    console.log("[Homepage JS v3] DOMContentLoaded event."); // Označení verze

    // --- JS pro TypeIt Animation (zůstává stejný) ---
    try {
        const stringsElement = document.getElementById('typing-strings');
        if (stringsElement && typeof TypeIt !== 'undefined') {
            const stringsToType = Array.from(stringsElement.children).map(span => span.textContent);
            const typedOutputElement = document.getElementById('typed-output');

            if(typedOutputElement && stringsToType.length > 0) {
                console.log("[Homepage JS v3] Initializing TypeIt animation...");
                new TypeIt('#typed-output', {
                    strings: stringsToType, speed: 70, breakLines: false,
                    waitUntilVisible: true, loop: true, loopDelay: 2000,
                    deleteSpeed: 50, lifeLike: true, cursor: false
                }).go();
                console.log("[Homepage JS v3] TypeIt initialized.");
            } else {
                console.warn("[Homepage JS v3] TypeIt target element #typed-output or strings not found. Applying fallback.");
                const firstString = stringsElement?.firstElementChild?.textContent;
                if(typedOutputElement && firstString) {
                    typedOutputElement.textContent = firstString;
                    const cssCursor = document.getElementById('typed-cursor');
                    if(cssCursor) cssCursor.style.display = 'none';
                }
            }
        } else {
            console.warn("[Homepage JS v3] Typing animation element #typing-strings or TypeIt library not found. Applying fallback.");
            const typedOutput = document.getElementById('typed-output');
            const stringsElementFallback = document.getElementById('typing-strings');
            const firstString = stringsElementFallback?.firstElementChild?.textContent;
            if(typedOutput && firstString) {
                typedOutputElement.textContent = firstString;
                const cssCursor = document.getElementById('typed-cursor');
                if(cssCursor) cssCursor.style.display = 'none';
            }
        }
    } catch (e) {
        console.error("[Homepage JS v3] Error during TypeIt initialization:", e);
        const typedOutput = document.getElementById('typed-output');
        if(typedOutput) typedOutput.textContent = 'snadné uskladnění dřeva.';
        const cssCursor = document.getElementById('typed-cursor');
        if(cssCursor) cssCursor.style.display = 'none';
    }

    console.log("[Homepage JS v3] Initialization finished.");
}); // Konec DOMContentLoaded