package com.activecourses.upwork.exception;

public class FeatureDisabledException extends RuntimeException {
    private final String featureKey;

    public FeatureDisabledException(String featureKey) {
        super("A funcionalidade '" + featureKey + "' está desativada neste momento. " +
              "As credenciais ou aprovações necessárias ainda não foram configuradas.");
        this.featureKey = featureKey;
    }

    public FeatureDisabledException(String featureKey, String customMessage) {
        super(customMessage);
        this.featureKey = featureKey;
    }

    public String getFeatureKey() {
        return featureKey;
    }
}
