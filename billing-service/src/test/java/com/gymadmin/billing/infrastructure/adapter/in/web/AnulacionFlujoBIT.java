package com.gymadmin.billing.infrastructure.adapter.in.web;

import com.gymadmin.billing.IntegrationTestBase;
import com.gymadmin.billing.domain.model.sri.RespuestaAutorizacion;
import com.gymadmin.billing.domain.model.sri.RespuestaRecepcion;
import com.gymadmin.billing.domain.port.out.CertificadoRepository;
import com.gymadmin.billing.domain.port.out.SriSoapPort;
import com.gymadmin.billing.domain.port.out.XmlSignaturePort;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.ComprobanteDetalleEntity;
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

/**
 * G3 · Flujo B · Anulación con nota de crédito.
 * <p>
 * Al aprobar se dispara la emisión de una NC (G4) reutilizando el pipeline
 * síncrono G2. Con los puertos externos mockeados a AUTORIZADO, la anulación
 * debe llegar a EJECUTADA y el comprobante original quedar ANULADO en el
 * mismo request.
 */
@DisplayName("G3 · Flujo B · Anulación con nota de crédito")
class AnulacionFlujoBIT extends IntegrationTestBase {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private R2dbcEntityTemplate r2dbcEntityTemplate;

    @Autowired
    private DatabaseClient databaseClient;

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
    void setup() {
        limpiarComprobantes(databaseClient);
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

        // G2 pipeline mocked to AUTORIZADO (mismo patrón que EmitirNotaCreditoIT).
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
                        .numeroAutorizacion("NC-G3-" + System.currentTimeMillis())
                        .fechaAutorizacion(OffsetDateTime.now())
                        .build()));
    }

    @Test
    @DisplayName("solicitar (generar_nota_credito=true) → aprobar → NC AUTORIZADA → EJECUTADA")
    void flujoB_ncAutorizada_ejecutaYCreaReferencia() {
        Long idFactura = insertarFacturaConDetalle("000000601", new BigDecimal("30.00"), LocalDate.now().minusDays(3));

        String staff = staffBearer(ID_COMPANIA);
        String admin = adminBearer(ID_COMPANIA);

        // 1. POST /comprobantes/{id}/anular con generar_nota_credito=true
        String solicitarBody = """
                {
                  "motivo": "Devolución completa del cargo mensual",
                  "codigo_motivo_anulacion": "DEVOLUCION",
                  "generar_nota_credito": true
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
                .jsonPath("$.estado").isEqualTo("SOLICITADA");

        Long idAnulacion = extraerIdAnulacion(idFactura);
        assertThat(idAnulacion).isNotNull();

        // 2. POST /anulaciones/{id}/aprobar → dispara NC (G4) → AUTORIZADO → EJECUTADA
        webTestClient.post()
                .uri("/api/v1/anulaciones/{id}/aprobar", idAnulacion)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody()
                .jsonPath("$.estado").isEqualTo("EJECUTADA")
                .jsonPath("$.id_comprobante_nc").isNotEmpty();

        // Verificar en BD:
        // - Comprobante original ANULADO.
        String estadoComprobante = databaseClient.sql(
                        "SELECT estado FROM facturacion.comprobantes WHERE id = :id")
                .bind("id", idFactura)
                .map(row -> row.get("estado", String.class))
                .one()
                .block();
        assertThat(estadoComprobante).as("factura original tras EJECUTADA").isEqualTo("ANULADO");

        // - Existe una NC tipo 04 referenciando la factura original.
        Long idNc = databaseClient.sql("""
                        SELECT id FROM facturacion.comprobantes
                         WHERE id_compania = :idCompania
                           AND tipo_comprobante = '04'
                           AND id_comprobante_ref = :idFactura
                         ORDER BY created_at DESC LIMIT 1
                        """)
                .bind("idCompania", ID_COMPANIA)
                .bind("idFactura", idFactura)
                .map(row -> row.get("id", Long.class))
                .one()
                .block();
        assertThat(idNc).as("NC generada por el Flujo B").isNotNull();

        // - Existe la fila en notas_credito_referencias apuntando a la NC.
        Long ncRefCount = databaseClient.sql("""
                        SELECT COUNT(*) AS c FROM facturacion.notas_credito_referencias
                         WHERE id_comprobante = :idNc
                        """)
                .bind("idNc", idNc)
                .map(row -> row.get("c", Long.class))
                .one()
                .block();
        assertThat(ncRefCount).as("fila en notas_credito_referencias").isEqualTo(1L);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Long extraerIdAnulacion(Long idFactura) {
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

    private Long insertarFacturaConDetalle(String secuencial, BigDecimal total, LocalDate fechaEmision) {
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
                .estado("AUTORIZADO")
                .idUsuarioRegistro(ID_PERSONA_TEST)
                .build();
        Long idFactura = r2dbcEntityTemplate.insert(ComprobanteEntity.class).using(factura).block().getId();

        // Insertar un detalle: la NC lo va a copiar.
        ComprobanteDetalleEntity detalle = ComprobanteDetalleEntity.builder()
                .idComprobante(idFactura)
                .idCompania(ID_COMPANIA)
                .idSucursal(ID_SUCURSAL)
                .codigoPrincipal("MEM-MENSUAL")
                .descripcion("Membresía mensual")
                .cantidad(new BigDecimal("1.000000"))
                .precioUnitario(total)
                .descuento(BigDecimal.ZERO)
                .precioTotalSinImpuesto(total)
                .orden(1)
                .build();
        r2dbcEntityTemplate.insert(ComprobanteDetalleEntity.class).using(detalle).block();

        return idFactura;
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
