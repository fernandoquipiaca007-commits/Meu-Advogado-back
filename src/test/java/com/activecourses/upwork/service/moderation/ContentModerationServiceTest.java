package com.activecourses.upwork.service.moderation;

import com.activecourses.upwork.exception.ContentModerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContentModerationServiceTest {

    private ContentModerationService moderationService;

    @BeforeEach
    void setUp() {
        moderationService = new ContentModerationServiceImpl();
    }

    @Test
    @DisplayName("Should detect formatted CNJ process number and throw ContentModerationException")
    void testDetectFormattedCNJ() {
        String text = "Preciso de auxílio para o processo 0001234-56.2023.8.26.0100 que está em andamento.";
        assertThrows(ContentModerationException.class, () ->
                moderationService.validateJobContent("Defesa Cível", text)
        );
    }

    @Test
    @DisplayName("Should detect unformatted 20-digit CNJ process number")
    void testDetectUnformattedCNJ() {
        String text = "Processo número 00012345620238260100 no TJSP";
        List<String> violations = moderationService.findViolations(text);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.contains("CNJ")));
    }

    @Test
    @DisplayName("Should detect CPF in formatted and unformatted styles")
    void testDetectCPF() {
        String formatted = "Meu documento é 123.456.789-00 para cadastro.";
        List<String> violations = moderationService.findViolations(formatted);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.contains("CPF")));

        String unformatted = "Documento 12345678900 do requerente";
        List<String> violationsUnformatted = moderationService.findViolations(unformatted);
        assertFalse(violationsUnformatted.isEmpty());
        assertTrue(violationsUnformatted.stream().anyMatch(v -> v.contains("CPF")));
    }

    @Test
    @DisplayName("Should detect CNPJ in formatted and unformatted styles")
    void testDetectCNPJ() {
        String formatted = "Empresa registrada sob o CNPJ 12.345.678/0001-90.";
        List<String> violations = moderationService.findViolations(formatted);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.contains("CNPJ")));

        String unformatted = "CNPJ 12345678000190 matriz";
        List<String> violationsUnformatted = moderationService.findViolations(unformatted);
        assertFalse(violationsUnformatted.isEmpty());
        assertTrue(violationsUnformatted.stream().anyMatch(v -> v.contains("CNPJ")));
    }

    @Test
    @DisplayName("Should detect direct email address")
    void testDetectEmail() {
        String text = "Favor entrar em contato pelo e-mail contato@cliente.com.br para enviar os documentos.";
        List<String> violations = moderationService.findViolations(text);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.contains("E-mail")));
    }

    @Test
    @DisplayName("Should detect Brazilian phone numbers")
    void testDetectPhone() {
        String text1 = "Me chame no WhatsApp (11) 98765-4321 para detalhes.";
        List<String> violations1 = moderationService.findViolations(text1);
        assertFalse(violations1.isEmpty());
        assertTrue(violations1.stream().anyMatch(v -> v.contains("Telefone")));

        String text2 = "Telefone para contato: +55 21 99999-8888";
        List<String> violations2 = moderationService.findViolations(text2);
        assertFalse(violations2.isEmpty());
        assertTrue(violations2.stream().anyMatch(v -> v.contains("Telefone")));
    }

    @Test
    @DisplayName("Should detect external URLs")
    void testDetectExternalUrl() {
        String text = "Veja o edital completo no link https://meusiteexterno.com/edital.pdf";
        List<String> violations = moderationService.findViolations(text);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.contains("URLs")));
    }

    @Test
    @DisplayName("Should pass clean legal description without violations")
    void testCleanLegalDescription() {
        String title = "Elaboração de Contestação em Ação de Cobrança";
        String description = "Busco advogado especialista em direito civil e contratos bancários para redigir contestação cível no prazo de 15 dias.";
        assertDoesNotThrow(() -> moderationService.validateJobContent(title, description));
        assertEquals(0, moderationService.findViolations(description).size());
    }

    @Test
    @DisplayName("Should correctly mask sensitive content in pre-contractual messages")
    void testMaskSensitiveContent() {
        String input = "Olá doutor, me ligue no (11) 98765-4321 ou envie para fulano@email.com. O processo é 0001234-56.2023.8.26.0100.";
        String masked = moderationService.maskSensitiveContent(input);

        assertFalse(masked.contains("(11) 98765-4321"));
        assertFalse(masked.contains("fulano@email.com"));
        assertFalse(masked.contains("0001234-56.2023.8.26.0100"));

        assertTrue(masked.contains("[CONTATO OCULTO]"));
        assertTrue(masked.contains("[E-MAIL OCULTO]"));
        assertTrue(masked.contains("[PROCESSO OCULTO]"));
    }
}
