document.addEventListener('DOMContentLoaded', function () {
    console.log("[Homepage JS v8] DOMContentLoaded event."); // Verze 8

    // --- JS pro TypeIt Animation (beze změny) ---
    try {
        const stringsElement = document.getElementById('typing-strings');
        if (stringsElement && typeof TypeIt !== 'undefined') {
            const stringsToType = Array.from(stringsElement.children).map(span => span.textContent);
            const typedOutputElement = document.getElementById('typed-output');

            if(typedOutputElement && stringsToType.length > 0) {
                console.log("[Homepage JS v8] Initializing TypeIt animation...");
                new TypeIt('#typed-output', {
                    strings: stringsToType, speed: 70, breakLines: false,
                    waitUntilVisible: true, loop: true, loopDelay: 2000,
                    deleteSpeed: 50, lifeLike: true, cursor: false // Cursor je řízen CSS
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
                    nextEl: '.swiper-review .swiper-button-next', // Specifický selektor
                    prevEl: '.swiper-review .swiper-button-prev', // Specifický selektor
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
                const prevButton = document.querySelector('.swiper-review .swiper-button-prev');
                const nextButton = document.querySelector('.swiper-review .swiper-button-next');
                if(prevButton) prevButton.style.display = 'none';
                if(nextButton) nextButton.style.display = 'none';
            }
        }
    } catch (e) {
        console.error("[Homepage JS v8] Error initializing Swiper for reviews:", e);
    }

    // --- Read More Functionality (beze změny) ---
    const swiperWrapper = document.querySelector('.swiper-review .swiper-wrapper');

    if (swiperWrapper) {
        swiperWrapper.addEventListener('click', function(event) {
            if (event.target.classList.contains('read-more')) {
                event.preventDefault();
                console.log("[Homepage JS v8] Read more link clicked.");

                const reviewCard = event.target.closest('.review-card-new');
                if (!reviewCard) return;

                const reviewText = reviewCard.querySelector('.review-text');
                if (!reviewText) return;

                reviewText.classList.toggle('truncated');
                event.target.textContent = reviewText.classList.contains('truncated') ? 'Přečtěte si více' : 'Zobrazit méně';
                console.log("[Homepage JS v8] Toggled 'truncated' class. Is now truncated:", reviewText.classList.contains('truncated'));

                if (reviewSwiperInstance && typeof reviewSwiperInstance.update === 'function') {
                    setTimeout(() => {
                        reviewSwiperInstance.update();
                        console.log("[Homepage JS v8] Swiper layout updated after text toggle.");
                    }, 50);
                }
            }
        });
        console.log("[Homepage JS v8] 'Read More' event listener attached to swiper wrapper.");

        document.querySelectorAll('.review-card-new').forEach(card => {
            const textElement = card.querySelector('.review-text');
            const readMoreLink = card.querySelector('.read-more');
            if (textElement && readMoreLink) {
                readMoreLink.style.display = textElement.classList.contains('truncated') ? 'inline-block' : 'none';
            }
        });
        console.log("[Homepage JS v8] Initial visibility check for 'Read More' links finished (simplified).");

    } else {
        console.warn("[Homepage JS v8] Swiper wrapper '.swiper-review .swiper-wrapper' not found. 'Read More' functionality disabled.");
    }

    // --- Swiper Initialization for Gallery (beze změny) ---
    try {
        const gallerySwiperElement = document.querySelector('.gallerySwiper');
        if (gallerySwiperElement && typeof Swiper !== 'undefined') {
            const gallerySwiper = new Swiper(gallerySwiperElement, {
                loop: true,
                slidesPerView: 1,
                spaceBetween: 10,
                breakpoints: {
                    576: { slidesPerView: 2, spaceBetween: 20, },
                    768: { slidesPerView: 3, spaceBetween: 30, },
                },
                pagination: {
                    el: '.gallerySwiper .swiper-pagination',
                    clickable: true,
                },
                navigation: {
                    nextEl: '.gallerySwiper .swiper-button-next',
                    prevEl: '.gallerySwiper .swiper-button-prev',
                },
                a11y: {
                    prevSlideMessage: 'Předchozí obrázek',
                    nextSlideMessage: 'Další obrázek',
                    paginationBulletMessage: 'Přejít na obrázek {{index}}',
                },
            });
            console.log("[Homepage JS v8] Swiper for gallery initialized.");
        } else {
            if (!gallerySwiperElement) {
                console.warn("[Homepage JS v8] Swiper container '.gallerySwiper' not found. Gallery Swiper not initialized.");
            }
            if (typeof Swiper === 'undefined') {
                console.error("[Homepage JS v8] Swiper library not loaded. Cannot initialize gallery carousel.");
                const galleryNavPrev = document.querySelector('.gallerySwiper .swiper-button-prev');
                const galleryNavNext = document.querySelector('.gallerySwiper .swiper-button-next');
                const galleryPagination = document.querySelector('.gallerySwiper .swiper-pagination');
                if(galleryNavPrev) galleryNavPrev.style.display = 'none';
                if(galleryNavNext) galleryNavNext.style.display = 'none';
                if(galleryPagination) galleryPagination.style.display = 'none';
            }
        }
    } catch (e) {
        console.error("[Homepage JS v8] Error initializing Swiper for gallery:", e);
    }
    // --- KONEC Swiper Initialization for Gallery ---

    // --- JavaScript pro propojení Swiper Galerie s Bootstrap Modalem (PŘIDÁNO/UPRAVENO) ---
    try {
        const gallerySwiperContainer = document.querySelector('.gallerySwiper');
        const modalElement = document.getElementById('galleryModal');
        const modalCarouselElement = document.getElementById('galleryCarousel');

        if (gallerySwiperContainer && modalElement && modalCarouselElement && typeof bootstrap !== 'undefined') {
            const galleryLinks = gallerySwiperContainer.querySelectorAll('.swiper-slide a[data-bs-toggle="modal"]');
            let modalCarouselInstance = bootstrap.Carousel.getInstance(modalCarouselElement);
            if (!modalCarouselInstance) {
                modalCarouselInstance = new bootstrap.Carousel(modalCarouselElement);
                console.log("[Homepage JS v8] Bootstrap Carousel instance created for modal.");
            }
            const modalInstance = new bootstrap.Modal(modalElement);

            galleryLinks.forEach((link) => { // Odebrán nepoužitý 'index'
                link.addEventListener('click', function(event) {
                    event.preventDefault(); // Zabráníme defaultnímu otevření modalu

                    const correctSlideIndex = parseInt(link.getAttribute('data-bs-slide-to'), 10);

                    if (!isNaN(correctSlideIndex) && modalCarouselInstance) {
                        console.log(`[Homepage JS v8] Clicked gallery image link. Target modal slide index: ${correctSlideIndex}`);
                        modalCarouselInstance.to(correctSlideIndex); // Nastavíme správný slide v modalu
                        modalInstance.show(); // Manuálně zobrazíme modal
                    } else {
                        console.error(`[Homepage JS v8] Could not get correct slide index ('${link.getAttribute('data-bs-slide-to')}') or modalCarouselInstance is null.`);
                    }
                });
            });
            console.log("[Homepage JS v8] Event listeners attached to gallery links for modal interaction.");

        } else {
            if (!gallerySwiperContainer) console.warn("[Homepage JS v8] Gallery Swiper container '.gallerySwiper' not found for modal linking.");
            if (!modalElement) console.warn("[Homepage JS v8] Modal element '#galleryModal' not found.");
            if (!modalCarouselElement) console.warn("[Homepage JS v8] Modal carousel element '#galleryCarousel' not found.");
            if (typeof bootstrap === 'undefined') console.error("[Homepage JS v8] Bootstrap JS library not loaded.");
        }
    } catch(e) {
        console.error("[Homepage JS v8] Error setting up gallery modal interaction:", e);
    }
    // --- KONEC JavaScriptu pro modal ---


    console.log("[Homepage JS v8] Initialization finished.");
}); // Konec DOMContentLoaded