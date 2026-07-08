package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

public record ActualizarMiEmpresaRequest(
        String nombre,
        String telefono,
        String whatsapp,
        String correo
) {}
