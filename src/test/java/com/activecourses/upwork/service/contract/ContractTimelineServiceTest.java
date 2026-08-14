package com.activecourses.upwork.service.contract;

import com.activecourses.upwork.dto.ContractTimelineDto;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContractTimelineServiceTest {

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private ContractMilestoneRepository milestoneRepository;

    @Mock
    private ProposalRepository proposalRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private AuthService authService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private ConflictCheckRepository conflictCheckRepository;

    @Mock
    private ContractSignatureRepository contractSignatureRepository;

    @Mock
    private SecureDocumentRepository secureDocumentRepository;

    @InjectMocks
    private ContractServiceImpl contractService;

    private User client;
    private User lawyer;
    private Job job;
    private Proposal proposal;
    private Contract contract;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        client = User.builder().id(10).firstName("Maria").lastName("Silva").build();
        lawyer = User.builder().id(20).firstName("Dr. Carlos").lastName("Advogado").build();

        job = Job.builder()
                .jobId(100)
                .title("Processo Tributário")
                .client(client)
                .createdAt(LocalDateTime.of(2026, 8, 1, 10, 0))
                .build();

        proposal = Proposal.builder()
                .proposalId(501)
                .job(job)
                .lawyer(lawyer)
                .totalValue(BigDecimal.valueOf(10000))
                .proposalVersion(1)
                .createdAt(LocalDateTime.of(2026, 8, 2, 14, 0))
                .build();

        contract = Contract.builder()
                .contractId(999)
                .job(job)
                .client(client)
                .lawyer(lawyer)
                .proposal(proposal)
                .title("Mandato Tributário")
                .createdAt(LocalDateTime.of(2026, 8, 3, 9, 0))
                .build();
    }

    @Test
    @DisplayName("getContractTimeline deve compilar eventos cronológicos consolidados")
    void testGetContractTimeline_Success() {
        when(authService.getCurrentUserId()).thenReturn(10);
        when(contractRepository.findById(999)).thenReturn(Optional.of(contract));
        doNothing().when(authorizationService).enforceContractParticipant(999, 10);

        ConflictCheck conflict = ConflictCheck.builder()
                .id(1L)
                .job(job)
                .lawyer(lawyer)
                .status(ConflictStatus.CLEAR)
                .resolvedAt(LocalDateTime.of(2026, 8, 3, 9, 1))
                .build();
        when(conflictCheckRepository.findByJobJobIdAndLawyerId(100, 20)).thenReturn(Optional.of(conflict));

        ContractSignature sig = ContractSignature.builder()
                .id(1L)
                .contract(contract)
                .user(client)
                .termsVersion("v1.0")
                .hashReceipt("abc123sha256receipt")
                .signedAt(LocalDateTime.of(2026, 8, 3, 9, 2))
                .build();
        when(contractSignatureRepository.findByContractContractId(999)).thenReturn(List.of(sig));

        ContractMilestone milestone = ContractMilestone.builder()
                .milestoneId(1)
                .contract(contract)
                .title("Apresentação de Defesa")
                .status(MilestoneStatus.Completed)
                .amount(BigDecimal.valueOf(5000))
                .createdAt(LocalDateTime.of(2026, 8, 3, 9, 3))
                .completedAt(LocalDateTime.of(2026, 8, 10, 15, 0))
                .build();
        when(milestoneRepository.findByContractContractId(999)).thenReturn(List.of(milestone));

        SecureDocument doc = SecureDocument.builder()
                .id(50L)
                .contract(contract)
                .owner(lawyer)
                .fileName("peticao_protocolada.pdf")
                .classification(DocumentClassification.CONFIDENTIAL)
                .sha256Hash("fedcba9876543210")
                .fileSize(1024L)
                .isDeleted(false)
                .createdAt(LocalDateTime.of(2026, 8, 10, 16, 0))
                .build();
        when(secureDocumentRepository.findByContractContractIdAndIsDeletedFalse(999)).thenReturn(List.of(doc));

        ContractTimelineDto timeline = contractService.getContractTimeline(999);

        assertNotNull(timeline);
        assertEquals(999, timeline.getContractId());
        assertEquals("Mandato Tributário", timeline.getContractTitle());
        assertFalse(timeline.getEvents().isEmpty());

        // Verify ordering
        for (int i = 0; i < timeline.getEvents().size() - 1; i++) {
            LocalDateTime t1 = timeline.getEvents().get(i).getTimestamp();
            LocalDateTime t2 = timeline.getEvents().get(i + 1).getTimestamp();
            if (t1 != null && t2 != null) {
                assertTrue(!t1.isAfter(t2), "Eventos devem estar ordenados cronologicamente");
            }
        }

        // Verify presence of event types
        List<String> eventTypes = timeline.getEvents().stream().map(e -> e.getEventType()).toList();
        assertTrue(eventTypes.contains("DEMAND_CREATED"));
        assertTrue(eventTypes.contains("PROPOSAL_SUBMITTED"));
        assertTrue(eventTypes.contains("CONFLICT_CLEARED"));
        assertTrue(eventTypes.contains("CONTRACT_SIGNED"));
        assertTrue(eventTypes.contains("MILESTONE_CREATED"));
        assertTrue(eventTypes.contains("MILESTONE_COMPLETED"));
        assertTrue(eventTypes.contains("DOCUMENT_ATTACHED"));
    }
}
