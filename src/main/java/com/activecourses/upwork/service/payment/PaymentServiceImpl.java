package com.activecourses.upwork.service.payment;

import com.activecourses.upwork.dto.PaymentDTO;
import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.contract.ContractMilestoneRepository;
import com.activecourses.upwork.repository.payment.PaymentRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final ContractMilestoneRepository milestoneRepository;
    private final AuthService authService;

    @Override
    @Transactional
    public PaymentDTO createPayment(int milestoneId, String description) {
        ContractMilestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new IllegalArgumentException("Milestone not found"));

        if (milestone.getStatus() != MilestoneStatus.Completed) {
            throw new IllegalStateException("Can only create payment for completed milestones");
        }

        // Prevent duplicate payments for the same milestone
        List<Payment> existingPayments = paymentRepository.findByContractContractId(
                milestone.getContract().getContractId());
        boolean alreadyPaid = existingPayments.stream()
                .anyMatch(p -> p.getMilestone() != null 
                        && p.getMilestone().getMilestoneId().equals(milestoneId)
                        && p.getStatus() != PaymentStatus.Refunded);
        if (alreadyPaid) {
            throw new IllegalStateException("Payment already exists for this milestone");
        }

        Contract contract = milestone.getContract();

        Payment payment = Payment.builder()
                .contract(contract)
                .milestone(milestone)
                .amount(milestone.getAmount() != null ? milestone.getAmount() : contract.getTotalValue())
                .status(PaymentStatus.Completed)
                .description(description != null ? description : "Pagamento: " + milestone.getTitle())
                .build();

        payment = paymentRepository.save(payment);
        return mapToDTO(payment);
    }

    @Override
    public List<PaymentDTO> getPaymentsByContract(int contractId) {
        return paymentRepository.findByContractContractId(contractId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<PaymentDTO> getMyPayments() {
        Integer userId = authService.getCurrentUserId();
        if (userId == null) return Collections.emptyList();

        List<PaymentDTO> asClient = paymentRepository.findByContractClientId(userId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
        List<PaymentDTO> asLawyer = paymentRepository.findByContractLawyerId(userId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        asClient.addAll(asLawyer);
        return asClient.stream()
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public Optional<PaymentDTO> getPaymentById(int paymentId) {
        return paymentRepository.findById(paymentId).map(this::mapToDTO);
    }

    @Override
    @Transactional
    public PaymentDTO completePayment(int paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        payment.setStatus(PaymentStatus.Completed);
        payment = paymentRepository.save(payment);
        return mapToDTO(payment);
    }

    @Override
    @Transactional
    public PaymentDTO refundPayment(int paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        payment.setStatus(PaymentStatus.Refunded);
        payment = paymentRepository.save(payment);
        return mapToDTO(payment);
    }

    private PaymentDTO mapToDTO(Payment payment) {
        Contract contract = payment.getContract();
        UserProfile clientProfile = contract.getClient().getUserProfile();
        UserProfile lawyerProfile = contract.getLawyer().getUserProfile();

        return PaymentDTO.builder()
                .paymentId(payment.getPaymentId())
                .contractId(contract.getContractId())
                .contractTitle(contract.getTitle())
                .milestoneId(payment.getMilestone() != null ? payment.getMilestone().getMilestoneId() : null)
                .milestoneTitle(payment.getMilestone() != null ? payment.getMilestone().getTitle() : null)
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .paymentDate(payment.getPaymentDate())
                .description(payment.getDescription())
                .clientName(contract.getClient().getFirstName() + " " + contract.getClient().getLastName())
                .lawyerName(contract.getLawyer().getFirstName() + " " + contract.getLawyer().getLastName())
                .lawyerOab(lawyerProfile != null ? lawyerProfile.getOabNumber() : null)
                .build();
    }
}
