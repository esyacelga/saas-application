package com.gymadmin.billing;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
public abstract class IntegrationTestBase {

    protected static final int ID_COMPANIA = 99999;
    protected static final int ID_SUCURSAL = 99999;
}
