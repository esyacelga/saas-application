package com.gymadmin.finance;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.util.retry.Retry;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = DotEnvInitializer.class)
public abstract class BaseIntegrationTest {

    // Misma clave que en .env / application-test.yml
    private static final String JWT_SECRET_B64 =
            "Y2hhbmdlLW1lLWluLXByb2R1Y3Rpb24tdGhpcy1rZXktbXVzdC1iZS0yNTYtYml0cw==";
    private static final SecretKey SECRET_KEY =
            Keys.hmacShaKeyFor(Decoders.BASE64.decode(JWT_SECRET_B64));

    /** Marca de auditoría con la que se insertan las filas de test, para el teardown selectivo. */
    protected static final String TEST_USER = "test";

    @Autowired
    protected WebTestClient webTestClient;

    @Autowired
    protected DatabaseClient databaseClient;

    @BeforeEach
    void cleanDatabase() {
        // Orden inverso de FK: ingresos/egresos referencian a las categorías.
        executeWithRetry("DELETE FROM finanzas.ingresos WHERE creacion_usuario = 'test'");
        executeWithRetry("DELETE FROM finanzas.egresos WHERE creacion_usuario = 'test'");
        executeWithRetry("DELETE FROM finanzas.categorias_ingreso WHERE creacion_usuario = 'test'");
        executeWithRetry("DELETE FROM finanzas.categorias_egreso WHERE creacion_usuario = 'test'");
    }

    /**
     * Ejecuta un DELETE con reintentos ante fallos transitorios de conexión R2DBC.
     * El driver r2dbc-postgresql sufre de una race intermitente en la negociación
     * SCRAM-SHA-256 cuando el pool abre conexiones nuevas; el retry con backoff la cubre.
     */
    private void executeWithRetry(String sql) {
        databaseClient.sql(sql)
                .then()
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                        .filter(t -> t instanceof DataAccessResourceFailureException)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .block();
    }

    // ── JWT helpers ──────────────────────────────────────────────────────────

    /** Recepcionista del gym */
    protected String jwtRecepcion(Integer idCompania) {
        return buildJwt("user-recepcion", "staff", null, "recepcion", idCompania.longValue(), List.of());
    }

    /** Dueño / admin_compania del gym */
    protected String jwtDueno(Integer idCompania) {
        return buildJwt("user-dueno", "staff", null, "dueno", idCompania.longValue(), List.of());
    }

    /** Staff con permisos de finanzas explícitos */
    protected String jwtStaffFinanzas(Integer idCompania, List<String> permisos) {
        return buildJwt("user-finanzas", "staff", null, "admin_compania", idCompania.longValue(), permisos);
    }

    /** Entrenador (sin acceso a finanzas) */
    protected String jwtEntrenador(Integer idCompania) {
        return buildJwt("user-entrenador", "staff", null, "entrenador", idCompania.longValue(), List.of());
    }

    /** Super admin de plataforma */
    protected String jwtSuperAdmin() {
        return buildJwt("user-super-admin", "plataforma", "super_admin", null, null, List.of());
    }

    protected String bearerHeader(String jwt) {
        return "Bearer " + jwt;
    }

    private String buildJwt(String subject, String tipo, String rolPlataforma,
                            String rolGym, Long idCompania, List<String> permisos) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tipo", tipo);
        if (rolPlataforma != null) claims.put("rol_plataforma", rolPlataforma);
        if (rolGym != null)        claims.put("rol_gym", rolGym);
        if (idCompania != null)    claims.put("id_compania", idCompania);
        if (permisos != null && !permisos.isEmpty()) claims.put("permisos", permisos);

        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(SECRET_KEY)
                .compact();
    }

    // ── DB helpers ──────────────────────────────────────────────────────────
    // Los id son GENERATED ALWAYS AS IDENTITY: no se pueden insertar explícitos.
    // Cada helper devuelve el id generado con RETURNING id.

    /** Inserta una categoría de ingreso y retorna su id. */
    protected Integer insertarCategoriaIngreso(Integer idCompania, Integer idSucursal, String nombre, boolean activo) {
        return databaseClient.sql("""
                INSERT INTO finanzas.categorias_ingreso
                  (id_compania, id_sucursal, nombre, activo, creacion_usuario)
                VALUES (:comp, :suc, :nombre, :activo, 'test')
                RETURNING id
                """)
                .bind("comp", idCompania).bind("suc", idSucursal)
                .bind("nombre", nombre).bind("activo", activo)
                .map(row -> row.get("id", Integer.class))
                .one()
                .block();
    }

    /** Inserta una categoría de egreso y retorna su id. */
    protected Integer insertarCategoriaEgreso(Integer idCompania, Integer idSucursal, String nombre, boolean activo) {
        return databaseClient.sql("""
                INSERT INTO finanzas.categorias_egreso
                  (id_compania, id_sucursal, nombre, activo, creacion_usuario)
                VALUES (:comp, :suc, :nombre, :activo, 'test')
                RETURNING id
                """)
                .bind("comp", idCompania).bind("suc", idSucursal)
                .bind("nombre", nombre).bind("activo", activo)
                .map(row -> row.get("id", Integer.class))
                .one()
                .block();
    }

    /** Inserta un ingreso y retorna su id. */
    protected Integer insertarIngreso(Integer idCompania, Integer idSucursal, Integer idCategoria,
                                      BigDecimal monto, LocalDate fecha) {
        return databaseClient.sql("""
                INSERT INTO finanzas.ingresos
                  (id_compania, id_sucursal, id_categoria, monto, descripcion, fecha, creacion_usuario)
                VALUES (:comp, :suc, :cat, :monto, 'IT ingreso', :fecha::date, 'test')
                RETURNING id
                """)
                .bind("comp", idCompania).bind("suc", idSucursal).bind("cat", idCategoria)
                .bind("monto", monto).bind("fecha", fecha.toString())
                .map(row -> row.get("id", Integer.class))
                .one()
                .block();
    }

    /** Inserta un egreso y retorna su id. */
    protected Integer insertarEgreso(Integer idCompania, Integer idSucursal, Integer idCategoria,
                                     BigDecimal monto, LocalDate fecha) {
        return databaseClient.sql("""
                INSERT INTO finanzas.egresos
                  (id_compania, id_sucursal, id_categoria, monto, descripcion, fecha, creacion_usuario)
                VALUES (:comp, :suc, :cat, :monto, 'IT egreso', :fecha::date, 'test')
                RETURNING id
                """)
                .bind("comp", idCompania).bind("suc", idSucursal).bind("cat", idCategoria)
                .bind("monto", monto).bind("fecha", fecha.toString())
                .map(row -> row.get("id", Integer.class))
                .one()
                .block();
    }
}
