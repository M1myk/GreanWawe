package com.greenwave.shared.api;

import java.util.List;

public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final List<ApiError.FieldError> fields;

    public ApiException(int status, String code, String message) {
        this(status, code, message, List.of());
    }
    public ApiException(int status, String code, String message, List<ApiError.FieldError> fields) {
        super(message); this.status = status; this.code = code; this.fields = fields;
    }
    public int status() { return status; }
    public String code() { return code; }
    public List<ApiError.FieldError> fields() { return fields; }
    public static ApiException invalid(String field, String message) {
        return new ApiException(422, "VALIDATION_FAILED", "Check the submitted values",
                List.of(new ApiError.FieldError(field, message)));
    }
}
