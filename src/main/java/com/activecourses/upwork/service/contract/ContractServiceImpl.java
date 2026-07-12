package com.activecourses.upwork.service.contract;

import com.activecourses.upwork.dto.ContractDTO;
import com.activecourses.upwork.dto.ContractMilestoneDTO;
import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.contract.ContractMilestoneRepository;
import com.activecourses.upwork.repository.contract.ContractRepository;
import com.activecourses.upwork.repository.job.ProposalRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.model.NotificationType;
import com.activecourses.upwork.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    private final ContractRepository contractRepository;
    private final ContractMilestoneRepository milestoneRepository;
    private final ProposalRepository proposalRepository;
    private final AuthService authService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public ContractDTO createContract(int proposalId) {
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Proposal not found"));

        if (proposal.getStatus() != ProposalStatus.Accepted) {
            throw new IllegalStateException("Can only create contract for accepted proposals");
        }

        Job job = proposal.getJob();
        User client = job.getClient();
        User lawyer = proposal.getLawyer();

        String title = "Mandato: " + job.getTitle();
        String description = job.getDescription();

        Contract contract = Contract.builder()
                .job(job)
                .client(client)
                .lawyer(lawyer)
                .proposal(proposal)
                .title(title)
                .description(description)
                .totalValue(proposal.getTotalValue() != null ? proposal.getTotalValue() : job.getBudget())
                .startDate(LocalDate.now())
                .endDate(proposal.getProposedDuration() != null
                        ? LocalDate.now().plusDays(proposal.getProposedDuration())
                        : null)
                .status(ContractStatus.Active)
                .build();

        contract = contractRepository.save(contract);

        // Notify both parties about new contract
        notificationService.createNotification(
                client.getId(),
                NotificationType.CONTRACT_CREATED,
                "Mandato criado",
                "O mandato para " + job.getTitle() + " foi criado com " + lawyer.getFirstName() + " " + lawyer.getLastName(),
                "contract",
                contract.getContractId()
        );
        notificationService.createNotification(
                lawyer.getId(),
                NotificationType.CONTRACT_CREATED,
                "Mandato criado",
                "O mandato para " + job.getTitle() + " foi criado com " + client.getFirstName() + " " + client.getLastName(),
                "contract",
                contract.getContractId()
        );

        return mapToDTO(contract);
    }

    @Override
    public List<ContractDTO> getContractsByClient(int clientId) {
        return contractRepository.findByClientId(clientId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ContractDTO> getContractsByLawyer(int lawyerId) {
        return contractRepository.findByLawyerId(lawyerId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ContractDTO> getMyContracts() {
        Integer userId = authService.getCurrentUserId();
        if (userId == null) return Collections.emptyList();

        List<ContractDTO> asClient = contractRepository.findByClientId(userId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
        List<ContractDTO> asLawyer = contractRepository.findByLawyerId(userId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        // Merge without duplicates
        asClient.addAll(asLawyer);
        return asClient.stream()
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ContractDTO> getContractById(int contractId) {
        return contractRepository.findById(contractId).map(this::mapToDTO);
    }

    @Override
    @Transactional
    public ContractDTO completeContract(int contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found"));

        // Ownership check: only participants can complete
        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId != null) {
            boolean isParticipant = currentUserId.equals(contract.getClient().getId())
                    || currentUserId.equals(contract.getLawyer().getId());
            if (!isParticipant) {
                throw new SecurityException("You can only complete contracts you participate in");
            }
        }

        contract.setStatus(ContractStatus.Completed);
        contract.setUpdatedAt(LocalDateTime.now());
        contract = contractRepository.save(contract);

        // Notify the other party
        User otherParty = currentUserId != null && currentUserId.equals(contract.getClient().getId())
                ? contract.getLawyer() : contract.getClient();
        notificationService.createNotification(
                otherParty.getId(),
                NotificationType.CONTRACT_COMPLETED,
                "Mandato concluído",
                "O mandato " + contract.getTitle() + " foi marcado como concluído.",
                "contract",
                contract.getContractId()
        );

        return mapToDTO(contract);
    }

    @Override
    @Transactional
    public ContractDTO terminateContract(int contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found"));

        // Ownership check: only the client can terminate
        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId != null && !currentUserId.equals(contract.getClient().getId())) {
            throw new SecurityException("You can only terminate your own contracts");
        }

        contract.setStatus(ContractStatus.Terminated);
        contract.setUpdatedAt(LocalDateTime.now());
        contract = contractRepository.save(contract);

        // Notify the other party
        User otherParty = currentUserId != null && currentUserId.equals(contract.getClient().getId())
                ? contract.getLawyer() : contract.getClient();
        notificationService.createNotification(
                otherParty.getId(),
                NotificationType.CONTRACT_TERMINATED,
                "Mandato encerrado",
                "O mandato " + contract.getTitle() + " foi encerrado.",
                "contract",
                contract.getContractId()
        );

        return mapToDTO(contract);
    }

    @Override
    @Transactional
    public ContractDTO cancelContract(int contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found"));
        contract.setStatus(ContractStatus.Cancelled);
        contract.setUpdatedAt(LocalDateTime.now());
        return mapToDTO(contractRepository.save(contract));
    }

    @Override
    @Transactional
    public ContractMilestoneDTO completeMilestone(int milestoneId) {
        ContractMilestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new IllegalArgumentException("Milestone not found"));

        // Ownership check: only participants can complete milestones
        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId != null) {
            Contract contract = milestone.getContract();
            boolean isParticipant = currentUserId.equals(contract.getClient().getId())
                    || currentUserId.equals(contract.getLawyer().getId());
            if (!isParticipant) {
                throw new SecurityException("You can only complete milestones in contracts you participate in");
            }
        }

        milestone.setStatus(MilestoneStatus.Completed);
        milestone.setCompletedAt(LocalDateTime.now());
        milestone = milestoneRepository.save(milestone);

        // Notify client that milestone was completed
        Contract contract = milestone.getContract();
        User client = contract.getClient();
        notificationService.createNotification(
                client.getId(),
                NotificationType.MILESTONE_COMPLETED,
                "Etapa concluída",
                "A etapa \"" + milestone.getTitle() + "\" do mandato " + contract.getTitle() + " foi concluída.",
                "contract",
                contract.getContractId()
        );

        return mapMilestoneToDTO(milestone);
    }

    @Override
    public List<ContractMilestoneDTO> getMilestones(int contractId) {
        return milestoneRepository.findByContractContractId(contractId).stream()
                .map(this::mapMilestoneToDTO)
                .collect(Collectors.toList());
    }

    private ContractDTO mapToDTO(Contract contract) {
        UserProfile clientProfile = contract.getClient().getUserProfile();
        UserProfile lawyerProfile = contract.getLawyer().getUserProfile();

        List<ContractMilestoneDTO> milestoneDTOs = contract.getMilestones() != null
                ? contract.getMilestones().stream().map(this::mapMilestoneToDTO).collect(Collectors.toList())
                : Collections.emptyList();

        return ContractDTO.builder()
                .contractId(contract.getContractId())
                .jobId(contract.getJob().getJobId())
                .jobTitle(contract.getJob().getTitle())
                .clientId(contract.getClient().getId())
                .clientName(contract.getClient().getFirstName() + " " + contract.getClient().getLastName())
                .lawyerId(contract.getLawyer().getId())
                .lawyerName(contract.getLawyer().getFirstName() + " " + contract.getLawyer().getLastName())
                .lawyerPhotoUrl(lawyerProfile != null ? lawyerProfile.getPhotoUrl() : null)
                .lawyerOab(lawyerProfile != null ? lawyerProfile.getOabNumber() : null)
                .proposalId(contract.getProposal() != null ? contract.getProposal().getProposalId() : null)
                .title(contract.getTitle())
                .description(contract.getDescription())
                .totalValue(contract.getTotalValue())
                .startDate(contract.getStartDate())
                .endDate(contract.getEndDate())
                .status(contract.getStatus())
                .createdAt(contract.getCreatedAt())
                .milestones(milestoneDTOs)
                .build();
    }

    private ContractMilestoneDTO mapMilestoneToDTO(ContractMilestone milestone) {
        return ContractMilestoneDTO.builder()
                .milestoneId(milestone.getMilestoneId())
                .contractId(milestone.getContract().getContractId())
                .title(milestone.getTitle())
                .description(milestone.getDescription())
                .amount(milestone.getAmount())
                .dueDate(milestone.getDueDate())
                .status(milestone.getStatus())
                .completedAt(milestone.getCompletedAt())
                .build();
    }
}
