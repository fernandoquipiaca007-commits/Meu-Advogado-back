package com.activecourses.upwork.service.proposal;

import com.activecourses.upwork.dto.ProposalDTO;
import com.activecourses.upwork.exception.DuplicateProposalException;
import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.job.JobRepository;
import com.activecourses.upwork.repository.job.ProposalRepository;
import com.activecourses.upwork.repository.negotiation.NegotiationThreadRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.service.contract.ContractService;
import com.activecourses.upwork.service.notification.NotificationService;
import com.activecourses.upwork.service.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProposalPhase2Test {

    @Mock
    private ProposalRepository proposalRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NegotiationThreadRepository negotiationThreadRepository;

    @Mock
    private AuthService authService;

    @Mock
    private ContractService contractService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuthorizationService authorizationService;

    private ProposalServiceImpl proposalService;

    private User lawyer;
    private User client;
    private Job job;

    @BeforeEach
    void setUp() {
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

        lawyer = User.builder().id(5).firstName("Dr. Lucas").lastName("Moraes").email("lucas@oab.org.br").build();
        client = User.builder().id(12).firstName("Mariana").lastName("Silva").email("mariana@empresa.com").build();
        job = Job.builder().jobId(50).title("Elaboração de Estatuto Social").client(client).build();
    }

    @Test
    @DisplayName("Should reject duplicate active proposal for same lawyer and job with DuplicateProposalException")
    void testRejectDuplicateActiveProposal() {
        when(authService.getCurrentUserId()).thenReturn(5);
        when(userRepository.findById(5)).thenReturn(Optional.of(lawyer));
        when(jobRepository.findById(50)).thenReturn(Optional.of(job));

        when(proposalRepository.existsByJobJobIdAndLawyerIdAndStatusIn(
                eq(50),
                eq(5),
                eq(List.of(ProposalStatus.Pending, ProposalStatus.Countered))
        )).thenReturn(true);

        ProposalDTO request = ProposalDTO.builder()
                .jobId(50)
                .proposedRate(BigDecimal.valueOf(1500))
                .coverLetter("Segunda proposta para o mesmo caso")
                .build();

        assertThrows(DuplicateProposalException.class, () -> proposalService.createProposal(request));
        verify(proposalRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should create proposal with version 1 and automatically create negotiation thread")
    void testCreateProposalInitialVersionAndThread() {
        when(authService.getCurrentUserId()).thenReturn(5);
        when(userRepository.findById(5)).thenReturn(Optional.of(lawyer));
        when(jobRepository.findById(50)).thenReturn(Optional.of(job));
        when(proposalRepository.existsByJobJobIdAndLawyerIdAndStatusIn(anyInt(), anyInt(), any())).thenReturn(false);

        when(proposalRepository.save(any(Proposal.class))).thenAnswer(invocation -> {
            Proposal p = invocation.getArgument(0);
            p.setProposalId(88);
            return p;
        });

        when(negotiationThreadRepository.save(any(NegotiationThread.class))).thenAnswer(invocation -> {
            NegotiationThread t = invocation.getArgument(0);
            t.setId(10L);
            return t;
        });

        ProposalDTO request = ProposalDTO.builder()
                .jobId(50)
                .proposedRate(BigDecimal.valueOf(2500))
                .proposedDuration(10)
                .strategy("Análise prévia e elaboração de minuta customizada.")
                .coverLetter("Proposta técnica e comercial.")
                .build();

        ProposalDTO result = proposalService.createProposal(request);

        assertNotNull(result);
        assertEquals(88, result.getProposalId());
        assertEquals(1, result.getProposalVersion());
        verify(authorizationService).enforceVerifiedLawyer(5);
        verify(negotiationThreadRepository).save(any(NegotiationThread.class));
        verify(notificationService).createNotification(eq(12), any(), any(), any(), eq("job"), eq(50));
    }

    @Test
    @DisplayName("Should increment proposal version upon material change in terms")
    void testIncrementProposalVersionOnMaterialUpdate() {
        when(authService.getCurrentUserId()).thenReturn(5);

        Proposal existingProposal = Proposal.builder()
                .proposalId(88)
                .job(job)
                .lawyer(lawyer)
                .proposedRate(BigDecimal.valueOf(2000))
                .proposedDuration(10)
                .strategy("Estratégia inicial")
                .coverLetter("Carta inicial")
                .proposalVersion(1)
                .status(ProposalStatus.Pending)
                .build();

        when(proposalRepository.findById(88)).thenReturn(Optional.of(existingProposal));
        when(proposalRepository.save(any(Proposal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProposalDTO updateRequest = ProposalDTO.builder()
                .proposedRate(BigDecimal.valueOf(3000)) // Changed rate!
                .proposedDuration(10)
                .strategy("Estratégia inicial")
                .coverLetter("Carta inicial")
                .build();

        ProposalDTO updated = proposalService.updateProposal(88, updateRequest);

        assertNotNull(updated);
        assertEquals(2, updated.getProposalVersion());
        verify(notificationService).createNotification(eq(12), any(), contains("v2"), any(), eq("job"), eq(50));
    }
}
