package com.activecourses.upwork.service.payment;

import com.activecourses.upwork.config.FeatureFlags;
import com.activecourses.upwork.exception.FeatureDisabledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 5 — PayPal Payout Service Tests")
class PayPalPayoutServiceTest {

    @Mock private FeatureFlags featureFlags;
    @Mock private com.activecourses.upwork.repository.payment.PayoutRequestRepository payoutRequestRepository;
    @Mock private com.activecourses.upwork.repository.payment.PayoutAccountRepository payoutAccountRepository;
    @Mock private PaymentLedgerService ledgerService;

    private PayPalPayoutService payPalPayoutService;

    @BeforeEach
    void setUp() {
        payPalPayoutService = new PayPalPayoutService(
                featureFlags, payoutRequestRepository, payoutAccountRepository, ledgerService);
    }

    @Test
    @DisplayName("M5 — Feature flag disabled: requestPayout throws FeatureDisabledException")
    void requestPayout_whenPayPalDisabled_throwsFeatureDisabledException() {
        when(featureFlags.isPaypalPayoutsEnabled()).thenReturn(false);

        assertThatThrownBy(() ->
            payPalPayoutService.requestPayout(new com.activecourses.upwork.model.User(),
                    1, java.math.BigDecimal.valueOf(500), "BRL", new com.activecourses.upwork.model.User())
        )
            .isInstanceOf(FeatureDisabledException.class)
            .hasMessageContaining("PayPal");

        verifyNoInteractions(payoutRequestRepository, payoutAccountRepository);
    }

    @Test
    @DisplayName("M5 — Feature flag disabled: checkEligibility throws FeatureDisabledException")
    void checkEligibility_whenPayPalDisabled_throwsFeatureDisabledException() {
        when(featureFlags.isPaypalPayoutsEnabled()).thenReturn(false);

        assertThatThrownBy(() -> payPalPayoutService.checkEligibility(1, 1))
                .isInstanceOf(FeatureDisabledException.class);

        verifyNoInteractions(ledgerService);
    }
}
