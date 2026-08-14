package com.activecourses.upwork.service.moderation;

import java.util.List;

public interface ContentModerationService {

    void validateJobContent(String title, String description);

    void validate(String fieldName, String text);

    List<String> findViolations(String text);

    String maskSensitiveContent(String text);

    boolean containsSensitiveContent(String text);
}
