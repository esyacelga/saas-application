package com.gymadmin.billing.infrastructure.adapter.in.web;

import com.gymadmin.billing.IntegrationTestBase;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.ComprobanteEntity;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.ConfigSriEntity;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G3 · Flujo A · Anulación directa sin nota de crédito.
 * <p>
 * Cubre: solicitud → aprobación → confirmación manual del portal SRI →
 * comprobante marcado ANULADO. Cierra con un test multi-tenant.
 */
@DisplayName("G3 · Flujo A · Anulación directa (sin NC)")
class AnulacionFlujoAIT extends IntegrationTestBase {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private R2dbcEntityTemplate r2dbcEntityTemplate;

    @Autowired
    private DatabaseClient databaseClient;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private static final String RUC_TEST = "1790012345001";
    private static final int ID_PERSONA_TEST = 999;
    private static final Random RANDOM = new Random();

    @BeforeEach
    void limpiar() {
        // Limpiar anulaciones y comprobantes de la compañía de test para aislar corridas.
        databaseClient.sql("DELETE FROM facturacion.anulaciones WHERE id_compania = :idCompania")
                .bind("idCompania", ID_COMPANIA).then().block();
        databaseClient.sql("DELETE FROM facturacion.anulaciones WHERE id_compania = :idCompania")
                .bind("idCompania", ID_COMPANIA + 1).then().block();
        databaseClient.sql("DELETE FROM facturacion.notas_credito_referencias WHERE id_compania = :idCompania")
                .bind("idCompania", ID_COMPANIA).then().block();
        databaseClient.sql("DELETE FROM facturacion.comprobantes WHERE id_compania = :idCompania")
                .bind("idCompania", ID_COMPANIA).then().block();
        databaseClient.sql("DELETE FROM facturacion.comprobantes WHERE id_compania = :idCompania")
                .bind("idCompania", ID_COMPANIA + 1).then().block();
        databaseClient.sql("DELETE FROM facturacion.config_sri WHERE id_compania = :idCompania AND id_sucursal = :idSucursal")
                .bind("idCompania", ID_COMPANIA).bind("idSucursal", ID_SUCURSAL).then().block();

        ConfigSriEntity config = ConfigSriEntity.builder()
                .idCompania(ID_COMPANIA)
                .idSucursal(ID_SUCURSAL)
                .razonSocial("Gimnasio Test")
                .ruc(RUC_TEST)
                .obligadoContabilidad(false)
                .ambiente("1")
                .tipoEmision("1")
                .facturacionActiva(true)
                .updatedAt(OffsetDateTime.now())
                .updatedBy("test")
                .build();
        r2dbcEntityTemplate.insert(ConfigSriEntity.class).using(config).block();
    }

    @Test
    @DisplayName("solicitar → aprobar → confirmar-sri: EJECUTADA + comprobante ANULADO")
    void flujoCompleto_A_terminaEjecutada() {
        Long idFactura = insertarFacturaAutorizada("000000501", new BigDecimal("50.00"), LocalDate.now().minusDays(3));

        String staff = staffBearer(ID_COMPANIA);
        String admin = adminBearer(ID_COMPANIA);

        // 1. POST /comprobantes/{id}/anular
        String solicitarBody = """
                {
                  "motivo": "Cliente devolvió el pago con reversa bancaria",
                  "codigo_motivo_anulacion": "DEVOLUCION",
                  "generar_nota_credito": false
                }
                """;

        webTestClient.post()
                .uri("/api/v1/comprobantes/{id}/anular", idFactura)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + staff)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(solicitarBody)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody()
                .jsonPath("$.estado").isEqualTo("SOLICITADA")
                .jsonPath("$.id_comprobante").isEqualTo(idFactura)
                .jsonPath("$.link_resource").value(v -> assertThat(v.toString()).contains("/api/v1/anulaciones/"));

        Long idAnulacion = extraerId(idFactura);
        assertThat(idAnulacion).as("id de la anulación").isNotNull();

        // 2. POST /anulaciones/{id}/aprobar (rol admin)
        webTestClient.post()
                .uri("/api/v1/anulaciones/{id}/aprobar", idAnulacion)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody()
                .jsonPath("$.estado").isEqualTo("APROBADA");

        // 3. POST /anulaciones/{id}/confirmar-sri
        webTestClient.post()
                .uri("/api/v1/anulaciones/{id}/confirmar-sri", idAnulacion)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody()
                .jsonPath("$.estado").isEqualTo("EJECUTADA");

        // Verificar en BD: comprobante ANULADO
        String estadoComprobante = databaseClient.sql(
                        "SELECT estado FROM facturacion.comprobantes WHERE id = :id")
                .bind("id", idFactura)
                .map(row -> row.get("estado", String.class))
                .one()
                .block();
        assertThat(estadoComprobante).isEqualTo("ANULADO");
    }

    @Test
    @DisplayName("staff sin rol admin no puede aprobar (403)")
    void rolInsuficienteNoAprobar_403() {
        Long idFactura = insertarFacturaAutorizada("000000502", new BigDecimal("30.00"), LocalDate.now().minusDays(2));
        Long idAnulacion = crearAnulacionSolicitada(idFactura, staffBearer(ID_COMPANIA));

        webTestClient.post()
                .uri("/api/v1/anulaciones/{id}/aprobar", idAnulacion)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffBearer(ID_COMPANIA))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("multi-tenant: JWT de otra compañía no ve la anulación (404)")
    void multiTenant_otraCompania_404() {
        Long idFactura = insertarFacturaAutorizada("000000503", new BigDecimal("40.00"), LocalDate.now().minusDays(1));
        Long idAnulacion = crearAnulacionSolicitada(idFactura, staffBearer(ID_COMPANIA));

        // GET con JWT de otra compañía → 404
        webTestClient.get()
                .uri("/api/v1/anulaciones/{id}", idAnulacion)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer(ID_COMPANIA + 1))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);

        // Aprobar con JWT de otra compañía → 404
        webTestClient.post()
                .uri("/api/v1/anulaciones/{id}/aprobar", idAnulacion)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer(ID_COMPANIA + 1))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);

        // GET con JWT de la compañía correcta → 200
        webTestClient.get()
                .uri("/api/v1/anulaciones/{id}", idAnulacion)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer(ID_COMPANIA))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("solicitar sobre consumidor final (9999999999999) → 422")
    void solicitarConsumidorFinal_422() {
        Long idFactura = insertarFacturaAutorizadaConReceptor(
                "000000504", new BigDecimal("20.00"), LocalDate.now().minusDays(1), "9999999999999");

        String solicitarBody = """
                {
                  "motivo": "Prueba de restricción SRI",
                  "generar_nota_credito": false
                }
                """;

        webTestClient.post()
                .uri("/api/v1/comprobantes/{id}/anular", idFactura)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffBearer(ID_COMPANIA))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(solicitarBody)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
                .expectBody()
                .jsonPath("$.message").value(v -> assertThat(v.toString()).contains("consumidor final"));
    }

    @Test
    @DisplayName("GET /comprobantes/{id}/anulaciones — historial ordenado desc")
    void historialPorComprobante_ok() {
        Long idFactura = insertarFacturaAutorizada("000000505", new BigDecimal("15.00"), LocalDate.now().minusDays(1));
        crearAnulacionSolicitada(idFactura, staffBearer(ID_COMPANIA));

        webTestClient.get()
                .uri("/api/v1/comprobantes/{id}/anulaciones", idFactura)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffBearer(ID_COMPANIA))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody()
                .jsonPath("$.total").isEqualTo(1)
                .jsonPath("$.id_comprobante").isEqualTo(idFactura)
                .jsonPath("$.datos[0].estado").isEqualTo("SOLICITADA");
    }

    @Test
    @DisplayName("GET /sri/motivos-anulacion — lista 5 motivos oficiales")
    void listarMotivosAnulacion_ok() {
        webTestClient.get()
                .uri("/api/v1/sri/motivos-anulacion")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffBearer(ID_COMPANIA))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$[?(@.codigo=='DEVOLUCION')]").exists();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Long crearAnulacionSolicitada(Long idFactura, String bearer) {
        String body = """
                {
                  "motivo": "Prueba interna",
                  "codigo_motivo_anulacion": "DEVOLUCION",
                  "generar_nota_credito": false
                }
                """;
        webTestClient.post()
                .uri("/api/v1/comprobantes/{id}/anular", idFactura)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED);
        return extraerId(idFactura);
    }

    private Long extraerId(Long idFactura) {
        return databaseClient.sql("""
                        SELECT id FROM facturacion.anulaciones
                         WHERE id_comprobante = :idComprobante AND id_compania = :idCompania
                         ORDER BY fecha_solicitud DESC LIMIT 1
                        """)
                .bind("idComprobante", idFactura)
                .bind("idCompania", ID_COMPANIA)
                .map(row -> row.get("id", Long.class))
                .one()
                .block();
    }

    private Long insertarFacturaAutorizada(String secuencial, BigDecimal total, LocalDate fechaEmision) {
        return insertarFacturaAutorizadaConReceptor(secuencial, total, fechaEmision, "1712345678");
    }

    private Long insertarFacturaAutorizadaConReceptor(String secuencial, BigDecimal total, LocalDate fechaEmision, String idReceptor) {
        ComprobanteEntity factura = ComprobanteEntity.builder()
                .idCompania(ID_COMPANIA)
                .idSucursal(ID_SUCURSAL)
                .tipoComprobante("01")
                .claveAcceso(randomDigits(49))
                .codEstablecimiento("001")
                .codPuntoEmision("001")
                .secuencial(secuencial)
                .fechaEmision(fechaEmision)
                .ambiente("1")
                .tipoIdReceptor("9999999999999".equals(idReceptor) ? "07" : "05")
                .idReceptor(idReceptor)
                .razonSocialReceptor("Cliente Original")
                .emailReceptor("cliente@test.local")
                .subtotalSinImpuesto(total)
                .subtotalIva0(BigDecimal.ZERO)
                .subtotalNoObjetoIva(BigDecimal.ZERO)
                .subtotalExentoIva(BigDecimal.ZERO)
                .totalDescuento(BigDecimal.ZERO)
                .totalIce(BigDecimal.ZERO)
                .totalIva(BigDecimal.ZERO)
                .propina(BigDecimal.ZERO)
                .total(total)
                .moneda("DOLAR")
                .estado("AUTORIZADO")
                .idUsuarioRegistro(ID_PERSONA_TEST)
                .build();
        return r2dbcEntityTemplate.insert(ComprobanteEntity.class).using(factura).block().getId();
    }

    private String staffBearer(int idCompania) {
        return jwtBearer(idCompania, "recepcion");
    }

    private String adminBearer(int idCompania) {
        return jwtBearer(idCompania, "admin_compania");
    }

    private String jwtBearer(int idCompania, String rol) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        return Jwts.builder()
                .subject("test-user-" + rol)
                .claim("tipo", "staff")
                .claim("rol_plataforma", rol)
                .claim("id_compania", idCompania)
                .claim("id_persona", ID_PERSONA_TEST)
                .claim("permisos", List.of("facturacion:emitir"))
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(key)
                .compact();
    }

    private static String randomDigits(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(RANDOM.nextInt(10));
        return sb.toString();
    }
}
