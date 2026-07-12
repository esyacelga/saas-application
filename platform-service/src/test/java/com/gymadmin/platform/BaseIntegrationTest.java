package com.gymadmin.platform;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = DotEnvInitializer.class)
public abstract class BaseIntegrationTest {

    @Value("${jwt.secret}")
    private String jwtSecretBase64;

    private SecretKey secretKey;

    @Autowired
    protected WebTestClient webTestClient;

    @Autowired
    protected DatabaseClient databaseClient;

    @BeforeEach
    void setUp() {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecretBase64));
        databaseClient.sql("DELETE FROM tenant.notificaciones_suscripcion").then().block();
        databaseClient.sql("DELETE FROM tenant.config_notif_suscripcion").then().block();
        databaseClient.sql("DELETE FROM tenant.pagos_suscripcion").then().block();
        databaseClient.sql("DELETE FROM tenant.pagos_pendientes_validacion").then().block();
        databaseClient.sql("DELETE FROM tenant.compania_planes").then().block();
        databaseClient.sql("DELETE FROM tenant.sucursales").then().block();
        databaseClient.sql("DELETE FROM saas.actividad_plataforma WHERE id_compania IS NOT NULL").then().block();
        databaseClient.sql("DELETE FROM tenant.companias").then().block();
        databaseClient.sql("DELETE FROM saas.plan_caracteristicas").then().block();
        databaseClient.sql("DELETE FROM saas.planes").then().block();
        databaseClient.sql("DELETE FROM saas.caracteristicas").then().block();
        databaseClient.sql("DELETE FROM seguridad.bitacora_accesos").then().block();
        databaseClient.sql("DELETE FROM seguridad.usuarios").then().block();
        databaseClient.sql("DELETE FROM seguridad.rol_permisos").then().block();
        databaseClient.sql("DELETE FROM seguridad.permisos").then().block();
        databaseClient.sql("DELETE FROM seguridad.roles").then().block();
        databaseClient.sql("DELETE FROM identidad.personas WHERE ci LIKE 'IT-%'").then().block();

        // REQ-SAAS-001 Sub-fase 1.6 item #4: el comprobante ahora es opcional en el
        // reporte de pago del owner. La migración que hace comprobante_url NULLABLE
        // aún NO fue aplicada al DDL local (pendiente changeset del DBA). Aquí lo
        // afinamos in-place para el entorno de test (ALTER idempotente).
        databaseClient.sql(
                "ALTER TABLE tenant.pagos_pendientes_validacion " +
                "ALTER COLUMN comprobante_url DROP NOT NULL")
                .then()
                .onErrorResume(err -> reactor.core.publisher.Mono.empty())
                .block();
    }

    protected Long crearPersona(String ci, String nombre) {
        return databaseClient.sql("""
                INSERT INTO identidad.personas (ci, nombre, creacion_fecha, creacion_usuario)
                VALUES (:ci, :nombre, NOW(), 'test')
                RETURNING id
                """)
                .bind("ci", ci)
                .bind("nombre", nombre)
                .map((row, meta) -> row.get("id", Long.class))
                .one().block();
    }

    // ── JWT helpers ──────────────────────────────────────────────────────────

    protected String jwtSuperAdmin() {
        return buildJwt("user-super-admin", "plataforma", "super_admin", null);
    }

    protected String jwtSoporte() {
        return buildJwt("user-soporte", "plataforma", "soporte", null);
    }

    protected String jwtAdminCompania(Long idCompania) {
        return buildJwt("user-admin-compania", "staff", "admin_compania", idCompania);
    }

    /**
     * REQ-SAAS-001 (Sub-fase 1.4): token owner/admin del tenant — usado por
     * los endpoints {@code POST /companias/{id}/pagos/reportar} y
     * {@code POST /companias/{id}/suscripcion/trial}. El principal debe pasar
     * {@link com.gymadmin.platform.application.service.AccessControlService#requireOwnerOrAdminOfCompania}.
     */
    protected String jwtOwnerCompania(Long idCompania) {
        return buildJwt("user-owner-compania", "staff", "admin_compania", idCompania);
    }

    protected String bearerHeader(String jwt) {
        return "Bearer " + jwt;
    }

    private String buildJwt(String subject, String tipo, String rolPlataforma, Long idCompania) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tipo", tipo);
        if (rolPlataforma != null) {
            claims.put("rol_plataforma", rolPlataforma);
        }
        if (idCompania != null) {
            claims.put("id_compania", idCompania);
        }

        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(secretKey)
                .compact();
    }
}
