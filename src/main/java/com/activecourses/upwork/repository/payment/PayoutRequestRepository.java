package com.activecourses.upwork.repository.payment;

import com.activecourses.upwork.model.PayoutRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, Long> {
    Optional<PayoutRequest> findBySenderBatchId(String senderBatchId);
    Optional<PayoutRequest> findBySenderItemId(String senderItemId);
    Optional<PayoutRequest> findByPaypalPayoutBatchId(String paypalPayoutBatchId);
    List<PayoutRequest> findByLawyerIdOrderByCreatedAtDesc(Integer lawyerId);
    List<PayoutRequest> findByStatus(String status);
}
