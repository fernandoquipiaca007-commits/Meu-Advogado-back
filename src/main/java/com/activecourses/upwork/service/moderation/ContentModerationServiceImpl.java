package com.activecourses.upwork.service.moderation;

import com.activecourses.upwork.exception.ContentModerationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ContentModerationServiceImpl implements ContentModerationService {

    // 1. Processo Judicial CNJ: NNNNNNN-DD.AAAA.J.TR.OOOO ou sequencial de 20 dígitos
    private static final Pattern CNJ_FORMATTED_PATTERN = Pattern.compile(
            "\\b\\d{7}[-\\.]?\\d{2}[\\.]?\\d{4}[\\.]?\\d[\\.]?\\d{2}[\\.]?\\d{4}\\b"
    );
    private static final Pattern CNJ_UNFORMATTED_PATTERN = Pattern.compile(
            "\\b\\d{20}\\b"
    );

    // 2. CPF (Cadastro de Pessoas Físicas)
    private static final Pattern CPF_FORMATTED_PATTERN = Pattern.compile(
            "\\b\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\b"
    );
    private static final Pattern CPF_UNFORMATTED_PATTERN = Pattern.compile(
            "(?<!\\d)\\d{11}(?!\\d)"
    );

    // 3. CNPJ (Cadastro Nacional da Pessoa Jurídica)
    private static final Pattern CNPJ_FORMATTED_PATTERN = Pattern.compile(
            "\\b\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}\\b"
    );
    private static final Pattern CNPJ_UNFORMATTED_PATTERN = Pattern.compile(
            "(?<!\\d)\\d{14}(?!\\d)"
    );

    // 4. Endereço de E-mail
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b",
            Pattern.CASE_INSENSITIVE
    );

    // 5. Telefones Nacionais (Fixo e Celular)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?:(?:\\+?55\\s?)?(?:\\(?0?[1-9]{2}\\)?\\s?)?(?:9\\s?\\d{4}|\\d{4})[-\\s]?\\d{4})\\b"
    );

    // 6. URLs Externas e Links Web
    private static final Pattern URL_PATTERN = Pattern.compile(
            "\\b(?:https?://|www\\.)\\S+\\b",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public void validateJobContent(String title, String description) {
        List<String> violations = new ArrayList<>();

        if (title != null && !title.trim().isEmpty()) {
            List<String> titleViolations = findViolations(title);
            for (String v : titleViolations) {
                violations.add("Título: " + v);
            }
        }

        if (description != null && !description.trim().isEmpty()) {
            List<String> descViolations = findViolations(description);
            for (String v : descViolations) {
                violations.add("Descrição: " + v);
            }
        }

        if (!violations.isEmpty()) {
            throw new ContentModerationException(
                    "Identificamos dados sensíveis, contatos diretos ou números de processo no conteúdo da demanda.",
                    violations
            );
        }
    }

    @Override
    public void validate(String fieldName, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        List<String> violations = findViolations(text);
        if (!violations.isEmpty()) {
            List<String> formatted = violations.stream()
                    .map(v -> (fieldName != null ? fieldName + ": " : "") + v)
                    .toList();
            throw new ContentModerationException(
                    "Conteúdo contém dados proibidos para publicação pública.",
                    formatted
            );
        }
    }

    @Override
    public List<String> findViolations(String text) {
        List<String> violations = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return violations;
        }

        if (CNJ_FORMATTED_PATTERN.matcher(text).find() || CNJ_UNFORMATTED_PATTERN.matcher(text).find()) {
            violations.add("Número de processo judicial (CNJ) não permitido no resumo ou título da demanda");
        }
        if (CPF_FORMATTED_PATTERN.matcher(text).find() || CPF_UNFORMATTED_PATTERN.matcher(text).find()) {
            violations.add("CPF não permitido no conteúdo da demanda");
        }
        if (CNPJ_FORMATTED_PATTERN.matcher(text).find() || CNPJ_UNFORMATTED_PATTERN.matcher(text).find()) {
            violations.add("CNPJ não permitido no conteúdo da demanda");
        }
        if (EMAIL_PATTERN.matcher(text).find()) {
            violations.add("E-mail de contato não permitido no conteúdo da demanda");
        }
        if (PHONE_PATTERN.matcher(text).find()) {
            violations.add("Telefone de contato não permitido no conteúdo da demanda");
        }
        if (URL_PATTERN.matcher(text).find()) {
            violations.add("Links externos ou URLs não permitidos no conteúdo da demanda");
        }

        return violations;
    }

    @Override
    public String maskSensitiveContent(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String masked = text;

        // Mask CNJ first (longer numbers)
        masked = CNJ_FORMATTED_PATTERN.matcher(masked).replaceAll("[PROCESSO OCULTO]");
        masked = CNJ_UNFORMATTED_PATTERN.matcher(masked).replaceAll("[PROCESSO OCULTO]");

        // Mask CNPJ
        masked = CNPJ_FORMATTED_PATTERN.matcher(masked).replaceAll("[CNPJ OCULTO]");
        masked = CNPJ_UNFORMATTED_PATTERN.matcher(masked).replaceAll("[CNPJ OCULTO]");

        // Mask CPF
        masked = CPF_FORMATTED_PATTERN.matcher(masked).replaceAll("[CPF OCULTO]");
        masked = CPF_UNFORMATTED_PATTERN.matcher(masked).replaceAll("[CPF OCULTO]");

        // Mask Email
        masked = EMAIL_PATTERN.matcher(masked).replaceAll("[E-MAIL OCULTO]");

        // Mask Phone
        masked = PHONE_PATTERN.matcher(masked).replaceAll("[CONTATO OCULTO]");

        // Mask URLs
        masked = URL_PATTERN.matcher(masked).replaceAll("[LINK EXTERNO OCULTO]");

        return masked;
    }

    @Override
    public boolean containsSensitiveContent(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        return !findViolations(text).isEmpty();
    }
}
