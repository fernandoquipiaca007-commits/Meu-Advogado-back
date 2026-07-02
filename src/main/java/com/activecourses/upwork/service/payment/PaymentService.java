package com.activecourses.upwork.service.payment;

import com.activecourses.upwork.dto.PaymentDTO;

import java.util.List;
import java.util.Optional;

public interface PaymentService {
    PaymentDTO createPayment(int milestoneId, String description);
    List<PaymentDTO> getPaymentsByContract(int contractId);
    List<PaymentDTO> getMyPayments();
    Optional<PaymentDTO> getPaymentById(int paymentId);
    PaymentDTO completePayment(int paymentId);
    PaymentDTO refundPayment(int paymentId);
}
