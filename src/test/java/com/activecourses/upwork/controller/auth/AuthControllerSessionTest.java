package com.activecourses.upwork.controller.auth;

import com.activecourses.upwork.config.FeatureFlags;
import com.activecourses.upwork.config.security.jwt.JwtService;
import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.dto.authentication.CurrentUserDto;
import com.activecourses.upwork.dto.authentication.login.LoginRequestDto;
import com.activecourses.upwork.dto.authentication.login.LoginResponseDto;
import com.activecourses.upwork.model.RefreshToken;
import com.activecourses.upwork.model.User;
import com.activecourses.upwork.model.UserProfile;
import com.activecourses.upwork.model.VerificationStatus;
import com.activecourses.upwork.repository.auth.RefreshTokenRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.service.RefreshTokenService;
import com.activecourses.upwork.service.authentication.AuditService;
import com.activecourses.upwork.service.authentication.AuthServiceImpl;
import com.activecourses.upwork.config.security.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthControllerSessionTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private AuditService auditService;

    @Mock
    private FeatureFlags featureFlags;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testEmailMasking() {
        assertEquals("a***d@example.com", AuthServiceImpl.maskEmail("ahmed@example.com"));
        assertEquals("j***n@legawork.com", AuthServiceImpl.maskEmail("john@legawork.com"));
        assertEquals("a***@b.com", AuthServiceImpl.maskEmail("ab@b.com"));
        assertEquals("***", AuthServiceImpl.maskEmail("invalid-email"));
        assertEquals("***", AuthServiceImpl.maskEmail(null));
    }

    @Test
    void testLogin_ReturnsTokensInBodyWhenCookieSessionDisabled() {
        when(featureFlags.isCookieSessionEnabled()).thenReturn(false);

        User user = User.builder().id(1).email("test@example.com").password("pass").accountEnabled(true).build();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(mock(Authentication.class));

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("test@example.com");
        when(customUserDetailsService.loadUserByUsername("test@example.com")).thenReturn(user);

        ResponseCookie jwtCookie = ResponseCookie.from("jwt_token", "jwt123").build();
        ResponseCookie refreshCookie = ResponseCookie.from("jwt_refresh_token", "ref123").build();
        when(jwtService.generateJwtCookie(any(UserDetails.class))).thenReturn(jwtCookie);
        when(jwtService.generateRefreshJwtCookie(anyString())).thenReturn(refreshCookie);
        when(jwtService.generateAccessToken(any(UserDetails.class))).thenReturn("jwt123");

        RefreshToken rt = new RefreshToken();
        rt.setToken("ref123");
        when(refreshTokenService.createRefreshToken(anyInt())).thenReturn(rt);

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");

        ResponseEntity<?> response = authService.loginWithAudit(
                new LoginRequestDto("test@example.com", "pass"), req
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getHeaders().get(HttpHeaders.SET_COOKIE));

        ResponseDto body = (ResponseDto) response.getBody();
        assertNotNull(body);
        assertTrue(body.isSuccess());
        assertTrue(body.getData() instanceof LoginResponseDto);
        LoginResponseDto loginResponse = (LoginResponseDto) body.getData();
        assertEquals("jwt123", loginResponse.getAccessToken());
        assertEquals("ref123", loginResponse.getRefreshToken());
    }

    @Test
    void testLogin_OmitsRawTokensInBodyWhenCookieSessionEnabled() {
        when(featureFlags.isCookieSessionEnabled()).thenReturn(true);

        User user = User.builder().id(1).email("test@example.com").password("pass").accountEnabled(true).build();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(mock(Authentication.class));

        when(customUserDetailsService.loadUserByUsername("test@example.com")).thenReturn(user);

        ResponseCookie jwtCookie = ResponseCookie.from("jwt_token", "jwt123").build();
        ResponseCookie refreshCookie = ResponseCookie.from("jwt_refresh_token", "ref123").build();
        when(jwtService.generateJwtCookie(any(UserDetails.class))).thenReturn(jwtCookie);
        when(jwtService.generateRefreshJwtCookie(anyString())).thenReturn(refreshCookie);
        when(jwtService.generateAccessToken(any(UserDetails.class))).thenReturn("jwt123");

        RefreshToken rt = new RefreshToken();
        rt.setToken("ref123");
        when(refreshTokenService.createRefreshToken(anyInt())).thenReturn(rt);

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");

        ResponseEntity<?> response = authService.loginWithAudit(
                new LoginRequestDto("test@example.com", "pass"), req
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getHeaders().get(HttpHeaders.SET_COOKIE));

        ResponseDto body = (ResponseDto) response.getBody();
        assertNotNull(body);
        assertTrue(body.isSuccess());
        assertFalse(body.getData() instanceof LoginResponseDto);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.getData();
        assertTrue(data.containsKey("message"));
        assertFalse(data.containsKey("accessToken"));
        assertFalse(data.containsKey("refreshToken"));
    }

    @Test
    void testLogout_RevokesRefreshTokenAndCleansCookies() {
        User user = User.builder().id(7).email("logout@example.com").build();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user, "pass", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        ResponseCookie cleanJwt = ResponseCookie.from("jwt_token", "").maxAge(0).build();
        ResponseCookie cleanRefresh = ResponseCookie.from("jwt_refresh_token", "").maxAge(0).build();
        when(jwtService.getCleanJwtCookie()).thenReturn(cleanJwt);
        when(jwtService.getCleanJwtRefreshCookie()).thenReturn(cleanRefresh);

        ResponseEntity<ResponseDto> response = authService.logout();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(refreshTokenRepository, times(1)).deleteByUserId(7);
        assertTrue(response.getHeaders().get(HttpHeaders.SET_COOKIE).get(0).contains("Max-Age=0"));
    }

    @Test
    void testGetCurrentUserWithRoles_IncludesVerificationStatusAndMfa() {
        User user = User.builder().id(3).email("current@example.com").firstName("Maria").lastName("Santos").roles(List.of()).build();
        UserProfile profile = UserProfile.builder()
                .verificationStatus(VerificationStatus.VERIFIED)
                .oabNumber("12345")
                .oabState("RJ")
                .mfaEnabled(true)
                .build();
        user.setUserProfile(profile);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user, "pass", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(userRepository.findById(3)).thenReturn(Optional.of(user));

        CurrentUserDto current = authService.getCurrentUserWithRoles();

        assertNotNull(current);
        assertEquals("VERIFIED", current.getVerificationStatus());
        assertEquals("12345", current.getOabNumber());
        assertEquals("RJ", current.getOabState());
        assertTrue(current.isMfaEnabled());
    }
}
