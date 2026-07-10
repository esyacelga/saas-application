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
