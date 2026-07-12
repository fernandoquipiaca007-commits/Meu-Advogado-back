package com.activecourses.upwork.dto.user;

import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {
    @Id
    private Integer id;
    
    @NotBlank(message = "First Name is required")
    private String firstName;
    
    @NotBlank(message = "Last Name is required")
    private String lastName;

    private String title;
    private String description;
    private BigDecimal hourlyRate;
    private String location;
    
    // Legal fields
    private String oabNumber;
    private String oabState;
    private String country;
    private String phone;
    private String photoUrl;
    private LocalDate dateOfBirth;
    private String languages;
    private Integer experienceYears;
    private String verificationStatus;
    private String clientType;
    private String companyName;
}
