package com.gymadmin.auth.dto.request;

public record UpdateAppUsuarioRequest(
        String login,
        String password
) {}
