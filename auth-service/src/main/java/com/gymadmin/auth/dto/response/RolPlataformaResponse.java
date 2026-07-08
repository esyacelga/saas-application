package com.gymadmin.auth.dto.response;

public record RolPlataformaResponse(
        Integer id,
        String nombre,
        String descripcion,
        Integer idCompania,
        String nombreCompania,
        Long totalUsuarios
) {}
