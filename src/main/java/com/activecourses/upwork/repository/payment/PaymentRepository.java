package com.activecourses.upwork.repository.payment;

import com.activecourses.upwork.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    List<Payment> findByContractContractId(int contractId);
    List<Payment> findByContractClientId(int clientId);
    List<Payment> findByContractLawyerId(int lawyerId);
    List<Payment> findByStatus(com.activecourses.upwork.model.PaymentStatus status);
}
