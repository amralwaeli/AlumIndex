package com.alumindex.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    record ErrorBody(String error, String message, Instant timestamp) {}

    @ExceptionHandler(TenantAccessException.class)
    public ResponseEntity<ErrorBody> handle(TenantAccessException ex) {
        // 401 for bad credentials, 403 for inactive — both use TenantAccessException
        // Distinguish by message content so we don't leak role info
        boolean isUnauthorized = ex.getMessage().contains("Invalid email");
        var status = isUnauthorized ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status)
                .body(new ErrorBody(status.getReasonPhrase(), ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorBody> handle(BadRequestException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorBody("Bad Request", ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorBody> handle(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorBody("Not Found", ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorBody> handle(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorBody("Conflict", ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorBody> handle(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorBody("Forbidden", "Access denied", Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handle(MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, f ->
                        f.getDefaultMessage() != null ? f.getDefaultMessage() : "invalid"));
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Validation failed",
                "fields", errors,
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorBody> handle(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorBody("Bad Request", "File exceeds the 50 MB limit", Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handle(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorBody("Internal Server Error",
                        "Service temporarily unavailable", Instant.now()));
    }
}
