package com.gymadmin.auth.dto.response;

public record TokenRefreshResponse(String accessToken, long expiresIn) {}
