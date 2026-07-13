package com.gymadmin.billing.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.billing.domain.port.out.SecuencialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Adaptador R2DBC que reserva atómicamente el siguiente secuencial usando
 * {@code INSERT ... ON CONFLICT ... DO UPDATE ... RETURNING} en una sola query.
 * <p>
 * Semántica de la query:
 * <ul>
 *   <li>Si la fila no existe (primer comprobante de esa combinación) → se inserta
 *       con {@code ultimo_secuencial = 1} y se devuelve {@code 1}.</li>
 *   <li>Si la fila existe → se incrementa {@code ultimo_secuencial + 1} y se
 *       devuelve el nuevo valor. Postgres garantiza atomicidad a través del
 *       índice UNIQUE {@code uq_facturacion_secuenciales}.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SecuencialPersistenceAdapter implements SecuencialRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO facturacion.secuenciales
              (id_compania, id_sucursal, cod_establecimiento, cod_punto_emision, tipo_comprobante, ultimo_secuencial)
            VALUES (:idCompania, :idSucursal, :codEstablecimiento, :codPuntoEmision, :tipoComprobante, 1)
            ON CONFLICT ON CONSTRAINT uq_facturacion_secuenciales
            DO UPDATE SET ultimo_secuencial = facturacion.secuenciales.ultimo_secuencial + 1
            RETURNING ultimo_secuencial
            """;

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Integer> reservarSiguiente(Integer idCompania,
                                           Integer idSucursal,
                                           String codEstablecimiento,
                                           String codPuntoEmision,
                                           String tipoComprobante) {
        return databaseClient.sql(UPSERT_SQL)
                .bind("idCompania", idCompania)
                .bind("idSucursal", idSucursal)
                .bind("codEstablecimiento", codEstablecimiento)
                .bind("codPuntoEmision", codPuntoEmision)
                .bind("tipoComprobante", tipoComprobante)
                .map(row -> row.get("ultimo_secuencial", Integer.class))
                .one();
    }
}
