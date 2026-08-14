package com.activecourses.upwork.service.negotiation;

import com.activecourses.upwork.dto.NegotiationMessageDTO;
import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.job.ProposalRepository;
import com.activecourses.upwork.repository.negotiation.NegotiationMessageRepository;
import com.activecourses.upwork.repository.negotiation.NegotiationThreadRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.service.moderation.ContentModerationService;
import com.activecourses.upwork.service.moderation.ContentModerationServiceImpl;
import com.activecourses.upwork.service.notification.NotificationService;
import com.activecourses.upwork.service.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NegotiationServiceTest {

    @Mock
    private NegotiationThreadRepository negotiationThreadRepository;

    @Mock
    private NegotiationMessageRepository negotiationMessageRepository;

    @Mock
    private ProposalRepository proposalRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private NotificationService notificationService;

    private ContentModerationService contentModerationService;
    private NegotiationServiceImpl negotiationService;

    private User client;
    private User lawyer;
    private Job job;
    private Proposal proposal;
    private NegotiationThread thread;

    @BeforeEach
    void setUp() {
        contentModerationService = new ContentModerationServiceImpl();
        negotiationService = new NegotiationServiceImpl(
                negotiationThreadRepository,
                negotiationMessageRepository,
                proposalRepository,
                userRepository,
                contentModerationService,
                authorizationService,
                notificationService
        );

        client = User.builder().id(10).firstName("Carlos").lastName("Cliente").email("carlos@client.com").build();
        lawyer = User.builder().id(20).firstName("Dra. Beatriz").lastName("Advogada").email("beatriz@law.com").build();

        job = Job.builder().jobId(100).title("Revisão de Contrato").client(client).build();
        proposal = Proposal.builder().proposalId(200).job(job).lawyer(lawyer).proposalVersion(1).build();
        thread = NegotiationThread.builder().id(1L).proposal(proposal).createdAt(LocalDateTime.now()).retentionDays(90).build();
    }

    @Test
    @DisplayName("Should send message and automatically mask sensitive PII")
    void testSendMessageWithMasking() {
        when(proposalRepository.findById(200)).thenReturn(Optional.of(proposal));
        when(userRepository.findById(20)).thenReturn(Optional.of(lawyer));
        when(negotiationThreadRepository.findByProposalProposalId(200)).thenReturn(Optional.of(thread));

        when(negotiationMessageRepository.save(any(NegotiationMessage.class))).thenAnswer(invocation -> {
            NegotiationMessage msg = invocation.getArgument(0);
            msg.setId(101L);
            return msg;
        });

        String rawContent = "Olá, me ligue no (11) 98765-4321 para alinharmos os detalhes.";
        NegotiationMessageDTO result = negotiationService.sendMessage(200, 20, rawContent);

        assertNotNull(result);
        assertTrue(result.isModerated());
        assertFalse(result.getContentMasked().contains("98765-4321"));
        assertTrue(result.getContentMasked().contains("[CONTATO OCULTO]"));
        verify(authorizationService).enforceNegotiationParticipant(200, 20);
        verify(notificationService).createNotification(eq(10), any(), any(), any(), eq("proposal"), eq(200));
    }

    @Test
    @DisplayName("Should send clean message without moderation flags")
    void testSendCleanMessage() {
        when(proposalRepository.findById(200)).thenReturn(Optional.of(proposal));
        when(userRepository.findById(20)).thenReturn(Optional.of(lawyer));
        when(negotiationThreadRepository.findByProposalProposalId(200)).thenReturn(Optional.of(thread));

        when(negotiationMessageRepository.save(any(NegotiationMessage.class))).thenAnswer(invocation -> {
            NegotiationMessage msg = invocation.getArgument(0);
            msg.setId(102L);
            return msg;
        });

        String cleanContent = "Gostaria de esclarecer se o prazo limite de 15 dias inclui feriados locais.";
        NegotiationMessageDTO result = negotiationService.sendMessage(200, 20, cleanContent);

        assertNotNull(result);
        assertFalse(result.isModerated());
        assertEquals(cleanContent, result.getContentMasked());
    }

    @Test
    @DisplayName("Should enforce authorization when non-participant attempts to send message")
    void testEnforceAuthorizationOnSend() {
        doThrow(new AccessDeniedException("Access denied")).when(authorizationService).enforceNegotiationParticipant(200, 999);

        assertThrows(AccessDeniedException.class, () ->
                negotiationService.sendMessage(200, 999, "Mensagem de um terceiro não autorizado.")
        );
        verify(negotiationMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should list messages for authorized participant")
    void testGetMessages() {
        when(negotiationThreadRepository.findByProposalProposalId(200)).thenReturn(Optional.of(thread));
        NegotiationMessage msg1 = NegotiationMessage.builder().id(1L).thread(thread).sender(lawyer).contentMasked("Pergunta 1").sentAt(LocalDateTime.now()).build();
        NegotiationMessage msg2 = NegotiationMessage.builder().id(2L).thread(thread).sender(client).contentMasked("Resposta 1").sentAt(LocalDateTime.now().plusMinutes(5)).build();

        when(negotiationMessageRepository.findByThreadIdOrderBySentAtAsc(eq(1L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(msg1, msg2)));

        Page<NegotiationMessageDTO> page = negotiationService.getMessages(200, 20, PageRequest.of(0, 10));

        assertNotNull(page);
        assertEquals(2, page.getContent().size());
        assertEquals("Pergunta 1", page.getContent().get(0).getContentMasked());
        verify(authorizationService).enforceNegotiationParticipant(200, 20);
    }
}
