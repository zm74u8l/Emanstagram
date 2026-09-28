package com.emanstagram.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Turns every failure into one predictable JSON shape so the frontend can
 * rely on {@code { error: { code, message, fieldErrors } }} everywhere.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Wraps a single error payload. */
    public record ApiErrorBody(ErrorDetail error) {
    }

    public record ErrorDetail(
            String code,
            String message,
            Map<String, String> fieldErrors,
            String path,
            String traceId,
            Instant timestamp
    ) {
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorBody> handleApi(ApiException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), null, request);
    }

    /** Bean-validation failures on @RequestBody payloads. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorBody> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage())
        );
        ex.getBindingResult().getGlobalErrors().forEach(ge ->
                fieldErrors.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage())
        );

        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "One or more fields are invalid.", fieldErrors, request);
    }

    /**
     * Gate #2 of the upload size limits. Spring rejects oversize bodies before
     * the controller ever sees them, so this must be translated explicitly or
     * the client gets an opaque 500.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorBody> handleTooLarge(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {

        log.info("Upload rejected - body exceeded multipart limit on {}", request.getRequestURI());
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                "That file is too large. Please use a smaller file or compress it first.",
                null, request);
    }

    /**
     * Malformed input that never reached a controller: a non-UUID path id,
     * a missing multipart part, unparseable JSON. These are client mistakes,
     * so they get a 400 rather than falling through to the 500 handler.
     */
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            HttpMessageNotReadableException.class,
            MultipartException.class
    })
    public ResponseEntity<ApiErrorBody> handleBadInput(Exception ex, HttpServletRequest request) {
        String message = switch (ex) {
            case MissingServletRequestParameterException m -> "Missing required field: " + m.getParameterName() + ".";
            case MissingServletRequestPartException m -> "Missing required field: " + m.getRequestPartName() + ".";
            case MethodArgumentTypeMismatchException m -> "Invalid value for " + m.getName() + ".";
            default -> "The request could not be read.";
        };
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, null, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorBody> handleNoRoute(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "There's nothing here.", null, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorBody> handleMethod(HttpRequestMethodNotSupportedException ex,
                                                     HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "That action isn't supported here.", null, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorBody> handleDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You do not have permission to do that.", null, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorBody> handleAuth(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                "You need to sign in to do that.", null, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorBody> handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("Unhandled exception [traceId={}] on {} {}",
                traceId, request.getMethod(), request.getRequestURI(), ex);

        // Never leak internals to the client; the traceId ties logs to the response.
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong on our end. Please try again.", null, request, traceId);
    }

    private ResponseEntity<ApiErrorBody> build(HttpStatus status, String code, String message,
                                               Map<String, String> fieldErrors,
                                               HttpServletRequest request) {
        return build(status, code, message, fieldErrors, request, UUID.randomUUID().toString());
    }

    private ResponseEntity<ApiErrorBody> build(HttpStatus status, String code, String message,
                                               Map<String, String> fieldErrors,
                                               HttpServletRequest request, String traceId) {
        ErrorDetail detail = new ErrorDetail(
                code, message, fieldErrors, request.getRequestURI(), traceId, Instant.now());
        return ResponseEntity.status(status).body(new ApiErrorBody(detail));
    }
}
