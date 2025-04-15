// Soubor: src/main/resources/static/js/homepage.js
// Verze používající window.onload

window.onload = function() { // <--- Používáme window.onload
    console.log("[Homepage JS - window.onload] Page fully loaded event.");

    // --- JS pro TypeIt Animation ---
    try {
        const stringsElement = document.getElementById('typing-strings');
        if (stringsElement && typeof TypeIt !== 'undefined') {
            const stringsToType = Array.from(stringsElement.children).map(span => span.textContent);
            const typedOutputElement = document.getElementById('typed-output');

            if(typedOutputElement && stringsToType.length > 0) {
                console.log("[Homepage JS - window.onload] Initializing TypeIt animation...");
                new TypeIt('#typed-output', {
                    strings: stringsToType,
                    speed: 70,
                    breakLines: false,
                    waitUntilVisible: true,
                    loop: true,
                    loopDelay: 2000,
                    deleteSpeed: 50,
                    lifeLike: true,
                    cursor: false // Vlastní kurzor je řešen přes CSS
                }).go();
                console.log("[Homepage JS - window.onload] TypeIt initialized.");
            } else {
                console.warn("[Homepage JS - window.onload] TypeIt target element #typed-output or strings not found. Applying fallback.");
                const firstString = stringsElement?.firstElementChild?.textContent;
                if(typedOutputElement && firstString) {
                    typedOutputElement.textContent = firstString;
                    const cssCursor = document.getElementById('typed-cursor');
                    if(cssCursor) cssCursor.style.display = 'none';
                }
            }
        } else {
            console.warn("[Homepage JS - window.onload] Typing animation element #typing-strings or TypeIt library not found. Applying fallback.");
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
        console.error("[Homepage JS - window.onload] Error during TypeIt initialization:", e);
        const typedOutput = document.getElementById('typed-output');
        if(typedOutput) typedOutput.textContent = 'snadné uskladnění dřeva.';
        const cssCursor = document.getElementById('typed-cursor');
        if(cssCursor) cssCursor.style.display = 'none';
    }

    // --- JS pro Bootstrap Modal Galerie ---
    console.log("[Homepage JS - window.onload] Setting up Gallery Modal logic...");
    const galleryModal = document.getElementById('galleryModal');
    const galleryCarouselElement = document.getElementById('galleryCarousel');
    let galleryCarouselInstance = null;

    if (galleryModal && galleryCarouselElement) {
        console.log("[Homepage JS - window.onload] Found #galleryModal and #galleryCarousel elements.");
        try {
            galleryCarouselInstance = bootstrap.Carousel.getOrCreateInstance(galleryCarouselElement);
            console.log("[Homepage JS - window.onload] Gallery Carousel instance created/retrieved.");
        } catch(e) {
            console.error("[Homepage JS - window.onload] Error creating/retrieving carousel instance:", e);
        }

        galleryModal.addEventListener('show.bs.modal', event => {
            console.log("[Homepage JS - window.onload] Modal 'show.bs.modal' event triggered.");
            const triggerLink = event.relatedTarget;
            if (triggerLink) {
                const slideIndex = parseInt(triggerLink.getAttribute('data-bs-slide-to') || '0');
                console.log("[Homepage JS - window.onload] Target slide index from trigger:", slideIndex);
                if (galleryCarouselInstance) {
                    try {
                        galleryCarouselInstance.to(slideIndex);
                        console.log("[Homepage JS - window.onload] Carousel moved to index:", slideIndex);
                    } catch (e) {
                        console.error("[Homepage JS - window.onload] Error executing carousel.to():", e);
                    }
                } else {
                    console.error("[Homepage JS - window.onload] Carousel instance is not available when modal is shown!");
                }
            } else {
                console.warn("[Homepage JS - window.onload] Modal trigger link (event.relatedTarget) not found.");
            }
        });
        console.log("[Homepage JS - window.onload] Modal event listener attached successfully to #galleryModal.");
    } else {
        console.error("[Homepage JS - window.onload] Modal (#galleryModal) or Carousel (#galleryCarousel) element not found in the DOM even on window.onload!");
    }

    console.log("[Homepage JS - window.onload] Initialization finished.");
}; // Konec window.onload