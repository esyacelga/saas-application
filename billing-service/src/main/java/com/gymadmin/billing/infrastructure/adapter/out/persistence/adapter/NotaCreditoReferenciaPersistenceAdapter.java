package com.gymadmin.billing.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.billing.domain.model.NotaCreditoReferencia;
import com.gymadmin.billing.domain.port.out.NotaCreditoReferenciaRepository;
import io.r2dbc.spi.Readable;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Adaptador R2DBC para {@code facturacion.notas_credito_referencias}. Usa
 * {@link DatabaseClient} con SQL nativo (mismo patrón que
 * {@link SecuencialPersistenceAdapter}) porque la tabla no tiene
 * {@code creacion_fecha} / {@code eliminado} y el mapeo declarativo con
 * {@code @Table} añade complejidad innecesaria para dos operaciones puntuales.
 */
@Component
@RequiredArgsConstructor
public class NotaCreditoReferenciaPersistenceAdapter implements NotaCreditoReferenciaRepository {

    private static final String INSERT_SQL = """
            INSERT INTO facturacion.notas_credito_referencias
              (id_compania, id_sucursal, id_comprobante, cod_doc_modificado,
               num_doc_modificado, fecha_emision_modif, id_motivo_anulacion,
               razon, valor_modificado)
            VALUES (:idCompania, :idSucursal, :idComprobante, :codDocModificado,
                    :numDocModificado, :fechaEmisionModif, :idMotivoAnulacion,
                    :razon, :valorModificado)
            RETURNING id, id_compania, id_sucursal, id_comprobante, cod_doc_modificado,
                      num_doc_modificado, fecha_emision_modif, id_motivo_anulacion,
                      razon, valor_modificado
            """;

    private static final String SELECT_BY_ID_COMPROBANTE_SQL = """
            SELECT id, id_compania, id_sucursal, id_comprobante, cod_doc_modificado,
                   num_doc_modificado, fecha_emision_modif, id_motivo_anulacion,
                   razon, valor_modificado
            FROM facturacion.notas_credito_referencias
            WHERE id_comprobante = :idComprobante
            LIMIT 1
            """;

    private final DatabaseClient databaseClient;

    @Override
    public Mono<NotaCreditoReferencia> save(NotaCreditoReferencia referencia) {
        return databaseClient.sql(INSERT_SQL)
                .bind("idCompania", referencia.getIdCompania())
                .bind("idSucursal", referencia.getIdSucursal())
                .bind("idComprobante", referencia.getIdComprobante())
                .bind("codDocModificado", referencia.getCodDocModificado())
                .bind("numDocModificado", referencia.getNumDocModificado())
                .bind("fechaEmisionModif", referencia.getFechaEmisionModif())
                .bind("idMotivoAnulacion", referencia.getIdMotivoAnulacion())
                .bind("razon", referencia.getRazon())
                .bind("valorModificado", referencia.getValorModificado())
                .map(NotaCreditoReferenciaPersistenceAdapter::mapRow)
                .one();
    }

    @Override
    public Mono<NotaCreditoReferencia> findByIdComprobante(Long idComprobante) {
        return databaseClient.sql(SELECT_BY_ID_COMPROBANTE_SQL)
                .bind("idComprobante", idComprobante)
                .map(NotaCreditoReferenciaPersistenceAdapter::mapRow)
                .one();
    }

    private static NotaCreditoReferencia mapRow(Readable row) {
        return NotaCreditoReferencia.builder()
                .id(row.get("id", Long.class))
                .idCompania(row.get("id_compania", Integer.class))
                .idSucursal(row.get("id_sucursal", Integer.class))
                .idComprobante(row.get("id_comprobante", Long.class))
                .codDocModificado(row.get("cod_doc_modificado", String.class))
                .numDocModificado(row.get("num_doc_modificado", String.class))
                .fechaEmisionModif(row.get("fecha_emision_modif", LocalDate.class))
                .idMotivoAnulacion(row.get("id_motivo_anulacion", Integer.class))
                .razon(row.get("razon", String.class))
                .valorModificado(row.get("valor_modificado", BigDecimal.class))
                .build();
    }
}
