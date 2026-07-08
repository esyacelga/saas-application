package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.NotNull;

public record AsignarPermisoRolRequest(
        @NotNull Integer idPermiso
) {}
