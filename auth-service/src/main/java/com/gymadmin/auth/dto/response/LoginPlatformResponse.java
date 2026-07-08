package com.gymadmin.auth.dto.response;

public record LoginPlatformResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UsuarioPlataformaInfo usuario
) {
    public record UsuarioPlataformaInfo(Integer id, String nombre, String rolPlataforma) {}
}
