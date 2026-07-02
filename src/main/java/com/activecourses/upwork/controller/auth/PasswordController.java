package com.activecourses.upwork.controller.auth;

import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.service.authentication.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Password", description = "Password API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class PasswordController {

    private final AuthService authService;

    @Operation(
            summary = "Forgot password",
            description = "Send a password reset link to the user's email"
    )
    @PostMapping("forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity
                    .badRequest()
                    .body(ResponseDto.builder()
                            .status(HttpStatus.BAD_REQUEST)
                            .success(false)
                            .error("Email is required")
                            .build());
        }

        authService.forgotPassword(email);

        // Always return success to avoid email enumeration
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ResponseDto.builder()
                        .status(HttpStatus.OK)
                        .success(true)
                        .data("If the email exists, a password reset link has been sent.")
                        .build());
    }

    @Operation(
            summary = "Reset password",
            description = "Reset password using the token received via email"
    )
    @PostMapping("reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        String newPassword = request.get("newPassword");

        if (token == null || token.isBlank()) {
            return ResponseEntity
                    .badRequest()
                    .body(ResponseDto.builder()
                            .status(HttpStatus.BAD_REQUEST)
                            .success(false)
                            .error("Token is required")
                            .build());
        }

        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity
                    .badRequest()
                    .body(ResponseDto.builder()
                            .status(HttpStatus.BAD_REQUEST)
                            .success(false)
                            .error("Password must be at least 6 characters")
                            .build());
        }

        boolean success = authService.resetPassword(token, newPassword);
        if (success) {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ResponseDto.builder()
                            .status(HttpStatus.OK)
                            .success(true)
                            .data("Password has been reset successfully.")
                            .build());
        } else {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ResponseDto.builder()
                            .status(HttpStatus.BAD_REQUEST)
                            .success(false)
                            .error("Invalid or expired reset token.")
                            .build());
        }
    }
}
