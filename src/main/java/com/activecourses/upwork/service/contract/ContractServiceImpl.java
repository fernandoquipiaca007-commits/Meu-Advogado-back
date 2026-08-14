package com.activecourses.upwork.service.contract;

import com.activecourses.upwork.dto.*;
import com.activecourses.upwork.exception.ConflictBlockedException;
import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.conflict.ConflictCheckRepository;
import com.activecourses.upwork.repository.contract.ContractMilestoneRepository;
import com.activecourses.upwork.repository.contract.ContractRepository;
import com.activecourses.upwork.repository.contract.ContractSignatureRepository;
import com.activecourses.upwork.repository.document.SecureDocumentRepository;
import com.activecourses.upwork.repository.job.JobRepository;
import com.activecourses.upwork.repository.job.ProposalRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.service.notification.NotificationService;
import com.activecourses.upwork.service.security.AuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    private final ContractRepository contractRepository;
    private final ContractMilestoneRepository milestoneRepository;
    private final ProposalRepository proposalRepository;
    private final JobRepository jobRepository;
    private final AuthService authService;
    private final NotificationService notificationService;
    private final AuthorizationService authorizationService;
    private final ConflictCheckRepository conflictCheckRepository;
    private final ContractSignatureRepository contractSignatureRepository;
    private final SecureDocumentRepository secureDocumentRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractDTO acceptAndContract(AcceptContractRequestDto request, HttpServletRequest httpRequest) {
        if (request == null || request.getProposalId() == null) {
            throw new IllegalArgumentException("O identificador da proposta é obrigatório.");
        }

        // 1. Current user authentication
        Integer currentUserId = authService != null ? authService.getCurrentUserId() : null;
        if (currentUserId == null) {
            throw new AccessDeniedException("Não autenticado.");
        }

        Proposal proposal = proposalRepository.findById(request.getProposalId())
                .orElseThrow(() -> new IllegalArgumentException("Proposta não encontrada com ID: " + request.getProposalId()));

        Job job = proposal.getJob();
        if (job == null) {
            throw new IllegalStateException("A proposta não possui demanda associada.");
        }

        // Precondition 1: Ownership check
        if (authorizationService != null) {
            authorizationService.enforceJobOwner(job.getJobId(), currentUserId);
        }

        // Precondition 2: Proposal status check (only Pending or Countered can be accepted via acceptAndContract)
        if (proposal.getStatus() != ProposalStatus.Pending && proposal.getStatus() != ProposalStatus.Countered) {
            throw new IllegalStateException("Apenas propostas pendentes podem ser aceitas. Estado atual: " + proposal.getStatus());
        }

        return acceptAndContractInternal(proposal, request, currentUserId, httpRequest);
    }

    @Override
    @Transactional
    public ContractDTO createContract(int proposalId) {
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Proposta não encontrada com ID: " + proposalId));

        Integer currentUserId = authService != null ? authService.getCurrentUserId() : null;
        if (currentUserId == null && proposal.getJob() != null && proposal.getJob().getClient() != null) {
            currentUserId = proposal.getJob().getClient().getId();
        }

        AcceptContractRequestDto request = AcceptContractRequestDto.builder()
                .proposalId(proposalId)
                .termsVersion("v1.0")
                .build();

        return acceptAndContractInternal(proposal, request, currentUserId, null);
    }

    private ContractDTO acceptAndContractInternal(Proposal proposal, AcceptContractRequestDto request, Integer currentUserId, HttpServletRequest httpRequest) {
        Job job = proposal.getJob();
        if (job == null) {
            throw new IllegalStateException("A proposta não possui demanda associada.");
        }

        // Precondition 3: Lawyer verified status and valid OAB
        User lawyer = proposal.getLawyer();
        if (lawyer == null) {
            throw new IllegalStateException("A proposta não possui advogado associado.");
        }
        if (authorizationService != null) {
            authorizationService.enforceVerifiedLawyer(lawyer.getId());
        }

        // Precondition 4: Conflict of interest check
        if (conflictCheckRepository != null) {
            ConflictCheck conflictCheck = conflictCheckRepository.findByJobJobIdAndLawyerId(job.getJobId(), lawyer.getId())
                    .orElse(null);

            if (conflictCheck != null && conflictCheck.getStatus() == ConflictStatus.BLOCKED) {
                throw new ConflictBlockedException("Não foi possível prosseguir com a contratação devido a impedimento ético-profissional nos termos do Código de Ética da OAB.");
            }

            if (conflictCheck == null) {
                conflictCheck = ConflictCheck.builder()
                        .job(job)
                        .lawyer(lawyer)
                        .status(ConflictStatus.CLEAR)
                        .createdAt(LocalDateTime.now())
                        .resolvedAt(LocalDateTime.now())
                        .build();
                conflictCheckRepository.save(conflictCheck);
            } else if (conflictCheck.getStatus() == ConflictStatus.NOT_STARTED || conflictCheck.getStatus() == ConflictStatus.IN_REVIEW) {
                conflictCheck.setStatus(ConflictStatus.CLEAR);
                conflictCheck.setResolvedAt(LocalDateTime.now());
                conflictCheckRepository.save(conflictCheck);
            }
        }

        // Proposal -> Accepted
        proposal.setStatus(ProposalStatus.Accepted);
        if (proposalRepository != null) {
            Proposal savedProp = proposalRepository.save(proposal);
            if (savedProp != null) {
                proposal = savedProp;
            }
        }

        // Competitors -> Rejected
        List<Proposal> competitors = proposalRepository.findByJobJobId(job.getJobId());
        if (competitors != null) {
            for (Proposal competitor : competitors) {
                if (!competitor.getProposalId().equals(proposal.getProposalId())
                        && (competitor.getStatus() == ProposalStatus.Pending || competitor.getStatus() == ProposalStatus.Countered)) {
                    competitor.setStatus(ProposalStatus.Rejected);
                    proposalRepository.save(competitor);

                    if (notificationService != null && competitor.getLawyer() != null) {
                        notificationService.createNotification(
                                competitor.getLawyer().getId(),
                                NotificationType.PROPOSAL_REJECTED,
                                "Proposta não selecionada",
                                "Outra proposta foi selecionada para o caso " + job.getTitle(),
                                "job",
                                job.getJobId()
                        );
                    }
                }
            }
        }

        // Job -> InProgress
        job.setStatus(JobStatus.InProgress);
        if (jobRepository != null) {
            jobRepository.save(job);
        }

        // SHA-256 digital signature receipt
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "127.0.0.1";
        String userAgent = httpRequest != null ? httpRequest.getHeader("User-Agent") : "LegaWork-Client";
        String termsVer = request.getTermsVersion() != null && !request.getTermsVersion().trim().isEmpty()
                ? request.getTermsVersion().trim() : "v1.0";
        LocalDateTime signedAt = LocalDateTime.now();
        BigDecimal totalVal = proposal.getTotalValue() != null ? proposal.getTotalValue() : job.getBudget();

        String rawReceiptPayload = "CONTRACT_MANDATE|JOB:" + job.getJobId()
                + "|CLIENT:" + (currentUserId != null ? currentUserId : (job.getClient() != null ? job.getClient().getId() : 0))
                + "|LAWYER:" + lawyer.getId()
                + "|PROPOSAL:" + proposal.getProposalId()
                + "|VALUE:" + (totalVal != null ? totalVal.toPlainString() : "0.00")
                + "|TERMS:" + termsVer
                + "|SIGNED_AT:" + signedAt.toString()
                + "|NONCE:" + UUID.randomUUID().toString();

        String sha256HexReceipt = computeSha256Hex(rawReceiptPayload);

        // Contract
        Contract contract = Contract.builder()
                .job(job)
                .client(job.getClient())
                .lawyer(lawyer)
                .proposal(proposal)
                .title("Mandato: " + job.getTitle())
                .description(request.getNotes() != null && !request.getNotes().trim().isEmpty()
                        ? request.getNotes().trim()
                        : (proposal.getCoverLetter() != null ? proposal.getCoverLetter() : job.getDescription()))
                .totalValue(totalVal)
                .startDate(LocalDate.now())
                .endDate(proposal.getProposedDuration() != null
                        ? LocalDate.now().plusDays(proposal.getProposedDuration())
                        : null)
                .status(ContractStatus.Active)
                .conflictStatus("CLEAR")
                .termsVersion(termsVer)
                .signedAt(signedAt)
                .hashReceipt(sha256HexReceipt)
                .build();

        contract = contractRepository.save(contract);

        // Milestone
        if (milestoneRepository != null) {
            ContractMilestone milestone = ContractMilestone.builder()
                    .contract(contract)
                    .title("Execução dos Serviços Contratados")
                    .description(proposal.getStrategy() != null ? proposal.getStrategy() : "Entrega dos serviços jurídicos acordados no mandato.")
                    .amount(contract.getTotalValue())
                    .dueDate(contract.getEndDate())
                    .status(MilestoneStatus.Pending)
                    .build();
            milestoneRepository.save(milestone);
        }

        // Signature
        if (contractSignatureRepository != null && job.getClient() != null) {
            ContractSignature signature = ContractSignature.builder()
                    .contract(contract)
                    .user(job.getClient())
                    .signatureType("CLIENT_ACCEPTANCE")
                    .termsVersion(termsVer)
                    .ipAddress(clientIp)
                    .userAgent(userAgent)
                    .hashReceipt(sha256HexReceipt)
                    .signedAt(signedAt)
                    .build();
            contractSignatureRepository.save(signature);
        }

        // Notifications
        if (notificationService != null) {
            if (contract.getClient() != null) {
                notificationService.createNotification(
                        contract.getClient().getId(),
                        NotificationType.CONTRACT_CREATED,
                        "Mandato formalizado com sucesso",
                        "O mandato para " + job.getTitle() + " foi formalizado com " + lawyer.getFirstName() + " " + lawyer.getLastName() + ". Recibo: " + sha256HexReceipt.substring(0, Math.min(12, sha256HexReceipt.length())) + "...",
                        "contract",
                        contract.getContractId()
                );
            }

            notificationService.createNotification(
                    lawyer.getId(),
                    NotificationType.CONTRACT_CREATED,
                    "Proposta aceita e Mandato criado",
                    "Sua proposta para " + job.getTitle() + " foi aceita por " + (contract.getClient() != null ? contract.getClient().getFirstName() : "o cliente") + ". O contrato está ativo.",
                    "contract",
                    contract.getContractId()
            );
        }

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
    public ContractTimelineDto getContractTimeline(int contractId) {
        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId == null) {
            throw new AccessDeniedException("Não autenticado.");
        }

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contrato não encontrado: " + contractId));

        authorizationService.enforceContractParticipant(contractId, currentUserId);

        List<ContractTimelineEventDto> events = new ArrayList<>();
        Job job = contract.getJob();

        // 1. Demand created
        if (job != null) {
            Map<String, Object> jobMeta = new HashMap<>();
            jobMeta.put("jobId", job.getJobId());
            if (job.getBudget() != null) jobMeta.put("budget", job.getBudget());

            events.add(ContractTimelineEventDto.builder()
                    .id("timeline-demand-" + job.getJobId())
                    .contractId(contractId)
                    .eventType("DEMAND_CREATED")
                    .title("Demanda Publicada")
                    .description("Demanda criada: " + job.getTitle())
                    .timestamp(job.getCreatedAt())
                    .actorId(job.getClient() != null ? job.getClient().getId() : null)
                    .actorName(job.getClient() != null ? job.getClient().getFirstName() + " " + job.getClient().getLastName() : null)
                    .actorRole("CLIENT")
                    .status("COMPLETED")
                    .metadata(jobMeta)
                    .build());
        }

        // 2. Proposal submitted
        Proposal proposal = contract.getProposal();
        if (proposal != null) {
            Map<String, Object> propMeta = new HashMap<>();
            propMeta.put("proposalId", proposal.getProposalId());
            if (proposal.getTotalValue() != null) propMeta.put("totalValue", proposal.getTotalValue());
            propMeta.put("proposalVersion", proposal.getProposalVersion() != null ? proposal.getProposalVersion() : 1);

            events.add(ContractTimelineEventDto.builder()
                    .id("timeline-proposal-" + proposal.getProposalId())
                    .contractId(contractId)
                    .eventType("PROPOSAL_SUBMITTED")
                    .title("Proposta Submetida")
                    .description("Proposta submetida pelo advogado (versão " + (proposal.getProposalVersion() != null ? proposal.getProposalVersion() : 1) + ")")
                    .timestamp(proposal.getCreatedAt())
                    .actorId(proposal.getLawyer() != null ? proposal.getLawyer().getId() : null)
                    .actorName(proposal.getLawyer() != null ? proposal.getLawyer().getFirstName() + " " + proposal.getLawyer().getLastName() : null)
                    .actorRole("LAWYER")
                    .status("COMPLETED")
                    .metadata(propMeta)
                    .build());
        }

        // 3. Conflict of interest check
        if (job != null && contract.getLawyer() != null) {
            ConflictCheck conflict = conflictCheckRepository.findByJobJobIdAndLawyerId(job.getJobId(), contract.getLawyer().getId()).orElse(null);
            if (conflict != null && conflict.getResolvedAt() != null) {
                Map<String, Object> confMeta = new HashMap<>();
                confMeta.put("conflictStatus", conflict.getStatus().name());

                events.add(ContractTimelineEventDto.builder()
                        .id("timeline-conflict-" + conflict.getId())
                        .contractId(contractId)
                        .eventType("CONFLICT_CLEARED")
                        .title("Verificação de Conflito de Interesses")
                        .description("Checagem de conflito concluída: " + conflict.getStatus().name())
                        .timestamp(conflict.getResolvedAt())
                        .actorId(contract.getLawyer().getId())
                        .actorName(contract.getLawyer().getFirstName() + " " + contract.getLawyer().getLastName())
                        .actorRole("LAWYER")
                        .status("COMPLETED")
                        .metadata(confMeta)
                        .build());
            }
        }

        // 4. Contract signed
        List<ContractSignature> signatures = contractSignatureRepository.findByContractContractId(contractId);
        for (ContractSignature sig : signatures) {
            Map<String, Object> sigMeta = new HashMap<>();
            sigMeta.put("termsVersion", sig.getTermsVersion());
            sigMeta.put("hashReceipt", sig.getHashReceipt());
            if (sig.getIpAddress() != null) sigMeta.put("ipAddress", sig.getIpAddress());

            events.add(ContractTimelineEventDto.builder()
                    .id("timeline-sig-" + sig.getId())
                    .contractId(contractId)
                    .eventType("CONTRACT_SIGNED")
                    .title("Mandato Formalizado e Assinado")
                    .description("Termos " + sig.getTermsVersion() + " aceitos eletronicamente. Recibo SHA-256 gerado.")
                    .timestamp(sig.getSignedAt())
                    .actorId(sig.getUser() != null ? sig.getUser().getId() : null)
                    .actorName(sig.getUser() != null ? sig.getUser().getFirstName() + " " + sig.getUser().getLastName() : null)
                    .actorRole("CLIENT")
                    .status("COMPLETED")
                    .metadata(sigMeta)
                    .build());
        }

        // 5. Milestones
        List<ContractMilestone> milestones = milestoneRepository.findByContractContractId(contractId);
        for (ContractMilestone m : milestones) {
            Map<String, Object> msMeta = new HashMap<>();
            msMeta.put("milestoneId", m.getMilestoneId());
            if (m.getAmount() != null) msMeta.put("amount", m.getAmount());

            events.add(ContractTimelineEventDto.builder()
                    .id("timeline-ms-" + m.getMilestoneId())
                    .contractId(contractId)
                    .eventType("MILESTONE_CREATED")
                    .title("Etapa Contratual: " + m.getTitle())
                    .description(m.getDescription() != null ? m.getDescription() : "Etapa definida para o mandato.")
                    .timestamp(m.getCreatedAt() != null ? m.getCreatedAt() : contract.getCreatedAt())
                    .actorId(contract.getLawyer() != null ? contract.getLawyer().getId() : null)
                    .actorName(contract.getLawyer() != null ? contract.getLawyer().getFirstName() + " " + contract.getLawyer().getLastName() : null)
                    .actorRole("LAWYER")
                    .status(m.getStatus().name())
                    .metadata(msMeta)
                    .build());

            if (m.getStatus() == MilestoneStatus.Completed && m.getCompletedAt() != null) {
                Map<String, Object> compMeta = new HashMap<>();
                compMeta.put("milestoneId", m.getMilestoneId());

                events.add(ContractTimelineEventDto.builder()
                        .id("timeline-ms-comp-" + m.getMilestoneId())
                        .contractId(contractId)
                        .eventType("MILESTONE_COMPLETED")
                        .title("Etapa Concluída: " + m.getTitle())
                        .description("Etapa contratual concluída.")
                        .timestamp(m.getCompletedAt())
                        .actorId(contract.getLawyer() != null ? contract.getLawyer().getId() : null)
                        .actorName(contract.getLawyer() != null ? contract.getLawyer().getFirstName() + " " + contract.getLawyer().getLastName() : null)
                        .actorRole("LAWYER")
                        .status("COMPLETED")
                        .metadata(compMeta)
                        .build());
            }
        }

        // 6. Secure Documents
        List<SecureDocument> documents = secureDocumentRepository.findByContractContractIdAndIsDeletedFalse(contractId);
        for (SecureDocument doc : documents) {
            Map<String, Object> docMeta = new HashMap<>();
            docMeta.put("documentId", doc.getId());
            docMeta.put("fileName", doc.getFileName());
            docMeta.put("sha256Hash", doc.getSha256Hash());
            docMeta.put("classification", doc.getClassification().name());
            docMeta.put("fileSize", doc.getFileSize());

            String shortHash = doc.getSha256Hash() != null && doc.getSha256Hash().length() > 16
                    ? doc.getSha256Hash().substring(0, 16) + "..." : doc.getSha256Hash();

            events.add(ContractTimelineEventDto.builder()
                    .id("timeline-doc-" + doc.getId())
                    .contractId(contractId)
                    .eventType("DOCUMENT_ATTACHED")
                    .title("Documento Seguro Anexado: " + doc.getFileName())
                    .description("Classificação: " + doc.getClassification().name() + " | SHA-256: " + shortHash)
                    .timestamp(doc.getCreatedAt())
                    .actorId(doc.getOwner() != null ? doc.getOwner().getId() : null)
                    .actorName(doc.getOwner() != null ? doc.getOwner().getFirstName() + " " + doc.getOwner().getLastName() : null)
                    .actorRole(doc.getOwner() != null && contract.getClient() != null && doc.getOwner().getId().equals(contract.getClient().getId()) ? "CLIENT" : "LAWYER")
                    .status("ACTIVE")
                    .metadata(docMeta)
                    .build());
        }

        // Sort events chronologically (null timestamps sorted first)
        events.sort(Comparator.comparing(ContractTimelineEventDto::getTimestamp, Comparator.nullsFirst(Comparator.naturalOrder())));

        return ContractTimelineDto.builder()
                .contractId(contractId)
                .contractTitle(contract.getTitle())
                .events(events)
                .build();
    }

    @Override
    @Transactional
    public ContractDTO completeContract(int contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found"));

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

        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId != null && !currentUserId.equals(contract.getClient().getId())) {
            throw new SecurityException("You can only terminate your own contracts");
        }

        contract.setStatus(ContractStatus.Terminated);
        contract.setUpdatedAt(LocalDateTime.now());
        contract = contractRepository.save(contract);

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

    private String computeSha256Hex(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algoritmo SHA-256 não encontrado no ambiente Java.", e);
        }
    }

    private ContractDTO mapToDTO(Contract contract) {
        UserProfile clientProfile = contract.getClient() != null ? contract.getClient().getUserProfile() : null;
        UserProfile lawyerProfile = contract.getLawyer() != null ? contract.getLawyer().getUserProfile() : null;

        List<ContractMilestoneDTO> milestoneDTOs = contract.getMilestones() != null
                ? contract.getMilestones().stream().map(this::mapMilestoneToDTO).collect(Collectors.toList())
                : Collections.emptyList();

        List<ContractSignatureDto> signatureDTOs = contract.getSignatures() != null
                ? contract.getSignatures().stream().map(this::mapSignatureToDTO).collect(Collectors.toList())
                : (contractSignatureRepository != null && contract.getContractId() != null
                        ? contractSignatureRepository.findByContractContractId(contract.getContractId()).stream()
                                .map(this::mapSignatureToDTO).collect(Collectors.toList())
                        : Collections.emptyList());

        return ContractDTO.builder()
                .contractId(contract.getContractId())
                .jobId(contract.getJob() != null ? contract.getJob().getJobId() : null)
                .jobTitle(contract.getJob() != null ? contract.getJob().getTitle() : null)
                .clientId(contract.getClient() != null ? contract.getClient().getId() : null)
                .clientName(contract.getClient() != null ? contract.getClient().getFirstName() + " " + contract.getClient().getLastName() : null)
                .lawyerId(contract.getLawyer() != null ? contract.getLawyer().getId() : null)
                .lawyerName(contract.getLawyer() != null ? contract.getLawyer().getFirstName() + " " + contract.getLawyer().getLastName() : null)
                .lawyerPhotoUrl(lawyerProfile != null ? lawyerProfile.getPhotoUrl() : null)
                .lawyerOab(lawyerProfile != null ? lawyerProfile.getOabNumber() : null)
                .proposalId(contract.getProposal() != null ? contract.getProposal().getProposalId() : null)
                .title(contract.getTitle())
                .description(contract.getDescription())
                .totalValue(contract.getTotalValue())
                .startDate(contract.getStartDate())
                .endDate(contract.getEndDate())
                .status(contract.getStatus())
                .conflictStatus(contract.getConflictStatus())
                .termsVersion(contract.getTermsVersion())
                .signedAt(contract.getSignedAt())
                .hashReceipt(contract.getHashReceipt())
                .createdAt(contract.getCreatedAt())
                .milestones(milestoneDTOs)
                .signatures(signatureDTOs)
                .build();
    }

    private ContractMilestoneDTO mapMilestoneToDTO(ContractMilestone milestone) {
        return ContractMilestoneDTO.builder()
                .milestoneId(milestone.getMilestoneId())
                .contractId(milestone.getContract() != null ? milestone.getContract().getContractId() : null)
                .title(milestone.getTitle())
                .description(milestone.getDescription())
                .amount(milestone.getAmount())
                .dueDate(milestone.getDueDate())
                .status(milestone.getStatus())
                .completedAt(milestone.getCompletedAt())
                .build();
    }

    private ContractSignatureDto mapSignatureToDTO(ContractSignature sig) {
        return ContractSignatureDto.builder()
                .id(sig.getId())
                .contractId(sig.getContract() != null ? sig.getContract().getContractId() : null)
                .userId(sig.getUser() != null ? sig.getUser().getId() : null)
                .userName(sig.getUser() != null ? sig.getUser().getFirstName() + " " + sig.getUser().getLastName() : null)
                .signatureType(sig.getSignatureType())
                .termsVersion(sig.getTermsVersion())
                .ipAddress(sig.getIpAddress())
                .userAgent(sig.getUserAgent())
                .hashReceipt(sig.getHashReceipt())
                .signedAt(sig.getSignedAt())
                .build();
    }
}
