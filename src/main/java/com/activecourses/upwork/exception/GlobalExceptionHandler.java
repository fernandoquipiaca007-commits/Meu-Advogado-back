package com.activecourses.upwork.exception;

import com.activecourses.upwork.dto.ResponseDto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.ArrayList;
import java.util.List;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return new ResponseEntity<>("Email already exists", HttpStatus.CONFLICT);
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

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ResponseDto> handleAccessDeniedException(org.springframework.security.access.AccessDeniedException ex) {
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
