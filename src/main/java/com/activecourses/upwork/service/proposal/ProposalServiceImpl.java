package com.activecourses.upwork.service.proposal;

import com.activecourses.upwork.dto.ContractDTO;
import com.activecourses.upwork.dto.ProposalDTO;
import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.job.JobRepository;
import com.activecourses.upwork.repository.job.ProposalRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.service.contract.ContractService;
import com.activecourses.upwork.model.NotificationType;
import com.activecourses.upwork.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProposalServiceImpl implements ProposalService {

    private final ProposalRepository proposalRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final ContractService contractService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public ProposalDTO createProposal(ProposalDTO proposalDTO) {
        Integer lawyerId = authService.getCurrentUserId();
        if (lawyerId == null) throw new IllegalStateException("Not authenticated");

        User lawyer = userRepository.findById(lawyerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Job job = jobRepository.findById(proposalDTO.getJobId())
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));

        Proposal proposal = Proposal.builder()
                .job(job)
                .lawyer(lawyer)
                .coverLetter(proposalDTO.getCoverLetter())
                .proposedRate(proposalDTO.getProposedRate())
                .status(ProposalStatus.Pending)
                .proposedDuration(proposalDTO.getProposedDuration())
                .strategy(proposalDTO.getStrategy())
                .totalValue(proposalDTO.getTotalValue())
                .build();

        proposal = proposalRepository.save(proposal);

        // Notify client about new proposal
        User client = job.getClient();
        notificationService.createNotification(
                client.getId(),
                NotificationType.PROPOSAL_RECEIVED,
                "Nova proposta recebida",
                lawyer.getFirstName() + " " + lawyer.getLastName() + " enviou uma proposta para: " + job.getTitle(),
                "job",
                job.getJobId()
        );

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
                .orElseThrow(() -> new IllegalArgumentException("Proposal not found"));
        proposal.setStatus(ProposalStatus.Accepted);
        proposal = proposalRepository.save(proposal);

        // Auto-create contract when proposal is accepted
        ContractDTO contract = contractService.createContract(proposalId);

        // Notify lawyer that proposal was accepted
        User lawyer = proposal.getLawyer();
        notificationService.createNotification(
                lawyer.getId(),
                NotificationType.PROPOSAL_ACCEPTED,
                "Proposta aceite",
                "A sua proposta para " + proposal.getJob().getTitle() + " foi aceite. Um mandato foi criado.",
                "contract",
                contract.getContractId()
        );

        ProposalDTO result = mapToDTO(proposal);
        result.setContractId(contract.getContractId());
        return result;
    }

    @Override
    @Transactional
    public ProposalDTO rejectProposal(int proposalId) {
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Proposal not found"));
        proposal.setStatus(ProposalStatus.Rejected);
        proposal = proposalRepository.save(proposal);

        // Notify lawyer that proposal was rejected
        User lawyer = proposal.getLawyer();
        notificationService.createNotification(
                lawyer.getId(),
                NotificationType.PROPOSAL_REJECTED,
                "Proposta recusada",
                "A sua proposta para " + proposal.getJob().getTitle() + " foi recusada.",
                "job",
                proposal.getJob().getJobId()
        );

        return mapToDTO(proposal);
    }

    @Override
    @Transactional
    public ProposalDTO withdrawProposal(int proposalId) {
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Proposal not found"));

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
                .orElseThrow(() -> new IllegalArgumentException("Proposal not found"));

        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(proposal.getLawyer().getId())) {
            throw new SecurityException("You can only update your own proposals");
        }

        if (proposal.getStatus() != ProposalStatus.Pending) {
            throw new IllegalStateException("Can only update pending proposals");
        }

        proposal.setCoverLetter(proposalDTO.getCoverLetter());
        proposal.setProposedRate(proposalDTO.getProposedRate());
        proposal.setProposedDuration(proposalDTO.getProposedDuration());
        proposal.setStrategy(proposalDTO.getStrategy());
        proposal.setTotalValue(proposalDTO.getTotalValue());
        proposal.setUpdatedAt(LocalDateTime.now());

        return mapToDTO(proposalRepository.save(proposal));
    }

    private ProposalDTO mapToDTO(Proposal proposal) {
        UserProfile profile = proposal.getLawyer().getUserProfile();
        return ProposalDTO.builder()
                .proposalId(proposal.getProposalId())
                .jobId(proposal.getJob().getJobId())
                .jobTitle(proposal.getJob().getTitle())
                .lawyerId(proposal.getLawyer().getId())
                .lawyerName(proposal.getLawyer().getFirstName() + " " + proposal.getLawyer().getLastName())
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
                .build();
    }
}
