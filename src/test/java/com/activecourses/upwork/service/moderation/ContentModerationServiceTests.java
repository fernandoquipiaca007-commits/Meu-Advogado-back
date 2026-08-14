package com.activecourses.upwork.service.moderation;

import com.activecourses.upwork.dto.JobDTO;
import com.activecourses.upwork.exception.ContentModerationException;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.service.job.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContentModerationServiceTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobService jobService;

    @MockBean
    private AuthService authService;

    private ContentModerationService moderationService;

    @BeforeEach
    void setUp() {
        moderationService = new ContentModerationServiceImpl();
    }

    @Test
    @DisplayName("Should detect formatted CNJ process number (1234567-89.2023.8.26.0100) and throw ContentModerationException")
    void testDetectFormattedCNJProcessNumber() {
        String formattedCNJ = "Ação revisional referente ao processo 1234567-89.2023.8.26.0100 em trâmite.";
        ContentModerationException ex = assertThrows(ContentModerationException.class, () ->
                moderationService.validateJobContent("Defesa Cível", formattedCNJ)
        );
        assertTrue(ex.getViolations().stream().anyMatch(v -> v.contains("CNJ")));
    }

    @Test
    @DisplayName("Should detect unformatted 20-digit CNJ process number and throw ContentModerationException")
    void testDetectUnformatted20DigitCNJ() {
        String unformattedCNJ = "Processo 12345678920238260100 na vara cível";
        List<String> violations = moderationService.findViolations(unformattedCNJ);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.contains("CNJ")));

        assertThrows(ContentModerationException.class, () ->
                moderationService.validate("Resumo", unformattedCNJ)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Meu CPF é 123.456.789-00 para contrato",
            "CPF sem pontos: 12345678900",
            "CNPJ da empresa: 12.345.678/0001-90",
            "CNPJ 12345678000190 matriz",
            "Envie proposta no email contato@cliente.com.br",
            "Ligue no WhatsApp (11) 98765-4321 urgente",
            "Telefone comercial +55 21 99999-8888",
            "Mais detalhes no site https://meusiteexterno.com/edital.pdf",
            "Acesse www.externo.com.br para baixar"
    })
    @DisplayName("Should detect CPF, CNPJ, Email, Phone, URL and throw ContentModerationException")
    void testDetectProhibitedPatterns(String offendingText) {
        List<String> violations = moderationService.findViolations(offendingText);
        assertFalse(violations.isEmpty(), "Expected violation for: " + offendingText);

        assertThrows(ContentModerationException.class, () ->
                moderationService.validateJobContent("Título da Demanda", offendingText)
        );
    }

    @Test
    @DisplayName("Should pass valid clean legal summary without violations")
    void testValidCleanLegalSummary() {
        String title = "Assessoria em Direito Societário e Redação de Estatuto";
        String summary = "Busco advogado especialista em contratos comerciais e governança corporativa para elaboração de estatuto social de startup no prazo de 20 dias.";

        assertDoesNotThrow(() -> moderationService.validateJobContent(title, summary));
        assertEquals(0, moderationService.findViolations(summary).size());
        assertFalse(moderationService.containsSensitiveContent(summary));
    }

    @Test
    @DisplayName("Should mask pre-contractual message PII (emails, phones, URLs, CPFs, CNJ)")
    void testPreContractualMessagePiiMasking() {
        String rawMessage = "Olá doutor, meu CPF é 123.456.789-00, me ligue no (11) 98765-4321 ou envie para contato@cliente.com.br. O processo é 1234567-89.2023.8.26.0100 e link https://externo.com.";
        String masked = moderationService.maskSensitiveContent(rawMessage);

        assertFalse(masked.contains("123.456.789-00"));
        assertFalse(masked.contains("(11) 98765-4321"));
        assertFalse(masked.contains("contato@cliente.com.br"));
        assertFalse(masked.contains("1234567-89.2023.8.26.0100"));
        assertFalse(masked.contains("https://externo.com"));

        assertTrue(masked.contains("[CPF OCULTO]"));
        assertTrue(masked.contains("[CONTATO OCULTO]"));
        assertTrue(masked.contains("[E-MAIL OCULTO]"));
        assertTrue(masked.contains("[PROCESSO OCULTO]"));
        assertTrue(masked.contains("[LINK EXTERNO OCULTO]"));
    }

    @Test
    @DisplayName("POST /api/jobs/post with CNJ process number should return 422 Unprocessable Entity")
    @WithMockUser(username = "client@legawork.com", roles = {"CLIENT"})
    void testPostJobWithCNJReturns422() throws Exception {
        when(authService.getCurrentUserId()).thenReturn(10);
        when(jobService.createJob(any(JobDTO.class))).thenThrow(
                new ContentModerationException(
                        "Identificamos dados sensíveis, contatos diretos ou números de processo no conteúdo da demanda.",
                        List.of("Descrição: Número de processo judicial (CNJ) não permitido no resumo ou título da demanda")
                )
        );

        String payload = """
                {
                    "title": "Ação de Cobrança",
                    "description": "Processo número 1234567-89.2023.8.26.0100 em andamento",
                    "budget": 5000
                }
                """;

        mockMvc.perform(post("/api/jobs/post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value("UNPROCESSABLE_ENTITY"));
    }

    @Test
    @DisplayName("POST /api/jobs/post with phone/email should return 422 Unprocessable Entity")
    @WithMockUser(username = "client@legawork.com", roles = {"CLIENT"})
    void testPostJobWithPhoneOrEmailReturns422() throws Exception {
        when(authService.getCurrentUserId()).thenReturn(10);
        when(jobService.createJob(any(JobDTO.class))).thenThrow(
                new ContentModerationException(
                        "Identificamos dados sensíveis no conteúdo da demanda.",
                        List.of("Descrição: Telefone de contato não permitido no conteúdo da demanda")
                )
        );

        String payload = """
                {
                    "title": "Consultoria Trabalhista",
                    "description": "Ligue para (11) 98765-4321 para detalhes da demanda",
                    "budget": 2000
                }
                """;

        mockMvc.perform(post("/api/jobs/post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
    }
}
