package com.gymadmin.core.domain.model;

import java.time.LocalDate;
import java.util.List;

/**
 * REQ-SAAS-001 (Fase 4, issue C3): resultado del endpoint interno "clientes por vencer".
 *
 * <p>{@code fechaCorte} es el "hoy" de negocio resuelto en la zona horaria de operación
 * (America/Guayaquil, issue C4) — <b>no</b> se delega al cliente para evitar desfases de día.
 */
public record ClientesPorVencerResult(
        Long companiaId,
        LocalDate fechaCorte,
        List<ClientePorVencer> clientes
) {}
