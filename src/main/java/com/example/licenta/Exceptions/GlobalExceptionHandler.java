package com.example.licenta.Exceptions;

import com.example.licenta.DTOs.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final String VALIDATION_FAILED = "Validation failed";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        List<String> errors = new ArrayList<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.add(error.getDefaultMessage())
        );

        ex.getBindingResult().getGlobalErrors().forEach(error ->
                errors.add(error.getDefaultMessage())
        );

        ApiResponse<Void> response = new ApiResponse<>(
                false,
                HttpStatus.BAD_REQUEST.value(),
                VALIDATION_FAILED,
                errors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> errors = new ArrayList<>();
        ex.getConstraintViolations().forEach(violation ->
                errors.add(violation.getMessage()));

        ApiResponse<Void> response = new ApiResponse<>(
                false,
                HttpStatus.BAD_REQUEST.value(),
                VALIDATION_FAILED,
                errors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParams(MissingServletRequestParameterException ex) {
        List<String> errors = Collections.singletonList(
                ex.getParameterName() + " parameter is required");

        ApiResponse<Void> response = new ApiResponse<>(
                false,
                HttpStatus.BAD_REQUEST.value(),
                VALIDATION_FAILED,
                errors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        List<String> errors = Collections.singletonList(
                ex.getName() + " should be of type " + ex.getRequiredType().getSimpleName());

        ApiResponse<Void> response = new ApiResponse<>(
                false,
                HttpStatus.BAD_REQUEST.value(),
                VALIDATION_FAILED,
                errors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(UserExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserExistsException(UserExistsException ex) {
        List<String> errors = Collections.singletonList(ex.getMessage());

        ApiResponse<Void> response = new ApiResponse<>(
                false,
                HttpStatus.CONFLICT.value(),
                VALIDATION_FAILED,
                errors
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceAlreadyExistsException(ResourceAlreadyExistsException ex) {
        List<String> errors = Collections.singletonList(ex.getMessage());

        ApiResponse<Void> response = new ApiResponse<>(
                false,
                HttpStatus.CONFLICT.value(),
                VALIDATION_FAILED,
                errors
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex,
                                                                             HttpServletRequest request) {
        List<String> errors = Collections.singletonList(ex.getMessage());
        HttpStatus status = HttpStatus.NOT_FOUND;

        ApiResponse<Void> response = new ApiResponse<>(
                false,
                status.value(),
                VALIDATION_FAILED,
                errors
        );

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentialsException(InvalidCredentialsException ex) {
        List<String> errors = Collections.singletonList(ex.getMessage());

        ApiResponse<Void> response = new ApiResponse<>(
                false,
                HttpStatus.BAD_REQUEST.value(),
                VALIDATION_FAILED,
                errors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        List<String> errors = Collections.singletonList(ex.getMessage());

        ApiResponse<Void> response = new ApiResponse<>(
                false,
                HttpStatus.UNAUTHORIZED.value(),
                VALIDATION_FAILED,
                errors
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(InvalidDataException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidDataException(InvalidDataException ex,
                                                                        HttpServletRequest request) {
        List<String> errors = Collections.singletonList(ex.getMessage());
        HttpStatus status = HttpStatus.UNPROCESSABLE_ENTITY;

        ApiResponse<Void> response = new ApiResponse<>(
                false,
                status.value(),
                VALIDATION_FAILED,
                errors
        );

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        List<String> errors = Collections.singletonList(ex.getMessage());

        ApiResponse<Void> response = new ApiResponse<>(
                false,
                HttpStatus.BAD_REQUEST.value(),
                VALIDATION_FAILED,
                errors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(FileProcessingException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileProcessingException(FileProcessingException ex) {
        List<String> errors = Collections.singletonList(ex.getMessage());

        ApiResponse<Void> response = new ApiResponse<>(
                false,
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                VALIDATION_FAILED,
                errors
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        String errorMessage = ex.getMessage();
        List<String> errors = new ArrayList<>();

        if (errorMessage.contains("Cannot construct instance of") && errorMessage.contains("problem:")) {
            // Extract just the problem message
            int problemIndex = errorMessage.indexOf("problem:");
            if (problemIndex >= 0) {
                String problem = errorMessage.substring(problemIndex + 8); // +8 for "problem: " length

                if (problem.endsWith("\n")) {
                    problem = problem.substring(0, problem.length() - 1);
                }
                if (problem.endsWith("]")) {
                    int lastOpenBracket = problem.lastIndexOf("[");
                    if (lastOpenBracket >= 0) {
                        problem = problem.substring(0, lastOpenBracket);
                    }
                }

                errors.add(problem.trim());
            } else {
                errors.add("Invalid value provided");
            }
        } else {
            // Extract a more user-friendly message for JSON parse errors
            if (errorMessage.startsWith("JSON parse error:")) {
                String simplifiedMessage = errorMessage.substring("JSON parse error:".length()).trim();
                // Further clean up the message if needed
                int colonIndex = simplifiedMessage.indexOf(":");
                if (colonIndex > 0) {
                    simplifiedMessage = simplifiedMessage.substring(0, colonIndex).trim();
                }
                errors.add(simplifiedMessage);
            } else {
                errors.add("Invalid request format");
            }
        }

        ApiResponse<Void> response = new ApiResponse<>(
                false,
                HttpStatus.BAD_REQUEST.value(),
                VALIDATION_FAILED,
                errors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllExceptions(Exception ex) {
        List<String> errors = Collections.singletonList(
                "An unexpected error occurred: " + ex.getMessage());

        ApiResponse<Void> response = new ApiResponse<>(
                false,
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Server error",
                errors
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}