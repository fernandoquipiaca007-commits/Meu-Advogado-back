package com.activecourses.upwork.service.proposal;

import com.activecourses.upwork.dto.ProposalDTO;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProposalVersioningTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProposalService proposalService;

    @MockBean
    private AuthService authService;

    @Test
    @DisplayName("PUT /api/proposals/{id} returns updated proposal with incremented version")
    @WithMockUser(username = "lawyer@legawork.com", roles = {"LAWYER"})
    void testPutProposalReturnsIncrementedVersion() throws Exception {
        ProposalDTO updatedDto = ProposalDTO.builder()
                .proposalId(50)
                .jobId(10)
                .lawyerId(5)
                .proposedRate(BigDecimal.valueOf(4500))
                .proposedDuration(12)
                .strategy("Estratégia revisada com novos prazos")
                .proposalVersion(2)
                .build();

        when(proposalService.updateProposal(eq(50), any(ProposalDTO.class))).thenReturn(updatedDto);

        String payload = """
                {
                    "proposedRate": 4500,
                    "proposedDuration": 12,
                    "strategy": "Estratégia revisada com novos prazos"
                }
                """;

        mockMvc.perform(put("/api/proposals/50")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.proposalVersion").value(2))
                .andExpect(jsonPath("$.data.proposedRate").value(4500));
    }

    @Test
    @DisplayName("Unit: Changing proposedRate increments proposalVersion from 1 to 2")
    void testRateChangeIncrementsVersion() {
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

        User lawyer = User.builder().id(5).firstName("Dr. Lucas").build();
        User client = User.builder().id(12).firstName("Cliente").build();
        Job job = Job.builder().jobId(50).title("Defesa Administrativa").client(client).build();

        Proposal proposal = Proposal.builder()
                .proposalId(88)
                .job(job)
                .lawyer(lawyer)
                .proposedRate(BigDecimal.valueOf(2000))
                .proposedDuration(10)
                .strategy("Estratégia inicial")
                .coverLetter("Carta")
                .proposalVersion(1)
                .status(ProposalStatus.Pending)
                .build();

        when(authSvc.getCurrentUserId()).thenReturn(5);
        when(proposalRepository.findById(88)).thenReturn(Optional.of(proposal));
        when(proposalRepository.save(any(Proposal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProposalDTO updateDto = ProposalDTO.builder()
                .proposedRate(BigDecimal.valueOf(2800)) // Changed rate!
                .proposedDuration(10)
                .strategy("Estratégia inicial")
                .coverLetter("Carta")
                .build();

        ProposalDTO result = service.updateProposal(88, updateDto);

        assertNotNull(result);
        assertEquals(2, result.getProposalVersion());
        assertEquals(BigDecimal.valueOf(2800), result.getProposedRate());
        verify(notificationService).createNotification(eq(12), any(), contains("v2"), any(), eq("job"), eq(50));
    }

    @Test
    @DisplayName("Unit: Changing estimated duration increments proposalVersion")
    void testDurationChangeIncrementsVersion() {
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

        User lawyer = User.builder().id(5).firstName("Dr. Lucas").build();
        User client = User.builder().id(12).firstName("Cliente").build();
        Job job = Job.builder().jobId(50).title("Defesa Administrativa").client(client).build();

        Proposal proposal = Proposal.builder()
                .proposalId(89)
                .job(job)
                .lawyer(lawyer)
                .proposedRate(BigDecimal.valueOf(2000))
                .proposedDuration(10)
                .strategy("Estratégia inicial")
                .coverLetter("Carta")
                .proposalVersion(1)
                .status(ProposalStatus.Pending)
                .build();

        when(authSvc.getCurrentUserId()).thenReturn(5);
        when(proposalRepository.findById(89)).thenReturn(Optional.of(proposal));
        when(proposalRepository.save(any(Proposal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProposalDTO updateDto = ProposalDTO.builder()
                .proposedRate(BigDecimal.valueOf(2000))
                .proposedDuration(20) // Changed duration!
                .strategy("Estratégia inicial")
                .coverLetter("Carta")
                .build();

        ProposalDTO result = service.updateProposal(89, updateDto);

        assertNotNull(result);
        assertEquals(2, result.getProposalVersion());
        assertEquals(20, result.getProposedDuration());
    }

    @Test
    @DisplayName("Unit: Changing strategy/scope increments proposalVersion")
    void testStrategyChangeIncrementsVersion() {
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

        User lawyer = User.builder().id(5).firstName("Dr. Lucas").build();
        User client = User.builder().id(12).firstName("Cliente").build();
        Job job = Job.builder().jobId(50).title("Defesa Administrativa").client(client).build();

        Proposal proposal = Proposal.builder()
                .proposalId(90)
                .job(job)
                .lawyer(lawyer)
                .proposedRate(BigDecimal.valueOf(2000))
                .proposedDuration(10)
                .strategy("Estratégia inicial simples")
                .coverLetter("Carta")
                .proposalVersion(2)
                .status(ProposalStatus.Pending)
                .build();

        when(authSvc.getCurrentUserId()).thenReturn(5);
        when(proposalRepository.findById(90)).thenReturn(Optional.of(proposal));
        when(proposalRepository.save(any(Proposal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProposalDTO updateDto = ProposalDTO.builder()
                .proposedRate(BigDecimal.valueOf(2000))
                .proposedDuration(10)
                .strategy("Estratégia completa incluindo sustentação oral e recurso ordinário") // Changed strategy!
                .coverLetter("Carta")
                .build();

        ProposalDTO result = service.updateProposal(90, updateDto);

        assertNotNull(result);
        assertEquals(3, result.getProposalVersion());
    }

    @Test
    @DisplayName("Unit: Updating inactive proposal throws IllegalStateException")
    void testUpdatingInactiveProposalThrowsException() {
        ProposalRepository proposalRepository = mock(ProposalRepository.class);
        AuthService authSvc = mock(AuthService.class);

        ProposalServiceImpl service = new ProposalServiceImpl(
                proposalRepository, null, null, null, authSvc, null, null, null
        );

        User lawyer = User.builder().id(5).build();
        Proposal proposal = Proposal.builder()
                .proposalId(91)
                .lawyer(lawyer)
                .status(ProposalStatus.Accepted) // Already accepted!
                .build();

        when(authSvc.getCurrentUserId()).thenReturn(5);
        when(proposalRepository.findById(91)).thenReturn(Optional.of(proposal));

        ProposalDTO updateDto = ProposalDTO.builder().proposedRate(BigDecimal.valueOf(3000)).build();

        assertThrows(IllegalStateException.class, () -> service.updateProposal(91, updateDto));
    }

    @Test
    @DisplayName("Unit: Updating another lawyer's proposal throws SecurityException")
    void testUpdatingAnotherLawyersProposalThrowsSecurityException() {
        ProposalRepository proposalRepository = mock(ProposalRepository.class);
        AuthService authSvc = mock(AuthService.class);

        ProposalServiceImpl service = new ProposalServiceImpl(
                proposalRepository, null, null, null, authSvc, null, null, null
        );

        User authorLawyer = User.builder().id(5).build();
        Proposal proposal = Proposal.builder()
                .proposalId(92)
                .lawyer(authorLawyer)
                .status(ProposalStatus.Pending)
                .build();

        when(authSvc.getCurrentUserId()).thenReturn(99); // Intruder user 99!
        when(proposalRepository.findById(92)).thenReturn(Optional.of(proposal));

        ProposalDTO updateDto = ProposalDTO.builder().proposedRate(BigDecimal.valueOf(3000)).build();

        assertThrows(SecurityException.class, () -> service.updateProposal(92, updateDto));
    }
}
