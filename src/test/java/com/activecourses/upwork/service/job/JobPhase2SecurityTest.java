package com.activecourses.upwork.service.job;

import com.activecourses.upwork.dto.JobDTO;
import com.activecourses.upwork.dto.JobDiscoveryDto;
import com.activecourses.upwork.exception.ContentModerationException;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobPhase2SecurityTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private ProposalRepository proposalRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthService authService;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private SpecialtyRepository specialtyRepository;

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private AdminAccessLogRepository adminAccessLogRepository;

    private ContentModerationService contentModerationService;
    private AuthorizationService authorizationService;
    private JobMapper jobMapper;
    private JobServiceImpl jobService;

    private User client;
    private User lawyer;
    private Job privateJob;
    private Job discoveryJob;

    @BeforeEach
    void setUp() {
        contentModerationService = new ContentModerationServiceImpl();
        jobMapper = new JobMapper();

        authorizationService = new ImplAuthorizationService(
                userRepository,
                contractRepository,
                jobRepository,
                proposalRepository,
                adminAccessLogRepository
        );

        jobService = new JobServiceImpl(
                jobRepository,
                proposalRepository,
                userRepository,
                authService,
                jobMapper,
                skillRepository,
                specialtyRepository,
                contentModerationService,
                authorizationService
        );

        client = User.builder().id(10).firstName("Ana").lastName("Cliente").email("ana@empresa.com").build();

        UserProfile lawyerProfile = UserProfile.builder()
                .verificationStatus(VerificationStatus.VERIFIED)
                .oabNumber("123456")
                .oabState("SP")
                .oabExpiryDate(LocalDate.now().plusYears(1))
                .build();
        lawyer = User.builder().id(20).firstName("Dr. Roberto").lastName("Advogado").email("roberto@law.com").userProfile(lawyerProfile).build();

        privateJob = Job.builder()
                .jobId(1)
                .title("Demanda Privada Interna")
                .description("Descrição estritamente confidencial")
                .client(client)
                .budget(BigDecimal.valueOf(5000))
                .visibility(JobVisibility.PRIVATE)
                .sensitivity(JobSensitivity.STRICTLY_CONFIDENTIAL)
                .moderationStatus(ModerationStatus.APPROVED)
                .status(JobStatus.Open)
                .build();

        discoveryJob = Job.builder()
                .jobId(2)
                .title("Assessoria Tributária Sanitizada")
                .description("Consultoria preventiva em ICMS sem dados confidenciais")
                .client(client)
                .budget(BigDecimal.valueOf(8000))
                .visibility(JobVisibility.DISCOVERY_SANITIZED)
                .sensitivity(JobSensitivity.STANDARD)
                .moderationStatus(ModerationStatus.APPROVED)
                .status(JobStatus.Open)
                .build();
    }

    @Test
    @DisplayName("Should block creation of job with CNJ process number in description (422)")
    void testCreateJobWithCNJThrowsModerationException() {
        when(authService.getCurrentUserId()).thenReturn(10);

        JobDTO request = new JobDTO();
        request.setTitle("Defesa em Processo Judicial");
        request.setDescription("Acompanhar o processo número 0009876-12.2024.8.26.0100 já distribuído.");
        request.setBudget(BigDecimal.valueOf(3000));

        assertThrows(ContentModerationException.class, () -> jobService.createJob(request));
        verify(jobRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should return sanitized discovery DTOs without sensitive client details")
    void testGetDiscoveryCasesReturnsSanitizedDTO() {
        when(jobRepository.findDiscoveryJobs(any(), eq(ModerationStatus.APPROVED), eq(JobStatus.Open), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(discoveryJob)));
        when(proposalRepository.countByJobJobId(2)).thenReturn(3L);

        Page<JobDiscoveryDto> result = jobService.getDiscoveryCases(null, null, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        JobDiscoveryDto dto = result.getContent().get(0);
        assertEquals(2, dto.getJobId());
        assertEquals("Assessoria Tributária Sanitizada", dto.getTitle());
        assertEquals(3, dto.getProposalsCount());
        assertEquals(JobVisibility.DISCOVERY_SANITIZED, dto.getVisibility());
    }

    @Test
    @DisplayName("Should allow owner client to view private job details")
    void testOwnerClientCanViewPrivateJob() {
        when(jobRepository.findById(1)).thenReturn(Optional.of(privateJob));
        when(proposalRepository.countByJobJobId(1)).thenReturn(0L);

        JobDTO dto = jobService.getJobDetail(1, 10); // client id 10
        assertNotNull(dto);
        assertEquals(1, dto.getJobId());
        assertEquals("Demanda Privada Interna", dto.getTitle());
    }

    @Test
    @DisplayName("Should forbid unrelated lawyer from accessing private job details (403)")
    void testUnrelatedLawyerForbiddenFromPrivateJob() {
        when(jobRepository.findById(1)).thenReturn(Optional.of(privateJob));
        when(proposalRepository.findByJobJobIdAndLawyerId(1, 20)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> jobService.getJobDetail(1, 20));
    }
}
