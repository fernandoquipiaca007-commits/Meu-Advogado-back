package com.activecourses.upwork.service.user;

import com.activecourses.upwork.dto.user.UserProfileDto;
import com.activecourses.upwork.dto.user.UserResponseDto;
import com.activecourses.upwork.model.User;

import java.util.List;

public interface UserService {
    UserResponseDto getAllUsers(int pageNo, int pageSize, String sortBy, String sortDir);
    User findByEmail(String email);
    List<UserProfileDto> getAllLawyers();
}
