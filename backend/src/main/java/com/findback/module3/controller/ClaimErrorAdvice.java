package com.findback.module3.controller;

import edu.findback.common.ApiError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Error mapping for the claims API only. Unlike the global handler, it keeps the service's
 * explanation (e.g. why an approval was blocked) so staff and claimants can act on it, and it turns
 * lost concurrency races into 409 responses instead of 500s (T10).
 */
@RestControllerAdvice(assignableTypes = ClaimCustodyController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ClaimErrorAdvice {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String reason = exception.getReason();
        String message = reason == null || reason.isBlank() ? status.getReasonPhrase() : reason;
        return ResponseEntity.status(status).body(new ApiError(status.value(), message));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class,
            DataIntegrityViolationException.class})
    public ResponseEntity<ApiError> handleConcurrentChange(Exception exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(HttpStatus.CONFLICT.value(),
                "This case was changed by another action at the same time. Reload and try again."));
    }
}
