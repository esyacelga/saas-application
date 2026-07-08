package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

public record ActualizarCompaniaRequest(
        String nombre,
        String logoUrl,
        String telefono,
        String whatsapp,
        String correo
) {}
