package com.activecourses.upwork.service.security;

import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.admin.AdminAccessLogRepository;
import com.activecourses.upwork.repository.contract.ContractRepository;
import com.activecourses.upwork.repository.job.JobRepository;
import com.activecourses.upwork.repository.job.ProposalRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthorizationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private ProposalRepository proposalRepository;

    @Mock
    private AdminAccessLogRepository adminAccessLogRepository;

    @InjectMocks
    private ImplAuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
    }

    @Test
    void testEnforceVerifiedLawyer_SuccessWhenVerifiedAndValidOab() {
        User lawyer = User.builder().id(10).email("lawyer@legawork.com").build();
        UserProfile profile = UserProfile.builder()
                .user(lawyer)
                .verificationStatus(VerificationStatus.VERIFIED)
                .oabNumber("123456")
                .oabState("SP")
                .oabExpiryDate(LocalDate.now().plusYears(1))
                .build();
        lawyer.setUserProfile(profile);

        when(userRepository.findById(10)).thenReturn(Optional.of(lawyer));

        assertDoesNotThrow(() -> authorizationService.enforceVerifiedLawyer(10));
    }

    @Test
    void testEnforceVerifiedLawyer_ThrowsWhenDraft() {
        User lawyer = User.builder().id(11).email("draft@legawork.com").build();
        UserProfile profile = UserProfile.builder()
                .user(lawyer)
                .verificationStatus(VerificationStatus.DRAFT)
                .build();
        lawyer.setUserProfile(profile);

        when(userRepository.findById(11)).thenReturn(Optional.of(lawyer));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> authorizationService.enforceVerifiedLawyer(11));
        assertTrue(ex.getMessage().contains("Lawyer verification required"));
    }

    @Test
    void testEnforceVerifiedLawyer_ThrowsWhenPending() {
        User lawyer = User.builder().id(12).email("pending@legawork.com").build();
        UserProfile profile = UserProfile.builder()
                .user(lawyer)
                .verificationStatus(VerificationStatus.PENDING)
                .build();
        lawyer.setUserProfile(profile);

        when(userRepository.findById(12)).thenReturn(Optional.of(lawyer));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> authorizationService.enforceVerifiedLawyer(12));
        assertTrue(ex.getMessage().contains("Lawyer verification required"));
    }

    @Test
    void testEnforceVerifiedLawyer_ThrowsWhenSuspended() {
        User lawyer = User.builder().id(13).email("suspended@legawork.com").build();
        UserProfile profile = UserProfile.builder()
                .user(lawyer)
                .verificationStatus(VerificationStatus.SUSPENDED)
                .build();
        lawyer.setUserProfile(profile);

        when(userRepository.findById(13)).thenReturn(Optional.of(lawyer));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> authorizationService.enforceVerifiedLawyer(13));
        assertTrue(ex.getMessage().contains("Lawyer verification required"));
    }

    @Test
    void testEnforceVerifiedLawyer_ThrowsWhenExpiredOab() {
        User lawyer = User.builder().id(14).email("expired@legawork.com").build();
        UserProfile profile = UserProfile.builder()
                .user(lawyer)
                .verificationStatus(VerificationStatus.VERIFIED)
                .oabExpiryDate(LocalDate.now().minusDays(1))
                .build();
        lawyer.setUserProfile(profile);

        when(userRepository.findById(14)).thenReturn(Optional.of(lawyer));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> authorizationService.enforceVerifiedLawyer(14));
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    void testEnforceVerifiedLawyer_ThrowsWhenNullId() {
        assertThrows(AccessDeniedException.class,
                () -> authorizationService.enforceVerifiedLawyer(null));
    }

    @Test
    void testEnforceContractParticipant_SuccessForClientAndLawyer() {
        User client = User.builder().id(1).build();
        User lawyer = User.builder().id(2).build();
        Contract contract = Contract.builder().contractId(100).client(client).lawyer(lawyer).build();

        when(contractRepository.findById(100)).thenReturn(Optional.of(contract));

        assertDoesNotThrow(() -> authorizationService.enforceContractParticipant(100, 1));
        assertDoesNotThrow(() -> authorizationService.enforceContractParticipant(100, 2));
    }

    @Test
    void testEnforceContractParticipant_ThrowsForNonParticipant() {
        User client = User.builder().id(1).build();
        User lawyer = User.builder().id(2).build();
        Contract contract = Contract.builder().contractId(100).client(client).lawyer(lawyer).build();

        when(contractRepository.findById(100)).thenReturn(Optional.of(contract));

        assertThrows(AccessDeniedException.class,
                () -> authorizationService.enforceContractParticipant(100, 99));
    }

    @Test
    void testEnforceContractParticipant_AllowsAdmin() {
        User client = User.builder().id(1).build();
        User lawyer = User.builder().id(2).build();
        Contract contract = Contract.builder().contractId(100).client(client).lawyer(lawyer).build();

        when(contractRepository.findById(100)).thenReturn(Optional.of(contract));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertDoesNotThrow(() -> authorizationService.enforceContractParticipant(100, 99));
    }

    @Test
    void testEnforceJobOwner_SuccessForClientAndThrowsForOther() {
        User client = User.builder().id(1).build();
        Job job = Job.builder().jobId(50).client(client).build();

        when(jobRepository.findById(50)).thenReturn(Optional.of(job));

        assertDoesNotThrow(() -> authorizationService.enforceJobOwner(50, 1));
        assertThrows(AccessDeniedException.class, () -> authorizationService.enforceJobOwner(50, 2));
    }

    @Test
    void testEnforceProposalOwnerOrClient_SuccessForBothAndThrowsForOther() {
        User client = User.builder().id(1).build();
        User lawyer = User.builder().id(2).build();
        Job job = Job.builder().jobId(50).client(client).build();
        Proposal proposal = Proposal.builder().proposalId(20).job(job).lawyer(lawyer).build();

        when(proposalRepository.findById(20)).thenReturn(Optional.of(proposal));

        assertDoesNotThrow(() -> authorizationService.enforceProposalOwnerOrClient(20, 1));
        assertDoesNotThrow(() -> authorizationService.enforceProposalOwnerOrClient(20, 2));
        assertThrows(AccessDeniedException.class, () -> authorizationService.enforceProposalOwnerOrClient(20, 3));
    }

    @Test
    void testLogAdminAccess_SavesLogSuccessfully() {
        User admin = User.builder().id(1).email("admin@legawork.com").build();
        User target = User.builder().id(2).email("target@legawork.com").build();

        when(userRepository.findById(1)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2)).thenReturn(Optional.of(target));
        when(adminAccessLogRepository.save(any(AdminAccessLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn("192.168.1.1");
        when(req.getHeader("User-Agent")).thenReturn("Mozilla/5.0");

        AdminAccessLog log = authorizationService.logAdminAccess(
                1, 2, "USER_PROFILE", "2", "VIEW_PII", "Support ticket #1234", req
        );

        assertNotNull(log);
        assertEquals("USER_PROFILE", log.getTargetResourceType());
        assertEquals("Support ticket #1234", log.getJustification());
        assertEquals("192.168.1.1", log.getIpAddress());
        verify(adminAccessLogRepository, times(1)).save(any(AdminAccessLog.class));
    }
}
