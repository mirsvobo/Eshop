document.addEventListener('DOMContentLoaded', function() {
    console.log("[Global JS] DOMContentLoaded.");

    const loader = document.getElementById('page-loader');

    if (loader) {
        console.log("[Global JS] Page loader found.");
        // Skrytí loaderu, až když je *vše* načteno (včetně obrázků atd.)
        window.addEventListener('load', function() {
            console.log("[Global JS] Window 'load' event fired. Hiding loader.");
            loader.classList.add('hidden');
        });

        // Pojistka, kdyby 'load' event z nějakého důvodu selhal (např. chyba v obrázku)
        // Skryje loader po určité době, i kdyby 'load' nenastal
        setTimeout(() => {
            if (!loader.classList.contains('hidden')) {
                console.warn("[Global JS] Loader timeout reached. Forcing hide.");
                loader.classList.add('hidden');
            }
        }, 5000); // Např. 5 sekund
    } else {
        console.warn("[Global JS] Page loader element '#page-loader' not found.");
    }
});