package com.activecourses.upwork.repository.payment;

import com.activecourses.upwork.model.PaymentIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, Long> {
    Optional<PaymentIntent> findByStripePaymentIntentId(String stripePaymentIntentId);
    Optional<PaymentIntent> findByIdempotencyKey(String idempotencyKey);
    List<PaymentIntent> findByContractContractId(Integer contractId);
    List<PaymentIntent> findByMilestoneMilestoneId(Integer milestoneId);
}
