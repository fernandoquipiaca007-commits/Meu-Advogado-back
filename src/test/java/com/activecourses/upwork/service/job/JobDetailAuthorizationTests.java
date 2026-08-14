package com.activecourses.upwork.service.job;

import com.activecourses.upwork.dto.JobDTO;
import com.activecourses.upwork.mapper.JobMapper;
import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.admin.AdminAccessLogRepository;
import com.activecourses.upwork.repository.contract.ContractRepository;
import com.activecourses.upwork.repository.job.JobRepository;
import com.activecourses.upwork.repository.job.ProposalRepository;
import com.activecourses.upwork.repository.skill.SkillRepository;
import com.activecourses.upwork.repository.skill.SpecialtyRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.service.moderation.ContentModerationService;
import com.activecourses.upwork.service.moderation.ContentModerationServiceImpl;
import com.activecourses.upwork.service.security.AuthorizationService;
import com.activecourses.upwork.service.security.ImplAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobDetailAuthorizationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobService jobService;

    @MockBean
    private AuthService authService;

    @Test
    @DisplayName("GET /api/jobs/{id} by client owner returns 200 OK with full details")
    @WithMockUser(username = "client@legawork.com", roles = {"CLIENT"})
    void testClientOwnerAccessesJobReturns200() throws Exception {
        when(authService.getCurrentUserId()).thenReturn(10);
        JobDTO dto = new JobDTO();
        dto.setJobId(1);
        dto.setTitle("Demanda Societária Privada");
        dto.setDescription("Minuta sigilosa do acordo de acionistas");
        dto.setBudget(BigDecimal.valueOf(15000));
        when(jobService.getJobDetail(1, 10)).thenReturn(dto);

        mockMvc.perform(get("/api/jobs/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(1))
                .andExpect(jsonPath("$.data.title").value("Demanda Societária Privada"));
    }

    @Test
    @DisplayName("GET /api/jobs/{id} by lawyer without proposal on PRIVATE job returns 403 Forbidden")
    @WithMockUser(username = "unrelated_lawyer@legawork.com", roles = {"LAWYER"})
    void testUnrelatedLawyerAccessesPrivateJobReturns403() throws Exception {
        when(authService.getCurrentUserId()).thenReturn(20);
        when(jobService.getJobDetail(1, 20)).thenThrow(
                new AccessDeniedException("Access denied: You do not have permission to view details for this legal case.")
        );

        mockMvc.perform(get("/api/jobs/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/cases/{id} by client owner returns 200 OK with full details")
    @WithMockUser(username = "client@legawork.com", roles = {"CLIENT"})
    void testCaseControllerClientOwnerReturns200() throws Exception {
        when(authService.getCurrentUserId()).thenReturn(10);
        JobDTO dto = new JobDTO();
        dto.setJobId(2);
        dto.setTitle("Auditoria Trabalhista");
        when(jobService.getJobDetail(2, 10)).thenReturn(dto);

        mockMvc.perform(get("/api/cases/2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Auditoria Trabalhista"));
    }

    @Test
    @DisplayName("Unit: Authorization rules for Job Details")
    void testJobDetailAuthorizationUnitRules() {
        UserRepository userRepository = mock(UserRepository.class);
        JobRepository jobRepository = mock(JobRepository.class);
        ProposalRepository proposalRepository = mock(ProposalRepository.class);
        ContractRepository contractRepository = mock(ContractRepository.class);
        AdminAccessLogRepository adminAccessLogRepository = mock(AdminAccessLogRepository.class);

        ImplAuthorizationService authZService = new ImplAuthorizationService(
                userRepository,
                contractRepository,
                jobRepository,
                proposalRepository,
                adminAccessLogRepository
        );

        User client = User.builder().id(10).email("client@empresa.com").build();
        User lawyerWithProposal = User.builder().id(20).email("lawyer1@law.com").build();
        User unrelatedLawyer = User.builder().id(30).email("lawyer2@law.com").build();

        Job privateJob = Job.builder()
                .jobId(1)
                .client(client)
                .visibility(JobVisibility.PRIVATE)
                .moderationStatus(ModerationStatus.APPROVED)
                .build();

        Job inviteOnlyJob = Job.builder()
                .jobId(2)
                .client(client)
                .visibility(JobVisibility.INVITE_ONLY)
                .moderationStatus(ModerationStatus.APPROVED)
                .build();

        when(jobRepository.findById(1)).thenReturn(Optional.of(privateJob));
        when(jobRepository.findById(2)).thenReturn(Optional.of(inviteOnlyJob));

        // 1. Client owner -> allowed
        assertDoesNotThrow(() -> authZService.enforceCanViewJobDetail(1, 10));

        // 2. Lawyer with proposal -> allowed
        when(proposalRepository.findByJobJobIdAndLawyerId(1, 20)).thenReturn(Optional.of(new Proposal()));
        assertDoesNotThrow(() -> authZService.enforceCanViewJobDetail(1, 20));

        // 3. Unrelated lawyer on PRIVATE job -> forbidden (403)
        when(proposalRepository.findByJobJobIdAndLawyerId(1, 30)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> authZService.enforceCanViewJobDetail(1, 30));

        // 4. Unrelated lawyer on INVITE_ONLY job -> forbidden (403)
        when(proposalRepository.findByJobJobIdAndLawyerId(2, 30)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> authZService.enforceCanViewJobDetail(2, 30));
    }
}
