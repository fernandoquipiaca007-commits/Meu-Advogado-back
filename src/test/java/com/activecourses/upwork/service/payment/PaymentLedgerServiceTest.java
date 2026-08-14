package com.activecourses.upwork.service.payment;

import com.activecourses.upwork.model.LedgerEntry;
import com.activecourses.upwork.model.PaymentIntent;
import com.activecourses.upwork.model.User;
import com.activecourses.upwork.repository.payment.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 4 — Payment Ledger Service Tests")
class PaymentLedgerServiceTest {

    @Mock private LedgerEntryRepository ledgerEntryRepository;

    private PaymentLedgerServiceImpl ledgerService;

    @BeforeEach
    void setUp() {
        ledgerService = new PaymentLedgerServiceImpl(ledgerEntryRepository);
    }

    @Test
    @DisplayName("M4.3 — recordEntry: always creates 2 entries (DEBIT + CREDIT)")
    void recordEntry_alwaysCreatesTwoBalancedEntries() {
        PaymentIntent pi = new PaymentIntent();
        User actor = new User();

        LedgerEntry mockEntry = new LedgerEntry();
        when(ledgerEntryRepository.save(any())).thenReturn(mockEntry);

        List<LedgerEntry> entries = ledgerService.recordEntry(
                pi, "PAYMENT_COLLECTED", BigDecimal.valueOf(1000), "BRL",
                "STRIPE", "pi_test_123", "corr_abc", actor
        );

        assertThat(entries).hasSize(2);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(captor.capture());

        List<LedgerEntry> saved = captor.getAllValues();
        assertThat(saved).anyMatch(e -> "DEBIT".equals(e.getDirection()));
        assertThat(saved).anyMatch(e -> "CREDIT".equals(e.getDirection()));
    }

    @Test
    @DisplayName("M4.3 — recordEntry: never calls UPDATE (save always creates new)")
    void recordEntry_neverUpdatesExistingEntries() {
        PaymentIntent pi = new PaymentIntent();
        User actor = new User();
        when(ledgerEntryRepository.save(any())).thenReturn(new LedgerEntry());

        ledgerService.recordEntry(pi, "TEST", BigDecimal.ONE, "BRL", null, null, null, actor);

        // save() is for both INSERT and UPDATE in JPA, but since entries have no ID set,
        // Spring Data always performs INSERT (not UPDATE)
        verify(ledgerEntryRepository, times(2)).save(argThat(e -> e.getId() == null));
    }

    @Test
    @DisplayName("M4.3 — getBalance: returns ZERO when no entries exist")
    void getBalance_returnsZeroWhenNoEntries() {
        when(ledgerEntryRepository.getBalanceByContractId(anyInt())).thenReturn(null);

        BigDecimal balance = ledgerService.getBalance(42);

        assertThat(balance).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
