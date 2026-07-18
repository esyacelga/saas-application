package com.gymadmin.core;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = DotEnvInitializer.class)
public abstract class BaseIntegrationTest {

    private static final String JWT_SECRET_B64 =
            "Y2hhbmdlLW1lLWluLXByb2R1Y3Rpb24tdGhpcy1rZXktbXVzdC1iZS0yNTYtYml0cw==";
    private static final SecretKey SECRET_KEY =
            Keys.hmacShaKeyFor(Decoders.BASE64.decode(JWT_SECRET_B64));

    protected static final long TEST_COMPANIA  = 1L;
    protected static final long TEST_SUCURSAL  = 1L;
    protected static final long TEST_USUARIO   = 1L;

    @Autowired protected WebTestClient webTestClient;
    @Autowired protected DatabaseClient databaseClient;

    // ── limpieza en orden inverso de FK ──────────────────────────────────────

    @BeforeEach
    void cleanDatabase() {
        // asistencia.asistencias tiene FK → core.membresias: se borra primero para no violarla.
        databaseClient.sql("DELETE FROM asistencia.asistencias").then().block();
        databaseClient.sql("DELETE FROM core.congelamientos").then().block();
        databaseClient.sql("DELETE FROM core.membresias").then().block();
        databaseClient.sql("DELETE FROM core.clientes").then().block();
        databaseClient.sql("DELETE FROM core.tipos_membresia").then().block();
        databaseClient.sql("DELETE FROM identidad.personas WHERE ci LIKE 'TEST-%'").then().block();
    }

    // ── JWT helpers ──────────────────────────────────────────────────────────

    protected String jwtAdminCompania(long idCompania) {
        return buildJwt("2", "staff", "admin_compania", idCompania);
    }

    protected String jwtRecepcion(long idCompania) {
        return buildJwt("3", "staff", "Recepción", idCompania);
    }

    protected String jwtSuperAdmin() {
        return buildJwt("1", "plataforma", "super_admin", null);
    }

    /**
     * Token staff con lista de {@code permisos} explícita. Sirve para probar el gate
     * granular de {@code membresias:confirmar_pago}: si el token trae permisos
     * pero no incluye la clave, el controller responde 403 (ver
     * {@link com.gymadmin.core.infrastructure.adapter.in.web.MembresiaController#requireConfirmarPagoPermiso}).
     */
    protected String jwtRecepcionConPermisos(long idCompania, List<String> permisos) {
        return buildJwt("3", "staff", "Recepción", idCompania, permisos);
    }

    protected String bearer(String jwt) {
        return "Bearer " + jwt;
    }

    // ── seed helpers ─────────────────────────────────────────────────────────

    /** Inserta una persona y devuelve su id. */
    protected Long seedPersona(String ci, String nombre) {
        return databaseClient.sql(
                "INSERT INTO identidad.personas(ci, nombre, creacion_usuario) " +
                "VALUES (:ci, :nombre, 'test') RETURNING id")
                .bind("ci", ci)
                .bind("nombre", nombre)
                .map(row -> row.get("id", Long.class))
                .one()
                .block();
    }

    /** Inserta un cliente y devuelve su id. */
    protected Long seedCliente(Long idPersona) {
        return databaseClient.sql(
                "INSERT INTO core.clientes(id_persona, id_compania, id_sucursal, " +
                "estado, fecha_ingreso, creacion_usuario) " +
                "VALUES (:idPersona, :comp, :suc, 'activo', CURRENT_DATE, 'test') RETURNING id")
                .bind("idPersona", idPersona)
                .bind("comp", TEST_COMPANIA)
                .bind("suc", TEST_SUCURSAL)
                .map(row -> row.get("id", Long.class))
                .one()
                .block();
    }

    /** Inserta un tipo membresía calendario y devuelve su id. */
    protected Long seedTipoCalendario(String nombre) {
        return databaseClient.sql(
                "INSERT INTO core.tipos_membresia(id_compania, id_sucursal, nombre, " +
                "modo_control, duracion_tipo, duracion_valor, precio, activo, creacion_usuario) " +
                "VALUES (:comp, :suc, :nombre, 'calendario', 'meses', 1, 35.00, true, 'test') RETURNING id")
                .bind("comp", TEST_COMPANIA)
                .bind("suc", TEST_SUCURSAL)
                .bind("nombre", nombre)
                .map(row -> row.get("id", Long.class))
                .one()
                .block();
    }

    /** Inserta un tipo membresía accesos y devuelve su id. */
    protected Long seedTipoAccesos(String nombre, int diasAcceso) {
        return databaseClient.sql(
                "INSERT INTO core.tipos_membresia(id_compania, id_sucursal, nombre, " +
                "modo_control, duracion_tipo, duracion_valor, dias_acceso, precio, activo, creacion_usuario) " +
                "VALUES (:comp, :suc, :nombre, 'accesos', 'meses', 3, :dias, 35.00, true, 'test') RETURNING id")
                .bind("comp", TEST_COMPANIA)
                .bind("suc", TEST_SUCURSAL)
                .bind("nombre", nombre)
                .bind("dias", diasAcceso)
                .map(row -> row.get("id", Long.class))
                .one()
                .block();
    }

    /** Inserta una membresía activa y devuelve su id. */
    protected Long seedMembresia(Long idCliente, Long idTipo, String fechaInicio, String fechaFin) {
        return databaseClient.sql(
                "INSERT INTO core.membresias(id_compania, id_sucursal, id_cliente, id_tipo_membresia, " +
                "fecha_inicio, fecha_fin, precio_pagado, descuento_aplicado, estado, creacion_usuario) " +
                "VALUES (:comp, :suc, :cli, :tipo, :inicio::date, :fin::date, 35.00, 0, 'activa', 'test') RETURNING id")
                .bind("comp", TEST_COMPANIA)
                .bind("suc", TEST_SUCURSAL)
                .bind("cli", idCliente)
                .bind("tipo", idTipo)
                .bind("inicio", fechaInicio)
                .bind("fin", fechaFin)
                .map(row -> row.get("id", Long.class))
                .one()
                .block();
    }

    /**
     * Inserta una membresía en estado PENDIENTE (sin fechas, respetando
     * {@code ck_membresias_fechas_por_estado_pago}) y la retorna vía id.
     */
    protected Long seedMembresiaPendiente(Long idCliente, Long idTipo) {
        return databaseClient.sql(
                "INSERT INTO core.membresias(id_compania, id_sucursal, id_cliente, id_tipo_membresia, " +
                "precio_pagado, descuento_aplicado, estado, estado_pago, creacion_usuario) " +
                "VALUES (:comp, :suc, :cli, :tipo, 35.00, 0, 'activa', 'PENDIENTE', 'test') RETURNING id")
                .bind("comp", TEST_COMPANIA)
                .bind("suc", TEST_SUCURSAL)
                .bind("cli", idCliente)
                .bind("tipo", idTipo)
                .map(row -> row.get("id", Long.class))
                .one()
                .block();
    }

    /**
     * Inserta una membresía PENDIENTE marcada como rechazada (eliminado=true,
     * con motivo/fecha/actor obligatorios por {@code ck_membresias_motivo_si_eliminado}).
     */
    protected Long seedMembresiaRechazada(Long idCliente, Long idTipo, String motivo) {
        return databaseClient.sql(
                "INSERT INTO core.membresias(id_compania, id_sucursal, id_cliente, id_tipo_membresia, " +
                "precio_pagado, descuento_aplicado, estado, estado_pago, eliminado, fecha_eliminacion, " +
                "eliminado_por, motivo_eliminacion, creacion_usuario) " +
                "VALUES (:comp, :suc, :cli, :tipo, 35.00, 0, 'activa', 'PENDIENTE', true, NOW(), " +
                ":usr, :motivo, 'test') RETURNING id")
                .bind("comp", TEST_COMPANIA)
                .bind("suc", TEST_SUCURSAL)
                .bind("cli", idCliente)
                .bind("tipo", idTipo)
                .bind("usr", (int) TEST_USUARIO)
                .bind("motivo", motivo)
                .map(row -> row.get("id", Long.class))
                .one()
                .block();
    }

    /** Inserta una membresía accesos activa y devuelve su id. */
    protected Long seedMembresiaAccesos(Long idCliente, Long idTipo, int diasTotal) {
        return databaseClient.sql(
                "INSERT INTO core.membresias(id_compania, id_sucursal, id_cliente, id_tipo_membresia, " +
                "fecha_inicio, fecha_fin, dias_acceso_total, precio_pagado, descuento_aplicado, estado, creacion_usuario) " +
                "VALUES (:comp, :suc, :cli, :tipo, CURRENT_DATE, CURRENT_DATE + INTERVAL '3 months', " +
                ":dias, 35.00, 0, 'activa', 'test') RETURNING id")
                .bind("comp", TEST_COMPANIA)
                .bind("suc", TEST_SUCURSAL)
                .bind("cli", idCliente)
                .bind("tipo", idTipo)
                .bind("dias", diasTotal)
                .map(row -> row.get("id", Long.class))
                .one()
                .block();
    }

    // ── API call helpers ─────────────────────────────────────────────────────

    /** POST JSON con token Bearer. */
    protected WebTestClient.ResponseSpec post(String uri, String jwt, Object body) {
        return webTestClient.post().uri(uri)
                .header("Authorization", bearer(jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    /** GET con token Bearer. */
    protected WebTestClient.ResponseSpec get(String uri, String jwt) {
        return webTestClient.get().uri(uri)
                .header("Authorization", bearer(jwt))
                .exchange();
    }

    /** PUT JSON con token Bearer. */
    protected WebTestClient.ResponseSpec put(String uri, String jwt, Object body) {
        return webTestClient.put().uri(uri)
                .header("Authorization", bearer(jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    /** PUT sin body con token Bearer. */
    protected WebTestClient.ResponseSpec put(String uri, String jwt) {
        return webTestClient.put().uri(uri)
                .header("Authorization", bearer(jwt))
                .exchange();
    }

    private String buildJwt(String subject, String tipo, String rol, Long idCompania) {
        return buildJwt(subject, tipo, rol, idCompania, null);
    }

    private String buildJwt(String subject, String tipo, String rol, Long idCompania, List<String> permisos) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tipo", tipo);
        if (rol != null)        claims.put("rol_plataforma", rol);
        if (idCompania != null) claims.put("id_compania", idCompania);
        if (permisos != null)   claims.put("permisos", permisos);
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(SECRET_KEY)
                .compact();
    }
}
