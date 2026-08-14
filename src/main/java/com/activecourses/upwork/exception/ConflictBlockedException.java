package com.activecourses.upwork.exception;

public class ConflictBlockedException extends RuntimeException {
    public ConflictBlockedException(String message) {
        super(message);
    }
}
