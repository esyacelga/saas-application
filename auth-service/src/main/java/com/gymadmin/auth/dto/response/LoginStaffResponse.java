package com.gymadmin.auth.dto.response;

public record LoginStaffResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        boolean requiereCambioPwd,
        UsuarioStaffInfo usuario
) {
    public record UsuarioStaffInfo(Integer id, String nombre, String correo, Integer idRol, String nombreRol) {}
}
