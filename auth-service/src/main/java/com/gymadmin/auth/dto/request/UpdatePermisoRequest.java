package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.Size;

public record UpdatePermisoRequest(
        @Size(max = 100) String nombre,
        @Size(max = 50) String modulo,
        @Size(max = 255) String descripcion
) {}
