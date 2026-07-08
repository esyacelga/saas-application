package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.Email;

public record UpdateUsuarioStaffRequest(
        @Email String correo,
        Integer idRol
) {}
