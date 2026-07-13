package com.gymadmin.billing.integration;

import com.gymadmin.billing.IntegrationTestBase;
import com.gymadmin.billing.domain.port.out.SecuencialRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que {@link SecuencialRepository#reservarSiguiente} entrega secuenciales
 * únicos y consecutivos bajo concurrencia — condición mínima para no emitir dos
 * comprobantes con la misma clave de acceso ante el SRI.
 * <p>
 * Convención: los tests IT del monorepo usan {@code .env} local (no Testcontainers).
 * Requiere que la tabla {@code facturacion.secuenciales} exista en la BD local.
 */
@DisplayName("SecuencialRepository — reserva atómica bajo concurrencia (G5)")
class SecuencialConcurrentIT extends IntegrationTestBase {

    private static final String COD_ESTABLECIMIENTO = "997";
    private static final String COD_PUNTO_EMISION = "997";
    private static final String TIPO_FACTURA = "01";

    @Autowired
    private SecuencialRepository secuencialRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @BeforeEach
    void cleanBefore() {
        deleteSecuencialRow().block();
    }

    @AfterEach
    void cleanAfter() {
        deleteSecuencialRow().block();
    }

    @Test
    @DisplayName("dos reservas en paralelo devuelven secuenciales distintos y consecutivos (1 y 2)")
    void reservarSiguiente_dosReservasParalelas_generaSecuencialesConsecutivos() {
        Mono<Integer> res1 = secuencialRepository.reservarSiguiente(
                ID_COMPANIA, ID_SUCURSAL, COD_ESTABLECIMIENTO, COD_PUNTO_EMISION, TIPO_FACTURA);
        Mono<Integer> res2 = secuencialRepository.reservarSiguiente(
                ID_COMPANIA, ID_SUCURSAL, COD_ESTABLECIMIENTO, COD_PUNTO_EMISION, TIPO_FACTURA);

        List<Integer> resultados = new CopyOnWriteArrayList<>();

        StepVerifier.create(Mono.zip(res1, res2)
                        .doOnNext(tuple -> {
                            resultados.add(tuple.getT1());
                            resultados.add(tuple.getT2());
                        }))
                .assertNext(tuple -> {
                    Integer a = tuple.getT1();
                    Integer b = tuple.getT2();
                    assertThat(a).as("primer secuencial no debe ser null").isNotNull();
                    assertThat(b).as("segundo secuencial no debe ser null").isNotNull();
                    assertThat(a).as("no deben ser iguales").isNotEqualTo(b);
                    assertThat(List.of(a, b)).containsExactlyInAnyOrder(1, 2);
                })
                .verifyComplete();

        // El valor persistido tras las dos reservas debe ser 2
        Mono<Integer> ultimoPersistido = databaseClient.sql("""
                        SELECT ultimo_secuencial
                          FROM facturacion.secuenciales
                         WHERE id_compania       = :idCompania
                           AND id_sucursal       = :idSucursal
                           AND cod_establecimiento = :codEst
                           AND cod_punto_emision = :codPto
                           AND tipo_comprobante  = :tipo
                        """)
                .bind("idCompania", ID_COMPANIA)
                .bind("idSucursal", ID_SUCURSAL)
                .bind("codEst", COD_ESTABLECIMIENTO)
                .bind("codPto", COD_PUNTO_EMISION)
                .bind("tipo", TIPO_FACTURA)
                .map(row -> row.get("ultimo_secuencial", Integer.class))
                .one();

        StepVerifier.create(ultimoPersistido)
                .assertNext(v -> assertThat(v).isEqualTo(2))
                .verifyComplete();
    }

    @Test
    @DisplayName("primera reserva devuelve 1 aunque la fila no exista previamente")
    void reservarSiguiente_primeraReserva_devuelveUno() {
        StepVerifier.create(secuencialRepository.reservarSiguiente(
                        ID_COMPANIA, ID_SUCURSAL, COD_ESTABLECIMIENTO, COD_PUNTO_EMISION, TIPO_FACTURA))
                .assertNext(v -> assertThat(v).isEqualTo(1))
                .verifyComplete();
    }

    private Mono<Void> deleteSecuencialRow() {
        return databaseClient.sql("""
                        DELETE FROM facturacion.secuenciales
                         WHERE id_compania       = :idCompania
                           AND id_sucursal       = :idSucursal
                           AND cod_establecimiento = :codEst
                           AND cod_punto_emision = :codPto
                           AND tipo_comprobante  = :tipo
                        """)
                .bind("idCompania", ID_COMPANIA)
                .bind("idSucursal", ID_SUCURSAL)
                .bind("codEst", COD_ESTABLECIMIENTO)
                .bind("codPto", COD_PUNTO_EMISION)
                .bind("tipo", TIPO_FACTURA)
                .then();
    }
}
