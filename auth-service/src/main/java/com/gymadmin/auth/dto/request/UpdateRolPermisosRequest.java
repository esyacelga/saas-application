package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateRolPermisosRequest(
        @NotNull List<Integer> idPermisos
) {}
