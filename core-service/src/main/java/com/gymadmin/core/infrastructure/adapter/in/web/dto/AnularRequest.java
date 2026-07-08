package com.gymadmin.core.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record AnularRequest(@NotBlank String motivo) {}
