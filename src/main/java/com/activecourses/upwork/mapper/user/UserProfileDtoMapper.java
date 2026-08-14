package com.activecourses.upwork.mapper.user;

import com.activecourses.upwork.dto.user.UserProfileDto;
import com.activecourses.upwork.mapper.Mapper;
import com.activecourses.upwork.model.User;
import com.activecourses.upwork.model.UserProfile;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class UserProfileDtoMapper implements Mapper<User, UserProfileDto> {
    @Override
    public UserProfileDto mapTo(User user) {
        UserProfile profile = user.getUserProfile();
        return UserProfileDto.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .title(profile != null ? profile.getTitle() : null)
                .description(profile != null ? profile.getDescription() : null)
                .hourlyRate(profile != null ? profile.getHourlyRate() : null)
                .location(profile != null ? profile.getLocation() : null)
                .oabNumber(profile != null ? profile.getOabNumber() : null)
                .oabState(profile != null ? profile.getOabState() : null)
                .country(profile != null ? profile.getCountry() : null)
                .phone(profile != null ? profile.getPhone() : null)
                .photoUrl(profile != null ? profile.getPhotoUrl() : null)
                .dateOfBirth(profile != null ? profile.getDateOfBirth() : null)
                .languages(profile != null ? profile.getLanguages() : null)
                .experienceYears(profile != null ? profile.getExperienceYears() : null)
                .verificationStatus(profile != null && profile.getVerificationStatus() != null ? profile.getVerificationStatus() : "unverified")
                .clientType(profile != null ? profile.getClientType() : null)
                .companyName(profile != null ? profile.getCompanyName() : null)
                .build();
    }

    @Override
    public User mapFrom(UserProfileDto userProfileDto) {
        return null;
    }

}
