package com.activecourses.upwork.service.conflict;

import com.activecourses.upwork.dto.ConflictCheckDto;
import com.activecourses.upwork.dto.ConflictCheckRequestDto;
import com.activecourses.upwork.model.ConflictCheck;
import com.activecourses.upwork.model.ConflictStatus;
import com.activecourses.upwork.model.Job;
import com.activecourses.upwork.model.User;
import com.activecourses.upwork.repository.conflict.ConflictCheckRepository;
import com.activecourses.upwork.repository.job.JobRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.service.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConflictServiceSecurityTest {

    @Mock
    private ConflictCheckRepository conflictCheckRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthService authService;

    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private ConflictServiceImpl conflictService;

    private User client;
    private User lawyer;
    private Job job;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        client = User.builder().id(10).firstName("Maria").lastName("Silva").build();
        lawyer = User.builder().id(20).firstName("Dr. Roberto").lastName("Almeida").build();

        job = Job.builder().jobId(100).title("Demanda Trabalhista").client(client).build();
    }

    @Test
    @DisplayName("checkConflict deve inicializar como CLEAR para o cliente da demanda")
    void testCheckConflict_SuccessForJobOwner() {
        when(authService.getCurrentUserId()).thenReturn(10);
        when(jobRepository.findById(100)).thenReturn(Optional.of(job));
        when(userRepository.findById(20)).thenReturn(Optional.of(lawyer));
        when(conflictCheckRepository.findByJobJobIdAndLawyerId(100, 20)).thenReturn(Optional.empty());
        when(conflictCheckRepository.save(any(ConflictCheck.class))).thenAnswer(inv -> {
            ConflictCheck cc = inv.getArgument(0);
            cc.setId(1L);
            return cc;
        });

        ConflictCheckDto dto = conflictService.checkConflict(100, 20);

        assertNotNull(dto);
        assertEquals(ConflictStatus.CLEAR, dto.getStatus());
        assertEquals(100, dto.getJobId());
        assertEquals(20, dto.getLawyerId());
    }

    @Test
    @DisplayName("declareConflict com BLOCKED deve mascarar a razão sem vazar dados de terceiros")
    void testDeclareConflict_MasksReasonOnBlocked() {
        when(authService.getCurrentUserId()).thenReturn(20);
        when(jobRepository.findById(100)).thenReturn(Optional.of(job));
        when(userRepository.findById(20)).thenReturn(Optional.of(lawyer));
        when(conflictCheckRepository.findByJobJobIdAndLawyerId(100, 20)).thenReturn(Optional.empty());
        when(conflictCheckRepository.save(any(ConflictCheck.class))).thenAnswer(inv -> {
            ConflictCheck cc = inv.getArgument(0);
            cc.setId(2L);
            return cc;
        });

        ConflictCheckRequestDto request = ConflictCheckRequestDto.builder()
                .jobId(100)
                .lawyerId(20)
                .status(ConflictStatus.BLOCKED)
                .reason("Tenho processo contra o autor João da Silva, CPF 123.456.789-00 no processo 0001234-56.2024.8.26.0100")
                .build();

        ConflictCheckDto dto = conflictService.declareConflict(request);

        assertNotNull(dto);
        assertEquals(ConflictStatus.BLOCKED, dto.getStatus());
        assertNotNull(dto.getReasonMasked());
        // Verify privacy preservation: zero leakage of raw third-party details
        assertFalse(dto.getReasonMasked().contains("João da Silva"));
        assertFalse(dto.getReasonMasked().contains("123.456.789-00"));
        assertFalse(dto.getReasonMasked().contains("0001234-56.2024.8.26.0100"));
        assertTrue(dto.getReasonMasked().contains("Impedimento ético-profissional"));
    }

    @Test
    @DisplayName("declareConflict deve lançar AccessDeniedException se outro usuário tentar declarar")
    void testDeclareConflict_ThrowsAccessDeniedForUnauthorizedUser() {
        when(authService.getCurrentUserId()).thenReturn(99); // different user
        when(jobRepository.findById(100)).thenReturn(Optional.of(job));

        ConflictCheckRequestDto request = ConflictCheckRequestDto.builder()
                .jobId(100)
                .lawyerId(20)
                .status(ConflictStatus.CLEAR)
                .build();

        assertThrows(AccessDeniedException.class, () -> conflictService.declareConflict(request));
    }
}
