package com.activecourses.upwork.adversarial;

import com.activecourses.upwork.dto.JobDTO;
import com.activecourses.upwork.dto.NegotiationMessageDTO;
import com.activecourses.upwork.dto.ProposalDTO;
import com.activecourses.upwork.exception.ContentModerationException;
import com.activecourses.upwork.exception.DuplicateProposalException;
import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.admin.AdminAccessLogRepository;
import com.activecourses.upwork.repository.contract.ContractRepository;
import com.activecourses.upwork.repository.document.ContractDocumentRepository;
import com.activecourses.upwork.repository.job.JobRepository;
import com.activecourses.upwork.repository.job.ProposalRepository;
import com.activecourses.upwork.repository.negotiation.NegotiationMessageRepository;
import com.activecourses.upwork.repository.negotiation.NegotiationThreadRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.service.contract.ContractService;
import com.activecourses.upwork.service.document.DocumentServiceImpl;
import com.activecourses.upwork.service.moderation.ContentModerationService;
import com.activecourses.upwork.service.moderation.ContentModerationServiceImpl;
import com.activecourses.upwork.service.negotiation.NegotiationServiceImpl;
import com.activecourses.upwork.service.notification.NotificationService;
import com.activecourses.upwork.service.proposal.ProposalServiceImpl;
import com.activecourses.upwork.service.security.AuthorizationService;
import com.activecourses.upwork.service.security.ImplAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Adversarial Security Stress Test Suite for LegaWork Phase 2 Backend.
 * Tests:
 * 1. Content Moderation Regex Resilience
 * 2. Anti-Duplicate Proposal Mechanism
 * 3. JobDetail Privacy & Access Control
 * 4. Negotiation Thread Isolation & Document Shielding
 */
public class AdversarialSecurityChallengerTest {

    private ContentModerationService moderationService;

    @BeforeEach
    void setUp() {
        moderationService = new ContentModerationServiceImpl();
    }

    // =========================================================================
    // 1. CONTENT MODERATION REGEX RESILIENCE
    // =========================================================================
    @Nested
    @DisplayName("1. Content Moderation Regex Resilience Tests")
    class ContentModerationStressTests {

        @ParameterizedTest(name = "CNJ Pattern: {0}")
        @ValueSource(strings = {
                "5001234-88.2023.8.13.0024",
                "0000001-00.2020.8.26.0100",
                "1234567-89.2021.4.01.0000",
                "5001234.88.2023.8.13.0024",
                "50012348820238130024",
                "Processo em curso: 5001234-88.2023.8.13.0024 na comarca",
                "Autos nº 12345678920214010000 aguardando audiência"
        })
        @DisplayName("Should detect formatted, unformatted, and dotted CNJ numbers")
        void testDetectCNJVariations(String cnjInput) {
            List<String> violations = moderationService.findViolations(cnjInput);
            assertFalse(violations.isEmpty(), "CNJ violation should be detected for: " + cnjInput);
            assertTrue(violations.stream().anyMatch(v -> v.contains("CNJ")), "Expected CNJ violation tag for: " + cnjInput);

            assertThrows(ContentModerationException.class, () ->
                    moderationService.validateJobContent("Título Válido", cnjInput)
            );

            String masked = moderationService.maskSensitiveContent(cnjInput);
            assertTrue(masked.contains("[PROCESSO OCULTO]"), "Masked output must contain [PROCESSO OCULTO]");
        }

        @ParameterizedTest(name = "CPF Pattern: {0}")
        @ValueSource(strings = {
                "123.456.789-00",
                "000.111.222-33",
                "12345678900",
                "Meu CPF é 123.456.789-00 favor cadastrar",
                "CPF do requerente 98765432100 para o contrato"
        })
        @DisplayName("Should detect formatted and unformatted CPFs")
        void testDetectCPFVariations(String cpfInput) {
            List<String> violations = moderationService.findViolations(cpfInput);
            assertFalse(violations.isEmpty(), "CPF violation should be detected for: " + cpfInput);
            assertTrue(violations.stream().anyMatch(v -> v.contains("CPF")));

            String masked = moderationService.maskSensitiveContent(cpfInput);
            assertTrue(masked.contains("[CPF OCULTO]"));
        }

        @ParameterizedTest(name = "CNPJ Pattern: {0}")
        @ValueSource(strings = {
                "12.345.678/0001-90",
                "00.000.000/0001-91",
                "12345678000190",
                "Empresa ré CNPJ 12.345.678/0001-90 na petição",
                "CNPJ 98765432000188 sede em SP"
        })
        @DisplayName("Should detect formatted and unformatted CNPJs")
        void testDetectCNPJVariations(String cnpjInput) {
            List<String> violations = moderationService.findViolations(cnpjInput);
            assertFalse(violations.isEmpty(), "CNPJ violation should be detected for: " + cnpjInput);
            assertTrue(violations.stream().anyMatch(v -> v.contains("CNPJ")));

            String masked = moderationService.maskSensitiveContent(cnpjInput);
            assertTrue(masked.contains("[CNPJ OCULTO]"));
        }

        @ParameterizedTest(name = "Email Pattern: {0}")
        @ValueSource(strings = {
                "contato@escritorio.adv.br",
                "doutor.silva+urgente@law.company.org",
                "ADVOGADO@EMPRESA.COM.BR",
                "Enviar contestação para draft.adv@gmail.com urgente"
        })
        @DisplayName("Should detect email addresses with case-insensitivity and tags")
        void testDetectEmailVariations(String emailInput) {
            List<String> violations = moderationService.findViolations(emailInput);
            assertFalse(violations.isEmpty(), "Email violation should be detected for: " + emailInput);
            assertTrue(violations.stream().anyMatch(v -> v.contains("E-mail")));

            String masked = moderationService.maskSensitiveContent(emailInput);
            assertTrue(masked.contains("[E-MAIL OCULTO]"));
        }

        @ParameterizedTest(name = "Phone Pattern: {0}")
        @ValueSource(strings = {
                "(11) 98765-4321",
                "(21) 3333-4444",
                "+55 11 98765-4321",
                "+55 (31) 99999-8888",
                "11987654321",
                "Me liga no 11987654321 para fechar o caso"
        })
        @DisplayName("Should detect cell and landline phone numbers with Brazilian formats")
        void testDetectPhoneVariations(String phoneInput) {
            List<String> violations = moderationService.findViolations(phoneInput);
            assertFalse(violations.isEmpty(), "Phone violation should be detected for: " + phoneInput);
            assertTrue(violations.stream().anyMatch(v -> v.contains("Telefone")));

            String masked = moderationService.maskSensitiveContent(phoneInput);
            assertTrue(masked.contains("[CONTATO OCULTO]") || masked.contains("[CPF OCULTO]"),
                    "Masked string must contain either [CONTATO OCULTO] or [CPF OCULTO]: " + masked);
        }


        @ParameterizedTest(name = "URL Pattern: {0}")
        @ValueSource(strings = {
                "https://drive.google.com/drive/folders/xyz123",
                "http://meuescritorio.com.br/documentos",
                "www.processosjudiciais.com.br",
                "Acesse o link www.consultaprocesso.com/detalhe?id=456"
        })
        @DisplayName("Should detect external links and URLs")
        void testDetectURLVariations(String urlInput) {
            List<String> violations = moderationService.findViolations(urlInput);
            assertFalse(violations.isEmpty(), "URL violation should be detected for: " + urlInput);
            assertTrue(violations.stream().anyMatch(v -> v.contains("URLs")));

            String masked = moderationService.maskSensitiveContent(urlInput);
            assertTrue(masked.contains("[LINK EXTERNO OCULTO]"));
        }

        @Test
        @DisplayName("Clean legal text without PII should pass with zero violations")
        void testCleanLegalDescriptionsPass() {
            String title = "Elaboração de Recurso Ordinário Trabalhista";
            String desc = "Contratação de advogado com experiência em direito do trabalho para elaboração de recurso em 8 dias úteis. Honorários estimados em R$ 4.000,00.";

            assertDoesNotThrow(() -> moderationService.validateJobContent(title, desc));
            assertEquals(0, moderationService.findViolations(desc).size());
            assertEquals(0, moderationService.findViolations(title).size());
            assertFalse(moderationService.containsSensitiveContent(desc));
        }
    }

    // =========================================================================
    // 2. ANTI-DUPLICATE PROPOSAL MECHANISM ACROSS STATUSES
    // =========================================================================
    @Nested
    @DisplayName("2. Anti-Duplicate Proposal Mechanism Tests")
    class AntiDuplicateProposalStressTests {

        private ProposalRepository proposalRepository;
        private JobRepository jobRepository;
        private UserRepository userRepository;
        private NegotiationThreadRepository negotiationThreadRepository;
        private AuthService authService;
        private ContractService contractService;
        private NotificationService notificationService;
        private AuthorizationService authorizationService;
        private ProposalServiceImpl proposalService;

        private User verifiedLawyer;
        private User verifiedLawyer2;
        private User client;
        private Job job1;
        private Job job2;

        @BeforeEach
        void setUpMocks() {
            proposalRepository = mock(ProposalRepository.class);
            jobRepository = mock(JobRepository.class);
            userRepository = mock(UserRepository.class);
            negotiationThreadRepository = mock(NegotiationThreadRepository.class);
            authService = mock(AuthService.class);
            contractService = mock(ContractService.class);
            notificationService = mock(NotificationService.class);
            authorizationService = mock(AuthorizationService.class);

            proposalService = new ProposalServiceImpl(
                    proposalRepository,
                    jobRepository,
                    userRepository,
                    negotiationThreadRepository,
                    authService,
                    contractService,
                    notificationService,
                    authorizationService
            );

            verifiedLawyer = User.builder().id(10).firstName("Dra. Beatriz").lastName("Alves").build();
            verifiedLawyer2 = User.builder().id(20).firstName("Dr. Carlos").lastName("Mendes").build();
            client = User.builder().id(30).firstName("Empresa Cliente").build();

            job1 = Job.builder().jobId(101).title("Caso Trabalhista 1").client(client).build();
            job2 = Job.builder().jobId(102).title("Caso Cível 2").client(client).build();

            when(userRepository.findById(10)).thenReturn(Optional.of(verifiedLawyer));
            when(userRepository.findById(20)).thenReturn(Optional.of(verifiedLawyer2));
            when(jobRepository.findById(101)).thenReturn(Optional.of(job1));
            when(jobRepository.findById(102)).thenReturn(Optional.of(job2));
        }

        @Test
        @DisplayName("Lawyer with existing PENDING proposal cannot submit another proposal for same job (409)")
        void testDuplicatePendingProposalBlocked() {
            when(authService.getCurrentUserId()).thenReturn(10);
            when(proposalRepository.existsByJobJobIdAndLawyerIdAndStatusIn(
                    eq(101), eq(10), eq(List.of(ProposalStatus.Pending, ProposalStatus.Countered))
            )).thenReturn(true);

            ProposalDTO request = ProposalDTO.builder()
                    .jobId(101)
                    .proposedRate(BigDecimal.valueOf(2500))
                    .coverLetter("Segunda proposta")
                    .build();

            DuplicateProposalException ex = assertThrows(DuplicateProposalException.class, () ->
                    proposalService.createProposal(request)
            );
            assertTrue(ex.getMessage().contains("já possui uma proposta ativa"));
            verify(proposalRepository, never()).save(any());
        }

        @Test
        @DisplayName("Lawyer with existing COUNTERED proposal cannot submit another proposal for same job (409)")
        void testDuplicateCounteredProposalBlocked() {
            when(authService.getCurrentUserId()).thenReturn(10);
            when(proposalRepository.existsByJobJobIdAndLawyerIdAndStatusIn(
                    eq(101), eq(10), eq(List.of(ProposalStatus.Pending, ProposalStatus.Countered))
            )).thenReturn(true);

            ProposalDTO request = ProposalDTO.builder()
                    .jobId(101)
                    .proposedRate(BigDecimal.valueOf(3000))
                    .coverLetter("Proposta após contraproposta")
                    .build();

            assertThrows(DuplicateProposalException.class, () ->
                    proposalService.createProposal(request)
            );
            verify(proposalRepository, never()).save(any());
        }

        @Test
        @DisplayName("Lawyer with previous REJECTED proposal CAN submit a fresh proposal for same job")
        void testProposalAllowedAfterRejection() {
            when(authService.getCurrentUserId()).thenReturn(10);
            // No active (Pending/Countered) proposal exists
            when(proposalRepository.existsByJobJobIdAndLawyerIdAndStatusIn(
                    eq(101), eq(10), eq(List.of(ProposalStatus.Pending, ProposalStatus.Countered))
            )).thenReturn(false);

            when(proposalRepository.save(any(Proposal.class))).thenAnswer(inv -> {
                Proposal p = inv.getArgument(0);
                p.setProposalId(501);
                return p;
            });
            when(negotiationThreadRepository.save(any(NegotiationThread.class))).thenAnswer(inv -> {
                NegotiationThread t = inv.getArgument(0);
                t.setId(901L);
                return t;
            });

            ProposalDTO request = ProposalDTO.builder()
                    .jobId(101)
                    .proposedRate(BigDecimal.valueOf(2200))
                    .coverLetter("Nova proposta ajustada após rejeição")
                    .build();

            ProposalDTO created = proposalService.createProposal(request);
            assertNotNull(created);
            assertEquals(501, created.getProposalId());
            assertEquals(1, created.getProposalVersion());
            verify(proposalRepository, times(1)).save(any(Proposal.class));
        }

        @Test
        @DisplayName("Lawyer with previous WITHDRAWN proposal CAN submit a fresh proposal for same job")
        void testProposalAllowedAfterWithdrawal() {
            when(authService.getCurrentUserId()).thenReturn(10);
            when(proposalRepository.existsByJobJobIdAndLawyerIdAndStatusIn(
                    eq(101), eq(10), eq(List.of(ProposalStatus.Pending, ProposalStatus.Countered))
            )).thenReturn(false);

            when(proposalRepository.save(any(Proposal.class))).thenAnswer(inv -> {
                Proposal p = inv.getArgument(0);
                p.setProposalId(502);
                return p;
            });

            ProposalDTO request = ProposalDTO.builder()
                    .jobId(101)
                    .proposedRate(BigDecimal.valueOf(2800))
                    .coverLetter("Nova proposta após desistência prévia")
                    .build();

            ProposalDTO created = proposalService.createProposal(request);
            assertNotNull(created);
            assertEquals(502, created.getProposalId());
        }

        @Test
        @DisplayName("Distinct lawyers submitting proposals for the same job are both allowed")
        void testDistinctLawyersAllowedForSameJob() {
            // Lawyer 1
            when(authService.getCurrentUserId()).thenReturn(10);
            when(proposalRepository.existsByJobJobIdAndLawyerIdAndStatusIn(eq(101), eq(10), any())).thenReturn(false);
            when(proposalRepository.save(any(Proposal.class))).thenAnswer(inv -> {
                Proposal p = inv.getArgument(0);
                p.setProposalId(1001);
                return p;
            });

            ProposalDTO res1 = proposalService.createProposal(ProposalDTO.builder().jobId(101).proposedRate(BigDecimal.valueOf(2000)).build());
            assertNotNull(res1);

            // Lawyer 2
            when(authService.getCurrentUserId()).thenReturn(20);
            when(proposalRepository.existsByJobJobIdAndLawyerIdAndStatusIn(eq(101), eq(20), any())).thenReturn(false);
            when(proposalRepository.save(any(Proposal.class))).thenAnswer(inv -> {
                Proposal p = inv.getArgument(0);
                p.setProposalId(1002);
                return p;
            });

            ProposalDTO res2 = proposalService.createProposal(ProposalDTO.builder().jobId(101).proposedRate(BigDecimal.valueOf(2500)).build());
            assertNotNull(res2);
            assertEquals(1002, res2.getProposalId());
        }

        @Test
        @DisplayName("Same lawyer submitting proposals for different jobs is allowed")
        void testSameLawyerAllowedForDifferentJobs() {
            when(authService.getCurrentUserId()).thenReturn(10);
            when(proposalRepository.existsByJobJobIdAndLawyerIdAndStatusIn(eq(101), eq(10), any())).thenReturn(false);
            when(proposalRepository.existsByJobJobIdAndLawyerIdAndStatusIn(eq(102), eq(10), any())).thenReturn(false);

            when(proposalRepository.save(any(Proposal.class))).thenAnswer(inv -> {
                Proposal p = inv.getArgument(0);
                p.setProposalId(2001);
                return p;
            });

            ProposalDTO resJob1 = proposalService.createProposal(ProposalDTO.builder().jobId(101).proposedRate(BigDecimal.valueOf(1800)).build());
            ProposalDTO resJob2 = proposalService.createProposal(ProposalDTO.builder().jobId(102).proposedRate(BigDecimal.valueOf(2200)).build());

            assertNotNull(resJob1);
            assertNotNull(resJob2);
        }
    }

    // =========================================================================
    // 3. JOBDETAIL PRIVACY & ACCESS CONTROL
    // =========================================================================
    @Nested
    @DisplayName("3. JobDetail Privacy Access Control Tests")
    class JobDetailPrivacyStressTests {

        private UserRepository userRepository;
        private JobRepository jobRepository;
        private ProposalRepository proposalRepository;
        private ContractRepository contractRepository;
        private AdminAccessLogRepository adminAccessLogRepository;
        private ImplAuthorizationService authZService;

        private User ownerClient;
        private User verifiedLawyerWithProposal;
        private User verifiedLawyerWithoutProposal;
        private User unverifiedLawyer;

        @BeforeEach
        void setUp() {
            userRepository = mock(UserRepository.class);
            jobRepository = mock(JobRepository.class);
            proposalRepository = mock(ProposalRepository.class);
            contractRepository = mock(ContractRepository.class);
            adminAccessLogRepository = mock(AdminAccessLogRepository.class);

            authZService = new ImplAuthorizationService(
                    userRepository,
                    contractRepository,
                    jobRepository,
                    proposalRepository,
                    adminAccessLogRepository
            );

            ownerClient = User.builder().id(1).email("owner@client.com").build();
            verifiedLawyerWithProposal = User.builder().id(2).email("lawyer1@law.com")
                    .userProfile(UserProfile.builder().verificationStatus(VerificationStatus.VERIFIED).build()).build();
            verifiedLawyerWithoutProposal = User.builder().id(3).email("lawyer2@law.com")
                    .userProfile(UserProfile.builder().verificationStatus(VerificationStatus.VERIFIED).build()).build();
            unverifiedLawyer = User.builder().id(4).email("lawyer3@law.com")
                    .userProfile(UserProfile.builder().verificationStatus(VerificationStatus.PENDING).build()).build();

            when(userRepository.findById(1)).thenReturn(Optional.of(ownerClient));
            when(userRepository.findById(2)).thenReturn(Optional.of(verifiedLawyerWithProposal));
            when(userRepository.findById(3)).thenReturn(Optional.of(verifiedLawyerWithoutProposal));
            when(userRepository.findById(4)).thenReturn(Optional.of(unverifiedLawyer));
        }

        @Test
        @DisplayName("PRIVATE Job: Owner client and Lawyer with proposal have access; Unrelated verified/unverified lawyers get 403")
        void testPrivateJobAccessControl() {
            Job privateJob = Job.builder()
                    .jobId(10)
                    .client(ownerClient)
                    .visibility(JobVisibility.PRIVATE)
                    .moderationStatus(ModerationStatus.APPROVED)
                    .build();

            when(jobRepository.findById(10)).thenReturn(Optional.of(privateJob));
            when(proposalRepository.findByJobJobIdAndLawyerId(10, 2)).thenReturn(Optional.of(new Proposal()));
            when(proposalRepository.findByJobJobIdAndLawyerId(10, 3)).thenReturn(Optional.empty());
            when(proposalRepository.findByJobJobIdAndLawyerId(10, 4)).thenReturn(Optional.empty());

            // 1. Owner Client -> OK
            assertDoesNotThrow(() -> authZService.enforceCanViewJobDetail(10, 1));

            // 2. Lawyer with proposal -> OK
            assertDoesNotThrow(() -> authZService.enforceCanViewJobDetail(10, 2));

            // 3. Unrelated verified lawyer -> 403 Forbidden
            assertThrows(AccessDeniedException.class, () -> authZService.enforceCanViewJobDetail(10, 3));

            // 4. Unverified lawyer -> 403 Forbidden
            assertThrows(AccessDeniedException.class, () -> authZService.enforceCanViewJobDetail(10, 4));
        }

        @Test
        @DisplayName("INVITE_ONLY Job: Owner client and Invited/Proposal Lawyer have access; Unrelated verified lawyers get 403")
        void testInviteOnlyJobAccessControl() {
            Job inviteOnlyJob = Job.builder()
                    .jobId(20)
                    .client(ownerClient)
                    .visibility(JobVisibility.INVITE_ONLY)
                    .moderationStatus(ModerationStatus.APPROVED)
                    .build();

            when(jobRepository.findById(20)).thenReturn(Optional.of(inviteOnlyJob));
            when(proposalRepository.findByJobJobIdAndLawyerId(20, 2)).thenReturn(Optional.of(new Proposal()));
            when(proposalRepository.findByJobJobIdAndLawyerId(20, 3)).thenReturn(Optional.empty());

            // Owner -> OK
            assertDoesNotThrow(() -> authZService.enforceCanViewJobDetail(20, 1));
            // Participant Lawyer -> OK
            assertDoesNotThrow(() -> authZService.enforceCanViewJobDetail(20, 2));
            // Unrelated Lawyer -> 403 Forbidden
            assertThrows(AccessDeniedException.class, () -> authZService.enforceCanViewJobDetail(20, 3));
        }

        @Test
        @DisplayName("DISCOVERY_SANITIZED Job with APPROVED status: Verified lawyers have access; Unverified lawyers get 403")
        void testDiscoverySanitizedApprovedAccessControl() {
            Job discoveryJob = Job.builder()
                    .jobId(30)
                    .client(ownerClient)
                    .visibility(JobVisibility.DISCOVERY_SANITIZED)
                    .moderationStatus(ModerationStatus.APPROVED)
                    .build();

            when(jobRepository.findById(30)).thenReturn(Optional.of(discoveryJob));
            when(proposalRepository.findByJobJobIdAndLawyerId(30, 3)).thenReturn(Optional.empty());
            when(proposalRepository.findByJobJobIdAndLawyerId(30, 4)).thenReturn(Optional.empty());

            // Verified lawyer without proposal -> OK
            assertDoesNotThrow(() -> authZService.enforceCanViewJobDetail(30, 3));

            // Unverified lawyer -> 403 Forbidden
            assertThrows(AccessDeniedException.class, () -> authZService.enforceCanViewJobDetail(30, 4));
        }

        @Test
        @DisplayName("DISCOVERY_SANITIZED Job with FLAGGED/PENDING_REVIEW status: Unrelated lawyers cannot access (403)")
        void testDiscoverySanitizedUnapprovedAccessControl() {
            Job flaggedJob = Job.builder()
                    .jobId(40)
                    .client(ownerClient)
                    .visibility(JobVisibility.DISCOVERY_SANITIZED)
                    .moderationStatus(ModerationStatus.FLAGGED)
                    .build();

            when(jobRepository.findById(40)).thenReturn(Optional.of(flaggedJob));
            when(proposalRepository.findByJobJobIdAndLawyerId(40, 3)).thenReturn(Optional.empty());

            // Unrelated verified lawyer on FLAGGED job -> 403 Forbidden
            assertThrows(AccessDeniedException.class, () -> authZService.enforceCanViewJobDetail(40, 3));

            // Owner client still has access to inspect their own flagged job -> OK
            assertDoesNotThrow(() -> authZService.enforceCanViewJobDetail(40, 1));
        }
    }

    // =========================================================================
    // 4. NEGOTIATION ISOLATION & DOCUMENT SHIELDING
    // =========================================================================
    @Nested
    @DisplayName("4. Negotiation Thread Isolation & Document Access Shielding")
    class NegotiationIsolationStressTests {

        @Test
        @DisplayName("Negotiation messages are strictly confidential between proposal lawyer and job owner")
        void testNegotiationThreadAccessEnforcement() {
            UserRepository userRepository = mock(UserRepository.class);
            ProposalRepository proposalRepository = mock(ProposalRepository.class);
            ContractRepository contractRepository = mock(ContractRepository.class);
            JobRepository jobRepository = mock(JobRepository.class);
            AdminAccessLogRepository adminAccessLogRepository = mock(AdminAccessLogRepository.class);

            ImplAuthorizationService authZService = new ImplAuthorizationService(
                    userRepository,
                    contractRepository,
                    jobRepository,
                    proposalRepository,
                    adminAccessLogRepository
            );

            User client = User.builder().id(10).email("client@empresa.com").build();
            User lawyer = User.builder().id(20).email("lawyer@law.com").build();
            User thirdPartyLawyer = User.builder().id(99).email("thirdparty@law.com").build();

            Job job = Job.builder().jobId(1).client(client).build();
            Proposal proposal = Proposal.builder().proposalId(100).job(job).lawyer(lawyer).build();

            when(proposalRepository.findById(100)).thenReturn(Optional.of(proposal));

            // 1. Proposal author lawyer -> OK
            assertDoesNotThrow(() -> authZService.enforceNegotiationParticipant(100, 20));

            // 2. Job client owner -> OK
            assertDoesNotThrow(() -> authZService.enforceNegotiationParticipant(100, 10));

            // 3. Third-party lawyer -> 403 AccessDeniedException
            assertThrows(AccessDeniedException.class, () ->
                    authZService.enforceNegotiationParticipant(100, 99)
            );
        }

        @Test
        @DisplayName("Pre-contractual negotiation does NOT grant document download access without signed contract")
        void testNegotiatingLawyerCannotDownloadDocuments() {
            ContractDocumentRepository docRepo = mock(ContractDocumentRepository.class);
            ContractRepository contractRepo = mock(ContractRepository.class);
            UserRepository userRepo = mock(UserRepository.class);
            AuthService authSvc = mock(AuthService.class);
            JobRepository jobRepo = mock(JobRepository.class);
            ProposalRepository proposalRepo = mock(ProposalRepository.class);
            AdminAccessLogRepository adminLogRepo = mock(AdminAccessLogRepository.class);

            ImplAuthorizationService authZService = new ImplAuthorizationService(
                    userRepo,
                    contractRepo,
                    jobRepo,
                    proposalRepo,
                    adminLogRepo
            );

            DocumentServiceImpl docService = new DocumentServiceImpl(
                    docRepo,
                    contractRepo,
                    userRepo,
                    authSvc,
                    authZService
            );

            User client = User.builder().id(10).email("client@corp.com").build();
            User contractSignedLawyer = User.builder().id(20).email("signed@law.com").build();
            Contract contract = Contract.builder().contractId(777).client(client).lawyer(contractSignedLawyer).build();

            ContractDocument confidentialDoc = ContractDocument.builder()
                    .documentId(888)
                    .contract(contract)
                    .fileName("relatorio_sigiloso_auditoria.pdf")
                    .contentType("application/pdf")
                    .storagePath("secure/888.pdf")
                    .build();

            when(docRepo.findById(888)).thenReturn(Optional.of(confidentialDoc));
            when(contractRepo.findById(777)).thenReturn(Optional.of(contract));

            // Case A: Lawyer who is only negotiating (id 30) attempts to access contract document 888
            when(authSvc.getCurrentUserId()).thenReturn(30);

            AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                    docService.getDocumentDownloadInfo(888)
            );
            assertTrue(ex.getMessage().contains("is not a participant in contract 777"));

            // Case B: Signed contract lawyer (id 20) attempts to access -> OK
            when(authSvc.getCurrentUserId()).thenReturn(20);
            assertDoesNotThrow(() -> docService.getDocumentDownloadInfo(888));

            // Case C: Client owner (id 10) attempts to access -> OK
            when(authSvc.getCurrentUserId()).thenReturn(10);
            assertDoesNotThrow(() -> docService.getDocumentDownloadInfo(888));
        }

        @Test
        @DisplayName("Negotiation messages automatically mask PII before saving")
        void testNegotiationMessagePiiMaskingBeforePersist() {
            NegotiationThreadRepository threadRepo = mock(NegotiationThreadRepository.class);
            NegotiationMessageRepository msgRepo = mock(NegotiationMessageRepository.class);
            ProposalRepository proposalRepo = mock(ProposalRepository.class);
            UserRepository userRepo = mock(UserRepository.class);
            AuthorizationService authZSvc = mock(AuthorizationService.class);
            NotificationService notifSvc = mock(NotificationService.class);
            ContentModerationService modSvc = new ContentModerationServiceImpl();

            NegotiationServiceImpl negService = new NegotiationServiceImpl(
                    threadRepo,
                    msgRepo,
                    proposalRepo,
                    userRepo,
                    modSvc,
                    authZSvc,
                    notifSvc
            );

            User client = User.builder().id(10).firstName("Cliente").build();
            User lawyer = User.builder().id(20).firstName("Dra. Renata").build();
            Job job = Job.builder().jobId(1).client(client).title("Ação Indenizatória").build();
            Proposal proposal = Proposal.builder().proposalId(50).job(job).lawyer(lawyer).build();
            NegotiationThread thread = NegotiationThread.builder().id(5L).proposal(proposal).build();

            when(proposalRepo.findById(50)).thenReturn(Optional.of(proposal));
            when(userRepo.findById(20)).thenReturn(Optional.of(lawyer));
            when(threadRepo.findByProposalProposalId(50)).thenReturn(Optional.of(thread));

            when(msgRepo.save(any(NegotiationMessage.class))).thenAnswer(inv -> {
                NegotiationMessage msg = inv.getArgument(0);
                msg.setId(999L);
                return msg;
            });

            String rawContent = "Podemos conversar por whatsapp no (11) 99999-0000 ou email renata@adv.com. O processo 0001234-56.2023.8.26.0100 tem audiência.";
            NegotiationMessageDTO sentMsg = negService.sendMessage(50, 20, rawContent);

            assertNotNull(sentMsg);
            assertTrue(sentMsg.isModerated());
            assertFalse(sentMsg.getContentMasked().contains("(11) 99999-0000"));
            assertFalse(sentMsg.getContentMasked().contains("renata@adv.com"));
            assertFalse(sentMsg.getContentMasked().contains("0001234-56.2023.8.26.0100"));

            assertTrue(sentMsg.getContentMasked().contains("[CONTATO OCULTO]"));
            assertTrue(sentMsg.getContentMasked().contains("[E-MAIL OCULTO]"));
            assertTrue(sentMsg.getContentMasked().contains("[PROCESSO OCULTO]"));
        }
    }
}
