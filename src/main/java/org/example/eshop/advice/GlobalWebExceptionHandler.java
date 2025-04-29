package org.example.eshop.advice; // Ujistěte se, že balíček odpovídá vaší struktuře

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod; // <-- PŘIDÁN SPRÁVNÝ IMPORT
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Globální handler pro webové výjimky, který zajišťuje zobrazení
 * uživatelsky přívětivých chybových stránek.
 * Má vysokou prioritu, aby přepsal výchozí Spring handlery.
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalWebExceptionHandler.class);

    // Cesty k Thymeleaf šablonám ve složce templates/error/
    public static final String NOT_FOUND_ERROR_VIEW = "error/404";
    public static final String FORBIDDEN_ERROR_VIEW = "error/403";
    public static final String INTERNAL_SERVER_ERROR_VIEW = "error/500";
    public static final String DEFAULT_ERROR_VIEW = "error"; // Obecná, pokud specifická neexistuje

    /**
     * Zpracovává výjimky vedoucí k chybě 404 Not Found.
     * Cílí na specifickou šablonu error/404.html.
     *
     * @param req Původní HTTP požadavek.
     * @param ex Zachycená výjimka (EntityNotFoundException nebo NoResourceFoundException).
     * @return ModelAndView pro zobrazení 404 stránky.
     */
    @ExceptionHandler({EntityNotFoundException.class, NoResourceFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView handleNotFoundException(HttpServletRequest req, Exception ex) {
        String errorMessage = (ex instanceof EntityNotFoundException)
                ? "Požadovaný zdroj nebyl nalezen."
                : "Požadovaná stránka neexistuje.";
        log.warn("Resource not found (404) handled by GlobalWebExceptionHandler: URL={}, Message={}", req.getRequestURL(), ex.getMessage());

        ModelAndView mav = new ModelAndView(NOT_FOUND_ERROR_VIEW);
        mav.addObject("errorCode", HttpStatus.NOT_FOUND.value());
        mav.addObject("errorTitle", "Stránka nenalezena");
        mav.addObject("errorMessage", errorMessage);
        Object requestUri = req.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        mav.addObject("path", requestUri != null ? requestUri.toString() : req.getRequestURI());
        mav.addObject("requestedUrl", req.getRequestURL());
        return mav;
    }

    /**
     * Zpracovává výjimky AccessDeniedException (403 Forbidden).
     * Cílí na specifickou šablonu error/403.html.
     *
     * @param req Původní HTTP požadavek.
     * @param ex Zachycená výjimka AccessDeniedException.
     * @return ModelAndView pro zobrazení 403 stránky.
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ModelAndView handleAccessDeniedException(HttpServletRequest req, AccessDeniedException ex) {
        log.warn("Access Denied (403) handled by GlobalWebExceptionHandler: URL={}, User={}, Message={}",
                req.getRequestURL(),
                req.getUserPrincipal() != null ? req.getUserPrincipal().getName() : "Anonymous",
                ex.getMessage());

        ModelAndView mav = new ModelAndView(FORBIDDEN_ERROR_VIEW);
        mav.addObject("errorCode", HttpStatus.FORBIDDEN.value());
        mav.addObject("errorTitle", "Přístup odepřen");
        mav.addObject("errorMessage", "Nemáte oprávnění pro přístup k této stránce nebo provedení požadované akce.");
        Object requestUri = req.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        mav.addObject("path", requestUri != null ? requestUri.toString() : req.getRequestURI());
        mav.addObject("requestedUrl", req.getRequestURL());
        return mav;
    }

    /**
     * Zpracovává všechny ostatní nezachycené výjimky (jako 500 Internal Server Error).
     * Cílí na specifickou šablonu error/500.html nebo obecnou error.html.
     *
     * @param req Původní HTTP požadavek.
     * @param ex Zachycená obecná výjimka Exception.
     * @return ModelAndView pro zobrazení 500 stránky nebo obecné chybové stránky.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR) // Default status pro obecné chyby
    public ModelAndView handleGenericException(HttpServletRequest req, Exception ex) {
        Object status = req.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int statusCode = HttpStatus.INTERNAL_SERVER_ERROR.value(); // Default na 500

        // Zkusíme získat skutečný status kód z requestu
        if (status != null) {
            try {
                statusCode = Integer.parseInt(status.toString());
            } catch (Exception parseEx) {
                log.warn("Could not parse error status code in generic handler: {}", status, parseEx);
            }
        }

        // --- Speciální ošetření pro 404 a 403, pokud je tento handler zachytí jako fallback ---
        if (statusCode == HttpStatus.NOT_FOUND.value()) {
            log.warn("GenericExceptionHandler caught a 404 for URL: {}. Redirecting to 404 handler logic.", req.getRequestURL());
            // ---- OPRAVA ZDE ----
            try {
                HttpMethod httpMethod = HttpMethod.valueOf(req.getMethod().toUpperCase()); // Získáme HttpMethod
                return handleNotFoundException(req, new NoResourceFoundException(httpMethod, req.getRequestURI()));
            } catch (IllegalArgumentException methodEx) {
                log.error("Invalid HTTP method '{}' encountered for 404 fallback.", req.getMethod(), methodEx);
                // Pokud metoda není validní, vrátíme obecnou 404
                return handleNotFoundException(req, new RuntimeException("Resource not found for invalid method " + req.getMethod()));
            }
            // ---- KONEC OPRAVY ----
        }
        if (statusCode == HttpStatus.FORBIDDEN.value()) {
            log.warn("GenericExceptionHandler caught a 403 for URL: {}. Redirecting to 403 handler logic.", req.getRequestURL());
            return handleAccessDeniedException(req, new AccessDeniedException("Access denied via generic handler fallback."));
        }

        // --- Zpracování jako 500 nebo jiná chyba ---
        log.error("Error ({}) handled by GlobalWebExceptionHandler: URL={}, Message={}", statusCode, req.getRequestURL(), ex.getMessage(), ex);

        ModelAndView mav = new ModelAndView(INTERNAL_SERVER_ERROR_VIEW); // Cílíme na error/500.html
        mav.addObject("errorCode", statusCode); // Použijeme zjištěný kód
        mav.addObject("errorTitle", "Chyba serveru"); // Obecný titul pro 500
        mav.addObject("errorMessage", "Omlouváme se, na serveru došlo k neočekávané technické chybě.");
        Object requestUri = req.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        mav.addObject("path", requestUri != null ? requestUri.toString() : req.getRequestURI());
        mav.addObject("requestedUrl", req.getRequestURL());
        mav.addObject("timestamp", java.time.LocalDateTime.now()); // Použij LocalDateTime        // mav.addObject("exception", ex); // Pouze pro dev
        // mav.addObject("trace", ExceptionUtils.getStackTrace(ex)); // Pouze pro dev
        return mav;
    }
}