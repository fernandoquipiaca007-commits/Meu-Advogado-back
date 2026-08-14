package com.activecourses.upwork.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Feature flags configuration for LegaWork Business Rules v2.
 * Phase 1: Identity, Authorization and Data Foundation.
 */
@Configuration
@ConfigurationProperties(prefix = "legawork.features")
public class FeatureFlags {

    public static final boolean STRIPE_ENABLED_DEFAULT = false;
    public static final boolean PAYPAL_PAYOUTS_ENABLED_DEFAULT = false;
    public static final boolean FUNDS_HOLD_ENABLED_DEFAULT = false;
    public static final boolean AUTO_RELEASE_ENABLED_DEFAULT = false;
    public static final boolean COOKIE_SESSION_ENABLED_DEFAULT = false;

    private Financials financials = new Financials();
    private Payouts payouts = new Payouts();
    private Funds funds = new Funds();
    private Automation automation = new Automation();
    private Auth auth = new Auth();

    public static class Financials {
        private boolean stripeEnabled = STRIPE_ENABLED_DEFAULT;

        public boolean isStripeEnabled() {
            return stripeEnabled;
        }

        public void setStripeEnabled(boolean stripeEnabled) {
            this.stripeEnabled = stripeEnabled;
        }
    }

    public static class Payouts {
        private boolean paypalEnabled = PAYPAL_PAYOUTS_ENABLED_DEFAULT;

        public boolean isPaypalEnabled() {
            return paypalEnabled;
        }

        public void setPaypalEnabled(boolean paypalEnabled) {
            this.paypalEnabled = paypalEnabled;
        }
    }

    public static class Funds {
        private boolean holdEnabled = FUNDS_HOLD_ENABLED_DEFAULT;

        public boolean isHoldEnabled() {
            return holdEnabled;
        }

        public void setHoldEnabled(boolean holdEnabled) {
            this.holdEnabled = holdEnabled;
        }
    }

    public static class Automation {
        private boolean autoReleaseEnabled = AUTO_RELEASE_ENABLED_DEFAULT;

        public boolean isAutoReleaseEnabled() {
            return autoReleaseEnabled;
        }

        public void setAutoReleaseEnabled(boolean autoReleaseEnabled) {
            this.autoReleaseEnabled = autoReleaseEnabled;
        }
    }

    public static class Auth {
        private boolean cookieSessionEnabled = COOKIE_SESSION_ENABLED_DEFAULT;

        public boolean isCookieSessionEnabled() {
            return cookieSessionEnabled;
        }

        public void setCookieSessionEnabled(boolean cookieSessionEnabled) {
            this.cookieSessionEnabled = cookieSessionEnabled;
        }
    }

    public Financials getFinancials() {
        return financials;
    }

    public void setFinancials(Financials financials) {
        this.financials = financials;
    }

    public Payouts getPayouts() {
        return payouts;
    }

    public void setPayouts(Payouts payouts) {
        this.payouts = payouts;
    }

    public Funds getFunds() {
        return funds;
    }

    public void setFunds(Funds funds) {
        this.funds = funds;
    }

    public Automation getAutomation() {
        return automation;
    }

    public void setAutomation(Automation automation) {
        this.automation = automation;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public boolean isStripeEnabled() {
        return financials != null && financials.isStripeEnabled();
    }

    public boolean isPaypalPayoutsEnabled() {
        return payouts != null && payouts.isPaypalEnabled();
    }

    public boolean isFundsHoldEnabled() {
        return funds != null && funds.isHoldEnabled();
    }

    public boolean isAutoReleaseEnabled() {
        return automation != null && automation.isAutoReleaseEnabled();
    }

    public boolean isCookieSessionEnabled() {
        return auth != null && auth.isCookieSessionEnabled();
    }
}
