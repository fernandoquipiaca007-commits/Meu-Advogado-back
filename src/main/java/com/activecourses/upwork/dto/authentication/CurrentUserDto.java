package com.activecourses.upwork.dto.authentication;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserDto {
    private Integer id;
    private String firstName;
    private String lastName;
    private String email;
    private List<String> roles;
    private boolean accountEnabled;
    private boolean accountLocked;
}
