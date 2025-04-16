document.addEventListener('DOMContentLoaded', function () {
    console.log("[Homepage JS v8] DOMContentLoaded event."); // Verze 8

    // --- JS pro TypeIt Animation (beze změny) ---
    try {
        // ... kód pro TypeIt ...
        const stringsElement = document.getElementById('typing-strings');
        if (stringsElement && typeof TypeIt !== 'undefined') {
            const stringsToType = Array.from(stringsElement.children).map(span => span.textContent);
            const typedOutputElement = document.getElementById('typed-output');

            if(typedOutputElement && stringsToType.length > 0) {
                console.log("[Homepage JS v8] Initializing TypeIt animation...");
                new TypeIt('#typed-output', {
                    strings: stringsToType, speed: 70, breakLines: false,
                    waitUntilVisible: true, loop: true, loopDelay: 2000,
                    deleteSpeed: 50, lifeLike: true, cursor: false
                }).go();
                console.log("[Homepage JS v8] TypeIt initialized.");
            } else {
                console.warn("[Homepage JS v8] TypeIt target element #typed-output or strings not found.");
            }
        } else {
            console.warn("[Homepage JS v8] Typing animation element #typing-strings or TypeIt library not found.");
        }
    } catch (e) {
        console.error("[Homepage JS v8] Error during TypeIt initialization:", e);
    }

    // --- Swiper Initialization for Reviews (beze změny) ---
    let reviewSwiperInstance = null;
    try {
        const swiperElement = document.querySelector('.swiper-review');
        if (swiperElement && typeof Swiper !== 'undefined') {
            reviewSwiperInstance = new Swiper(swiperElement, {
                loop: false,
                slidesPerView: 1,
                spaceBetween: 16,
                navigation: {
                    nextEl: '.swiper-button-next',
                    prevEl: '.swiper-button-prev',
                },
                breakpoints: {
                    768: { slidesPerView: 2, spaceBetween: 20 },
                    992: { slidesPerView: 3, spaceBetween: 24 }
                },
                a11y: {
                    prevSlideMessage: 'Předchozí recenze',
                    nextSlideMessage: 'Další recenze',
                },
            });
            console.log("[Homepage JS v8] Swiper for reviews initialized.");
        } else {
            if (!swiperElement) {
                console.warn("[Homepage JS v8] Swiper container '.swiper-review' not found. Swiper not initialized.");
            }
            if (typeof Swiper === 'undefined') {
                console.error("[Homepage JS v8] Swiper library not loaded. Cannot initialize reviews carousel.");
                const prevButton = document.querySelector('.swiper-button-prev');
                const nextButton = document.querySelector('.swiper-button-next');
                if(prevButton) prevButton.style.display = 'none';
                if(nextButton) nextButton.style.display = 'none';
            }
        }
    } catch (e) {
        console.error("[Homepage JS v8] Error initializing Swiper:", e);
    }

    // --- Read More Functionality (Listener cílí na '.swiper-review .swiper-wrapper') ---
    const swiperWrapper = document.querySelector('.swiper-review .swiper-wrapper'); // <<<< ZMĚNA ZDE

    if (swiperWrapper) { // Kontrolujeme existenci wrapperu
        swiperWrapper.addEventListener('click', function(event) {
            if (event.target.classList.contains('read-more')) {
                event.preventDefault();
                console.log("[Homepage JS v8] Read more link clicked.");

                const reviewCard = event.target.closest('.review-card-new');
                if (!reviewCard) return;

                const reviewText = reviewCard.querySelector('.review-text');
                if (!reviewText) return;

                // Toggle třídy a textu
                reviewText.classList.toggle('truncated');
                event.target.textContent = reviewText.classList.contains('truncated') ? 'Přečtěte si více' : 'Zobrazit méně';
                console.log("[Homepage JS v8] Toggled 'truncated' class. Is now truncated:", reviewText.classList.contains('truncated'));

                // Aktualizace Swiperu
                if (reviewSwiperInstance && typeof reviewSwiperInstance.update === 'function') {
                    setTimeout(() => {
                        reviewSwiperInstance.update();
                        console.log("[Homepage JS v8] Swiper layout updated after text toggle.");
                    }, 50);
                }
            }
        });
        console.log("[Homepage JS v8] 'Read More' event listener attached to swiper wrapper.");

        // Inicializace viditelnosti "Read More" (jednoduchá verze)
        document.querySelectorAll('.review-card-new').forEach(card => {
            const textElement = card.querySelector('.review-text');
            const readMoreLink = card.querySelector('.read-more');
            if (textElement && readMoreLink) {
                // Zobraz odkaz POUZE pokud text má třídu truncated
                // Skutečné oříznutí řeší CSS
                readMoreLink.style.display = textElement.classList.contains('truncated') ? 'inline-block' : 'none';
            }
        });
        console.log("[Homepage JS v8] Initial visibility check for 'Read More' links finished (simplified).");

    } else {
        console.warn("[Homepage JS v8] Swiper wrapper '.swiper-review .swiper-wrapper' not found. 'Read More' functionality disabled.");
    }

    console.log("[Homepage JS v8] Initialization finished.");
});