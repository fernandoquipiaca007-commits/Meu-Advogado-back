package com.activecourses.upwork.controller.dashboard;

import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.contract.ContractRepository;
import com.activecourses.upwork.repository.job.JobRepository;
import com.activecourses.upwork.repository.job.ProposalRepository;
import com.activecourses.upwork.repository.notification.NotificationRepository;
import com.activecourses.upwork.repository.payment.PaymentRepository;
import com.activecourses.upwork.repository.review.ReviewRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "Dashboard", description = "Métricas e resumo do dashboard")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard/")
public class DashboardController {

    private final AuthService authService;
    private final ContractRepository contractRepository;
    private final ProposalRepository proposalRepository;
    private final PaymentRepository paymentRepository;
    private final ReviewRepository reviewRepository;
    private final NotificationRepository notificationRepository;
    private final JobRepository jobRepository;

    @Operation(summary = "Métricas do dashboard", description = "Métricas consolidadas para o utilizador logado",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/metrics")
    @PreAuthorize("hasRole('CLIENT') or hasRole('LAWYER') or hasRole('ADMIN')")
    public ResponseEntity<ResponseDto> getDashboardMetrics() {
        Integer userId = authService.getCurrentUserId();
        if (userId == null) {
            return buildResponse(HttpStatus.UNAUTHORIZED, false, null, "Not authenticated");
        }

        // Contracts
        List<Contract> asClient = contractRepository.findByClientId(userId);
        List<Contract> asLawyer = contractRepository.findByLawyerId(userId);

        long activeContracts = countByStatus(asClient, ContractStatus.Active) 
                + countByStatus(asLawyer, ContractStatus.Active);
        long completedContracts = countByStatus(asClient, ContractStatus.Completed)
                + countByStatus(asLawyer, ContractStatus.Completed);
        long terminatedContracts = countByStatus(asClient, ContractStatus.Terminated)
                + countByStatus(asLawyer, ContractStatus.Terminated);

        // Payments
        List<Payment> paymentsAsClient = paymentRepository.findByContractClientId(userId);
        List<Payment> paymentsAsLawyer = paymentRepository.findByContractLawyerId(userId);

        BigDecimal totalPaidAsClient = paymentsAsClient.stream()
                .filter(p -> p.getStatus() == PaymentStatus.Completed)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalReceivedAsLawyer = paymentsAsLawyer.stream()
                .filter(p -> p.getStatus() == PaymentStatus.Completed)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Proposals
        long pendingProposals = proposalRepository.findByLawyerId(userId).stream()
                .filter(p -> p.getStatus() == ProposalStatus.Pending)
                .count();
        long proposalsForMyCases = 0;
        List<Job> myJobs = jobRepository.findByClientId(userId);
        for (Job job : myJobs) {
            proposalsForMyCases += proposalRepository.countByJobJobId(job.getJobId());
        }

        // Reviews
        List<Review> receivedReviews = reviewRepository.findByRevieweeId(userId);
        double averageRating = receivedReviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);

        // Notifications
        long unreadNotifications = notificationRepository.countByUserIdAndIsReadFalse(userId);

        // Recent activity (last 10 contracts sorted by creation)
        List<Contract> allContracts = new ArrayList<>();
        allContracts.addAll(asClient);
        allContracts.addAll(asLawyer);
        allContracts.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        List<Map<String, Object>> recentActivity = allContracts.stream()
                .limit(10)
                .map(c -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("type", "contract");
                    item.put("id", c.getContractId());
                    item.put("title", c.getTitle());
                    item.put("status", c.getStatus().name());
                    item.put("date", c.getCreatedAt().toString());
                    boolean isClient = c.getClient().getId().equals(userId);
                    item.put("role", isClient ? "client" : "lawyer");
                    item.put("otherParty", isClient 
                            ? c.getLawyer().getFirstName() + " " + c.getLawyer().getLastName()
                            : c.getClient().getFirstName() + " " + c.getClient().getLastName());
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("userId", userId);
        metrics.put("activeContracts", activeContracts);
        metrics.put("completedContracts", completedContracts);
        metrics.put("terminatedContracts", terminatedContracts);
        metrics.put("totalPaid", totalPaidAsClient);
        metrics.put("totalReceived", totalReceivedAsLawyer);
        metrics.put("pendingProposals", pendingProposals);
        metrics.put("proposalsForMyCases", proposalsForMyCases);
        metrics.put("averageRating", Math.round(averageRating * 10.0) / 10.0);
        metrics.put("totalReviews", receivedReviews.size());
        metrics.put("unreadNotifications", unreadNotifications);
        metrics.put("recentActivity", recentActivity);

        return buildResponse(HttpStatus.OK, true, metrics, null);
    }

    private long countByStatus(List<Contract> contracts, ContractStatus status) {
        return contracts.stream().filter(c -> c.getStatus() == status).count();
    }

    private ResponseEntity<ResponseDto> buildResponse(HttpStatus status, boolean success, Object data, Object error) {
        return ResponseEntity.status(status)
                .body(ResponseDto.builder().status(status).success(success).data(data).error(error).build());
    }
}
