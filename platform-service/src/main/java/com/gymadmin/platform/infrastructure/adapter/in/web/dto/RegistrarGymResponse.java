package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

public record RegistrarGymResponse(
        Long idCompania,
        Long idCompaniaPlan,
        Long idSucursal,
        String qrToken
) {}
