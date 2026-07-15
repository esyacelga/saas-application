package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

/**
 * Respuesta de la verificación pública de correo del registro.
 * Deliberadamente mínima (solo booleanos) para no filtrar datos del usuario existente.
 */
public record CorreoDisponibleResponse(boolean disponible, boolean existe) {
}
