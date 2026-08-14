package com.activecourses.upwork.service.proposal;

import com.activecourses.upwork.dto.ProposalDTO;
import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.job.JobRepository;
import com.activecourses.upwork.repository.job.ProposalRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.service.contract.ContractService;
import com.activecourses.upwork.service.notification.NotificationService;
import com.activecourses.upwork.service.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProposalServiceVerificationTest {

    @Mock
    private ProposalRepository proposalRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthService authService;

    @Mock
    private ContractService contractService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private ProposalServiceImpl proposalService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateProposal_SuccessWhenLawyerVerified() {
        int lawyerId = 5;
        when(authService.getCurrentUserId()).thenReturn(lawyerId);
        doNothing().when(authorizationService).enforceVerifiedLawyer(lawyerId);

        User lawyer = User.builder().id(lawyerId).firstName("Ana").lastName("Silva").build();
        User client = User.builder().id(1).firstName("Carlos").lastName("Oliveira").build();
        Job job = Job.builder().jobId(10).title("Revisão de Contrato").client(client).budget(BigDecimal.valueOf(1000)).build();

        when(userRepository.findById(lawyerId)).thenReturn(Optional.of(lawyer));
        when(jobRepository.findById(10)).thenReturn(Optional.of(job));
        when(proposalRepository.save(any(Proposal.class))).thenAnswer(invocation -> {
            Proposal p = invocation.getArgument(0);
            p.setProposalId(101);
            return p;
        });

        ProposalDTO request = ProposalDTO.builder()
                .jobId(10)
                .coverLetter("Proposta detalhada")
                .proposedRate(BigDecimal.valueOf(500))
                .totalValue(BigDecimal.valueOf(500))
                .build();

        ProposalDTO result = proposalService.createProposal(request);

        assertNotNull(result);
        assertEquals(101, result.getProposalId());
        verify(authorizationService, times(1)).enforceVerifiedLawyer(lawyerId);
        verify(proposalRepository, times(1)).save(any(Proposal.class));
    }

    @Test
    void testCreateProposal_Throws403WhenLawyerNotVerified() {
        int lawyerId = 6;
        when(authService.getCurrentUserId()).thenReturn(lawyerId);
        doThrow(new AccessDeniedException("Lawyer verification required. Current status: DRAFT"))
                .when(authorizationService).enforceVerifiedLawyer(lawyerId);

        ProposalDTO request = ProposalDTO.builder()
                .jobId(10)
                .coverLetter("Tentativa não verificada")
                .build();

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> proposalService.createProposal(request));

        assertTrue(ex.getMessage().contains("Lawyer verification required"));
        verify(proposalRepository, never()).save(any(Proposal.class));
    }
}
