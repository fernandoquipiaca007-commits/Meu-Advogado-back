package com.activecourses.upwork.adversarial;

import com.activecourses.upwork.dto.AcceptContractRequestDto;
import com.activecourses.upwork.dto.ContractDTO;
import com.activecourses.upwork.dto.SecureDocumentDto;
import com.activecourses.upwork.exception.ConflictBlockedException;
import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.conflict.ConflictCheckRepository;
import com.activecourses.upwork.repository.contract.ContractMilestoneRepository;
import com.activecourses.upwork.repository.contract.ContractRepository;
import com.activecourses.upwork.repository.contract.ContractSignatureRepository;
import com.activecourses.upwork.repository.document.DocumentAccessLogRepository;
import com.activecourses.upwork.repository.document.SecureDocumentRepository;
import com.activecourses.upwork.repository.job.JobRepository;
import com.activecourses.upwork.repository.job.ProposalRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.service.conflict.ConflictServiceImpl;
import com.activecourses.upwork.service.contract.ContractServiceImpl;
import com.activecourses.upwork.service.document.SecureDocumentService.SecureDownloadInfo;
import com.activecourses.upwork.service.document.SecureDocumentServiceImpl;
import com.activecourses.upwork.service.notification.NotificationService;
import com.activecourses.upwork.service.security.ImplAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Empirical Adversarial & Security Challenge Suite for LegaWork Phase 3 Backend.
 * 
 * Focus Areas:
 * 1. Precondition enforcement on acceptAndContract (non-owner, unverified, non-pending, conflict BLOCKED)
 * 2. Privacy preservation on BLOCKED conflict (zero leak of third-party names/CPF/CNPJ/process numbers)
 * 3. Secure Document Vault access control (unauthorized download 403, infected file 403, deleted file 403)
 * 4. Secure Document Vault integrity (SHA-256 computation, download streaming, immutable audit logging)
 */
public class Phase3BackendSecurityChallengerTest {

    // =========================================================================
    // 1. ATOMIC CONTRACT PRECONDITION ENFORCEMENT & CONFLICT ISOLATION
    // =========================================================================
    @Nested
    @DisplayName("1. acceptAndContract Precondition Enforcement & Privacy")
    class AtomicContractPreconditionTests {

        private ContractRepository contractRepository;
        private ContractMilestoneRepository milestoneRepository;
        private ProposalRepository proposalRepository;
        private JobRepository jobRepository;
        private AuthService authService;
        private NotificationService notificationService;
        private ImplAuthorizationService authorizationService;
        private ConflictCheckRepository conflictCheckRepository;
        private ContractSignatureRepository contractSignatureRepository;
        private SecureDocumentRepository secureDocumentRepository;
        private UserRepository userRepository;
        private HttpServletRequest httpRequest;

        private ContractServiceImpl contractService;

        private User ownerClient;
        private User attackerClient;
        private User verifiedLawyer;
        private User unverifiedLawyer;
        private Job targetJob;
        private Proposal validProposal;
        private Proposal competitorProposal;

        @BeforeEach
        void setUp() {
            contractRepository = mock(ContractRepository.class);
            milestoneRepository = mock(ContractMilestoneRepository.class);
            proposalRepository = mock(ProposalRepository.class);
            jobRepository = mock(JobRepository.class);
            authService = mock(AuthService.class);
            notificationService = mock(NotificationService.class);
            conflictCheckRepository = mock(ConflictCheckRepository.class);
            contractSignatureRepository = mock(ContractSignatureRepository.class);
            secureDocumentRepository = mock(SecureDocumentRepository.class);
            userRepository = mock(UserRepository.class);
            httpRequest = mock(HttpServletRequest.class);

            authorizationService = new ImplAuthorizationService(
                    userRepository,
                    contractRepository,
                    jobRepository,
                    proposalRepository,
                    null,
                    secureDocumentRepository
            );

            contractService = new ContractServiceImpl(
                    contractRepository,
                    milestoneRepository,
                    proposalRepository,
                    jobRepository,
                    authService,
                    notificationService,
                    authorizationService,
                    conflictCheckRepository,
                    contractSignatureRepository,
                    secureDocumentRepository
            );

            ownerClient = User.builder().id(10).email("owner@client.com").firstName("Maria").lastName("Silva").build();
            attackerClient = User.builder().id(99).email("attacker@client.com").firstName("Attacker").lastName("Hacker").build();

            UserProfile verifiedProfile = UserProfile.builder()
                    .verificationStatus(VerificationStatus.VERIFIED)
                    .oabNumber("123456/SP")
                    .oabExpiryDate(LocalDate.now().plusYears(2))
                    .build();
            verifiedLawyer = User.builder().id(20).email("lawyer@law.com").firstName("Dr. Carlos").lastName("Advogado")
                    .userProfile(verifiedProfile).build();

            UserProfile pendingProfile = UserProfile.builder()
                    .verificationStatus(VerificationStatus.PENDING)
                    .oabNumber("654321/SP")
                    .build();
            unverifiedLawyer = User.builder().id(21).email("unverified@law.com").firstName("Dr. Bob").lastName("Pending")
                    .userProfile(pendingProfile).build();

            targetJob = Job.builder()
                    .jobId(100)
                    .title("Revisão de Contrato Societário")
                    .client(ownerClient)
                    .budget(BigDecimal.valueOf(6000))
                    .status(JobStatus.Open)
                    .build();

            validProposal = Proposal.builder()
                    .proposalId(501)
                    .job(targetJob)
                    .lawyer(verifiedLawyer)
                    .status(ProposalStatus.Pending)
                    .totalValue(BigDecimal.valueOf(5500))
                    .coverLetter("Proposta técnica para assessoria.")
                    .proposalVersion(1)
                    .build();

            competitorProposal = Proposal.builder()
                    .proposalId(502)
                    .job(targetJob)
                    .lawyer(User.builder().id(30).firstName("Dra. Beatriz").build())
                    .status(ProposalStatus.Pending)
                    .totalValue(BigDecimal.valueOf(5800))
                    .proposalVersion(1)
                    .build();

            when(userRepository.findById(10)).thenReturn(Optional.of(ownerClient));
            when(userRepository.findById(99)).thenReturn(Optional.of(attackerClient));
            when(userRepository.findById(20)).thenReturn(Optional.of(verifiedLawyer));
            when(userRepository.findById(21)).thenReturn(Optional.of(unverifiedLawyer));
            when(jobRepository.findById(100)).thenReturn(Optional.of(targetJob));
            when(proposalRepository.findById(501)).thenReturn(Optional.of(validProposal));
            when(httpRequest.getRemoteAddr()).thenReturn("200.100.50.25");
            when(httpRequest.getHeader("User-Agent")).thenReturn("LegaWork-Client-Test");
        }

        @Test
        @DisplayName("CHALLENGE 1A: Non-owner client attempting acceptAndContract must be rejected with 403 / AccessDeniedException")
        void testNonOwnerClientAttemptAcceptance_MustBeRejected403() {
            // Attacker user 99 is authenticated
            when(authService.getCurrentUserId()).thenReturn(99);

            AcceptContractRequestDto request = AcceptContractRequestDto.builder()
                    .proposalId(501)
                    .termsVersion("v1.0")
                    .signatureConsent(true)
                    .build();

            AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                    contractService.acceptAndContract(request, httpRequest)
            );

            assertTrue(ex.getMessage().contains("is not the owner of job 100"),
                    "Expected owner check failure message. Actual: " + ex.getMessage());

            // Verify no state change or contract creation
            verify(contractRepository, never()).save(any(Contract.class));
            verify(contractSignatureRepository, never()).save(any(ContractSignature.class));
            assertEquals(ProposalStatus.Pending, validProposal.getStatus());
            assertEquals(JobStatus.Open, targetJob.getStatus());
        }

        @ParameterizedTest(name = "Unverified Status: {0}")
        @EnumSource(value = VerificationStatus.class, names = {"DRAFT", "PENDING", "REJECTED", "SUSPENDED", "EXPIRED"})
        @DisplayName("CHALLENGE 1B: Attempting acceptance with unverified lawyer must be rejected with 403 for all non-VERIFIED statuses")
        void testUnverifiedLawyerStatus_MustBeRejected403(VerificationStatus nonVerifiedStatus) {
            UserProfile invalidProfile = UserProfile.builder()
                    .verificationStatus(nonVerifiedStatus)
                    .oabNumber("123456/SP")
                    .build();
            User unverified = User.builder().id(88).userProfile(invalidProfile).build();
            Proposal unverifiedProposal = Proposal.builder()
                    .proposalId(600)
                    .job(targetJob)
                    .lawyer(unverified)
                    .status(ProposalStatus.Pending)
                    .build();

            when(userRepository.findById(88)).thenReturn(Optional.of(unverified));
            when(proposalRepository.findById(600)).thenReturn(Optional.of(unverifiedProposal));
            when(authService.getCurrentUserId()).thenReturn(10); // Owner client

            AcceptContractRequestDto request = AcceptContractRequestDto.builder()
                    .proposalId(600)
                    .termsVersion("v1.0")
                    .signatureConsent(true)
                    .build();

            AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                    contractService.acceptAndContract(request, httpRequest)
            );

            assertTrue(ex.getMessage().contains("Lawyer verification required"),
                    "Expected verification check failure for status: " + nonVerifiedStatus);
            verify(contractRepository, never()).save(any(Contract.class));
        }

        @Test
        @DisplayName("CHALLENGE 1B (Expired OAB): Verified status but expired OAB date must be rejected with 403")
        void testExpiredOabDate_MustBeRejected403() {
            UserProfile expiredOabProfile = UserProfile.builder()
                    .verificationStatus(VerificationStatus.VERIFIED)
                    .oabNumber("123456/SP")
                    .oabExpiryDate(LocalDate.now().minusDays(1)) // Expired yesterday
                    .build();
            User expiredLawyer = User.builder().id(89).userProfile(expiredOabProfile).build();
            Proposal expiredOabProposal = Proposal.builder()
                    .proposalId(601)
                    .job(targetJob)
                    .lawyer(expiredLawyer)
                    .status(ProposalStatus.Pending)
                    .build();

            when(userRepository.findById(89)).thenReturn(Optional.of(expiredLawyer));
            when(proposalRepository.findById(601)).thenReturn(Optional.of(expiredOabProposal));
            when(authService.getCurrentUserId()).thenReturn(10); // Owner client

            AcceptContractRequestDto request = AcceptContractRequestDto.builder()
                    .proposalId(601)
                    .termsVersion("v1.0")
                    .signatureConsent(true)
                    .build();

            AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                    contractService.acceptAndContract(request, httpRequest)
            );

            assertTrue(ex.getMessage().contains("Lawyer OAB registration has expired"));
            verify(contractRepository, never()).save(any(Contract.class));
        }

        @Test
        @DisplayName("CHALLENGE 1C: Attempting acceptance on non-pending proposals (Rejected/Accepted/Withdrawn) must be rejected")
        void testNonPendingProposal_MustBeRejected() {
            when(authService.getCurrentUserId()).thenReturn(10); // Owner client

            // Test Rejected
            validProposal.setStatus(ProposalStatus.Rejected);
            AcceptContractRequestDto request = AcceptContractRequestDto.builder().proposalId(501).build();
            assertThrows(IllegalStateException.class, () -> contractService.acceptAndContract(request, httpRequest));

            // Test Accepted
            validProposal.setStatus(ProposalStatus.Accepted);
            assertThrows(IllegalStateException.class, () -> contractService.acceptAndContract(request, httpRequest));

            // Test Withdrawn
            validProposal.setStatus(ProposalStatus.Withdrawn);
            assertThrows(IllegalStateException.class, () -> contractService.acceptAndContract(request, httpRequest));

            verify(contractRepository, never()).save(any(Contract.class));
        }

        @Test
        @DisplayName("CHALLENGE 1D: BLOCKED conflict of interest throws ConflictBlockedException with zero leakage of third-party PII")
        void testBlockedConflict_ThrowsConflictBlockedWithoutDataLeakage() {
            when(authService.getCurrentUserId()).thenReturn(10); // Owner client

            // Simulate lawyer having a BLOCKED conflict check record
            ConflictCheck blockedConflict = ConflictCheck.builder()
                    .id(77L)
                    .job(targetJob)
                    .lawyer(verifiedLawyer)
                    .status(ConflictStatus.BLOCKED)
                    .reasonMasked("Impedimento ético-profissional detectado nos termos do Código de Ética da OAB.")
                    .build();
            when(conflictCheckRepository.findByJobJobIdAndLawyerId(100, 20)).thenReturn(Optional.of(blockedConflict));

            AcceptContractRequestDto request = AcceptContractRequestDto.builder()
                    .proposalId(501)
                    .termsVersion("v1.0")
                    .signatureConsent(true)
                    .build();

            ConflictBlockedException ex = assertThrows(ConflictBlockedException.class, () ->
                    contractService.acceptAndContract(request, httpRequest)
            );

            // Verify exception message contains ethical barrier statement
            assertTrue(ex.getMessage().contains("impedimento ético-profissional"));
            assertTrue(ex.getMessage().contains("Código de Ética da OAB"));

            // Verify STRICT ZERO-LEAKAGE: No names, CNJ, CPF, or competitor details
            assertFalse(ex.getMessage().contains("Maria"));
            assertFalse(ex.getMessage().contains("Carlos"));
            assertFalse(ex.getMessage().contains("5001234"));
            assertFalse(ex.getMessage().contains("123.456.789"));

            // Verify rollback & zero mutations
            verify(contractRepository, never()).save(any(Contract.class));
            verify(contractSignatureRepository, never()).save(any(ContractSignature.class));
            verify(milestoneRepository, never()).save(any(ContractMilestone.class));
            assertEquals(ProposalStatus.Pending, validProposal.getStatus());
        }
    }

    // =========================================================================
    // 2. SECURE DOCUMENT VAULT SECURITY & AUDIT TRAIL
    // =========================================================================
    @Nested
    @DisplayName("2. Secure Document Vault Security & Audit Trails")
    class SecureDocumentVaultTests {

        private SecureDocumentRepository secureDocumentRepository;
        private DocumentAccessLogRepository documentAccessLogRepository;
        private ContractRepository contractRepository;
        private JobRepository jobRepository;
        private UserRepository userRepository;
        private AuthService authService;
        private ImplAuthorizationService authorizationService;
        private ProposalRepository proposalRepository;
        private HttpServletRequest httpRequest;

        private SecureDocumentServiceImpl secureDocumentService;

        @TempDir
        Path tempDir;

        private User clientOwner;
        private User lawyerParticipant;
        private User unauthorizedUser;
        private Contract activeContract;
        private SecureDocument confidentialDocument;

        @BeforeEach
        void setUp() throws IOException {
            secureDocumentRepository = mock(SecureDocumentRepository.class);
            documentAccessLogRepository = mock(DocumentAccessLogRepository.class);
            contractRepository = mock(ContractRepository.class);
            jobRepository = mock(JobRepository.class);
            userRepository = mock(UserRepository.class);
            authService = mock(AuthService.class);
            proposalRepository = mock(ProposalRepository.class);
            httpRequest = mock(HttpServletRequest.class);

            authorizationService = new ImplAuthorizationService(
                    userRepository,
                    contractRepository,
                    jobRepository,
                    proposalRepository,
                    null,
                    secureDocumentRepository
            );

            secureDocumentService = new SecureDocumentServiceImpl(
                    secureDocumentRepository,
                    documentAccessLogRepository,
                    contractRepository,
                    jobRepository,
                    userRepository,
                    authService,
                    authorizationService
            );

            ReflectionTestUtils.setField(secureDocumentService, "uploadDir", tempDir.toString() + File.separator);

            clientOwner = User.builder().id(10).firstName("Maria").lastName("Silva").build();
            lawyerParticipant = User.builder().id(20).firstName("Dr. Carlos").lastName("Advogado").build();
            unauthorizedUser = User.builder().id(99).firstName("Eve").lastName("Attacker").build();

            activeContract = Contract.builder()
                    .contractId(500)
                    .client(clientOwner)
                    .lawyer(lawyerParticipant)
                    .title("Mandato 500")
                    .status(ContractStatus.Active)
                    .build();

            Path physicalFile = tempDir.resolve("contrato_assinado.pdf");
            byte[] fileBytes = "CONTEUDO SECRETO JURIDICO CONFIDENCIAL".getBytes();
            Files.write(physicalFile, fileBytes);

            confidentialDocument = SecureDocument.builder()
                    .id(1001L)
                    .contract(activeContract)
                    .owner(clientOwner)
                    .fileName("contrato_assinado.pdf")
                    .fileSize((long) fileBytes.length)
                    .contentType("application/pdf")
                    .storagePath(physicalFile.toAbsolutePath().toString())
                    .sha256Hash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                    .classification(DocumentClassification.CONFIDENTIAL)
                    .virusScanStatus(VirusScanStatus.CLEAN)
                    .isDeleted(false)
                    .version(1)
                    .build();

            when(userRepository.findById(10)).thenReturn(Optional.of(clientOwner));
            when(userRepository.findById(20)).thenReturn(Optional.of(lawyerParticipant));
            when(userRepository.findById(99)).thenReturn(Optional.of(unauthorizedUser));
            when(contractRepository.findById(500)).thenReturn(Optional.of(activeContract));
            when(secureDocumentRepository.findByIdAndIsDeletedFalse(1001L)).thenReturn(Optional.of(confidentialDocument));

            when(httpRequest.getRemoteAddr()).thenReturn("177.18.99.10");
            when(httpRequest.getHeader("User-Agent")).thenReturn("Challenger-Audit-Test");
        }

        @Test
        @DisplayName("CHALLENGE 2A: Attempt unauthorized file download by a user not participating in contract/job -> 403 / AccessDeniedException")
        void testUnauthorizedDownload_MustReturn403() {
            // Unauthorized user 99 attempts to download document 1001
            when(authService.getCurrentUserId()).thenReturn(99);

            AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                    secureDocumentService.downloadSecureDocument(1001L, httpRequest)
            );

            assertTrue(ex.getMessage().contains("does not have permission to access document"));
            // No download log recorded for unauthorized attempts
            verify(documentAccessLogRepository, never()).save(argThat(log -> "DOWNLOAD".equals(log.getAction())));
        }

        @Test
        @DisplayName("CHALLENGE 2B: Valid download by participant returns stream, SHA-256, and registers DOWNLOAD audit log")
        void testValidDownload_ReturnsDataAndRegistersAuditLog() {
            // Authorized lawyer participant (id 20)
            when(authService.getCurrentUserId()).thenReturn(20);

            SecureDownloadInfo downloadInfo = secureDocumentService.downloadSecureDocument(1001L, httpRequest);

            assertNotNull(downloadInfo);
            assertEquals("contrato_assinado.pdf", downloadInfo.fileName());
            assertEquals("application/pdf", downloadInfo.contentType());
            assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", downloadInfo.sha256Hash());
            assertNotNull(downloadInfo.resource());
            assertTrue(downloadInfo.resource().exists());

            // Verify DOWNLOAD log entry recorded in document_access_logs
            ArgumentCaptor<DocumentAccessLog> logCaptor = ArgumentCaptor.forClass(DocumentAccessLog.class);
            verify(documentAccessLogRepository, times(1)).save(logCaptor.capture());

            DocumentAccessLog savedLog = logCaptor.getValue();
            assertEquals("DOWNLOAD", savedLog.getAction());
            assertEquals(1001L, savedLog.getDocument().getId());
            assertEquals(20, savedLog.getUser().getId());
            assertEquals("177.18.99.10", savedLog.getIpAddress());
            assertEquals("Challenger-Audit-Test", savedLog.getUserAgent());
            assertNotNull(savedLog.getTimestamp());
        }

        @Test
        @DisplayName("CHALLENGE 2C: Infected file download attempt must be rejected with 403")
        void testInfectedFileDownload_MustReturn403() {
            confidentialDocument.setVirusScanStatus(VirusScanStatus.INFECTED);
            when(authService.getCurrentUserId()).thenReturn(20); // Lawyer participant

            AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                    secureDocumentService.downloadSecureDocument(1001L, httpRequest)
            );

            assertTrue(ex.getMessage().contains("flagged as infected"));
            verify(documentAccessLogRepository, never()).save(argThat(log -> "DOWNLOAD".equals(log.getAction())));
        }

        @Test
        @DisplayName("CHALLENGE 2D: Deleted document download attempt must be rejected")
        void testDeletedDocumentDownload_MustBeRejected() {
            when(secureDocumentRepository.findByIdAndIsDeletedFalse(1001L)).thenReturn(Optional.empty());
            when(authService.getCurrentUserId()).thenReturn(20);

            assertThrows(IllegalArgumentException.class, () ->
                    secureDocumentService.downloadSecureDocument(1001L, httpRequest)
            );
        }

        @Test
        @DisplayName("CHALLENGE 2E: Document deletion attempt by non-owner participant must be rejected with 403")
        void testDeleteDocument_NonOwnerRejected403() {
            // Lawyer participant (id 20) is not the owner (owner is client id 10)
            when(authService.getCurrentUserId()).thenReturn(20);

            AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                    secureDocumentService.deleteSecureDocument(1001L, httpRequest)
            );

            assertTrue(ex.getMessage().contains("Apenas o proprietário pode excluir este documento"));
            verify(secureDocumentRepository, never()).save(any(SecureDocument.class));
        }
    }
}
