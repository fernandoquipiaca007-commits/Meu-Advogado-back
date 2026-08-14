package com.activecourses.upwork.service.proposal;

import com.activecourses.upwork.dto.ProposalDTO;
import com.activecourses.upwork.exception.DuplicateProposalException;
import com.activecourses.upwork.model.Job;
import com.activecourses.upwork.model.Proposal;
import com.activecourses.upwork.model.ProposalStatus;
import com.activecourses.upwork.model.User;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DuplicateProposalSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProposalService proposalService;

    @MockBean
    private AuthService authService;

    @Test
    @DisplayName("POST /api/proposals/ with duplicate active proposal returns 409 Conflict")
    @WithMockUser(username = "lawyer@legawork.com", roles = {"LAWYER"})
    void testCreateDuplicateProposalReturns409Conflict() throws Exception {
        when(authService.getCurrentUserId()).thenReturn(5);
        when(proposalService.createProposal(any(ProposalDTO.class))).thenThrow(
                new DuplicateProposalException("Você já possui uma proposta ativa para este caso jurídico.")
        );

        String payload = """
                {
                    "jobId": 50,
                    "proposedRate": 3000,
                    "proposedDuration": 15,
                    "coverLetter": "Segunda tentativa de proposta"
                }
                """;

        mockMvc.perform(post("/api/proposals/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value("CONFLICT"))
                .andExpect(jsonPath("$.error").value("Você já possui uma proposta ativa para este caso jurídico."));
    }

    @Test
    @DisplayName("Unit: Verified lawyer cannot submit second active proposal for same job (409)")
    void testServiceRejectsDuplicateActiveProposal() {
        ProposalRepository proposalRepository = mock(ProposalRepository.class);
        JobRepository jobRepository = mock(JobRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        NegotiationThreadRepository negotiationThreadRepository = mock(NegotiationThreadRepository.class);
        AuthService authSvc = mock(AuthService.class);
        ContractService contractService = mock(ContractService.class);
        NotificationService notificationService = mock(NotificationService.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);

        ProposalServiceImpl service = new ProposalServiceImpl(
                proposalRepository,
                jobRepository,
                userRepository,
                negotiationThreadRepository,
                authSvc,
                contractService,
                notificationService,
                authorizationService
        );

        User lawyer = User.builder().id(5).firstName("Dr. Roberto").email("roberto@law.com").build();
        User client = User.builder().id(12).firstName("Cliente").email("cliente@empresa.com").build();
        Job job = Job.builder().jobId(50).title("Demanda Trabalhista").client(client).build();

        when(authSvc.getCurrentUserId()).thenReturn(5);
        when(userRepository.findById(5)).thenReturn(Optional.of(lawyer));
        when(jobRepository.findById(50)).thenReturn(Optional.of(job));

        // Active proposal already exists (Pending or Countered)
        when(proposalRepository.existsByJobJobIdAndLawyerIdAndStatusIn(
                eq(50), eq(5), eq(List.of(ProposalStatus.Pending, ProposalStatus.Countered))
        )).thenReturn(true);

        ProposalDTO request = ProposalDTO.builder()
                .jobId(50)
                .proposedRate(BigDecimal.valueOf(2000))
                .coverLetter("Nova proposta para caso existente")
                .build();

        DuplicateProposalException ex = assertThrows(DuplicateProposalException.class, () ->
                service.createProposal(request)
        );

        assertTrue(ex.getMessage().contains("já possui uma proposta ativa"));
        verify(proposalRepository, never()).save(any());
    }

    @Test
    @DisplayName("Unit: First proposal from verified lawyer is successfully created")
    void testServiceAllowsFirstProposal() {
        ProposalRepository proposalRepository = mock(ProposalRepository.class);
        JobRepository jobRepository = mock(JobRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        NegotiationThreadRepository negotiationThreadRepository = mock(NegotiationThreadRepository.class);
        AuthService authSvc = mock(AuthService.class);
        ContractService contractService = mock(ContractService.class);
        NotificationService notificationService = mock(NotificationService.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);

        ProposalServiceImpl service = new ProposalServiceImpl(
                proposalRepository,
                jobRepository,
                userRepository,
                negotiationThreadRepository,
                authSvc,
                contractService,
                notificationService,
                authorizationService
        );

        User lawyer = User.builder().id(5).firstName("Dr. Roberto").email("roberto@law.com").build();
        User client = User.builder().id(12).firstName("Cliente").email("cliente@empresa.com").build();
        Job job = Job.builder().jobId(50).title("Demanda Trabalhista").client(client).build();

        when(authSvc.getCurrentUserId()).thenReturn(5);
        when(userRepository.findById(5)).thenReturn(Optional.of(lawyer));
        when(jobRepository.findById(50)).thenReturn(Optional.of(job));
        when(proposalRepository.existsByJobJobIdAndLawyerIdAndStatusIn(anyInt(), anyInt(), any())).thenReturn(false);

        when(proposalRepository.save(any(Proposal.class))).thenAnswer(invocation -> {
            Proposal p = invocation.getArgument(0);
            p.setProposalId(101);
            return p;
        });

        ProposalDTO request = ProposalDTO.builder()
                .jobId(50)
                .proposedRate(BigDecimal.valueOf(2500))
                .proposedDuration(7)
                .coverLetter("Primeira proposta")
                .build();

        ProposalDTO created = service.createProposal(request);

        assertNotNull(created);
        assertEquals(101, created.getProposalId());
        assertEquals(1, created.getProposalVersion());
        verify(authorizationService).enforceVerifiedLawyer(5);
        verify(proposalRepository, times(1)).save(any(Proposal.class));
    }
}
