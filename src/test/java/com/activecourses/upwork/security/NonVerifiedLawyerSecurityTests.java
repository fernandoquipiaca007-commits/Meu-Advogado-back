package com.activecourses.upwork.security;

import com.activecourses.upwork.controller.job.CaseController;
import com.activecourses.upwork.controller.job.JobController;
import com.activecourses.upwork.exception.GlobalExceptionHandler;
import com.activecourses.upwork.model.User;
import com.activecourses.upwork.model.UserProfile;
import com.activecourses.upwork.model.VerificationStatus;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.service.job.JobService;
import com.activecourses.upwork.service.security.AuthorizationService;
import com.activecourses.upwork.service.security.ImplAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NonVerifiedLawyerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private AuthorizationService authorizationService;

    @MockBean
    private JobService jobService;

    @Test
    @DisplayName("GET /api/cases/discovery with ROLE_CLIENT (non-lawyer) returns 403 Forbidden")
    @WithMockUser(username = "client@legawork.com", roles = {"CLIENT"})
    void testGetDiscoveryCasesWithClientRoleReturns403() throws Exception {
        mockMvc.perform(get("/api/cases/discovery")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/cases/discovery with unverified lawyer returns 403 Forbidden")
    @WithMockUser(username = "unverified@legawork.com", roles = {"LAWYER"})
    void testGetDiscoveryCasesWithUnverifiedLawyerReturns403() throws Exception {
        when(authService.getCurrentUserId()).thenReturn(100);
        doThrow(new AccessDeniedException("Lawyer verification required. Current status: PENDING"))
                .when(authorizationService).enforceVerifiedLawyer(100);

        mockMvc.perform(get("/api/cases/discovery")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/jobs/discovery with unverified lawyer returns 403 Forbidden")
    @WithMockUser(username = "unverified@legawork.com", roles = {"LAWYER"})
    void testGetJobDiscoveryWithUnverifiedLawyerReturns403() throws Exception {
        when(authService.getCurrentUserId()).thenReturn(100);
        doThrow(new AccessDeniedException("Lawyer verification required. Current status: DRAFT"))
                .when(authorizationService).enforceVerifiedLawyer(100);

        mockMvc.perform(get("/api/jobs/discovery")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/jobs/discovery with non-lawyer (ROLE_CLIENT) returns 403 Forbidden")
    @WithMockUser(username = "client@legawork.com", roles = {"CLIENT"})
    void testGetJobDiscoveryWithClientRoleReturns403() throws Exception {
        mockMvc.perform(get("/api/jobs/discovery")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unit test: enforceVerifiedLawyer throws 403 AccessDeniedException for unverified statuses")
    void testEnforceVerifiedLawyerDirectUnitStatuses() {
        UserRepository userRepository = mock(UserRepository.class);
        ImplAuthorizationService authZService = new ImplAuthorizationService(
                userRepository, null, null, null, null
        );

        // 1. User without profile
        User noProfileUser = User.builder().id(1).email("noprofile@law.com").build();
        when(userRepository.findById(1)).thenReturn(Optional.of(noProfileUser));
        assertThrows(AccessDeniedException.class, () -> authZService.enforceVerifiedLawyer(1));

        // 2. User with DRAFT status
        User draftUser = User.builder().id(2).email("draft@law.com")
                .userProfile(UserProfile.builder().verificationStatus(VerificationStatus.DRAFT).build()).build();
        when(userRepository.findById(2)).thenReturn(Optional.of(draftUser));
        assertThrows(AccessDeniedException.class, () -> authZService.enforceVerifiedLawyer(2));

        // 3. User with PENDING status
        User pendingUser = User.builder().id(3).email("pending@law.com")
                .userProfile(UserProfile.builder().verificationStatus(VerificationStatus.PENDING).build()).build();
        when(userRepository.findById(3)).thenReturn(Optional.of(pendingUser));
        assertThrows(AccessDeniedException.class, () -> authZService.enforceVerifiedLawyer(3));

        // 4. User with SUSPENDED status
        User suspendedUser = User.builder().id(4).email("suspended@law.com")
                .userProfile(UserProfile.builder().verificationStatus(VerificationStatus.SUSPENDED).build()).build();
        when(userRepository.findById(4)).thenReturn(Optional.of(suspendedUser));
        assertThrows(AccessDeniedException.class, () -> authZService.enforceVerifiedLawyer(4));

        // 5. User with expired OAB
        User expiredUser = User.builder().id(5).email("expired@law.com")
                .userProfile(UserProfile.builder()
                        .verificationStatus(VerificationStatus.VERIFIED)
                        .oabNumber("123456")
                        .oabState("SP")
                        .oabExpiryDate(LocalDate.now().minusDays(10))
                        .build()).build();
        when(userRepository.findById(5)).thenReturn(Optional.of(expiredUser));
        assertThrows(AccessDeniedException.class, () -> authZService.enforceVerifiedLawyer(5));

        // 6. User with VERIFIED and valid OAB succeeds
        User verifiedUser = User.builder().id(6).email("verified@law.com")
                .userProfile(UserProfile.builder()
                        .verificationStatus(VerificationStatus.VERIFIED)
                        .oabNumber("123456")
                        .oabState("SP")
                        .oabExpiryDate(LocalDate.now().plusMonths(6))
                        .build()).build();
        when(userRepository.findById(6)).thenReturn(Optional.of(verifiedUser));
        assertDoesNotThrow(() -> authZService.enforceVerifiedLawyer(6));
    }
}
