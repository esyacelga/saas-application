package com.gymadmin.billing.application.service;

import com.gymadmin.billing.domain.model.AuditoriaEmision;
import com.gymadmin.billing.domain.port.in.AuditoriaUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditoriaService implements AuditoriaUseCase {

    private final DatabaseClient databaseClient;

    @Override
    public Flux<AuditoriaEmision> listarAuditoria(Integer idCompania, LocalDate desde, LocalDate hasta, String estado) {
        return databaseClient.sql("""
                SELECT c.id,
                       c.clave_acceso,
                       c.estado,
                       c.fecha_emision,
                       c.fecha_autorizacion,
                       c.numero_autorizacion,
                       cs.ambiente,
                       (SELECT COUNT(*) FROM facturacion.envios_sri e
                        WHERE e.id_comprobante = c.id) AS intentos_sri
                FROM facturacion.comprobantes c
                JOIN facturacion.config_sri cs ON cs.id_compania = c.id_compania AND cs.facturacion_activa = true
                WHERE c.id_compania = :idCompania
                  AND c.fecha_emision >= :desde
                  AND c.fecha_emision <= :hasta
                  AND (:estado IS NULL OR c.estado = :estado)
                ORDER BY c.fecha_emision DESC
                LIMIT 500
                """)
                .bind("idCompania", idCompania)
                .bind("desde", desde)
                .bind("hasta", hasta)
                .bind("estado", estado != null ? estado : null)
                .map(row -> new AuditoriaEmision(
                        row.get("id", Long.class),
                        row.get("clave_acceso", String.class),
                        row.get("estado", String.class),
                        toLocalDateTime(row.get("fecha_emision", Object.class)),
                        toLocalDateTime(row.get("fecha_autorizacion", Object.class)),
                        safeLong(row.get("intentos_sri", Long.class)).intValue(),
                        row.get("numero_autorizacion", String.class),
                        row.get("ambiente", String.class)
                ))
                .all();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof java.time.OffsetDateTime odt) return odt.toLocalDateTime();
        if (value instanceof java.time.LocalDate ld) return ld.atStartOfDay();
        return null;
    }

    private Long safeLong(Long value) {
        return value != null ? value : 0L;
    }
}
