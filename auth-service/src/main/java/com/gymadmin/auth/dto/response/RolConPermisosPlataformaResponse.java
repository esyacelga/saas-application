package com.gymadmin.auth.dto.response;

import java.util.List;

public record RolConPermisosPlataformaResponse(
        RolPlataformaResponse rol,
        List<PermisoResponse> permisos
) {}
