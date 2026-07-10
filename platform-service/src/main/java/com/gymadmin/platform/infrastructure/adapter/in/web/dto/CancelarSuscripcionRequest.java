package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

/**
 * REQ-SAAS-001 (RN-09): body para {@code POST /companias/{id}/suscripcion/cancelar}.
 * El motivo es opcional (owner puede omitir).
 */
public record CancelarSuscripcionRequest(String motivo) {}
