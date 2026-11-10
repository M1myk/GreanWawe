package com.greenwave.shared.api;

import java.util.List;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException ex) {
        return ResponseEntity.status(ex.status()).body(new ApiError(ex.code(), ex.getMessage(), ex.fields()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        var fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldError(error.getField(), error.getDefaultMessage())).toList();
        return ResponseEntity.unprocessableContent().body(new ApiError("VALIDATION_FAILED", "Check the submitted values", fields));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> malformed(Exception ex) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "Invalid JSON or parameter format", List.of()));
    }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> conflict(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(409).body(new ApiError("VERSION_CONFLICT", "This trip was changed. Reload it before saving.", List.of()));
    }
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ResponseEntity<ApiError> missing(Exception ex) {
        return ResponseEntity.status(404).body(new ApiError("NOT_FOUND", "Resource not found", List.of()));
    }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(Exception ex) {
        return ResponseEntity.status(405).body(new ApiError("METHOD_NOT_ALLOWED", "HTTP method not allowed", List.of()));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex) {
        org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class).error("Unexpected API failure", ex);
        return ResponseEntity.internalServerError().body(new ApiError("INTERNAL_ERROR", "The request could not be completed", List.of()));
    }
}
