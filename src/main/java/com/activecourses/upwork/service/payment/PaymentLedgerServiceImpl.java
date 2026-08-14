package com.activecourses.upwork.service.payment;

import com.activecourses.upwork.model.LedgerEntry;
import com.activecourses.upwork.model.PaymentIntent;
import com.activecourses.upwork.model.User;
import com.activecourses.upwork.repository.payment.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentLedgerServiceImpl implements PaymentLedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    @Override
    @Transactional
    public List<LedgerEntry> recordEntry(
            PaymentIntent paymentIntent,
            String entryType,
            BigDecimal amount,
            String currency,
            String source,
            String providerReference,
            String correlationId,
            User actor
    ) {
        // Double-entry: always creates both DEBIT and CREDIT entries
        LedgerEntry debit = LedgerEntry.builder()
                .paymentIntent(paymentIntent)
                .entryType(entryType + "_DEBIT")
                .direction("DEBIT")
                .amount(amount)
                .currency(currency != null ? currency : "BRL")
                .status("CONFIRMED")
                .source(source)
                .providerReference(providerReference)
                .correlationId(correlationId)
                .actor(actor)
                .build();

        LedgerEntry credit = LedgerEntry.builder()
                .paymentIntent(paymentIntent)
                .entryType(entryType + "_CREDIT")
                .direction("CREDIT")
                .amount(amount)
                .currency(currency != null ? currency : "BRL")
                .status("CONFIRMED")
                .source(source)
                .providerReference(providerReference)
                .correlationId(correlationId)
                .actor(actor)
                .build();

        LedgerEntry savedDebit  = ledgerEntryRepository.save(debit);
        LedgerEntry savedCredit = ledgerEntryRepository.save(credit);

        log.info("[LEDGER] Recorded entry type={} amount={} currency={} correlationId={}",
                entryType, amount, currency, correlationId);

        return List.of(savedDebit, savedCredit);
    }

    @Override
    @Transactional
    public LedgerEntry reverseEntry(Long entryId, String reason, User actor) {
        LedgerEntry original = ledgerEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Entrada do ledger não encontrada: " + entryId));

        // Reversal: flip direction, reference original entry — NEVER update original
        String reversalDirection = "DEBIT".equals(original.getDirection()) ? "CREDIT" : "DEBIT";

        LedgerEntry reversal = LedgerEntry.builder()
                .paymentIntent(original.getPaymentIntent())
                .entryType("REVERSAL_" + original.getEntryType())
                .direction(reversalDirection)
                .amount(original.getAmount())
                .currency(original.getCurrency())
                .status("CONFIRMED")
                .source("REVERSAL")
                .providerReference(original.getProviderReference())
                .correlationId(original.getCorrelationId())
                .actor(actor)
                .reversesEntry(original)
                .build();

        LedgerEntry saved = ledgerEntryRepository.save(reversal);
        log.info("[LEDGER] Reversed entry id={} reason={}", entryId, reason);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntry> getEntriesForContract(Integer contractId) {
        return ledgerEntryRepository.findAll().stream()
                .filter(e -> e.getPaymentIntent() != null &&
                             e.getPaymentIntent().getContract() != null &&
                             contractId.equals(e.getPaymentIntent().getContract().getContractId()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getBalance(Integer contractId) {
        BigDecimal balance = ledgerEntryRepository.getBalanceByContractId(contractId);
        return balance != null ? balance : BigDecimal.ZERO;
    }
}
