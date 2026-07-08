package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import java.time.LocalDateTime;

public record QrRenovarResponse(
        String qrToken,
        LocalDateTime qrTokenExpira
) {}
