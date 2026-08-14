package com.activecourses.upwork.service.payment;

import com.activecourses.upwork.config.FeatureFlags;
import com.activecourses.upwork.exception.FeatureDisabledException;
import com.activecourses.upwork.model.Contract;
import com.activecourses.upwork.model.User;
import com.activecourses.upwork.repository.payment.PaymentIntentRepository;
import com.activecourses.upwork.repository.payment.ProviderEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 4 — Stripe Payment Adapter Tests")
class StripePaymentAdapterTest {

    @Mock private FeatureFlags featureFlags;
    @Mock private PaymentIntentRepository paymentIntentRepository;
    @Mock private ProviderEventRepository providerEventRepository;

    private StripePaymentAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new StripePaymentAdapter(featureFlags, paymentIntentRepository, providerEventRepository);
    }

    @Test
    @DisplayName("M4.8 — Feature flag disabled: createPaymentIntent throws FeatureDisabledException")
    void createPaymentIntent_whenStripeDisabled_throwsFeatureDisabledException() {
        when(featureFlags.isStripeEnabled()).thenReturn(false);

        Contract contract = Contract.builder().contractId(1).build();
        User client = new User();

        assertThatThrownBy(() ->
            adapter.createPaymentIntent(contract, null, BigDecimal.valueOf(500), "BRL", client)
        )
            .isInstanceOf(FeatureDisabledException.class)
            .hasMessageContaining("desativado");

        verifyNoInteractions(paymentIntentRepository);
    }

    @Test
    @DisplayName("M4.8 — Feature flag disabled: processWebhookEvent throws FeatureDisabledException")
    void processWebhookEvent_whenStripeDisabled_throwsFeatureDisabledException() {
        when(featureFlags.isStripeEnabled()).thenReturn(false);

        assertThatThrownBy(() ->
            adapter.processWebhookEvent("evt_123", "payment_intent.succeeded", "{}", true)
        )
            .isInstanceOf(FeatureDisabledException.class);

        verifyNoInteractions(providerEventRepository);
    }
}
