package com.gymadmin.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PermisoRolResponse(
        Integer id,
        @JsonProperty("nombre_sucursal") String nombreSucursal,
        String nombre,
        String descripcion,
        String modulo
) {}
