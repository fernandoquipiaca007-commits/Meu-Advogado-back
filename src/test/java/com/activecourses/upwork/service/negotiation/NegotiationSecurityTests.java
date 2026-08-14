package com.activecourses.upwork.service.negotiation;

import com.activecourses.upwork.dto.NegotiationMessageDTO;
import com.activecourses.upwork.dto.SendNegotiationMessageRequest;
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
import com.activecourses.upwork.service.document.DocumentService;
import com.activecourses.upwork.service.document.DocumentServiceImpl;
import com.activecourses.upwork.service.moderation.ContentModerationService;
import com.activecourses.upwork.service.moderation.ContentModerationServiceImpl;
import com.activecourses.upwork.service.notification.NotificationService;
import com.activecourses.upwork.service.security.AuthorizationService;
import com.activecourses.upwork.service.security.ImplAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NegotiationSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NegotiationService negotiationService;

    @MockBean
    private AuthService authService;

    @Test
    @DisplayName("POST /api/negotiations/{proposalId}/messages by participant returns 201 with masked content")
    @WithMockUser(username = "lawyer@legawork.com", roles = {"LAWYER"})
    void testSendMessageByParticipantReturns201AndMasked() throws Exception {
        when(authService.getCurrentUserId()).thenReturn(20);

        NegotiationMessageDTO responseDto = NegotiationMessageDTO.builder()
                .id(1L)
                .threadId(10L)
                .senderId(20)
                .senderName("Dra. Beatriz")
                .contentMasked("Olá, ligue no [CONTATO OCULTO] para alinharmos.")
                .isModerated(true)
                .sentAt(LocalDateTime.now())
                .build();

        when(negotiationService.sendMessage(eq(100), eq(20), anyString())).thenReturn(responseDto);

        String payload = "{\"content\": \"Olá, ligue no (11) 98765-4321 para alinharmos.\"}";

        mockMvc.perform(post("/api/negotiations/100/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.moderated").value(true))
                .andExpect(jsonPath("$.data.contentMasked").value("Olá, ligue no [CONTATO OCULTO] para alinharmos."));
    }

    @Test
    @DisplayName("POST /api/negotiations/{proposalId}/messages by non-participant returns 403 Forbidden")
    @WithMockUser(username = "stranger@legawork.com", roles = {"LAWYER"})
    void testSendMessageByNonParticipantReturns403() throws Exception {
        when(authService.getCurrentUserId()).thenReturn(999);
        when(negotiationService.sendMessage(eq(100), eq(999), anyString()))
                .thenThrow(new AccessDeniedException("User 999 is neither the author nor the client for proposal 100"));

        String payload = "{\"content\": \"Mensagem indevida de terceiro\"}";

        mockMvc.perform(post("/api/negotiations/100/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/negotiations/{proposalId}/messages by non-participant returns 403 Forbidden")
    @WithMockUser(username = "stranger@legawork.com", roles = {"LAWYER"})
    void testGetMessagesByNonParticipantReturns403() throws Exception {
        when(authService.getCurrentUserId()).thenReturn(999);
        when(negotiationService.getMessages(eq(100), eq(999), any(PageRequest.class)))
                .thenThrow(new AccessDeniedException("User 999 is neither the author nor the client for proposal 100"));

        mockMvc.perform(get("/api/negotiations/100/messages")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Document Access Isolation: Pre-contractual negotiation participants cannot access private case documents without signed contract")
    void testDocumentAccessIsolationDuringNegotiation() {
        ContractDocumentRepository documentRepository = mock(ContractDocumentRepository.class);
        ContractRepository contractRepository = mock(ContractRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AuthService docAuthService = mock(AuthService.class);
        AdminAccessLogRepository adminAccessLogRepository = mock(AdminAccessLogRepository.class);
        JobRepository jobRepository = mock(JobRepository.class);
        ProposalRepository proposalRepository = mock(ProposalRepository.class);

        ImplAuthorizationService authZService = new ImplAuthorizationService(
                userRepository,
                contractRepository,
                jobRepository,
                proposalRepository,
                adminAccessLogRepository
        );

        DocumentServiceImpl documentService = new DocumentServiceImpl(
                documentRepository,
                contractRepository,
                userRepository,
                docAuthService,
                authZService
        );

        User client = User.builder().id(10).email("client@empresa.com").build();
        User acceptedLawyer = User.builder().id(20).email("accepted@law.com").build();
        User negotiatingLawyer = User.builder().id(30).email("negotiating@law.com").build();

        // Contract exists only between client (10) and acceptedLawyer (20)
        Contract contract = Contract.builder().contractId(500).client(client).lawyer(acceptedLawyer).build();
        ContractDocument doc = ContractDocument.builder()
                .documentId(77)
                .contract(contract)
                .fileName("documento_sigiloso.pdf")
                .contentType("application/pdf")
                .storagePath("uploads/doc77.pdf")
                .build();

        when(documentRepository.findById(77)).thenReturn(Optional.of(doc));
        when(contractRepository.findById(500)).thenReturn(Optional.of(contract));

        // Negotiating lawyer (id 30) who is only in pre-contractual thread attempts to download document 77
        when(docAuthService.getCurrentUserId()).thenReturn(30);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                documentService.getDocumentDownloadInfo(77)
        );

        assertTrue(ex.getMessage().contains("is not a participant in contract 500"));
    }

    @Test
    @DisplayName("Unit: NegotiationService masks PII and notifies counterparty")
    void testNegotiationServicePiiMaskingAndNotification() {
        NegotiationThreadRepository threadRepo = mock(NegotiationThreadRepository.class);
        NegotiationMessageRepository msgRepo = mock(NegotiationMessageRepository.class);
        ProposalRepository proposalRepo = mock(ProposalRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        AuthorizationService authZSvc = mock(AuthorizationService.class);
        NotificationService notifSvc = mock(NotificationService.class);
        ContentModerationService modSvc = new ContentModerationServiceImpl();

        NegotiationServiceImpl service = new NegotiationServiceImpl(
                threadRepo,
                msgRepo,
                proposalRepo,
                userRepo,
                modSvc,
                authZSvc,
                notifSvc
        );

        User client = User.builder().id(10).firstName("Cliente").email("client@corp.com").build();
        User lawyer = User.builder().id(20).firstName("Dr. Roberto").email("roberto@law.com").build();
        Job job = Job.builder().jobId(100).client(client).build();
        Proposal proposal = Proposal.builder().proposalId(200).job(job).lawyer(lawyer).build();
        NegotiationThread thread = NegotiationThread.builder().id(1L).proposal(proposal).retentionDays(90).build();

        when(proposalRepo.findById(200)).thenReturn(Optional.of(proposal));
        when(userRepo.findById(20)).thenReturn(Optional.of(lawyer));
        when(threadRepo.findByProposalProposalId(200)).thenReturn(Optional.of(thread));

        when(msgRepo.save(any(NegotiationMessage.class))).thenAnswer(invocation -> {
            NegotiationMessage m = invocation.getArgument(0);
            m.setId(555L);
            return m;
        });

        String raw = "Dúvida sobre o caso. Me contate no email advogado@escritorio.com.br ou telefone (11) 98765-4321.";
        NegotiationMessageDTO sent = service.sendMessage(200, 20, raw);

        assertNotNull(sent);
        assertTrue(sent.isModerated());
        assertFalse(sent.getContentMasked().contains("advogado@escritorio.com.br"));
        assertFalse(sent.getContentMasked().contains("(11) 98765-4321"));
        assertTrue(sent.getContentMasked().contains("[E-MAIL OCULTO]"));
        assertTrue(sent.getContentMasked().contains("[CONTATO OCULTO]"));

        verify(authZSvc).enforceNegotiationParticipant(200, 20);
        verify(notifSvc).createNotification(eq(10), any(), any(), any(), eq("proposal"), eq(200));
    }
}
