package org.example.eshop.controller; // Adjust package if needed

import org.example.eshop.admin.controller.AdminCouponController;
import org.example.eshop.config.SecurityTestConfig;
import org.example.eshop.model.Coupon;
import org.example.eshop.service.CouponService;
import org.example.eshop.service.CurrencyService; // For advice bean
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminCouponController.class)
@WithMockUser(roles = "ADMIN")
@Import(SecurityTestConfig.class)
class AdminCouponControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private CouponService couponService;
    @MockBean private CurrencyService currencyService; // Mock advice bean

    private Coupon couponPercent;
    private Coupon couponFreeShipping;

    @BeforeEach
    void setUp() {
        couponPercent = new Coupon(); couponPercent.setId(1L); couponPercent.setCode("SLEVA10"); couponPercent.setName("Sleva 10%");
        couponPercent.setPercentage(true); couponPercent.setValue(new BigDecimal("10.00")); couponPercent.setFreeShipping(false);
        couponPercent.setStartDate(LocalDateTime.now().minusDays(1)); couponPercent.setExpirationDate(LocalDateTime.now().plusDays(10));
        couponPercent.setActive(true);

        couponFreeShipping = new Coupon(); couponFreeShipping.setId(2L); couponFreeShipping.setCode("FREESHIP"); couponFreeShipping.setName("Doprava zdarma");
        couponFreeShipping.setPercentage(false); couponFreeShipping.setFreeShipping(true);
        couponFreeShipping.setStartDate(LocalDateTime.now().minusDays(1)); couponFreeShipping.setExpirationDate(LocalDateTime.now().plusDays(5));
        couponFreeShipping.setActive(true);

        lenient().when(currencyService.getSelectedCurrency()).thenReturn("CZK"); // Mock advice
    }

    @Test
    @DisplayName("GET /admin/coupons - Zobrazí seznam")
    void listCoupons_Success() throws Exception {
        when(couponService.getAllCoupons()).thenReturn(Arrays.asList(couponPercent, couponFreeShipping));
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/coupons"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/coupons-list"))
                .andExpect(model().attributeExists("coupons"))
                .andExpect(model().attribute("coupons", hasSize(2)));
        verify(couponService).getAllCoupons();
    }

    @Test
    @DisplayName("GET /admin/coupons/new - Zobrazí formulář")
    void showCreateForm_Success() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/coupons/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/coupon-form"))
                .andExpect(model().attributeExists("coupon", "pageTitle"))
                .andExpect(model().attribute("coupon", hasProperty("id", nullValue())));
    }

    @Test
    @DisplayName("POST /admin/coupons - Úspěšné vytvoření (procenta + doprava)")
    void createCoupon_Combined_Success() throws Exception {
        Coupon createdCoupon = new Coupon(); createdCoupon.setId(3L); createdCoupon.setCode("KOMBO15");
        when(couponService.createCoupon(any(Coupon.class))).thenReturn(createdCoupon);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/coupons")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("code", "KOMBO15")
                        .param("name", "Kombinovaná Sleva 15%")
                        .param("isPercentage", "true") // Typ procenta
                        .param("value", "15.00")       // Hodnota procent
                        .param("freeShipping", "true")  // + Doprava zdarma
                        .param("startDateString", "2025-04-01")
                        .param("active", "true")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/coupons"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(couponService).createCoupon(argThat(coupon ->
                coupon.isPercentage() && coupon.isFreeShipping() && coupon.getValue().compareTo(new BigDecimal("15.00")) == 0
        ));
    }

    @Test
    @DisplayName("POST /admin/coupons - Úspěšné vytvoření (jen doprava zdarma)")
    void createCoupon_FreeShippingOnly_Success() throws Exception {
        Coupon createdCoupon = new Coupon(); createdCoupon.setId(4L); createdCoupon.setCode("SHIPONLY");
        when(couponService.createCoupon(any(Coupon.class))).thenReturn(createdCoupon);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/coupons")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("code", "SHIPONLY")
                        .param("name", "Jen Doprava")
                        .param("isPercentage", "false") // Typ fixní/doprava
                        .param("valueCZK", "0")        // Nulová hodnota
                        .param("valueEUR", "0")        // Nulová hodnota
                        .param("freeShipping", "true") // Doprava zdarma zaškrtnuta
                        .param("startDateString", "2025-04-01")
                        .param("active", "true")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/coupons"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(couponService).createCoupon(argThat(coupon ->
                !coupon.isPercentage() && coupon.isFreeShipping()
                        && (coupon.getValueCZK() == null || coupon.getValueCZK().compareTo(BigDecimal.ZERO)==0) // Service by měl normalizovat
                        && (coupon.getValueEUR() == null || coupon.getValueEUR().compareTo(BigDecimal.ZERO)==0)
        ));
    }

    @Test
    @DisplayName("POST /admin/coupons - Chyba validace (služba vrátí IllegalArgumentException)")
    void createCoupon_ServiceValidationError() throws Exception {
        String errorMessage = "Hodnota slevy nebo doprava zdarma musí být nastavena.";
        when(couponService.createCoupon(any(Coupon.class))).thenThrow(new IllegalArgumentException(errorMessage));

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/coupons")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("code", "CHYBA")
                        .param("name", "Chybný Kupón")
                        .param("isPercentage", "false")
                        .param("freeShipping", "false") // Není sleva ani doprava
                        .param("valueCZK", "") // Prázdné hodnoty
                        .param("valueEUR", "")
                        .param("active", "true")
                )
                .andExpect(status().isOk())
                .andExpect(view().name("admin/coupon-form"))
                .andExpect(model().attributeExists("coupon", "pageTitle"))
                .andExpect(model().attribute("errorMessage", containsString(errorMessage))); // Očekáváme obecnou chybu z controlleru

        verify(couponService).createCoupon(any(Coupon.class));
    }

    @Test
    @DisplayName("GET /admin/coupons/{id}/edit - Zobrazí formulář pro úpravu")
    void showEditForm_Success() throws Exception {
        when(couponService.findById(2L)).thenReturn(Optional.of(couponFreeShipping));

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/coupons/2/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/coupon-form"))
                .andExpect(model().attributeExists("coupon", "pageTitle", "isPercentage", "freeShipping"))
                .andExpect(model().attribute("coupon", hasProperty("id", is(2L))))
                .andExpect(model().attribute("isPercentage", is(false)))
                .andExpect(model().attribute("freeShipping", is(true)));

        verify(couponService).findById(2L);
    }

    @Test
    @DisplayName("POST /admin/coupons/{id} - Úspěšná aktualizace")
    void updateCoupon_Success() throws Exception {
        long id = 1L;
        Coupon updatedCoupon = new Coupon(); updatedCoupon.setId(id); updatedCoupon.setCode("SLEVA10"); updatedCoupon.setName("Upravená Sleva");
        when(couponService.updateCoupon(eq(id), any(Coupon.class))).thenReturn(updatedCoupon);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/coupons/{id}", id)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("code", "SLEVA10")
                        .param("name", "Upravená Sleva")
                        .param("isPercentage", "true")
                        .param("value", "12.00") // Změna hodnoty
                        .param("freeShipping", "true") // Přidání dopravy
                        .param("active", "true")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/coupons"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(couponService).updateCoupon(eq(id), argThat(coupon ->
                coupon.isPercentage() && coupon.isFreeShipping() && coupon.getValue().compareTo(new BigDecimal("12.00")) == 0
        ));
    }

    @Test
    @DisplayName("POST /admin/coupons/{id}/delete - Úspěšná deaktivace")
    void deactivateCoupon_Success() throws Exception {
        long id = 1L;
        doNothing().when(couponService).deactivateCoupon(id);
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/coupons/{id}/delete", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/coupons"))
                .andExpect(flash().attributeExists("successMessage"));
        verify(couponService).deactivateCoupon(id);
    }
}