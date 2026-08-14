package com.activecourses.upwork.service.payment;

import com.activecourses.upwork.model.LedgerEntry;
import com.activecourses.upwork.model.PaymentIntent;
import com.activecourses.upwork.model.User;

import java.math.BigDecimal;
import java.util.List;

/**
 * Double-entry append-only financial ledger service.
 * INVARIANT: Entries are NEVER updated. Reversals create new entries.
 */
public interface PaymentLedgerService {

    /**
     * Records a pair of balanced ledger entries (DEBIT + CREDIT).
     * Every financial event must produce at least two balanced entries.
     */
    List<LedgerEntry> recordEntry(
            PaymentIntent paymentIntent,
            String entryType,
            BigDecimal amount,
            String currency,
            String source,
            String providerReference,
            String correlationId,
            User actor
    );

    /**
     * Reverses an existing entry by creating a new reversal entry.
     * NEVER modifies the original entry.
     */
    LedgerEntry reverseEntry(Long entryId, String reason, User actor);

    /**
     * Returns all ledger entries for a given contract (via payment intents).
     */
    List<LedgerEntry> getEntriesForContract(Integer contractId);

    /**
     * Calculates available balance for a contract: sum(CREDIT) - sum(DEBIT).
     */
    BigDecimal getBalance(Integer contractId);
}
