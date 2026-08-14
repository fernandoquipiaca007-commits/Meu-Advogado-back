package com.activecourses.upwork.service.user.profile;

import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.dto.user.UserProfileDto;
import com.activecourses.upwork.mapper.Mapper;
import com.activecourses.upwork.model.User;
import com.activecourses.upwork.model.UserProfile;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImplUserProfileService implements UserProfileService {
    private final UserRepository userRepository;
    private final Mapper<User, UserProfileDto> userProfileMapper;
    private final AuthService authService;

    @Override
    public ResponseEntity<?> getUserProfile(int id) {
        User user = userRepository.findById(id).
                orElseThrow(() -> new UsernameNotFoundException("user Not Found"));
        UserProfileDto userProfileDto = userProfileMapper.mapTo(user);

        return ResponseEntity.ok()
                .body(ResponseDto.builder()
                        .status(HttpStatus.OK)
                        .success(true)
                        .data(userProfileDto)
                        .build());
    }

    public UserProfile createUserProfile(User user) {
        return UserProfile.builder()
                .user(user).build();
    }

    @Override
    public ResponseEntity<?> UpdateUserProfile(int userId, UserProfileDto updateRequest) {
        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId == null || (!currentUserId.equals(userId) && !isAdmin())) {
            throw new AccessDeniedException("You are not authorized to update profile of user " + userId);
        }

        User user = userRepository.findById(userId).
                orElseThrow(() -> new UsernameNotFoundException("user Not Found"));
        return ResponseEntity.ok()
                .body(ResponseDto.builder()
                        .status(HttpStatus.OK)
                        .success(true)
                        .data(doUpdate(updateRequest, user))
                        .build());
    }

    private UserProfileDto doUpdate(UserProfileDto updateRequest, User user) {
        // Update fields in user table if provided and not blank
        if (updateRequest.getFirstName() != null && !updateRequest.getFirstName().isBlank()) {
            user.setFirstName(updateRequest.getFirstName());
        }
        if (updateRequest.getLastName() != null && !updateRequest.getLastName().isBlank()) {
            user.setLastName(updateRequest.getLastName());
        }

        // Get userProfile and update it
        UserProfile userProfile = user.getUserProfile();
        if (userProfile == null) {
            userProfile = new UserProfile();
            userProfile.setUser(user);
        }

        if (updateRequest.getTitle() != null) userProfile.setTitle(updateRequest.getTitle());
        if (updateRequest.getDescription() != null) userProfile.setDescription(updateRequest.getDescription());
        if (updateRequest.getLocation() != null) userProfile.setLocation(updateRequest.getLocation());
        if (updateRequest.getHourlyRate() != null) userProfile.setHourlyRate(updateRequest.getHourlyRate());

        // Legal fields
        if (updateRequest.getOabNumber() != null) userProfile.setOabNumber(updateRequest.getOabNumber());
        if (updateRequest.getOabState() != null) userProfile.setOabState(updateRequest.getOabState());
        if (updateRequest.getOabExpiryDate() != null) userProfile.setOabExpiryDate(updateRequest.getOabExpiryDate());
        if (updateRequest.getJurisdictionStates() != null) userProfile.setJurisdictionStates(updateRequest.getJurisdictionStates());
        userProfile.setMfaEnabled(updateRequest.isMfaEnabled());
        if (updateRequest.getCountry() != null) userProfile.setCountry(updateRequest.getCountry());
        if (updateRequest.getPhone() != null) userProfile.setPhone(updateRequest.getPhone());
        if (updateRequest.getPhotoUrl() != null) userProfile.setPhotoUrl(updateRequest.getPhotoUrl());
        if (updateRequest.getDateOfBirth() != null) userProfile.setDateOfBirth(updateRequest.getDateOfBirth());
        if (updateRequest.getLanguages() != null) userProfile.setLanguages(updateRequest.getLanguages());
        if (updateRequest.getExperienceYears() != null) userProfile.setExperienceYears(updateRequest.getExperienceYears());
        // NOTE: verificationStatus cannot be modified directly via user profile update
        if (updateRequest.getClientType() != null) userProfile.setClientType(updateRequest.getClientType());
        if (updateRequest.getCompanyName() != null) userProfile.setCompanyName(updateRequest.getCompanyName());

        // Update user in database
        user.setUserProfile(userProfile);
        user = userRepository.save(user);
        return userProfileMapper.mapTo(user);
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority()) || "ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
