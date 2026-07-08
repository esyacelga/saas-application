package com.gymadmin.auth.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        int status,
        String error,
        String mensaje,
        List<String> errores,
        OffsetDateTime timestamp
) {
    public static ApiError of(int status, String error, String mensaje) {
        return new ApiError(status, error, mensaje, null, OffsetDateTime.now());
    }

    public static ApiError ofValidation(int status, List<String> errores) {
        return new ApiError(status, "Validation Failed", "Datos de entrada inválidos", errores, OffsetDateTime.now());
    }
}
