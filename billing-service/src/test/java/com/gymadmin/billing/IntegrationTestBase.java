package com.gymadmin.billing;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * Base para los tests de integración de repositorios R2DBC.
 * <p>
 * Convención del monorepo (documentada en {@code auth-service/CLAUDE.md}):
 * los tests de integración corren contra el PostgreSQL local del desarrollador
 * usando las variables de entorno {@code DB_HOST}, {@code DB_PORT}, {@code DB_NAME},
 * {@code DB_USER}, {@code DB_PASSWORD} definidas en {@code ./.env}. No se utiliza
 * Testcontainers.
 * <p>
 * Aislamiento de datos: cada fila insertada usa {@link #ID_COMPANIA} = {@value #ID_COMPANIA}
 * (el mismo valor que auth-service) para no colisionar con datos reales del entorno
 * de desarrollo. Las filas quedan en la BD tras la corrida — el filtro por
 * {@code id_compania} las mantiene invisibles al resto de la aplicación.
 * <p>
 * Nota: la clase base de auth-service no aplica {@code @Transactional}
 * porque el {@code TransactionalTestExecutionListener} de Spring requiere un
 * {@code PlatformTransactionManager}, y esta pila reactiva solo expone
 * {@code R2dbcTransactionManager} (que implementa {@code ReactiveTransactionManager}).
 * Se mantiene la misma decisión aquí para no divergir del patrón del monorepo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@ContextConfiguration(initializers = DotEnvInitializer.class)
public abstract class IntegrationTestBase {

    protected static final int ID_COMPANIA = 99999;
    protected static final int ID_SUCURSAL = 99999;

    /**
     * Borra los comprobantes de la compañía de test junto con todas sus filas hijas.
     * <p>
     * {@code facturacion.comprobantes} tiene 9 tablas dependientes vía FK más una
     * autorreferencia ({@code id_comprobante_ref}, que apunta de la NC a su factura
     * original). Borrar solo la tabla padre viola las FK, así que el orden aquí va de
     * las hojas hacia la raíz. {@code detalle_impuestos} cuelga de
     * {@code comprobantes_detalle} —no de {@code comprobantes}— y {@code info_adicional}
     * no tiene {@code id_compania}: ambas se filtran por subconsulta.
     * <p>
     * La autorreferencia obliga a que el {@code DELETE} final borre notas de crédito y
     * facturas en una sola sentencia; PostgreSQL resuelve las dependencias internas
     * dentro del mismo statement.
     */
    protected void limpiarComprobantes(DatabaseClient databaseClient) {
        limpiarComprobantes(databaseClient, ID_COMPANIA);
    }

    /** Variante para limpiar una compañía distinta (p. ej. la vecina en tests multi-tenant). */
    protected void limpiarComprobantes(DatabaseClient databaseClient, int idCompania) {
        String[] porCompania = {
                "DELETE FROM facturacion.comprobante_detalle_impuestos WHERE id_detalle IN "
                        + "(SELECT id FROM facturacion.comprobantes_detalle WHERE id_compania = :idCompania)",
                "DELETE FROM facturacion.comprobante_info_adicional WHERE id_comprobante IN "
                        + "(SELECT id FROM facturacion.comprobantes WHERE id_compania = :idCompania)",
                "DELETE FROM facturacion.comprobantes_detalle WHERE id_compania = :idCompania",
                "DELETE FROM facturacion.comprobante_impuestos_totales WHERE id_compania = :idCompania",
                "DELETE FROM facturacion.comprobante_pagos WHERE id_compania = :idCompania",
                "DELETE FROM facturacion.notificaciones_receptor WHERE id_compania = :idCompania",
                "DELETE FROM facturacion.cola_envio WHERE id_compania = :idCompania",
                "DELETE FROM facturacion.envios_sri WHERE id_compania = :idCompania",
                "DELETE FROM facturacion.anulaciones WHERE id_compania = :idCompania",
                "DELETE FROM facturacion.notas_credito_referencias WHERE id_compania = :idCompania",
                "DELETE FROM facturacion.comprobantes WHERE id_compania = :idCompania",
        };

        for (String sql : porCompania) {
            databaseClient.sql(sql)
                    .bind("idCompania", idCompania)
                    .then()
                    .block();
        }
    }
}
