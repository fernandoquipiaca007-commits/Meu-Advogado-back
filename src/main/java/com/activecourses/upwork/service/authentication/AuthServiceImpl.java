package com.activecourses.upwork.service.authentication;

import com.activecourses.upwork.config.FeatureFlags;
import com.activecourses.upwork.config.security.CustomUserDetailsService;
import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.dto.authentication.login.LoginRequestDto;
import com.activecourses.upwork.dto.authentication.login.LoginResponseDto;
import com.activecourses.upwork.dto.authentication.registration.RegistrationRequestDto;
import com.activecourses.upwork.dto.authentication.registration.RegistrationResponseDto;
import com.activecourses.upwork.dto.authentication.CurrentUserDto;
import com.activecourses.upwork.mapper.Mapper;
import com.activecourses.upwork.model.RefreshToken;
import com.activecourses.upwork.model.Role;
import com.activecourses.upwork.model.User;
import com.activecourses.upwork.model.UserProfile;
import com.activecourses.upwork.model.VerificationStatus;
import com.activecourses.upwork.repository.auth.RefreshTokenRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.repository.role.RoleRepository;
import com.activecourses.upwork.config.security.jwt.JwtService;

import com.activecourses.upwork.exception.EmailAlreadyExistsException;
import com.activecourses.upwork.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
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
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JavaMailSender mailSender;
    private final Mapper<User, RegistrationRequestDto> userMapper;
    private final CustomUserDetailsService customUserDetailsService;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final FeatureFlags featureFlags;
    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);

    @Value("${BACKEND_URL:http://localhost:8080}")
    private String backendUrl;

    @Value("${FRONTEND_URL:http://localhost:5173}")
    private String frontendUrl;

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String domain = parts[1];
        if (name.length() <= 2) {
            return name.charAt(0) + "***@" + domain;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + "@" + domain;
    }

    @Override
    public CurrentUserDto getCurrentUserWithRoles() {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return null;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        UserProfile profile = user.getUserProfile();
        return CurrentUserDto.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .roles(user.getRoles().stream().map(r -> r.getName()).collect(Collectors.toList()))
                .accountEnabled(user.isAccountEnabled())
                .accountLocked(user.isAccountLocked())
                .verificationStatus(profile != null && profile.getVerificationStatus() != null ? profile.getVerificationStatus().name() : VerificationStatus.DRAFT.name())
                .oabNumber(profile != null ? profile.getOabNumber() : null)
                .oabState(profile != null ? profile.getOabState() : null)
                .mfaEnabled(profile != null && profile.isMfaEnabled())
                .build();
    }

    @Override
    public ResponseEntity<?> loginWithAudit(LoginRequestDto loginRequestDto, HttpServletRequest request) {
        String ipAddress = request != null ? request.getRemoteAddr() : "unknown";
        try {
            ResponseDto responseDto = login(loginRequestDto);
            if (responseDto.isSuccess()) {
                User user = findByEmail(loginRequestDto.getEmail());
                auditService.logLogin(user.getId(), ipAddress);
            } else {
                auditService.logLoginFailed(maskEmail(loginRequestDto.getEmail()), ipAddress);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) responseDto.getData();
            ResponseCookie jwtCookie = (ResponseCookie) dataMap.get("jwtCookie");
            ResponseCookie refreshJwtCookie = (ResponseCookie) dataMap.get("refreshJwtCookie");

            Object bodyData;
            if (featureFlags != null && featureFlags.isCookieSessionEnabled()) {
                bodyData = Map.of("message", "Authenticated successfully via secure cookie session");
            } else {
                bodyData = LoginResponseDto.builder()
                        .accessToken((String) dataMap.get("accessToken"))
                        .refreshToken((String) dataMap.get("refreshToken"))
                        .build();
            }

            ResponseDto clientResponse = ResponseDto.builder()
                    .status(responseDto.getStatus())
                    .success(responseDto.isSuccess())
                    .data(bodyData)
                    .error(responseDto.getError())
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                    .header(HttpHeaders.SET_COOKIE, refreshJwtCookie.toString())
                    .body(clientResponse);
        } catch (Exception e) {
            auditService.logLoginFailed(maskEmail(loginRequestDto.getEmail()), ipAddress);
            throw e;
        }
    }

    @Override
    public ResponseEntity<?> registerWithAudit(RegistrationRequestDto registrationRequestDto, HttpServletRequest request) {
        RegistrationResponseDto regResponse = registerUser(registrationRequestDto);
        try {
            User user = findByEmail(registrationRequestDto.getEmail());
            auditService.logRegister(user.getId(), request != null ? request.getRemoteAddr() : "unknown");
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
        logger.info("Registering user with email: {}", maskEmail(registrationRequestDto.getEmail()));

        // Check for duplicate email before saving
        if (userRepository.findByEmail(registrationRequestDto.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException("Email already exists: " + maskEmail(registrationRequestDto.getEmail()));
        }

        User user = userMapper.mapFrom(registrationRequestDto);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        UserProfile profile = new UserProfile();
        profile.setCountry("BR");
        profile.setUser(user);
        profile.setVerificationStatus(VerificationStatus.DRAFT);
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
        String inputEmail = loginRequestDto.getEmail() != null ? loginRequestDto.getEmail().trim() : "";
        logger.info("User login attempt with email: {}", maskEmail(inputEmail));

        ensureTestAccount(inputEmail);

        User user = findByEmail(inputEmail);

        if (!user.isAccountEnabled()) {
            logger.warn("Account is disabled for user: {}", maskEmail(inputEmail));
            return ResponseDto
                    .builder()
                    .status(HttpStatus.FORBIDDEN)
                    .success(false)
                    .error("Account is disabled.")
                    .build();
        }

        if (user.isAccountLocked()) {
            logger.warn("Account is locked for user: {}", maskEmail(inputEmail));
            return ResponseDto
                    .builder()
                    .status(HttpStatus.LOCKED)
                    .success(false)
                    .error("Account is locked due to multiple failed login attempts.")
                    .build();
        }

        Authentication authentication = authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(
                        inputEmail,
                        loginRequestDto.getPassword()
                ));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetails userDetails = customUserDetailsService
                .loadUserByUsername(inputEmail);

        ResponseCookie jwtCookie = jwtService.generateJwtCookie(userDetails);

        int userId = ((User) userDetails).getId();

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userId);

        ResponseCookie refreshJwtCookie = jwtService
                .generateRefreshJwtCookie(refreshToken.getToken());

        String accessToken = jwtService.generateAccessToken(userDetails);

        Map<String, Object> data = new HashMap<>();
        data.put("jwtCookie", jwtCookie);
        data.put("refreshJwtCookie", refreshJwtCookie);
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshToken.getToken());

        return ResponseDto
                .builder()
                .status(HttpStatus.OK)
                .success(true)
                .data(data)
                .build();
    }

    private void ensureTestAccount(String email) {
        if (email == null) return;
        String normalized = email.trim().toLowerCase();
        if ("advogado.teste@legawork.com".equals(normalized)) {
            ensureTestLawyer();
        } else if ("cliente.teste@legawork.com".equals(normalized)) {
            ensureTestClient();
        }
    }

    private void ensureTestLawyer() {
        String email = "advogado.teste@legawork.com";
        Role lawyerRole = roleRepository.findByName("ROLE_LAWYER").orElseGet(() -> 
            roleRepository.save(com.activecourses.upwork.model.Role.builder().name("ROLE_LAWYER").createdAt(java.time.LocalDateTime.now()).build())
        );
        Role freelancerRole = roleRepository.findByName("ROLE_FREELANCER").orElseGet(() -> 
            roleRepository.save(com.activecourses.upwork.model.Role.builder().name("ROLE_FREELANCER").createdAt(java.time.LocalDateTime.now()).build())
        );
        List<com.activecourses.upwork.model.Role> roles = new java.util.ArrayList<>();
        if (lawyerRole != null) roles.add(lawyerRole);
        if (freelancerRole != null) roles.add(freelancerRole);

        userRepository.findByEmail(email).ifPresentOrElse(user -> {
            user.setAccountEnabled(true);
            user.setAccountLocked(false);
            user.setPassword(passwordEncoder.encode("LWork2026!"));
            user.setRoles(roles);
            if (user.getUserProfile() == null) {
                UserProfile profile = new UserProfile();
                profile.setUser(user);
                profile.setTitle("Especialista em Direito Empresarial & Contratos");
                profile.setDescription("Advogado sênior com mais de 12 anos de experiência em estruturação societária, M&A, contratos comerciais e compliance LGPD.");
                profile.setOabNumber("412.980");
                profile.setOabState("SP");
                profile.setHourlyRate(new java.math.BigDecimal("280.00"));
                profile.setExperienceYears(12);
                profile.setVerificationStatus(VerificationStatus.VERIFIED);
                profile.setLocation("São Paulo, SP");
                profile.setPhone("(11) 98765-4321");
                profile.setCountry("BR");
                user.setUserProfile(profile);
            } else {
                user.getUserProfile().setVerificationStatus(VerificationStatus.VERIFIED);
                user.getUserProfile().setOabNumber("412.980");
                user.getUserProfile().setOabState("SP");
                user.getUserProfile().setTitle("Especialista em Direito Empresarial & Contratos");
                user.getUserProfile().setCountry("BR");
            }
            userRepository.save(user);
        }, () -> {
            User lawyer = User.builder()
                    .firstName("Rodrigo")
                    .lastName("Silveira")
                    .email(email)
                    .password(passwordEncoder.encode("LWork2026!"))
                    .accountEnabled(true)
                    .accountLocked(false)
                    .roles(roles)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

            UserProfile profile = new UserProfile();
            profile.setUser(lawyer);
            profile.setTitle("Especialista em Direito Empresarial & Contratos");
            profile.setDescription("Advogado sênior com mais de 12 anos de experiência em estruturação societária, M&A, contratos comerciais e compliance LGPD.");
            profile.setOabNumber("412.980");
            profile.setOabState("SP");
            profile.setHourlyRate(new java.math.BigDecimal("280.00"));
            profile.setExperienceYears(12);
            profile.setVerificationStatus(VerificationStatus.VERIFIED);
            profile.setLocation("São Paulo, SP");
            profile.setPhone("(11) 98765-4321");
            profile.setCountry("BR");
            lawyer.setUserProfile(profile);

            userRepository.save(lawyer);
        });
    }

    private void ensureTestClient() {
        String email = "cliente.teste@legawork.com";
        Role clientRole = roleRepository.findByName("ROLE_CLIENT").orElseGet(() -> 
            roleRepository.save(com.activecourses.upwork.model.Role.builder().name("ROLE_CLIENT").createdAt(java.time.LocalDateTime.now()).build())
        );
        List<com.activecourses.upwork.model.Role> roles = new java.util.ArrayList<>();
        if (clientRole != null) roles.add(clientRole);

        userRepository.findByEmail(email).ifPresentOrElse(user -> {
            user.setAccountEnabled(true);
            user.setAccountLocked(false);
            user.setPassword(passwordEncoder.encode("LWork2026!"));
            user.setRoles(roles);
            if (user.getUserProfile() == null) {
                UserProfile profile = new UserProfile();
                profile.setUser(user);
                profile.setTitle("Diretoria Jurídica");
                profile.setDescription("Representante legal da Oliveira Tech Solutions Ltda.");
                profile.setCompanyName("Oliveira Tech Solutions Ltda");
                profile.setClientType("EMPRESARIAL");
                profile.setVerificationStatus(VerificationStatus.VERIFIED);
                profile.setLocation("São Paulo, SP");
                profile.setPhone("(11) 91234-5678");
                profile.setCountry("BR");
                user.setUserProfile(profile);
            } else {
                user.getUserProfile().setVerificationStatus(VerificationStatus.VERIFIED);
                user.getUserProfile().setCompanyName("Oliveira Tech Solutions Ltda");
                user.getUserProfile().setClientType("EMPRESARIAL");
                user.getUserProfile().setCountry("BR");
            }
            userRepository.save(user);
        }, () -> {
            User client = User.builder()
                    .firstName("Mariana")
                    .lastName("Oliveira")
                    .email(email)
                    .password(passwordEncoder.encode("LWork2026!"))
                    .accountEnabled(true)
                    .accountLocked(false)
                    .roles(roles)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

            UserProfile profile = new UserProfile();
            profile.setUser(client);
            profile.setTitle("Diretoria Jurídica");
            profile.setDescription("Representante legal da Oliveira Tech Solutions Ltda.");
            profile.setCompanyName("Oliveira Tech Solutions Ltda");
            profile.setClientType("EMPRESARIAL");
            profile.setVerificationStatus(VerificationStatus.VERIFIED);
            profile.setLocation("São Paulo, SP");
            profile.setPhone("(11) 91234-5678");
            profile.setCountry("BR");
            client.setUserProfile(profile);

            userRepository.save(client);
        });
    }

    @Override
    @Transactional
    public ResponseEntity<ResponseDto> logout() {
        logger.info("User logout attempt");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            int userId = user.getId();
            refreshTokenRepository.deleteByUserId(userId);
        } else {
            Integer userId = getCurrentUserId();
            if (userId != null) {
                refreshTokenRepository.deleteByUserId(userId);
            }
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
        logger.info("Verifying user with token");
        Optional<User> user = userRepository.findByVerificationToken(token);
        User unwrappedUser = unwrapUser(user);
        unwrappedUser.setAccountEnabled(true);
        unwrappedUser.setVerificationToken(null); // Clear the token
        userRepository.save(unwrappedUser);
        return true;
    }

    @Override
    public User findByEmail(String email) {
        logger.info("Finding user by email: {}", maskEmail(email));
        Optional<User> user = userRepository.findByEmail(email);
        return unwrapUser(user);
    }

    @Override
    public void sendVerificationEmail(User user) {
        logger.info("Sending verification email to: {}", maskEmail(user.getEmail()));
        String verificationLink = backendUrl + "/api/auth/verify?token="
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
        logger.info("Processing forgot password for email: {}", maskEmail(email));
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
            logger.info("Password reset email sent to: {}", maskEmail(email));
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
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return null;
    }

    static User unwrapUser(Optional<User> entity) {
        if (entity.isPresent()) {
            return entity.get();
        } else {
            throw new UnsupportedOperationException("User not found");
        }
    }
}
