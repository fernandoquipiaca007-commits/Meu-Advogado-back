package com.activecourses.upwork.service.authentication;

import com.activecourses.upwork.config.security.CustomUserDetailsService;
import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.dto.authentication.login.LoginRequestDto;
import com.activecourses.upwork.dto.authentication.registration.RegistrationRequestDto;
import com.activecourses.upwork.dto.authentication.registration.RegistrationResponseDto;
import com.activecourses.upwork.dto.authentication.CurrentUserDto;
import com.activecourses.upwork.mapper.Mapper;
import com.activecourses.upwork.model.RefreshToken;
import com.activecourses.upwork.model.User;
import com.activecourses.upwork.model.UserProfile;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.repository.role.RoleRepository;
import com.activecourses.upwork.config.security.jwt.JwtService;

import com.activecourses.upwork.exception.EmailAlreadyExistsException;
import com.activecourses.upwork.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JavaMailSender mailSender;
    private final Mapper<User, RegistrationRequestDto> userMapper;
    private final CustomUserDetailsService customUserDetailsService;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);

    @Value("${BACKEND_URL:http://localhost:8080}")
    private String backendUrl;

    @Value("${FRONTEND_URL:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public CurrentUserDto getCurrentUserWithRoles() {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return null;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return CurrentUserDto.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .roles(user.getRoles().stream().map(r -> r.getName()).collect(Collectors.toList()))
                .accountEnabled(user.isAccountEnabled())
                .accountLocked(user.isAccountLocked())
                .build();
    }

    @Override
    public ResponseEntity<?> loginWithAudit(LoginRequestDto loginRequestDto, HttpServletRequest request) {
        String ipAddress = request.getRemoteAddr();
        try {
            ResponseDto responseDto = login(loginRequestDto);
            if (responseDto.isSuccess()) {
                User user = findByEmail(loginRequestDto.getEmail());
                auditService.logLogin(user.getId(), ipAddress);
            } else {
                auditService.logLoginFailed(loginRequestDto.getEmail(), ipAddress);
            }
            // Rebuild response with cookies
            Map<String, ResponseCookie> cookies = (Map<String, ResponseCookie>) responseDto.getData();
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookies.get("jwtCookie").toString())
                    .header(HttpHeaders.SET_COOKIE, cookies.get("refreshJwtCookie").toString())
                    .body(responseDto);
        } catch (Exception e) {
            auditService.logLoginFailed(loginRequestDto.getEmail(), ipAddress);
            throw e;
        }
    }

    @Override
    public ResponseEntity<?> registerWithAudit(RegistrationRequestDto registrationRequestDto, HttpServletRequest request) {
        RegistrationResponseDto regResponse = registerUser(registrationRequestDto);
        try {
            User user = findByEmail(registrationRequestDto.getEmail());
            auditService.logRegister(user.getId(), request.getRemoteAddr());
        } catch (Exception e) {
            logger.warn("Could not log register audit: {}", e.getMessage());
        }
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ResponseDto
                        .builder()
                        .status(HttpStatus.OK)
                        .success(true)
                        .data(regResponse)
                        .build());
    }

    @Override
    public RegistrationResponseDto registerUser(RegistrationRequestDto registrationRequestDto) {
        logger.info("Registering user with email: {}", registrationRequestDto.getEmail());

        // Check for duplicate email before saving
        if (userRepository.findByEmail(registrationRequestDto.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException("Email already exists: " + registrationRequestDto.getEmail());
        }

        User user = userMapper.mapFrom(registrationRequestDto);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        UserProfile profile = new UserProfile();
        profile.setCountry("BR");
        profile.setUser(user);
        user.setUserProfile(profile);
        userRepository.save(user);

        return RegistrationResponseDto
                .builder()
                .message("User registered successfully, please verify your email")
                .build();
    }

    @Transactional
    @Override
    public ResponseDto login(LoginRequestDto loginRequestDto) {
        logger.info("User login attempt with email: {}", loginRequestDto.getEmail());
        User user = findByEmail(loginRequestDto.getEmail());

        if (!user.isAccountEnabled()) {
            logger.warn("Account is disabled for user: {}", loginRequestDto.getEmail());
            return ResponseDto
                    .builder()
                    .status(HttpStatus.FORBIDDEN)
                    .success(false)
                    .error("Account is disabled.")
                    .build();
        }

        if (user.isAccountLocked()) {
            logger.warn("Account is locked for user: {}", loginRequestDto.getEmail());
            return ResponseDto
                    .builder()
                    .status(HttpStatus.LOCKED)
                    .success(false)
                    .error("Account is locked due to multiple failed login attempts.")
                    .build();
        }

        Authentication authentication = authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(
                        loginRequestDto.getEmail(),
                        loginRequestDto.getPassword()
                ));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetails userDetails = customUserDetailsService
                .loadUserByUsername(loginRequestDto.getEmail());

        ResponseCookie jwtCookie = jwtService.generateJwtCookie(userDetails);

        int userId = ((User) userDetails).getId();

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userId);

        ResponseCookie refreshJwtCookie = jwtService
                .generateRefreshJwtCookie(refreshToken.getToken());

        return ResponseDto
                .builder()
                .status(HttpStatus.OK)
                .success(true)
                .data(Map.of("jwtCookie", jwtCookie, "refreshJwtCookie", refreshJwtCookie))
                .build();
    }

    @Override
    public ResponseEntity<ResponseDto> logout() {
        logger.info("User logout attempt");
        Object principal = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        if (!principal.toString().equals("anonymousUser")) {
            int userId = ((User) principal).getId();
            refreshTokenService.deleteByUserId(userId);
        }

        ResponseCookie jwtCookie = jwtService.getCleanJwtCookie();
        ResponseCookie refreshJwtCookie = jwtService.getCleanJwtRefreshCookie();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshJwtCookie.toString())
                .body(ResponseDto
                        .builder()
                        .status(HttpStatus.OK)
                        .success(true)
                        .data("User logged out successfully!")
                        .build()
                );
    }

    @Override
    public Optional<User> refreshToken(String refreshToken) {
        logger.info("Refreshing token");
        String username = jwtService.getUserNameFromJwtToken(refreshToken);
        return userRepository.findByEmail(username);
    }

    @Override
    public boolean verifyUser(String token) {
        logger.info("Verifying user with token: {}", token);
        Optional<User> user = userRepository.findByVerificationToken(token);
        User unwrappedUser = unwrapUser(user);
        unwrappedUser.setAccountEnabled(true);
        unwrappedUser.setVerificationToken(null); // Clear the token
        userRepository.save(unwrappedUser);
        return true;
    }

    @Override
    public User findByEmail(String email) {
        logger.info("Finding user by email: {}", email);
        Optional<User> user = userRepository.findByEmail(email);
        return unwrapUser(user);
    }

    @Override
    public void sendVerificationEmail(User user) {
        logger.info("Sending verification email to: {}", user.getEmail());
        String verificationLink = backendUrl + "/api/users/verify?token="
                                  + user.getVerificationToken();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("Email Verification");
        message.setText("Click the link to verify your email: " + verificationLink);
        mailSender.send(message);
    }

    @Override
    public boolean deactivateUser(int userId) {
        logger.info("Deactivating user with id: {}", userId);
        Optional<User> user = userRepository.findById(userId);
        User unwrappedUser = unwrapUser(user);
        unwrappedUser.setAccountEnabled(false);
        userRepository.save(unwrappedUser);
        return true;
    }

    @Override
    public boolean reactivateUser(int userId) {
        logger.info("Reactivating user with id: {}", userId);
        Optional<User> user = userRepository.findById(userId);
        User unwrappedUser = unwrapUser(user);
        unwrappedUser.setAccountEnabled(true);
        userRepository.save(unwrappedUser);
        return true;
    }

    @Override
    public void forgotPassword(String email) {
        logger.info("Processing forgot password for email: {}", email);
        try {
            User user = findByEmail(email);
            // Generate a reset token (UUID-based)
            String resetToken = java.util.UUID.randomUUID().toString();
            user.setVerificationToken(resetToken);
            userRepository.save(user);

            // Send reset email
            String resetLink = frontendUrl + "/reset-password/" + resetToken;

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(user.getEmail());
            message.setSubject("Password Reset Request");
            message.setText("Click the link to reset your password: " + resetLink
                    + "\n\nThis link will expire in 24 hours."
                    + "\n\nIf you did not request this, please ignore this email.");
            mailSender.send(message);
            logger.info("Password reset email sent to: {}", email);
        } catch (Exception e) {
            // Log but don't reveal if email exists (prevent enumeration)
            logger.warn("Forgot password processing failed: {}", e.getMessage());
        }
    }

    @Override
    public boolean resetPassword(String token, String newPassword) {
        logger.info("Resetting password with token");
        try {
            User user = unwrapUser(userRepository.findByVerificationToken(token));
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setVerificationToken(null); // Clear the token
            userRepository.save(user);
            logger.info("Password reset successfully");
            return true;
        } catch (Exception e) {
            logger.error("Password reset failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Integer getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = (User) userDetails;
            return user.getId();
        }
        return null;
    }

    static User unwrapUser(Optional<User> entity) {
        if (entity.isPresent()) {
            return entity.get();
        } else {
            throw new UnsupportedOperationException("Unimplemented method 'unwrapUser'");
        }
    }
}
