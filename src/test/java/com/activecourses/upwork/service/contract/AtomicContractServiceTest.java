package com.activecourses.upwork.service.contract;

import com.activecourses.upwork.dto.AcceptContractRequestDto;
import com.activecourses.upwork.dto.ContractDTO;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AtomicContractServiceTest {

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

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private ContractServiceImpl contractService;

    private User client;
    private User lawyer;
    private User competitorLawyer;
    private Job job;
    private Proposal proposal;
    private Proposal competitorProposal;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        client = User.builder().id(10).firstName("Maria").lastName("Silva").build();
        lawyer = User.builder().id(20).firstName("Dr. Carlos").lastName("Advogado").build();
        competitorLawyer = User.builder().id(30).firstName("Dra. Ana").lastName("Concorrente").build();

        job = Job.builder()
                .jobId(100)
                .title("Assessoria Jurídica em Contratos")
                .client(client)
                .budget(BigDecimal.valueOf(5000))
                .status(JobStatus.Open)
                .build();

        proposal = Proposal.builder()
                .proposalId(501)
                .job(job)
                .lawyer(lawyer)
                .status(ProposalStatus.Pending)
                .totalValue(BigDecimal.valueOf(4500))
                .coverLetter("Proposta detalhada de prestação de serviços.")
                .strategy("Fase 1: Minuta, Fase 2: Negociação")
                .proposedDuration(30)
                .proposalVersion(1)
                .build();

        competitorProposal = Proposal.builder()
                .proposalId(502)
                .job(job)
                .lawyer(competitorLawyer)
                .status(ProposalStatus.Pending)
                .totalValue(BigDecimal.valueOf(4800))
                .proposalVersion(1)
                .build();

        when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.100");
        when(httpRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
    }

    @Test
    @DisplayName("acceptAndContract deve executar contratação atômica com sucesso e rejeitar concorrentes")
    void testAcceptAndContract_Success() {
        when(authService.getCurrentUserId()).thenReturn(10);
        when(proposalRepository.findById(501)).thenReturn(Optional.of(proposal));
        doNothing().when(authorizationService).enforceJobOwner(100, 10);
        doNothing().when(authorizationService).enforceVerifiedLawyer(20);
        when(conflictCheckRepository.findByJobJobIdAndLawyerId(100, 20)).thenReturn(Optional.empty());
        when(proposalRepository.findByJobJobId(100)).thenReturn(List.of(proposal, competitorProposal));

        when(proposalRepository.save(any(Proposal.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
        when(conflictCheckRepository.save(any(ConflictCheck.class))).thenAnswer(inv -> inv.getArgument(0));

        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> {
            Contract c = inv.getArgument(0);
            c.setContractId(999);
            return c;
        });

        when(contractSignatureRepository.save(any(ContractSignature.class))).thenAnswer(inv -> {
            ContractSignature s = inv.getArgument(0);
            s.setId(101L);
            return s;
        });

        AcceptContractRequestDto request = AcceptContractRequestDto.builder()
                .proposalId(501)
                .termsVersion("v1.0")
                .signatureConsent(true)
                .build();

        ContractDTO result = contractService.acceptAndContract(request, httpRequest);

        assertNotNull(result);
        assertEquals(999, result.getContractId());
        assertEquals(ContractStatus.Active, result.getStatus());
        assertEquals("CLEAR", result.getConflictStatus());
        assertEquals("v1.0", result.getTermsVersion());
        assertNotNull(result.getHashReceipt());
        assertNotNull(result.getSignedAt());

        // Verify proposal accepted
        assertEquals(ProposalStatus.Accepted, proposal.getStatus());

        // Verify competitor rejected
        assertEquals(ProposalStatus.Rejected, competitorProposal.getStatus());

        // Verify job in progress
        assertEquals(JobStatus.InProgress, job.getStatus());

        // Verify milestone created
        verify(milestoneRepository, times(1)).save(any(ContractMilestone.class));

        // Verify digital signature recorded with SHA-256 receipt
        verify(contractSignatureRepository, times(1)).save(argThat(sig ->
                sig.getTermsVersion().equals("v1.0")
                        && sig.getIpAddress().equals("192.168.1.100")
                        && sig.getHashReceipt() != null
                        && sig.getHashReceipt().length() == 64
        ));

        // Verify notifications sent to client, accepted lawyer, and rejected competitor
        verify(notificationService, times(1)).createNotification(
                eq(10), eq(NotificationType.CONTRACT_CREATED), anyString(), anyString(), eq("contract"), eq(999)
        );
        verify(notificationService, times(1)).createNotification(
                eq(20), eq(NotificationType.CONTRACT_CREATED), anyString(), anyString(), eq("contract"), eq(999)
        );
        verify(notificationService, times(1)).createNotification(
                eq(30), eq(NotificationType.PROPOSAL_REJECTED), anyString(), anyString(), eq("job"), eq(100)
        );
    }

    @Test
    @DisplayName("acceptAndContract deve lançar ConflictBlockedException sem vazar dados se o conflito for BLOCKED")
    void testAcceptAndContract_ThrowsConflictBlockedException() {
        when(authService.getCurrentUserId()).thenReturn(10);
        when(proposalRepository.findById(501)).thenReturn(Optional.of(proposal));
        doNothing().when(authorizationService).enforceJobOwner(100, 10);
        doNothing().when(authorizationService).enforceVerifiedLawyer(20);

        ConflictCheck blockedCheck = ConflictCheck.builder()
                .job(job)
                .lawyer(lawyer)
                .status(ConflictStatus.BLOCKED)
                .reasonMasked("Impedimento ético-profissional detectado nos termos do Código de Ética da OAB.")
                .build();
        when(conflictCheckRepository.findByJobJobIdAndLawyerId(100, 20)).thenReturn(Optional.of(blockedCheck));

        AcceptContractRequestDto request = AcceptContractRequestDto.builder()
                .proposalId(501)
                .termsVersion("v1.0")
                .build();

        ConflictBlockedException ex = assertThrows(ConflictBlockedException.class,
                () -> contractService.acceptAndContract(request, httpRequest));

        assertTrue(ex.getMessage().contains("impedimento ético-profissional"));
        assertFalse(ex.getMessage().contains("Empresa XYZ"));
        assertFalse(ex.getMessage().contains("000.000.000-00"));

        // No contract saved and no state mutated
        verify(contractRepository, never()).save(any(Contract.class));
        assertEquals(ProposalStatus.Pending, proposal.getStatus());
    }

    @Test
    @DisplayName("acceptAndContract deve lançar AccessDeniedException se o advogado não for verificado")
    void testAcceptAndContract_ThrowsAccessDeniedIfLawyerNotVerified() {
        when(authService.getCurrentUserId()).thenReturn(10);
        when(proposalRepository.findById(501)).thenReturn(Optional.of(proposal));
        doNothing().when(authorizationService).enforceJobOwner(100, 10);
        doThrow(new AccessDeniedException("Lawyer verification required. Current status: DRAFT"))
                .when(authorizationService).enforceVerifiedLawyer(20);

        AcceptContractRequestDto request = AcceptContractRequestDto.builder()
                .proposalId(501)
                .build();

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> contractService.acceptAndContract(request, httpRequest));

        assertTrue(ex.getMessage().contains("Lawyer verification required"));
        verify(contractRepository, never()).save(any(Contract.class));
    }

    @Test
    @DisplayName("acceptAndContract deve lançar AccessDeniedException se o usuário autenticado não for o dono da demanda")
    void testAcceptAndContract_ThrowsAccessDeniedIfNonOwnerClient() {
        when(authService.getCurrentUserId()).thenReturn(99); // different client
        when(proposalRepository.findById(501)).thenReturn(Optional.of(proposal));
        doThrow(new AccessDeniedException("Access denied: Only job owner can accept proposals and contract."))
                .when(authorizationService).enforceJobOwner(100, 99);

        AcceptContractRequestDto request = AcceptContractRequestDto.builder()
                .proposalId(501)
                .build();

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> contractService.acceptAndContract(request, httpRequest));

        assertTrue(ex.getMessage().contains("Access denied"));
        verify(contractRepository, never()).save(any(Contract.class));
        assertEquals(ProposalStatus.Pending, proposal.getStatus());
    }

    @Test
    @DisplayName("acceptAndContract deve lançar IllegalStateException se a proposta não estiver pendente")
    void testAcceptAndContract_ThrowsIfProposalNotPending() {
        proposal.setStatus(ProposalStatus.Rejected);
        when(authService.getCurrentUserId()).thenReturn(10);
        when(proposalRepository.findById(501)).thenReturn(Optional.of(proposal));
        doNothing().when(authorizationService).enforceJobOwner(100, 10);

        AcceptContractRequestDto request = AcceptContractRequestDto.builder()
                .proposalId(501)
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> contractService.acceptAndContract(request, httpRequest));

        assertTrue(ex.getMessage().contains("Apenas propostas pendentes podem ser aceitas"));
        verify(contractRepository, never()).save(any(Contract.class));
    }
}
