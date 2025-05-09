document.addEventListener("DOMContentLoaded", function () {
    console.log("[Homepage JS v8 - Cleaned] DOMContentLoaded event.");
    try {
        const e = document.getElementById("typing-strings");
        if (e && "undefined" != typeof TypeIt) {
            const t = Array.from(e.children).map(e => e.textContent), o = document.getElementById("typed-output");
            o && t.length > 0 ? (console.log("[Homepage JS v8 - Cleaned] Initializing TypeIt animation..."), new TypeIt("#typed-output", {
                strings: t,
                speed: 70,
                breakLines: !1,
                waitUntilVisible: !0,
                loop: !0,
                loopDelay: 2e3,
                deleteSpeed: 50,
                lifeLike: !0,
                cursor: !1
            }).go(), console.log("[Homepage JS v8 - Cleaned] TypeIt initialized.")) : console.warn("[Homepage JS v8 - Cleaned] TypeIt target element #typed-output or strings not found.")
        } else console.warn("[Homepage JS v8 - Cleaned] Typing animation element #typing-strings or TypeIt library not found.")
    } catch (e) {
        console.error("[Homepage JS v8 - Cleaned] Error during TypeIt initialization:", e)
    }
    let e = null;
    try {
        const t = document.querySelector(".swiper-review");
        if (t && "undefined" != typeof Swiper) e = new Swiper(t, {
            loop: !1,
            slidesPerView: 1,
            spaceBetween: 16,
            navigation: {nextEl: ".swiper-review .swiper-button-next", prevEl: ".swiper-review .swiper-button-prev"},
            breakpoints: {768: {slidesPerView: 2, spaceBetween: 20}, 992: {slidesPerView: 3, spaceBetween: 24}},
            a11y: {prevSlideMessage: "Předchozí recenze", nextSlideMessage: "Další recenze"}
        }), console.log("[Homepage JS v8 - Cleaned] Swiper for reviews initialized."); else {
            if (!t) console.warn("[Homepage JS v8 - Cleaned] Swiper container '.swiper-review' not found. Swiper not initialized.");
            if ("undefined" == typeof Swiper) {
                console.error("[Homepage JS v8 - Cleaned] Swiper library not loaded. Cannot initialize reviews carousel.");
                const e = document.querySelector(".swiper-review .swiper-button-prev"),
                    o = document.querySelector(".swiper-review .swiper-button-next");
                e && (e.style.display = "none"), o && (o.style.display = "none")
            }
        }
    } catch (t) {
        console.error("[Homepage JS v8 - Cleaned] Error initializing Swiper for reviews:", t)
    }
    const t = document.querySelector(".swiper-review .swiper-wrapper");
    t ? (t.addEventListener("click", function (t) {
        if (t.target.classList.contains("read-more")) {
            t.preventDefault(), console.log("[Homepage JS v8 - Cleaned] Read more link clicked.");
            const o = t.target.closest(".review-card-new");
            if (!o) return;
            const n = o.querySelector(".review-text");
            n && (n.classList.toggle("truncated"), t.target.textContent = n.classList.contains("truncated") ? "Přečtěte si více" : "Zobrazit méně", console.log("[Homepage JS v8 - Cleaned] Toggled 'truncated' class. Is now truncated:", n.classList.contains("truncated")), e && "function" == typeof e.update && setTimeout(() => {
                e.update(), console.log("[Homepage JS v8 - Cleaned] Swiper layout updated after text toggle.")
            }, 50))
        }
    }), console.log("[Homepage JS v8 - Cleaned] 'Read More' event listener attached to swiper wrapper."), document.querySelectorAll(".review-card-new").forEach(e => {
        const t = e.querySelector(".review-text"), o = e.querySelector(".read-more");
        t && o && (o.style.display = t.classList.contains("truncated") ? "inline-block" : "none")
    }), console.log("[Homepage JS v8 - Cleaned] Initial visibility check for 'Read More' links finished (simplified).")) : console.warn("[Homepage JS v8 - Cleaned] Swiper wrapper '.swiper-review .swiper-wrapper' not found. 'Read More' functionality disabled.");
    try {
        const e = document.querySelector(".gallerySwiper");
        if (e && "undefined" != typeof Swiper) new Swiper(e, {
            loop: !0,
            slidesPerView: 1,
            spaceBetween: 10,
            breakpoints: {576: {slidesPerView: 2, spaceBetween: 20}, 768: {slidesPerView: 3, spaceBetween: 30}},
            pagination: {el: ".gallerySwiper .swiper-pagination", clickable: !0},
            navigation: {nextEl: ".gallerySwiper .swiper-button-next", prevEl: ".gallerySwiper .swiper-button-prev"},
            a11y: {
                prevSlideMessage: "Předchozí obrázek",
                nextSlideMessage: "Další obrázek",
                paginationBulletMessage: "Přejít na obrázek {{index}}"
            }
        }), console.log("[Homepage JS v8 - Cleaned] Swiper for gallery initialized."); else {
            if (!e) console.warn("[Homepage JS v8 - Cleaned] Swiper container '.gallerySwiper' not found. Gallery Swiper not initialized.");
            if ("undefined" == typeof Swiper) {
                console.error("[Homepage JS v8 - Cleaned] Swiper library not loaded. Cannot initialize gallery carousel.");
                const e = document.querySelector(".gallerySwiper .swiper-button-prev"),
                    t = document.querySelector(".gallerySwiper .swiper-button-next"),
                    o = document.querySelector(".gallerySwiper .swiper-pagination");
                e && (e.style.display = "none"), t && (t.style.display = "none"), o && (o.style.display = "none")
            }
        }
    } catch (e) {
        console.error("[Homepage JS v8 - Cleaned] Error initializing Swiper for gallery:", e)
    }
    console.log("[Homepage JS v8 - Cleaned] Initialization finished.")
    // === UPRAVENÁ SEKCE PRO KONTAKTY ===
    console.log("[Event Listeners] Připojuji sledování kliknutí na kontakty...");
    const contactLinks = document.querySelectorAll('.track-contact-click');

    contactLinks.forEach(link => {
        link.addEventListener('click', function (event) {
            let method = 'unknown';
            let value = '';
            const href = this.href;

            // Extrakce metody a hodnoty (stejná logika jako předtím)
            if (href) {
                if (href.startsWith('tel:')) {
                    method = 'phone';
                    value = href.substring(4);
                } else if (href.startsWith('mailto:')) {
                    method = 'email';
                    value = href.substring(7);
                } else {
                    method = this.dataset.contactMethod || 'link';
                    value = this.dataset.contactValue || this.textContent.trim();
                }
            } else {
                method = this.dataset.contactMethod || 'button';
                value = this.dataset.contactValue || this.textContent.trim();
            }

            console.log(`[Contact Click] Detekováno kliknutí. Metoda: ${method}, Hodnota: ${value}`);

            // Použijeme executeTrackingWhenReady
            const contactMethodFinal = method;
            const contactValueFinal = value;
            executeTrackingWhenReady(
                () => window.trackingService.trackContact(contactMethodFinal, contactValueFinal),
                'trackContact'
            );

            // Výchozí akci nepřerušujeme
        });
    });

    if (contactLinks.length > 0) {
        console.log(`[Event Listeners] Připojeno ${contactLinks.length} posluchačů pro sledování kontaktů.`);
    } else {
        console.warn("[Event Listeners] Nebyly nalezeny žádné prvky s třídou .track-contact-click.");
    }
    // === KONEC UPRAVENÉ SEKCE PRO KONTAKTY ===


    console.log("[Homepage JS v8 - Cleaned] Initialization finished.");
}); // Konec DOMContentLoaded listeneru