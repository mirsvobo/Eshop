package org.example.eshop.admin.controller;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.config.SecurityTestConfig;
import org.example.eshop.model.EmailTemplateConfig;
import org.example.eshop.model.OrderState;
import org.example.eshop.repository.EmailTemplateConfigRepository; // Import repository
import org.example.eshop.service.CurrencyService;
import org.example.eshop.service.EmailService;
import org.example.eshop.service.OrderStateService;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminEmailConfigController.class)
@WithMockUser(roles = "ADMIN")
@Import(SecurityTestConfig.class)
class AdminEmailConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private EmailTemplateConfigRepository emailTemplateConfigRepository; // Mock repository
    @MockBean private OrderStateService orderStateService;
    @MockBean private EmailService emailService; // Pro čištění cache
    @MockBean private CurrencyService currencyService;

    private EmailTemplateConfig configShipped;
    private EmailTemplateConfig configProcessing;
    private OrderState stateNew;
    private OrderState stateShipped;

    @BeforeEach
    void setUp() {
        configShipped = new EmailTemplateConfig(); configShipped.setId(1L); configShipped.setStateCode("SHIPPED"); configShipped.setSendEmail(true); configShipped.setTemplateName("t1"); configShipped.setSubjectTemplate("Subj1");
        configProcessing = new EmailTemplateConfig(); configProcessing.setId(2L); configProcessing.setStateCode("PROCESSING"); configProcessing.setSendEmail(false); configProcessing.setTemplateName("t2"); configProcessing.setSubjectTemplate("Subj2");

        stateNew = new OrderState(); stateNew.setCode("NEW"); stateNew.setName("Nová");
        stateShipped = new OrderState(); stateShipped.setCode("SHIPPED"); stateShipped.setName("Odesláno");

        lenient().when(currencyService.getSelectedCurrency()).thenReturn("CZK");
        lenient().when(orderStateService.getAllOrderStatesSorted()).thenReturn(Arrays.asList(stateNew, stateShipped));
        lenient().doNothing().when(emailService).clearConfigCache(); // Mock čištění cache
    }

    @Test
    @DisplayName("GET /admin/email-configs - Zobrazí seznam")
    void listEmailConfigs_Success() throws Exception {
        List<EmailTemplateConfig> configList = Arrays.asList(configProcessing, configShipped);
        when(emailTemplateConfigRepository.findAll()).thenReturn(configList);

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/email-configs"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/email-configs-list"))
                .andExpect(model().attributeExists("emailConfigs", "orderStateNames"))
                .andExpect(model().attribute("emailConfigs", hasSize(2)))
                .andExpect(model().attribute("orderStateNames", hasEntry("SHIPPED", "Odesláno"))); // Ověříme načtení názvu stavu

        verify(emailTemplateConfigRepository).findAll();
        verify(orderStateService).getAllOrderStatesSorted(); // Ověříme načtení stavů
    }

    @Test
    @DisplayName("GET /admin/email-configs/new - Zobrazí formulář")
    void showCreateEmailConfigForm_Success() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/email-configs/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/email-config-form"))
                .andExpect(model().attributeExists("emailTemplateConfig", "allOrderStates", "pageTitle"))
                .andExpect(model().attribute("emailTemplateConfig", instanceOf(EmailTemplateConfig.class)))
                .andExpect(model().attribute("allOrderStates", hasSize(2)));

        verify(orderStateService).getAllOrderStatesSorted();
    }

    @Test
    @DisplayName("POST /admin/email-configs - Úspěšné vytvoření")
    void createEmailConfig_Success() throws Exception {
        EmailTemplateConfig savedConfig = new EmailTemplateConfig(); savedConfig.setId(3L); savedConfig.setStateCode("NEW");
        when(emailTemplateConfigRepository.findByStateCodeIgnoreCase("NEW")).thenReturn(Optional.empty());
        when(emailTemplateConfigRepository.save(any(EmailTemplateConfig.class))).thenReturn(savedConfig);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/email-configs")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("stateCode", "NEW")
                        .param("sendEmail", "true")
                        .param("templateName", "emails/new-order")
                        .param("subjectTemplate", "Nová objednávka {orderCode}")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/email-configs"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(emailTemplateConfigRepository).save(any(EmailTemplateConfig.class));
        verify(emailService).clearConfigCache(); // Ověření čištění cache
    }

    @Test
    @DisplayName("POST /admin/email-configs - Chyba (duplicitní stateCode)")
    void createEmailConfig_DuplicateStateCode() throws Exception {
        when(emailTemplateConfigRepository.findByStateCodeIgnoreCase("SHIPPED")).thenReturn(Optional.of(configShipped));

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/email-configs")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("stateCode", "SHIPPED") // Duplicitní kód
                        .param("sendEmail", "true")
                        .param("templateName", "some/template")
                        .param("subjectTemplate", "Some Subject")
                )
                .andExpect(status().isOk())
                .andExpect(view().name("admin/email-config-form"))
                .andExpect(model().attributeExists("emailTemplateConfig", "allOrderStates", "pageTitle"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrorCode("emailTemplateConfig", "stateCode", "error.emailConfig.duplicate"));

        verify(emailTemplateConfigRepository, never()).save(any());
        verify(orderStateService).getAllOrderStatesSorted(); // Znovu načíst stavy pro formulář
        verify(emailService, never()).clearConfigCache();
    }

    @Test
    @DisplayName("GET /admin/email-configs/{id}/edit - Zobrazí formulář")
    void showEditEmailConfigForm_Success() throws Exception {
        when(emailTemplateConfigRepository.findById(1L)).thenReturn(Optional.of(configShipped));

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/email-configs/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/email-config-form"))
                .andExpect(model().attributeExists("emailTemplateConfig", "allOrderStates", "pageTitle"))
                .andExpect(model().attribute("emailTemplateConfig", hasProperty("id", is(1L))));

        verify(emailTemplateConfigRepository).findById(1L);
        verify(orderStateService).getAllOrderStatesSorted();
    }

    @Test
    @DisplayName("POST /admin/email-configs/{id} - Úspěšná aktualizace")
    void updateEmailConfig_Success() throws Exception {
        long id = 1L;
        // Musíme mockovat findById pro načtení existující entity v controlleru
        when(emailTemplateConfigRepository.findById(id)).thenReturn(Optional.of(configShipped));
        // Mockujeme save, aby vrátil upravenou entitu
        when(emailTemplateConfigRepository.save(any(EmailTemplateConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        // Mockujeme kontrolu unikátnosti kódu (předpokládáme, že se nemění)
        when(emailTemplateConfigRepository.findByStateCodeIgnoreCase("SHIPPED")).thenReturn(Optional.of(configShipped));


        mockMvc.perform(MockMvcRequestBuilders.post("/admin/email-configs/{id}", id)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("stateCode", "SHIPPED") // Kód neměníme
                        .param("sendEmail", "false") // Měníme odesílání
                        .param("templateName", "new/template/path")
                        .param("subjectTemplate", "Nový předmět {orderCode}")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/email-configs"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(emailTemplateConfigRepository).findById(id);
        verify(emailTemplateConfigRepository).save(argThat(config ->
                !config.isSendEmail() && "new/template/path".equals(config.getTemplateName())
        ));
        verify(emailService).clearConfigCache();
    }
}