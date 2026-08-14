package com.activecourses.upwork.repository.payment;

import com.activecourses.upwork.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByPaymentIntentId(Long paymentIntentId);
    List<LedgerEntry> findByCorrelationId(String correlationId);

    @Query("SELECT SUM(CASE WHEN le.direction = 'CREDIT' THEN le.amount ELSE -le.amount END) " +
           "FROM LedgerEntry le WHERE le.paymentIntent.contract.contractId = :contractId AND le.status = 'CONFIRMED'")
    BigDecimal getBalanceByContractId(@Param("contractId") Integer contractId);
}
