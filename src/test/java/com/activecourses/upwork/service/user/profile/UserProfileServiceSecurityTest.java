package com.activecourses.upwork.service.user.profile;

import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.dto.user.UserProfileDto;
import com.activecourses.upwork.mapper.Mapper;
import com.activecourses.upwork.model.User;
import com.activecourses.upwork.model.UserProfile;
import com.activecourses.upwork.model.VerificationStatus;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserProfileServiceSecurityTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private Mapper<User, UserProfileDto> userProfileMapper;

    @Mock
    private AuthService authService;

    @InjectMocks
    private ImplUserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
    }

    @Test
    void testUpdateUserProfile_OwnerSuccess_DoesNotAllowVerificationStatusOverride() {
        int userId = 10;
        when(authService.getCurrentUserId()).thenReturn(userId);

        User user = User.builder().id(userId).firstName("Original").lastName("Name").build();
        UserProfile existingProfile = UserProfile.builder()
                .user(user)
                .verificationStatus(VerificationStatus.DRAFT)
                .title("Original Title")
                .build();
        user.setUserProfile(existingProfile);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfileDto returnDto = UserProfileDto.builder()
                .id(userId)
                .title("New Title")
                .verificationStatus("DRAFT")
                .build();
        when(userProfileMapper.mapTo(any(User.class))).thenReturn(returnDto);

        UserProfileDto updateRequest = UserProfileDto.builder()
                .title("New Title")
                .verificationStatus("VERIFIED") // Malicious attempt to elevate status
                .build();

        ResponseEntity<?> response = userProfileService.UpdateUserProfile(userId, updateRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        // Verify that profile status remains DRAFT in the entity
        assertEquals(VerificationStatus.DRAFT, user.getUserProfile().getVerificationStatus());
        assertEquals("New Title", user.getUserProfile().getTitle());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void testUpdateUserProfile_IDOR_BlockedWhenUpdatingOtherUser() {
        int currentUserId = 10;
        int targetUserId = 20;
        when(authService.getCurrentUserId()).thenReturn(currentUserId);

        UserProfileDto updateRequest = UserProfileDto.builder()
                .title("Hacked Title")
                .build();

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> userProfileService.UpdateUserProfile(targetUserId, updateRequest));

        assertTrue(ex.getMessage().contains("not authorized to update profile"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testUpdateUserProfile_AdminCanUpdateOtherUserProfile() {
        int currentUserId = 1;
        int targetUserId = 20;
        when(authService.getCurrentUserId()).thenReturn(currentUserId);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        User targetUser = User.builder().id(targetUserId).build();
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userProfileMapper.mapTo(any(User.class))).thenReturn(UserProfileDto.builder().id(targetUserId).title("Admin Update").build());

        UserProfileDto updateRequest = UserProfileDto.builder()
                .title("Admin Update")
                .build();

        ResponseEntity<?> response = userProfileService.UpdateUserProfile(targetUserId, updateRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository, times(1)).save(targetUser);
    }
}
