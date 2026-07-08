package com.gymadmin.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PermisoPlataformaResponse(
        Integer id,
        String nombre,
        String modulo,
        String descripcion,
        @JsonProperty("id_compania") Integer idCompania,
        @JsonProperty("id_sucursal") Integer idSucursal,
        @JsonProperty("nombre_sucursal") String nombreSucursal
) {}
