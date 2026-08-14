package com.activecourses.upwork.exception;

import com.activecourses.upwork.dto.ResponseDto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.ArrayList;
import java.util.List;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ContentModerationException.class)
    public ResponseEntity<ResponseDto> handleContentModerationException(ContentModerationException ex) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ResponseDto
                        .builder()
                        .status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .success(false)
                        .error(ex.getViolations() != null && !ex.getViolations().isEmpty() ? ex.getViolations() : ex.getMessage())
                        .build()
                );
    }

    @ExceptionHandler(DuplicateProposalException.class)
    public ResponseEntity<ResponseDto> handleDuplicateProposalException(DuplicateProposalException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ResponseDto
                        .builder()
                        .status(HttpStatus.CONFLICT)
                        .success(false)
                        .error(ex.getMessage())
                        .build()
                );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ResponseDto> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ResponseDto
                        .builder()
                        .status(HttpStatus.CONFLICT)
                        .success(false)
                        .error("Violação de integridade de dados: registro duplicado ou restrição violada.")
                        .build()
                );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationExceptions(MethodArgumentNotValidException ex) {
        List<String> errors = new ArrayList<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String errorMessage = error.getDefaultMessage();
            errors.add(errorMessage);
        });
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ResponseDto
                        .builder()
                        .status(HttpStatus.BAD_REQUEST)
                        .success(false)
                        .error(errors)
                        .build()
                );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ResponseDto> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ResponseDto
                        .builder()
                        .status(HttpStatus.UNAUTHORIZED)
                        .success(false)
                        .error(ex.getMessage())
                        .build()
                );
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ResponseDto> handleEmailAlreadyExistsException(EmailAlreadyExistsException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ResponseDto
                        .builder()
                        .status(HttpStatus.CONFLICT)
                        .success(false)
                        .error(ex.getMessage() != null ? ex.getMessage() : "Este e-mail já está cadastrado. Acesse a aba Entrar para fazer login.")
                        .build()
                );
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ResponseDto> handleBadCredentialsException(BadCredentialsException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ResponseDto
                        .builder()
                        .status(HttpStatus.UNAUTHORIZED)
                        .success(false)
                        .error("E-mail ou senha incorretos.")
                        .build()
                );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResponseDto> handleAccessDeniedException(AccessDeniedException ex) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ResponseDto
                            .builder()
                            .status(HttpStatus.UNAUTHORIZED)
                            .success(false)
                            .error("Não autenticado: " + ex.getMessage())
                            .build()
                    );
        }
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ResponseDto
                        .builder()
                        .status(HttpStatus.FORBIDDEN)
                        .success(false)
                        .error("Access denied: " + ex.getMessage())
                        .build()
                );
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ResponseDto> handleSecurityException(SecurityException ex) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ResponseDto
                            .builder()
                            .status(HttpStatus.UNAUTHORIZED)
                            .success(false)
                            .error("Não autenticado: " + ex.getMessage())
                            .build()
                    );
        }
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ResponseDto
                        .builder()
                        .status(HttpStatus.FORBIDDEN)
                        .success(false)
                        .error("Access denied: " + ex.getMessage())
                        .build()
                );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResponseDto> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ResponseDto
                        .builder()
                        .status(HttpStatus.BAD_REQUEST)
                        .success(false)
                        .error(ex.getMessage())
                        .build()
                );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ResponseDto> handleIllegalStateException(IllegalStateException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ResponseDto
                        .builder()
                        .status(HttpStatus.BAD_REQUEST)
                        .success(false)
                        .error(ex.getMessage())
                        .build()
                );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseDto> handleGenericException(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResponseDto
                        .builder()
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .success(false)
                        .error(ex.getMessage() != null ? ex.getMessage() : "Ocorreu um erro interno no servidor.")
                        .build()
                );
    }
}
