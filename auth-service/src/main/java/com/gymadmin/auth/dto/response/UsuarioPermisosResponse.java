package com.gymadmin.auth.dto.response;

import java.util.List;

public record UsuarioPermisosResponse(
        UsuarioInfo usuario,
        RolResponse rol,
        List<String> permisos
) {
    public record UsuarioInfo(Integer id, String nombre) {}
}
