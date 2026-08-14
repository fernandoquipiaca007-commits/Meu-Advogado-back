package com.activecourses.upwork.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;

/**
 * Stripe configuration properties.
 * Reads from:
 *   stripe.secret-key        → STRIPE_SECRET_KEY env var (Railway)
 *   stripe.webhook-secret    → STRIPE_WEBHOOK_SECRET env var (Railway)
 *   stripe.publishable-key   → STRIPE_PUBLISHABLE_KEY (public, ok in config)
 *
 * Startup validator: if stripe is enabled but secret/webhook are missing,
 * logs a WARNING and disables Stripe in-memory (never throws exception).
 */
@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "stripe")
public class StripeConfigProperties {

    private String secretKey = "";
    private String webhookSecret = "";
    private String publishableKey = "";

    private final FeatureFlags featureFlags;

    public StripeConfigProperties(FeatureFlags featureFlags) {
        this.featureFlags = featureFlags;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateOnStartup() {
        if (!featureFlags.isStripeEnabled()) {
            log.info("[STRIPE] Feature flag disabled — Stripe integration inactive.");
            return;
        }

        boolean secretMissing  = secretKey  == null || secretKey.isBlank();
        boolean webhookMissing = webhookSecret == null || webhookSecret.isBlank();

        if (secretMissing || webhookMissing) {
            log.warn("[STRIPE] ⚠️  stripe-enabled=true but credentials missing:");
            if (secretMissing)  log.warn("[STRIPE]   STRIPE_SECRET_KEY is not set");
            if (webhookMissing) log.warn("[STRIPE]   STRIPE_WEBHOOK_SECRET is not set");
            log.warn("[STRIPE]   Disabling Stripe in-memory. Set Railway variables to activate.");
            featureFlags.getFinancials().setStripeEnabled(false);
        } else {
            log.info("[STRIPE] ✅ Stripe sandbox credentials loaded. Secret key: sk_test_***{}",
                    secretKey.length() > 8 ? secretKey.substring(secretKey.length() - 4) : "***");
            log.info("[STRIPE] ✅ Publishable key: {}...", publishableKey.substring(0, Math.min(20, publishableKey.length())));
            // Initialize Stripe SDK with secret key
            com.stripe.Stripe.apiKey = secretKey;
            log.info("[STRIPE] ✅ Stripe SDK initialized for sandbox.");
        }
    }

    public boolean isFullyConfigured() {
        return secretKey != null && !secretKey.isBlank()
            && webhookSecret != null && !webhookSecret.isBlank()
            && publishableKey != null && !publishableKey.isBlank();
    }
}
