package com.gymadmin.core.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body de {@code POST /api/v1/clientes/me/membresias/solicitar}. Cliente PWA elige
 * un tipo del catálogo y envía la solicitud; el resto de campos (precio, fecha inicio,
 * método de pago) los completa el staff al confirmar.
 */
public record SolicitarMembresiaRequest(
        @NotNull Long idTipoMembresia
) {}
