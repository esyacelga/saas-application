package com.gymadmin.billing.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.billing.domain.model.AtsPagoComprobante;
import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.model.PeriodoResumen;
import com.gymadmin.billing.domain.port.out.ReporteRepository;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.ComprobanteEntity;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.repository.ComprobanteReporteR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ReportePersistenceAdapter implements ReporteRepository {

    private final ComprobanteReporteR2dbcRepository reporteRepository;
    private final DatabaseClient databaseClient;

    @Override
    public Flux<Comprobante> findAutorizadosPorMes(Integer idCompania, Integer anio, Integer mes) {
        return reporteRepository.findAutorizadosPorMes(idCompania, anio, mes)
                .map(this::toDomain);
    }

    @Override
    public Flux<Comprobante> findAnuladosPorMes(Integer idCompania, Integer anio, Integer mes) {
        return reporteRepository.findAnuladosPorMes(idCompania, anio, mes)
                .map(this::toDomain);
    }

    @Override
    public Flux<AtsPagoComprobante> findFormasPagoAutorizadasPorMes(Integer idCompania, Integer anio, Integer mes) {
        return databaseClient.sql("""
                SELECT p.id_comprobante, p.forma_pago
                  FROM facturacion.comprobante_pagos p
                  JOIN facturacion.comprobantes c ON c.id = p.id_comprobante
                 WHERE c.id_compania = :idCompania
                   AND c.estado = 'AUTORIZADO'
                   AND EXTRACT(YEAR FROM c.fecha_emision) = :anio
                   AND EXTRACT(MONTH FROM c.fecha_emision) = :mes
                """)
                .bind("idCompania", idCompania)
                .bind("anio", anio)
                .bind("mes", mes)
                .map(row -> new AtsPagoComprobante(
                        row.get("id_comprobante", Long.class),
                        // forma_pago es CHAR(2): Postgres lo devuelve con padding si el valor es más corto.
                        trim(row.get("forma_pago", String.class))
                ))
                .all();
    }

    private String trim(String value) {
        return value != null ? value.trim() : null;
    }

    @Override
    public Mono<PeriodoResumen> resumenPorPeriodo(Integer idCompania, LocalDate desde, LocalDate hasta) {
        Mono<ResumenAgregado> agregadoMono = databaseClient.sql("""
                SELECT
                  COUNT(*) AS total_emitidos,
                  COUNT(*) FILTER (WHERE estado = 'AUTORIZADO') AS total_autorizados,
                  COUNT(*) FILTER (WHERE estado IN ('ERROR','FALLIDO_DEFINITIVO')) AS total_error,
                  COALESCE(SUM(subtotal_sin_impuesto) FILTER (WHERE estado = 'AUTORIZADO'), 0) AS subtotal_sin_iva,
                  COALESCE(SUM(total_iva) FILTER (WHERE estado = 'AUTORIZADO'), 0) AS total_iva,
                  COALESCE(SUM(total) FILTER (WHERE estado = 'AUTORIZADO'), 0) AS total_facturado
                FROM facturacion.comprobantes
                WHERE id_compania = :idCompania
                  AND fecha_emision BETWEEN :desde AND :hasta
                """)
                .bind("idCompania", idCompania)
                .bind("desde", desde)
                .bind("hasta", hasta)
                .map(row -> new ResumenAgregado(
                        row.get("total_emitidos", Long.class),
                        row.get("total_autorizados", Long.class),
                        row.get("total_error", Long.class),
                        row.get("subtotal_sin_iva", BigDecimal.class),
                        row.get("total_iva", BigDecimal.class),
                        row.get("total_facturado", BigDecimal.class)
                ))
                .one();

        Mono<Map<String, Long>> porEstadoMono = databaseClient.sql("""
                SELECT estado, COUNT(*) AS cantidad
                FROM facturacion.comprobantes
                WHERE id_compania = :idCompania
                  AND fecha_emision BETWEEN :desde AND :hasta
                GROUP BY estado
                """)
                .bind("idCompania", idCompania)
                .bind("desde", desde)
                .bind("hasta", hasta)
                .map(row -> Map.entry(
                        safeString(row.get("estado", String.class)),
                        safeLong(row.get("cantidad", Long.class))
                ))
                .all()
                .collectList()
                .map(entries -> {
                    Map<String, Long> map = new HashMap<>();
                    entries.forEach(e -> map.put(e.getKey(), e.getValue()));
                    return map;
                });

        return Mono.zip(agregadoMono, porEstadoMono)
                .map(tuple -> {
                    ResumenAgregado agg = tuple.getT1();
                    Map<String, Long> porEstado = tuple.getT2();
                    return new PeriodoResumen(
                            desde,
                            hasta,
                            agg.totalEmitidos(),
                            agg.totalAutorizados(),
                            agg.totalError(),
                            agg.subtotalSinIva(),
                            agg.totalIva(),
                            agg.totalFacturado(),
                            porEstado
                    );
                });
    }

    private Comprobante toDomain(ComprobanteEntity e) {
        return Comprobante.builder()
                .id(e.getId())
                .idCompania(e.getIdCompania())
                .idSucursal(e.getIdSucursal())
                .tipoComprobante(e.getTipoComprobante())
                .claveAcceso(e.getClaveAcceso())
                .numeroAutorizacion(e.getNumeroAutorizacion())
                .codEstablecimiento(e.getCodEstablecimiento())
                .codPuntoEmision(e.getCodPuntoEmision())
                .secuencial(e.getSecuencial())
                .fechaEmision(e.getFechaEmision())
                .ambiente(e.getAmbiente())
                .tipoIdReceptor(e.getTipoIdReceptor())
                .idReceptor(e.getIdReceptor())
                .razonSocialReceptor(e.getRazonSocialReceptor())
                .emailReceptor(e.getEmailReceptor())
                .direccionReceptor(e.getDireccionReceptor())
                .telefonoReceptor(e.getTelefonoReceptor())
                .subtotalSinImpuesto(e.getSubtotalSinImpuesto())
                .subtotalIva0(e.getSubtotalIva0())
                .subtotalNoObjetoIva(e.getSubtotalNoObjetoIva())
                .subtotalExentoIva(e.getSubtotalExentoIva())
                .totalDescuento(e.getTotalDescuento())
                .totalIce(e.getTotalIce())
                .totalIva(e.getTotalIva())
                .propina(e.getPropina())
                .total(e.getTotal())
                .moneda(e.getMoneda())
                .idMembresia(e.getIdMembresia())
                .idVenta(e.getIdVenta())
                .idComprobanteRef(e.getIdComprobanteRef())
                .estado(e.getEstado())
                .fechaAutorizacion(e.getFechaAutorizacion())
                .xmlFirmadoPath(e.getXmlFirmadoPath())
                .xmlAutorizadoPath(e.getXmlAutorizadoPath())
                .ridePdfPath(e.getRidePdfPath())
                .idUsuarioRegistro(e.getIdUsuarioRegistro())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private String safeString(String value) {
        return value != null ? value : "";
    }

    private Long safeLong(Long value) {
        return value != null ? value : 0L;
    }

    private record ResumenAgregado(
            Long totalEmitidos,
            Long totalAutorizados,
            Long totalError,
            BigDecimal subtotalSinIva,
            BigDecimal totalIva,
            BigDecimal totalFacturado
    ) {}
}
