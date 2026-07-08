package com.gymadmin.auth.dto.response;

import java.util.List;

public record RolConPermisosResponse(
        RolResponse rol,
        List<PermisoResponse> permisos
) {}
