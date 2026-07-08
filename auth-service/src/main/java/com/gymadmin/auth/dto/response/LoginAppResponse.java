package com.gymadmin.auth.dto.response;

public record LoginAppResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        PersonaInfo persona,
        CompaniaInfo compania
) {
    public record PersonaInfo(Integer id, String nombre, String fotoUrl, String sexo) {}
    public record CompaniaInfo(Integer id, String nombre, String logoUrl) {}
}
