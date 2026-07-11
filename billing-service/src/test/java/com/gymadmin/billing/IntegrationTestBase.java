package com.gymadmin.billing;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base para los tests de integración de repositorios R2DBC.
 * <p>
 * Convención del monorepo (documentada en {@code auth-service/CLAUDE.md}):
 * los tests de integración corren contra el PostgreSQL local del desarrollador
 * usando las variables de entorno {@code DB_HOST}, {@code DB_PORT}, {@code DB_NAME},
 * {@code DB_USER}, {@code DB_PASSWORD} definidas en {@code ./.env}. No se utiliza
 * Testcontainers.
 * <p>
 * Cada test corre dentro de una transacción que se hace rollback al terminar
 * ({@link Transactional}), de modo que no queda residuo en la BD de desarrollo.
 * Todo lo insertado usa {@link #ID_COMPANIA} = {@value #ID_COMPANIA} para aislar
 * la data de los tests del resto de la aplicación.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Transactional
public abstract class IntegrationTestBase {

    protected static final int ID_COMPANIA = 99999;
    protected static final int ID_SUCURSAL = 99999;
}
