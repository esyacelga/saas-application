package com.gymadmin.attendance.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record RegistrarQrRequest(
        @JsonProperty("qr_token")
        @NotBlank(message = "qr_token es obligatorio")
        String qrToken
) {}
