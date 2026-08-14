package com.activecourses.upwork.service.security;

import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.admin.AdminAccessLogRepository;
import com.activecourses.upwork.repository.contract.ContractRepository;
import com.activecourses.upwork.repository.document.SecureDocumentRepository;
import com.activecourses.upwork.repository.job.JobRepository;
import com.activecourses.upwork.repository.job.ProposalRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class ImplAuthorizationService implements AuthorizationService {

    private static final Logger logger = LoggerFactory.getLogger(ImplAuthorizationService.class);

    private final UserRepository userRepository;
    private final ContractRepository contractRepository;
    private final JobRepository jobRepository;
    private final ProposalRepository proposalRepository;
    private final AdminAccessLogRepository adminAccessLogRepository;
    private final SecureDocumentRepository secureDocumentRepository;

    public ImplAuthorizationService(
            UserRepository userRepository,
            ContractRepository contractRepository,
            JobRepository jobRepository,
            ProposalRepository proposalRepository,
            AdminAccessLogRepository adminAccessLogRepository) {
        this(userRepository, contractRepository, jobRepository, proposalRepository, adminAccessLogRepository, null);
    }

    @Autowired
    public ImplAuthorizationService(
            UserRepository userRepository,
            ContractRepository contractRepository,
            JobRepository jobRepository,
            ProposalRepository proposalRepository,
            AdminAccessLogRepository adminAccessLogRepository,
            SecureDocumentRepository secureDocumentRepository) {
        this.userRepository = userRepository;
        this.contractRepository = contractRepository;
        this.jobRepository = jobRepository;
        this.proposalRepository = proposalRepository;
        this.adminAccessLogRepository = adminAccessLogRepository;
        this.secureDocumentRepository = secureDocumentRepository;
    }

    @Override
    public void enforceVerifiedLawyer(Integer lawyerId) {
        if (lawyerId == null) {
            throw new AccessDeniedException("Authentication required");
        }

        User user = userRepository.findById(lawyerId)
                .orElseThrow(() -> new AccessDeniedException("User not found: " + lawyerId));

        UserProfile profile = user.getUserProfile();
        if (profile == null) {
            throw new AccessDeniedException("User has no profile. Verification required.");
        }

        VerificationStatus status = profile.getVerificationStatus();
        if (status != VerificationStatus.VERIFIED) {
            throw new AccessDeniedException("Lawyer verification required. Current status: " + (status != null ? status : "NONE"));
        }

        if (profile.getOabExpiryDate() != null && profile.getOabExpiryDate().isBefore(LocalDate.now())) {
            throw new AccessDeniedException("Lawyer OAB registration has expired on " + profile.getOabExpiryDate());
        }
    }

    @Override
    public void enforceContractParticipant(Integer contractId, Integer userId) {
        if (userId == null) {
            throw new AccessDeniedException("Authentication required");
        }

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found with id: " + contractId));

        boolean isParticipant = (contract.getClient() != null && userId.equals(contract.getClient().getId()))
                || (contract.getLawyer() != null && userId.equals(contract.getLawyer().getId()));

        if (!isParticipant && !isAdmin()) {
            throw new AccessDeniedException("User " + userId + " is not a participant in contract " + contractId);
        }
    }

    @Override
    public void enforceJobOwner(Integer jobId, Integer userId) {
        if (userId == null) {
            throw new AccessDeniedException("Authentication required");
        }

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found with id: " + jobId));

        boolean isOwner = job.getClient() != null && userId.equals(job.getClient().getId());

        if (!isOwner && !isAdmin()) {
            throw new AccessDeniedException("User " + userId + " is not the owner of job " + jobId);
        }
    }

    @Override
    public void enforceProposalOwnerOrClient(Integer proposalId, Integer userId) {
        if (userId == null) {
            throw new AccessDeniedException("Authentication required");
        }

        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Proposal not found with id: " + proposalId));

        boolean isAuthor = proposal.getLawyer() != null && userId.equals(proposal.getLawyer().getId());
        boolean isJobClient = proposal.getJob() != null
                && proposal.getJob().getClient() != null
                && userId.equals(proposal.getJob().getClient().getId());

        if (!isAuthor && !isJobClient && !isAdmin()) {
            throw new AccessDeniedException("User " + userId + " is neither the author nor the client for proposal " + proposalId);
        }
    }

    @Override
    public void enforceCanViewJobDetail(Integer jobId, Integer userId) {
        if (userId == null) {
            throw new AccessDeniedException("Authentication required");
        }

        if (isAdmin()) {
            return;
        }

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found with id: " + jobId));

        // 1. Owner client
        if (job.getClient() != null && userId.equals(job.getClient().getId())) {
            return;
        }

        // 2. Lawyer with submitted proposal
        if (proposalRepository.findByJobJobIdAndLawyerId(jobId, userId).isPresent()) {
            return;
        }

        // 3. Sanitized discovery job visible to verified lawyers
        if (job.getVisibility() == JobVisibility.DISCOVERY_SANITIZED && job.getModerationStatus() == ModerationStatus.APPROVED) {
            enforceVerifiedLawyer(userId);
            return;
        }

        // 4. Otherwise forbidden
        throw new AccessDeniedException("Access denied: You do not have permission to view details for this legal case.");
    }

    @Override
    public void enforceNegotiationParticipant(Integer proposalId, Integer userId) {
        enforceProposalOwnerOrClient(proposalId, userId);
    }

    @Override
    public void enforceDocumentAccess(Long documentId, Integer userId) {
        if (documentId == null) {
            throw new IllegalArgumentException("Document ID cannot be null");
        }
        SecureDocument doc = secureDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Secure document not found with id: " + documentId));
        enforceDocumentAccess(doc, userId);
    }

    @Override
    public void enforceDocumentAccess(SecureDocument doc, Integer userId) {
        if (userId == null) {
            throw new AccessDeniedException("Authentication required");
        }

        if (Boolean.TRUE.equals(doc.getIsDeleted())) {
            throw new AccessDeniedException("Document has been deleted.");
        }

        if (doc.getVirusScanStatus() == VirusScanStatus.INFECTED) {
            throw new AccessDeniedException("Document is flagged as infected and cannot be accessed.");
        }

        if (isAdmin()) {
            return;
        }

        // 1. Owner of the document
        if (doc.getOwner() != null && userId.equals(doc.getOwner().getId())) {
            return;
        }

        // 2. Contract participant
        if (doc.getContract() != null) {
            Contract contract = doc.getContract();
            boolean isParticipant = (contract.getClient() != null && userId.equals(contract.getClient().getId()))
                    || (contract.getLawyer() != null && userId.equals(contract.getLawyer().getId()));
            if (isParticipant) {
                return;
            }
        }

        // 3. Job owner or assigned lawyer for the job
        if (doc.getJob() != null) {
            Job job = doc.getJob();
            if (job.getClient() != null && userId.equals(job.getClient().getId())) {
                return;
            }
            // Check if user has an accepted proposal for this job
            boolean isAssignedLawyer = proposalRepository.findByJobJobId(job.getJobId()).stream()
                    .anyMatch(p -> p.getLawyer() != null && userId.equals(p.getLawyer().getId()) && p.getStatus() == ProposalStatus.Accepted);
            if (isAssignedLawyer) {
                return;
            }
            // Check if document is PUBLIC and user is a verified lawyer
            if (doc.getClassification() == DocumentClassification.PUBLIC) {
                return;
            }
        }

        throw new AccessDeniedException("User " + userId + " does not have permission to access document " + (doc.getId() != null ? doc.getId() : ""));
    }

    @Override
    @Transactional
    public AdminAccessLog logAdminAccess(Integer adminUserId,
                                        Integer targetUserId,
                                        String resourceType,
                                        String resourceId,
                                        String action,
                                        String justification,
                                        HttpServletRequest request) {
        if (adminUserId == null) {
            throw new IllegalArgumentException("Admin user ID cannot be null");
        }
        if (justification == null || justification.trim().isEmpty()) {
            throw new IllegalArgumentException("Justification is required for admin privileged access");
        }

        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found: " + adminUserId));

        User targetUser = null;
        if (targetUserId != null) {
            targetUser = userRepository.findById(targetUserId).orElse(null);
        }

        String ipAddress = request != null ? request.getRemoteAddr() : null;
        String userAgent = request != null ? request.getHeader("User-Agent") : null;

        AdminAccessLog log = AdminAccessLog.builder()
                .adminUser(adminUser)
                .targetUser(targetUser)
                .targetResourceType(resourceType != null ? resourceType : "UNKNOWN")
                .targetResourceId(resourceId)
                .action(action != null ? action : "ACCESS")
                .justification(justification.trim())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .createdAt(LocalDateTime.now())
                .build();

        logger.info("ADMIN ACCESS AUDIT: Admin ID {} accessed {} ID {} - Action: {}",
                adminUserId, resourceType, resourceId, action);

        return adminAccessLogRepository.save(log);
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority()) || "ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
