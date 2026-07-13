package com.gymadmin.billing.infrastructure.adapter.in.web;

import com.gymadmin.billing.IntegrationTestBase;
import com.gymadmin.billing.domain.model.sri.RespuestaAutorizacion;
import com.gymadmin.billing.domain.model.sri.RespuestaRecepcion;
import com.gymadmin.billing.domain.port.out.CertificadoRepository;
import com.gymadmin.billing.domain.port.out.SriSoapPort;
import com.gymadmin.billing.domain.port.out.XmlSignaturePort;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.ComprobanteEntity;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.ConfigSriEntity;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.repository.ComprobanteR2dbcRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("POST /api/v1/notas-credito — G4 emisión de notas de crédito")
class EmitirNotaCreditoIT extends IntegrationTestBase {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private R2dbcEntityTemplate r2dbcEntityTemplate;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private ComprobanteR2dbcRepository comprobanteRepository;

    @MockitoBean
    private SriSoapPort sriSoapPort;

    @MockitoBean
    private XmlSignaturePort xmlSignaturePort;

    @MockitoBean
    private CertificadoRepository certificadoRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private static final String RUC_TEST = "1790012345001";
    private static final int ID_PERSONA_TEST = 999;
    private static final Random RANDOM = new Random();

    @BeforeEach
    void seedConfigSri() {
        // Limpieza de comprobantes y NC de la compañía de test para aislar corridas.
        limpiarComprobantes(databaseClient);
        databaseClient.sql("DELETE FROM facturacion.config_sri WHERE id_compania = :idCompania AND id_sucursal = :idSucursal")
                .bind("idCompania", ID_COMPANIA)
                .bind("idSucursal", ID_SUCURSAL).then().block();

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

        // Mocks del pipeline síncrono — happy path AUTORIZADO.
        when(certificadoRepository.getActiveCertificateContent(anyInt(), anyInt()))
                .thenReturn(Mono.just(new byte[]{1, 2, 3}));
        when(certificadoRepository.getActiveCertificatePassword(anyInt(), anyInt()))
                .thenReturn(Mono.just("password"));
        when(xmlSignaturePort.sign(anyString(), org.mockito.ArgumentMatchers.any(byte[].class), anyString()))
                .thenReturn(Mono.just("<signed>xml</signed>"));
        when(sriSoapPort.enviarComprobante(anyString(), anyString()))
                .thenReturn(Mono.just(RespuestaRecepcion.builder().estado("RECIBIDA").build()));
        when(sriSoapPort.autorizarComprobante(anyString(), anyString()))
                .thenReturn(Mono.just(RespuestaAutorizacion.builder()
                        .estado("AUTORIZADO")
                        .numeroAutorizacion("NC-AUT-" + System.currentTimeMillis())
                        .fechaAutorizacion(OffsetDateTime.now())
                        .build()));
    }

    @Test
    @DisplayName("happy path · emite NC, alcanza AUTORIZADO y persiste la referencia en notas_credito_referencias")
    void emitirNotaCredito_happyPath() {
        Long idFactura = insertarFacturaAutorizadaTest("000000123", new BigDecimal("50.00"));

        String bearer = staffBearer();
        String codigoNumerico = randomDigits(9);

        String requestJson = """
                {
                  "id_sucursal": %d,
                  "cod_establecimiento": "001",
                  "cod_punto_emision": "001",
                  "codigo_numerico": "%s",
                  "id_factura_original": %d,
                  "codigo_motivo": "DEVOLUCION",
                  "razon": "Devolución mensual por error de cargo",
                  "valor_modificacion": 30.00,
                  "detalles": [
                    {
                      "codigo_principal": "MEM-MENSUAL",
                      "descripcion": "Membresía mensual (ajuste)",
                      "cantidad": 1.0,
                      "precio_unitario": 30.00,
                      "descuento": 0.00
                    }
                  ]
                }
                """.formatted(ID_SUCURSAL, codigoNumerico, idFactura);

        webTestClient.post()
                .uri("/api/v1/notas-credito")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestJson)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody()
                .jsonPath("$.id").isNotEmpty()
                .jsonPath("$.tipo_comprobante").isEqualTo("04")
                .jsonPath("$.estado").isEqualTo("AUTORIZADO")
                .jsonPath("$.clave_acceso").value(v -> assertThat(v.toString()).hasSize(49))
                // La clave de acceso posición 9-10 debe ser el tipo comprobante "04"
                .jsonPath("$.clave_acceso").value(v -> assertThat(v.toString().substring(8, 10)).isEqualTo("04"))
                .jsonPath("$.secuencial").value(v -> assertThat(v.toString()).matches("\\d{9}"))
                .jsonPath("$.total").isEqualTo(30.00)
                .jsonPath("$.id_compania").isEqualTo(ID_COMPANIA)
                .jsonPath("$.numero_autorizacion").value(v -> assertThat(v.toString()).startsWith("NC-AUT-"));

        // Verificar fila en notas_credito_referencias — clave del test.
        Long referenciaCount = databaseClient.sql("""
                        SELECT COUNT(*) AS c FROM facturacion.notas_credito_referencias
                        WHERE id_compania = :idCompania AND num_doc_modificado = :num
                        """)
                .bind("idCompania", ID_COMPANIA)
                .bind("num", "001-001-000000123")
                .map(row -> row.get("c", Long.class))
                .one()
                .block();
        assertThat(referenciaCount).as("debe existir una fila en notas_credito_referencias").isEqualTo(1L);

        Long ncIdComprobanteRef = databaseClient.sql("""
                        SELECT id_comprobante_ref FROM facturacion.comprobantes
                        WHERE id_compania = :idCompania AND tipo_comprobante = '04'
                        ORDER BY created_at DESC LIMIT 1
                        """)
                .bind("idCompania", ID_COMPANIA)
                .map(row -> row.get("id_comprobante_ref", Long.class))
                .one()
                .block();
        assertThat(ncIdComprobanteRef)
                .as("la NC recién emitida debe apuntar a la factura original")
                .isEqualTo(idFactura);
    }

    @Test
    @DisplayName("404 si la factura original no existe")
    void emitirNotaCredito_facturaInexistente_404() {
        String bearer = staffBearer();
        String codigoNumerico = randomDigits(9);
        String requestJson = """
                {
                  "id_sucursal": %d,
                  "cod_establecimiento": "001",
                  "cod_punto_emision": "001",
                  "codigo_numerico": "%s",
                  "id_factura_original": 99999999999,
                  "codigo_motivo": "DEVOLUCION",
                  "razon": "Prueba",
                  "valor_modificacion": 10.00,
                  "detalles": [
                    { "codigo_principal": "X", "descripcion": "x", "cantidad": 1.0, "precio_unitario": 10.00, "descuento": 0.00 }
                  ]
                }
                """.formatted(ID_SUCURSAL, codigoNumerico);

        webTestClient.post()
                .uri("/api/v1/notas-credito")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestJson)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("422 si la factura original no está AUTORIZADO")
    void emitirNotaCredito_facturaNoAutorizada_422() {
        Long idFactura = insertarFacturaEstadoTest("000000124", new BigDecimal("50.00"), "GENERADO");

        String bearer = staffBearer();
        String codigoNumerico = randomDigits(9);
        String requestJson = """
                {
                  "id_sucursal": %d,
                  "cod_establecimiento": "001",
                  "cod_punto_emision": "001",
                  "codigo_numerico": "%s",
                  "id_factura_original": %d,
                  "codigo_motivo": "DEVOLUCION",
                  "razon": "Prueba",
                  "valor_modificacion": 10.00,
                  "detalles": [
                    { "codigo_principal": "X", "descripcion": "x", "cantidad": 1.0, "precio_unitario": 10.00, "descuento": 0.00 }
                  ]
                }
                """.formatted(ID_SUCURSAL, codigoNumerico, idFactura);

        webTestClient.post()
                .uri("/api/v1/notas-credito")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestJson)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
                .expectBody()
                .jsonPath("$.message").value(v -> assertThat(v.toString()).contains("AUTORIZADO"));
    }

    @Test
    @DisplayName("422 si el motivo no existe en sri.motivos_anulacion_nc")
    void emitirNotaCredito_motivoInvalido_422() {
        Long idFactura = insertarFacturaAutorizadaTest("000000125", new BigDecimal("50.00"));

        String bearer = staffBearer();
        String codigoNumerico = randomDigits(9);
        String requestJson = """
                {
                  "id_sucursal": %d,
                  "cod_establecimiento": "001",
                  "cod_punto_emision": "001",
                  "codigo_numerico": "%s",
                  "id_factura_original": %d,
                  "codigo_motivo": "NO_EXISTE_XYZ",
                  "razon": "x",
                  "valor_modificacion": 10.00,
                  "detalles": [
                    { "codigo_principal": "X", "descripcion": "x", "cantidad": 1.0, "precio_unitario": 10.00, "descuento": 0.00 }
                  ]
                }
                """.formatted(ID_SUCURSAL, codigoNumerico, idFactura);

        webTestClient.post()
                .uri("/api/v1/notas-credito")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestJson)
                .exchange()
                // Motivo -> NotFoundException del catálogo => 404
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("multi-tenant: la NC emitida es visible con el JWT de la compañía correcta")
    void emitirNotaCredito_esVisibleParaMismaCompania() {
        Long idFactura = insertarFacturaAutorizadaTest("000000126", new BigDecimal("50.00"));
        String bearer = staffBearer();
        String codigoNumerico = randomDigits(9);

        String requestJson = """
                {
                  "id_sucursal": %d,
                  "cod_establecimiento": "001",
                  "cod_punto_emision": "001",
                  "codigo_numerico": "%s",
                  "id_factura_original": %d,
                  "codigo_motivo": "DESCUENTO",
                  "razon": "Descuento por fidelidad",
                  "valor_modificacion": 5.00,
                  "detalles": [
                    { "codigo_principal": "X", "descripcion": "x", "cantidad": 1.0, "precio_unitario": 5.00, "descuento": 0.00 }
                  ]
                }
                """.formatted(ID_SUCURSAL, codigoNumerico, idFactura);

        webTestClient.post()
                .uri("/api/v1/notas-credito")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestJson)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED);

        // Recuperamos el ID de la NC recién emitida desde BD (el body ya vino OK arriba).
        Long ncId = databaseClient.sql("""
                        SELECT id FROM facturacion.comprobantes
                        WHERE id_compania = :idCompania AND tipo_comprobante = '04'
                        ORDER BY created_at DESC LIMIT 1
                        """)
                .bind("idCompania", ID_COMPANIA)
                .map(row -> row.get("id", Long.class))
                .one()
                .block();

        // GET con el mismo JWT: 200
        webTestClient.get()
                .uri("/api/v1/notas-credito/{id}", ncId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody()
                .jsonPath("$.tipo_comprobante").isEqualTo("04");

        // GET con JWT de otra compañía: 404
        String otroBearer = bearerConCompania(ID_COMPANIA + 1);
        webTestClient.get()
                .uri("/api/v1/notas-credito/{id}", ncId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otroBearer)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- helpers ----

    /** Inserta una factura AUTORIZADO en la BD y devuelve su ID. */
    private Long insertarFacturaAutorizadaTest(String secuencial, BigDecimal total) {
        return insertarFacturaEstadoTest(secuencial, total, "AUTORIZADO");
    }

    private Long insertarFacturaEstadoTest(String secuencial, BigDecimal total, String estado) {
        ComprobanteEntity factura = ComprobanteEntity.builder()
                .idCompania(ID_COMPANIA)
                .idSucursal(ID_SUCURSAL)
                .tipoComprobante("01")
                .claveAcceso(randomDigits(49))
                .codEstablecimiento("001")
                .codPuntoEmision("001")
                .secuencial(secuencial)
                .fechaEmision(LocalDate.now().minusDays(3))
                .ambiente("1")
                .tipoIdReceptor("05")
                .idReceptor("1712345678")
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
                .estado(estado)
                .idUsuarioRegistro(ID_PERSONA_TEST)
                .build();

        return r2dbcEntityTemplate.insert(ComprobanteEntity.class).using(factura).block().getId();
    }

    private String staffBearer() {
        return bearerConCompania(ID_COMPANIA);
    }

    private String bearerConCompania(int idCompania) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        return Jwts.builder()
                .subject("test-staff")
                .claim("tipo", "staff")
                .claim("id_compania", idCompania)
                .claim("id_persona", ID_PERSONA_TEST)
                .claim("permisos", List.of("facturacion:emitir"))
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(key)
                .compact();
    }

    private static String randomDigits(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
