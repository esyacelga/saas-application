package com.gymadmin.platform.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.platform.domain.model.PagoPendienteValidacion;
import com.gymadmin.platform.domain.port.out.PagoPendienteValidacionRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.PagoPendienteValidacionEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.PagoPendienteValidacionR2dbcRepository;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Persistence adapter para {@code tenant.pagos_pendientes_validacion} (RN-08).
 * <p>
 * Convención de enum (decisión D4): en DB {@code estado} vive en <b>minúsculas</b>
 * ({@code pendiente / aprobado / rechazado}); en Java se representa como
 * {@link PagoPendienteValidacion.Estado} en MAYÚSCULAS. El mapeo se hace aquí.
 */
@Component
public class PagoPendienteValidacionPersistenceAdapter implements PagoPendienteValidacionRepository {

    private final PagoPendienteValidacionR2dbcRepository repository;
    private final DatabaseClient databaseClient;

    public PagoPendienteValidacionPersistenceAdapter(PagoPendienteValidacionR2dbcRepository repository,
                                                     DatabaseClient databaseClient) {
        this.repository = repository;
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<PagoPendienteValidacion> save(PagoPendienteValidacion pago) {
        return repository.save(toEntity(pago)).map(this::toDomain);
    }

    @Override
    public Mono<PagoPendienteValidacion> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Mono<PagoPendienteValidacion> findByHashIdempotencia(String hash) {
        return repository.findByHashIdempotencia(hash).map(this::toDomain);
    }

    @Override
    public Flux<PagoPendienteValidacion> listar(String estado, int offset, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM tenant.pagos_pendientes_validacion ");
        if (estado != null && !estado.isBlank()) {
            sql.append("WHERE estado = :estado ");
        }
        sql.append("ORDER BY fecha_reporte DESC LIMIT :limit OFFSET :offset");
        var spec = databaseClient.sql(sql.toString());
        if (estado != null && !estado.isBlank()) {
            spec = spec.bind("estado", estado.toLowerCase());
        }
        return spec
                .bind("limit", limit)
                .bind("offset", offset)
                .map((row, meta) -> mapRow(row))
                .all();
    }

    @Override
    public Mono<Long> contar(String estado) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) AS cnt FROM tenant.pagos_pendientes_validacion ");
        if (estado != null && !estado.isBlank()) {
            sql.append("WHERE estado = :estado");
        }
        var spec = databaseClient.sql(sql.toString());
        if (estado != null && !estado.isBlank()) {
            spec = spec.bind("estado", estado.toLowerCase());
        }
        return spec
                .map((row, meta) -> {
                    Number n = row.get("cnt", Number.class);
                    return n == null ? 0L : n.longValue();
                })
                .one()
                .defaultIfEmpty(0L);
    }

    private PagoPendienteValidacion mapRow(io.r2dbc.spi.Row row) {
        PagoPendienteValidacion p = new PagoPendienteValidacion();
        p.setId(row.get("id", Long.class));
        p.setIdCompania(row.get("id_compania", Long.class));
        p.setIdPlanDestino(row.get("id_plan_destino", Long.class));
        p.setMonto(row.get("monto", java.math.BigDecimal.class));
        p.setMoneda(row.get("moneda", String.class));
        OffsetDateTime fr = row.get("fecha_reporte", OffsetDateTime.class);
        if (fr != null) p.setFechaReporte(fr.toInstant());
        p.setFechaTransferencia(row.get("fecha_transferencia", java.time.LocalDate.class));
        p.setComprobanteUrl(row.get("comprobante_url", String.class));
        p.setComprobanteHash(row.get("comprobante_hash", String.class));
        p.setBancoOrigen(row.get("banco_origen", String.class));
        p.setReferencia(row.get("referencia", String.class));
        p.setHashIdempotencia(row.get("hash_idempotencia", String.class));
        String estado = row.get("estado", String.class);
        if (estado != null) {
            p.setEstado(PagoPendienteValidacion.Estado.valueOf(estado.toUpperCase()));
        }
        p.setMotivoRechazo(row.get("motivo_rechazo", String.class));
        p.setAprobadoPor(row.get("aprobado_por", Long.class));
        OffsetDateTime fa = row.get("fecha_aprobacion", OffsetDateTime.class);
        if (fa != null) p.setFechaAprobacion(fa.toInstant());
        Boolean ap = row.get("activacion_programada", Boolean.class);
        p.setActivacionProgramada(Boolean.TRUE.equals(ap));
        p.setFacturaEmitidaId(row.get("factura_emitida_id", Long.class));
        return p;
    }

    @Override
    public Mono<Long> marcarAprobado(Long idPago, Long idUsuarioRoot, Instant fechaAprobacion) {
        return databaseClient.sql(
                "UPDATE tenant.pagos_pendientes_validacion " +
                "SET estado = 'aprobado', " +
                "    aprobado_por = :idRoot, " +
                "    fecha_aprobacion = :fecha " +
                "WHERE id = :id AND estado = 'pendiente'")
                .bind("idRoot", idUsuarioRoot)
                .bind("fecha", OffsetDateTime.ofInstant(fechaAprobacion, ZoneOffset.UTC))
                .bind("id", idPago)
                .fetch()
                .rowsUpdated();
    }

    @Override
    public Mono<PagoPendienteValidacion> findUltimoRechazadoByCompania(Long idCompania) {
        return databaseClient.sql(
                "SELECT * FROM tenant.pagos_pendientes_validacion " +
                "WHERE id_compania = :idCompania AND estado = 'rechazado' " +
                "ORDER BY fecha_aprobacion DESC LIMIT 1")
                .bind("idCompania", idCompania)
                .map((row, meta) -> mapRow(row))
                .one()
                .onErrorResume(e -> Mono.empty());
    }

    @Override
    public Mono<Long> marcarRechazado(Long idPago, Long idUsuarioRoot, String motivo, Instant fechaRechazo) {
        return databaseClient.sql(
                "UPDATE tenant.pagos_pendientes_validacion " +
                "SET estado = 'rechazado', " +
                "    motivo_rechazo = :motivo, " +
                "    aprobado_por = :idRoot, " +
                "    fecha_aprobacion = :fecha " +
                "WHERE id = :id AND estado = 'pendiente'")
                .bind("motivo", motivo)
                .bind("idRoot", idUsuarioRoot)
                .bind("fecha", OffsetDateTime.ofInstant(fechaRechazo, ZoneOffset.UTC))
                .bind("id", idPago)
                .fetch()
                .rowsUpdated();
    }

    private PagoPendienteValidacion toDomain(PagoPendienteValidacionEntity entity) {
        PagoPendienteValidacion p = new PagoPendienteValidacion();
        p.setId(entity.getId());
        p.setIdCompania(entity.getIdCompania());
        p.setIdPlanDestino(entity.getIdPlanDestino());
        p.setMonto(entity.getMonto());
        p.setMoneda(entity.getMoneda());
        if (entity.getFechaReporte() != null) {
            p.setFechaReporte(entity.getFechaReporte().toInstant());
        }
        p.setFechaTransferencia(entity.getFechaTransferencia());
        p.setComprobanteUrl(entity.getComprobanteUrl());
        p.setComprobanteHash(entity.getComprobanteHash());
        p.setBancoOrigen(entity.getBancoOrigen());
        p.setReferencia(entity.getReferencia());
        p.setHashIdempotencia(entity.getHashIdempotencia());
        if (entity.getEstado() != null) {
            p.setEstado(PagoPendienteValidacion.Estado.valueOf(entity.getEstado().toUpperCase()));
        }
        p.setMotivoRechazo(entity.getMotivoRechazo());
        p.setAprobadoPor(entity.getAprobadoPor());
        if (entity.getFechaAprobacion() != null) {
            p.setFechaAprobacion(entity.getFechaAprobacion().toInstant());
        }
        p.setActivacionProgramada(Boolean.TRUE.equals(entity.getActivacionProgramada()));
        p.setFacturaEmitidaId(entity.getFacturaEmitidaId());
        p.setEliminado(entity.getEliminado());
        p.setCreacionFecha(entity.getCreacionFecha());
        p.setCreacionUsuario(entity.getCreacionUsuario());
        p.setModificaFecha(entity.getModificaFecha());
        p.setModificaUsuario(entity.getModificaUsuario());
        return p;
    }

    private PagoPendienteValidacionEntity toEntity(PagoPendienteValidacion p) {
        PagoPendienteValidacionEntity entity = new PagoPendienteValidacionEntity();
        entity.setId(p.getId());
        entity.setIdCompania(p.getIdCompania());
        entity.setIdPlanDestino(p.getIdPlanDestino());
        entity.setMonto(p.getMonto());
        entity.setMoneda(p.getMoneda());
        if (p.getFechaReporte() != null) {
            entity.setFechaReporte(OffsetDateTime.ofInstant(p.getFechaReporte(), ZoneOffset.UTC));
        }
        entity.setFechaTransferencia(p.getFechaTransferencia());
        entity.setComprobanteUrl(p.getComprobanteUrl());
        entity.setComprobanteHash(p.getComprobanteHash());
        entity.setBancoOrigen(p.getBancoOrigen());
        entity.setReferencia(p.getReferencia());
        entity.setHashIdempotencia(p.getHashIdempotencia());
        if (p.getEstado() != null) {
            entity.setEstado(p.getEstado().name().toLowerCase());
        }
        entity.setMotivoRechazo(p.getMotivoRechazo());
        entity.setAprobadoPor(p.getAprobadoPor());
        if (p.getFechaAprobacion() != null) {
            entity.setFechaAprobacion(OffsetDateTime.ofInstant(p.getFechaAprobacion(), ZoneOffset.UTC));
        }
        entity.setActivacionProgramada(p.isActivacionProgramada());
        entity.setFacturaEmitidaId(p.getFacturaEmitidaId());
        return entity;
    }
}
