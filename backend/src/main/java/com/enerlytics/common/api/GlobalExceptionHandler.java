package com.enerlytics.common.api;

import com.enerlytics.identity.application.RefreshTokenService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String PROBLEM_BASE = "https://docs.enerlytics.example/problems/";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Problem> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.add(Map.of(
                "field", error.getField(),
                "code", error.getCode() != null ? error.getCode() : "INVALID",
                "message", error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value")));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(buildProblem("validation-error", "Request validation failed",
                        HttpStatus.BAD_REQUEST, "One or more fields are invalid.", request, errors));
    }

    @ExceptionHandler({BadCredentialsException.class, DisabledException.class, AuthenticationException.class,
            RefreshTokenService.InvalidRefreshTokenException.class})
    public ResponseEntity<Problem> handleAuthentication(RuntimeException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(buildProblem("authentication-failed", "Authentication required",
                        HttpStatus.UNAUTHORIZED, ex.getMessage(), request, null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Problem> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(buildProblem("access-denied", "Access denied",
                        HttpStatus.FORBIDDEN, ex.getMessage(), request, null));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Problem> handleNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(buildProblem("resource-not-found", "Resource not found",
                        HttpStatus.NOT_FOUND, ex.getMessage(), request, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Problem> handleGeneric(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(buildProblem("internal-error", "Internal server error",
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "An unexpected error occurred. Please try again later.", request, null));
    }

    private static Problem buildProblem(String type, String title, HttpStatus status, String detail,
                                        HttpServletRequest request, List<Map<String, String>> errors) {
        return new Problem(
                PROBLEM_BASE + type,
                title,
                status.value(),
                detail,
                request.getRequestURI(),
                type.toUpperCase().replace("-", "_"),
                UUID.randomUUID().toString(),
                errors
        );
    }
}
