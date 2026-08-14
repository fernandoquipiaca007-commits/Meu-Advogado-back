package com.activecourses.upwork.exception;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class ContentModerationException extends RuntimeException {

    private final List<String> violations;

    public ContentModerationException(String message) {
        super(message);
        this.violations = List.of(message);
    }

    public ContentModerationException(String message, List<String> violations) {
        super(message);
        this.violations = violations != null ? violations : new ArrayList<>();
    }
}
