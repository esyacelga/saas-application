package com.gymadmin.attendance;

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
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
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

    @Autowired
    protected WebTestClient webTestClient;

    @Autowired
    protected DatabaseClient databaseClient;

    @BeforeEach
    void cleanDatabase() {
        // Orden inverso de FK
        executeWithRetry("DELETE FROM asistencia.mensajes_log");
        executeWithRetry("DELETE FROM asistencia.plantillas_mensajes");
        executeWithRetry("DELETE FROM asistencia.asistencias");
        executeWithRetry("DELETE FROM core.membresias WHERE creacion_usuario = 'test'");
        executeWithRetry("DELETE FROM core.tipos_membresia WHERE creacion_usuario = 'test'");
        executeWithRetry("DELETE FROM core.clientes WHERE creacion_usuario = 'test'");
        executeWithRetry("DELETE FROM identidad.personas WHERE ci LIKE 'IT%'");
    }

    /**
     * Ejecuta un DELETE con reintentos ante fallos transitorios de conexión R2DBC.
     * El driver r2dbc-postgresql 1.0.7 sufre de una race intermitente en la
     * negociación SCRAM-SHA-256 cuando el pool abre conexiones nuevas contra
     * PostgreSQL en Docker (fuera de las reglas trust locales), lo que produce
     * {@link DataAccessResourceFailureException} con causa "password
     * authentication failed". El retry con backoff cubre esos casos.
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

    /** Cliente de app móvil */
    protected String jwtCliente(Integer idCliente, Integer idCompania) {
        return buildJwt(String.valueOf(idCliente), "cliente", null, null, idCompania.longValue());
    }

    /** Recepcionista del gym */
    protected String jwtRecepcion(Integer idCompania) {
        return buildJwt("user-recepcion", "staff", null, "recepcion", idCompania.longValue());
    }

    /** Dueño / admin_compania del gym */
    protected String jwtDueno(Integer idCompania) {
        return buildJwt("user-dueno", "staff", null, "dueno", idCompania.longValue());
    }

    /** Super admin de plataforma */
    protected String jwtSuperAdmin() {
        return buildJwt("user-super-admin", "plataforma", "super_admin", null, null);
    }

    /** Entrenador */
    protected String jwtEntrenador(Integer idCompania) {
        return buildJwt("user-entrenador", "staff", null, "entrenador", idCompania.longValue());
    }

    protected String bearerHeader(String jwt) {
        return "Bearer " + jwt;
    }

    private String buildJwt(String subject, String tipo, String rolPlataforma,
                             String rolGym, Long idCompania) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tipo", tipo);
        if (rolPlataforma != null) claims.put("rol_plataforma", rolPlataforma);
        if (rolGym != null)        claims.put("rol_gym", rolGym);
        if (idCompania != null)    claims.put("id_compania", idCompania);

        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(SECRET_KEY)
                .compact();
    }

    // ── DB helpers ──────────────────────────────────────────────────────────

    /** Inserta una asistencia directamente en BD y retorna su id. */
    protected Long insertarAsistencia(Integer idCompania, Integer idSucursal,
                                       Integer idCliente, Integer idMembresia,
                                       String fecha, String horaEntrada, String metodo) {
        return databaseClient.sql("""
                INSERT INTO asistencia.asistencias
                  (id_compania, id_sucursal, id_cliente, id_membresia,
                   fecha, hora_entrada, metodo_registro, creacion_usuario)
                VALUES (:comp, :suc, :cli, :mem, :fecha::date, :hora::time, :metodo, 'test')
                RETURNING id
                """)
                .bind("comp", idCompania).bind("suc", idSucursal)
                .bind("cli", idCliente).bind("mem", idMembresia)
                .bind("fecha", fecha).bind("hora", horaEntrada)
                .bind("metodo", metodo)
                .map(row -> row.get("id", Long.class))
                .one()
                .block();
    }

    /** Inserta una plantilla de mensaje y retorna su id. */
    protected Integer insertarPlantilla(Integer idCompania, Integer idSucursal,
                                         String tipo, String nombre, String contenido) {
        return databaseClient.sql("""
                INSERT INTO asistencia.plantillas_mensajes
                  (id_compania, id_sucursal, tipo, nombre, contenido, creacion_usuario)
                VALUES (:comp, :suc, :tipo, :nombre, :contenido, 'test')
                RETURNING id
                """)
                .bind("comp", idCompania).bind("suc", idSucursal)
                .bind("tipo", tipo).bind("nombre", nombre).bind("contenido", contenido)
                .map(row -> row.get("id", Integer.class))
                .one()
                .block();
    }

    /** Inserta un cliente mínimo en core.clientes para satisfacer FK. */
    protected Integer insertarClienteCore(Integer idCompania, Integer idSucursal) {
        // Primero inserta persona en identidad.personas (ci único por llamada de test)
        String ci = "IT" + String.format("%014d", Math.abs(System.nanoTime()) % 100_000_000_000_000L);
        Integer idPersona = databaseClient.sql("""
                INSERT INTO identidad.personas (ci, nombre, creacion_usuario)
                VALUES (:ci, 'Test Cliente', 'test')
                RETURNING id
                """)
                .bind("ci", ci)
                .map(row -> row.get("id", Integer.class))
                .one()
                .block();

        return databaseClient.sql("""
                INSERT INTO core.clientes
                  (id_compania, id_sucursal, id_persona, codigo_carnet, estado, creacion_usuario)
                VALUES (:comp, :suc, :persona, :carnet, 'activo', 'test')
                RETURNING id
                """)
                .bind("comp", idCompania).bind("suc", idSucursal)
                .bind("persona", idPersona)
                .bind("carnet", "C" + String.format("%014d", Math.abs(System.nanoTime()) % 100_000_000_000_000L))
                .map(row -> row.get("id", Integer.class))
                .one()
                .block();
    }

    /** Inserta una membresía mínima y retorna su id. */
    protected Integer insertarMembresia(Integer idCliente, Integer idCompania) {
        String nombre = "TM" + String.format("%012d", Math.abs(System.nanoTime()) % 1_000_000_000_000L);
        Integer idTipo = databaseClient.sql("""
                INSERT INTO core.tipos_membresia
                  (id_compania, id_sucursal, nombre, modo_control,
                   duracion_tipo, duracion_valor, precio, creacion_usuario)
                VALUES (:comp, 1, :nombre, 'calendario', 'meses', 1, 0, 'test')
                RETURNING id
                """)
                .bind("comp", idCompania)
                .bind("nombre", nombre)
                .map(row -> row.get("id", Integer.class))
                .one()
                .block();

        return databaseClient.sql("""
                INSERT INTO core.membresias
                  (id_compania, id_sucursal, id_cliente, id_tipo_membresia,
                   fecha_inicio, fecha_fin, precio_pagado, estado, creacion_usuario)
                VALUES (:comp, 1, :cli, :tipo,
                        CURRENT_DATE, CURRENT_DATE + INTERVAL '30 days', 0, 'activa', 'test')
                RETURNING id
                """)
                .bind("comp", idCompania)
                .bind("cli", idCliente)
                .bind("tipo", idTipo)
                .map(row -> row.get("id", Integer.class))
                .one()
                .block();
    }
}
