package com.activecourses.upwork.service.proposal;

import com.activecourses.upwork.dto.ContractDTO;
import com.activecourses.upwork.dto.ProposalDTO;
import com.activecourses.upwork.exception.DuplicateProposalException;
import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.job.JobRepository;
import com.activecourses.upwork.repository.job.ProposalRepository;
import com.activecourses.upwork.repository.negotiation.NegotiationThreadRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.service.contract.ContractService;
import com.activecourses.upwork.model.NotificationType;
import com.activecourses.upwork.service.notification.NotificationService;
import com.activecourses.upwork.service.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProposalServiceImpl implements ProposalService {

    private final ProposalRepository proposalRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final NegotiationThreadRepository negotiationThreadRepository;
    private final AuthService authService;
    private final ContractService contractService;
    private final NotificationService notificationService;
    private final AuthorizationService authorizationService;

    @Override
    @Transactional
    public ProposalDTO createProposal(ProposalDTO proposalDTO) {
        Integer lawyerId = authService.getCurrentUserId();
        if (lawyerId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Not authenticated");
        }

        // Enforce verified lawyer status (HTTP 403 / AccessDeniedException if not verified)
        authorizationService.enforceVerifiedLawyer(lawyerId);

        User lawyer = userRepository.findById(lawyerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + lawyerId));
        Job job = jobRepository.findById(proposalDTO.getJobId())
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + proposalDTO.getJobId()));

        // Anti-duplication check: prevent multiple active proposals for the same lawyer and job
        boolean hasActiveProposal = proposalRepository.existsByJobJobIdAndLawyerIdAndStatusIn(
                job.getJobId(),
                lawyer.getId(),
                List.of(ProposalStatus.Pending, ProposalStatus.Countered)
        );
        if (hasActiveProposal) {
            throw new DuplicateProposalException("Você já possui uma proposta ativa para este caso jurídico.");
        }

        Proposal proposal = Proposal.builder()
                .job(job)
                .lawyer(lawyer)
                .coverLetter(proposalDTO.getCoverLetter())
                .proposedRate(proposalDTO.getProposedRate())
                .status(ProposalStatus.Pending)
                .proposedDuration(proposalDTO.getProposedDuration())
                .strategy(proposalDTO.getStrategy())
                .totalValue(proposalDTO.getTotalValue())
                .proposalVersion(1)
                .build();

        proposal = proposalRepository.save(proposal);

        // Automatically create pre-contractual negotiation thread
        NegotiationThread thread = NegotiationThread.builder()
                .proposal(proposal)
                .createdAt(LocalDateTime.now())
                .retentionDays(90)
                .build();
        thread = negotiationThreadRepository.save(thread);
        proposal.setNegotiationThread(thread);

        // Notify client about new proposal
        User client = job.getClient();
        if (client != null) {
            notificationService.createNotification(
                    client.getId(),
                    NotificationType.PROPOSAL_RECEIVED,
                    "Nova proposta recebida (v1)",
                    lawyer.getFirstName() + " " + lawyer.getLastName() + " enviou uma proposta para: " + job.getTitle(),
                    "job",
                    job.getJobId()
            );
        }

        return mapToDTO(proposal);
    }

    @Override
    public List<ProposalDTO> getProposalsByJob(int jobId) {
        return proposalRepository.findByJobJobId(jobId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProposalDTO> getMyProposals(int lawyerId) {
        return proposalRepository.findByLawyerId(lawyerId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProposalDTO> getProposalsForMyCases(int clientId) {
        List<Job> myJobs = jobRepository.findByClientId(clientId);
        return myJobs.stream()
                .flatMap(job -> proposalRepository.findByJobJobId(job.getJobId()).stream())
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ProposalDTO> getProposalById(int proposalId) {
        return proposalRepository.findById(proposalId).map(this::mapToDTO);
    }

    @Override
    @Transactional
    public ProposalDTO acceptProposal(int proposalId) {
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Proposal not found: " + proposalId));
        proposal.setStatus(ProposalStatus.Accepted);
        proposal = proposalRepository.save(proposal);

        // Auto-create contract when proposal is accepted
        ContractDTO contract = contractService.createContract(proposalId);

        // Notify lawyer that proposal was accepted
        User lawyer = proposal.getLawyer();
        if (lawyer != null) {
            notificationService.createNotification(
                    lawyer.getId(),
                    NotificationType.PROPOSAL_ACCEPTED,
                    "Proposta aceite",
                    "A sua proposta para " + proposal.getJob().getTitle() + " foi aceite. Um mandato foi criado.",
                    "contract",
                    contract.getContractId()
            );
        }

        ProposalDTO result = mapToDTO(proposal);
        result.setContractId(contract.getContractId());
        return result;
    }

    @Override
    @Transactional
    public ProposalDTO rejectProposal(int proposalId) {
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Proposal not found: " + proposalId));
        proposal.setStatus(ProposalStatus.Rejected);
        proposal = proposalRepository.save(proposal);

        // Notify lawyer that proposal was rejected
        User lawyer = proposal.getLawyer();
        if (lawyer != null) {
            notificationService.createNotification(
                    lawyer.getId(),
                    NotificationType.PROPOSAL_REJECTED,
                    "Proposta recusada",
                    "A sua proposta para " + proposal.getJob().getTitle() + " foi recusada.",
                    "job",
                    proposal.getJob().getJobId()
            );
        }

        return mapToDTO(proposal);
    }

    @Override
    @Transactional
    public ProposalDTO withdrawProposal(int proposalId) {
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Proposal not found: " + proposalId));

        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(proposal.getLawyer().getId())) {
            throw new SecurityException("You can only withdraw your own proposals");
        }

        proposal.setStatus(ProposalStatus.Withdrawn);
        return mapToDTO(proposalRepository.save(proposal));
    }

    @Override
    @Transactional
    public ProposalDTO updateProposal(int proposalId, ProposalDTO proposalDTO) {
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Proposal not found: " + proposalId));

        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(proposal.getLawyer().getId())) {
            throw new SecurityException("You can only update your own proposals");
        }

        if (proposal.getStatus() != ProposalStatus.Pending && proposal.getStatus() != ProposalStatus.Countered) {
            throw new IllegalStateException("Can only update active proposals (Pending or Countered)");
        }

        // Check if material fields changed
        boolean isMaterialChange = !Objects.equals(proposal.getProposedRate(), proposalDTO.getProposedRate())
                || !Objects.equals(proposal.getProposedDuration(), proposalDTO.getProposedDuration())
                || !Objects.equals(proposal.getStrategy(), proposalDTO.getStrategy())
                || !Objects.equals(proposal.getTotalValue(), proposalDTO.getTotalValue())
                || !Objects.equals(proposal.getCoverLetter(), proposalDTO.getCoverLetter());

        if (isMaterialChange) {
            int currentVersion = proposal.getProposalVersion() != null ? proposal.getProposalVersion() : 1;
            proposal.setProposalVersion(currentVersion + 1);
        }

        proposal.setCoverLetter(proposalDTO.getCoverLetter());
        proposal.setProposedRate(proposalDTO.getProposedRate());
        proposal.setProposedDuration(proposalDTO.getProposedDuration());
        proposal.setStrategy(proposalDTO.getStrategy());
        proposal.setTotalValue(proposalDTO.getTotalValue());
        proposal.setUpdatedAt(LocalDateTime.now());

        Proposal saved = proposalRepository.save(proposal);

        // Notify client if material update occurred
        if (isMaterialChange && proposal.getJob() != null && proposal.getJob().getClient() != null) {
            notificationService.createNotification(
                    proposal.getJob().getClient().getId(),
                    NotificationType.PROPOSAL_RECEIVED,
                    "Proposta atualizada (v" + saved.getProposalVersion() + ")",
                    proposal.getLawyer().getFirstName() + " atualizou os termos da proposta para: " + proposal.getJob().getTitle(),
                    "job",
                    proposal.getJob().getJobId()
            );
        }

        return mapToDTO(saved);
    }

    private ProposalDTO mapToDTO(Proposal proposal) {
        UserProfile profile = proposal.getLawyer() != null ? proposal.getLawyer().getUserProfile() : null;
        Long threadId = null;
        if (proposal.getNegotiationThread() != null) {
            threadId = proposal.getNegotiationThread().getId();
        } else {
            Optional<NegotiationThread> threadOpt = negotiationThreadRepository.findByProposalProposalId(proposal.getProposalId());
            if (threadOpt.isPresent()) {
                threadId = threadOpt.get().getId();
            }
        }

        return ProposalDTO.builder()
                .proposalId(proposal.getProposalId())
                .jobId(proposal.getJob() != null ? proposal.getJob().getJobId() : null)
                .jobTitle(proposal.getJob() != null ? proposal.getJob().getTitle() : null)
                .lawyerId(proposal.getLawyer() != null ? proposal.getLawyer().getId() : null)
                .lawyerName(proposal.getLawyer() != null ? proposal.getLawyer().getFirstName() + " " + proposal.getLawyer().getLastName() : null)
                .coverLetter(proposal.getCoverLetter())
                .proposedRate(proposal.getProposedRate())
                .status(proposal.getStatus())
                .createdAt(proposal.getCreatedAt())
                .proposedDuration(proposal.getProposedDuration())
                .strategy(proposal.getStrategy())
                .totalValue(proposal.getTotalValue())
                .lawyerPhotoUrl(profile != null ? profile.getPhotoUrl() : null)
                .lawyerOab(profile != null ? profile.getOabNumber() : null)
                .lawyerExperienceYears(profile != null ? profile.getExperienceYears() : null)
                .proposalVersion(proposal.getProposalVersion() != null ? proposal.getProposalVersion() : 1)
                .negotiationThreadId(threadId)
                .build();
    }
}
