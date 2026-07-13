package com.gymadmin.billing.infrastructure.adapter.in.web;

import com.gymadmin.billing.IntegrationTestBase;
import com.gymadmin.billing.domain.model.sri.RespuestaAutorizacion;
import com.gymadmin.billing.domain.model.sri.RespuestaRecepcion;
import com.gymadmin.billing.domain.port.out.SriSoapPort;
import com.gymadmin.billing.domain.port.out.XmlSignaturePort;
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
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("POST /api/v1/comprobantes/facturas — emitir factura electrónica")
class EmitirFacturaIT extends IntegrationTestBase {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private R2dbcEntityTemplate r2dbcEntityTemplate;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private ComprobanteR2dbcRepository comprobanteRepository;

    // G2 · el pipeline síncrono se dispara dentro del POST. Mockeamos los puertos
    // externos (SRI SOAP, firma XAdES, certificado P12) para no salir a la red real
    // ni requerir un P12 sembrado en la BD de IT.
    @MockitoBean
    private SriSoapPort sriSoapPort;

    @MockitoBean
    private XmlSignaturePort xmlSignaturePort;

    @MockitoBean
    private com.gymadmin.billing.domain.port.out.CertificadoRepository certificadoRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private static final String RUC_TEST = "1790012345001";
    private static final int ID_PERSONA_TEST = 999;
    private static final Random RANDOM = new Random();

    @BeforeEach
    void seedConfigSri() {
        databaseClient.sql("DELETE FROM facturacion.config_sri WHERE id_compania = :idCompania AND id_sucursal = :idSucursal")
                .bind("idCompania", ID_COMPANIA)
                .bind("idSucursal", ID_SUCURSAL)
                .then()
                .block();

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

        r2dbcEntityTemplate.insert(ConfigSriEntity.class)
                .using(config)
                .block();

        // G2 · pipeline síncrono: por defecto happy path AUTORIZADO. Tests
        // individuales sobrescriben estos stubs para simular otros escenarios.
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
                        .numeroAutorizacion("AUT-IT-" + System.currentTimeMillis())
                        .fechaAutorizacion(OffsetDateTime.now())
                        .build()));
    }

    @Test
    @DisplayName("G2 · emite una factura correctamente y llega a estado AUTORIZADO tras el pipeline síncrono")
    void emitirFactura_happyPath_creaComprobante() {
        String bearer = staffBearer();
        String codigoNumerico = randomDigits(9);

        // Desde G5: el servidor asigna el secuencial reservándolo atómicamente contra
        // facturacion.secuenciales. El request ya no lo envía.
        String requestJson = """
                {
                  "tipo_id_receptor": "05",
                  "id_receptor": "1712345678",
                  "razon_social_receptor": "Cliente de Prueba",
                  "email_receptor": "cliente@test.local",
                  "direccion_receptor": "Av. Test 123",
                  "telefono_receptor": "0999999999",
                  "cod_establecimiento": "001",
                  "cod_punto_emision": "001",
                  "codigo_numerico": "%s",
                  "id_sucursal": %d,
                  "detalles": [
                    {
                      "codigo_principal": "PROD001",
                      "descripcion": "Membresía mensual",
                      "cantidad": 1.0,
                      "precio_unitario": 50.00,
                      "descuento": 0.00
                    }
                  ],
                  "pagos": [
                    { "forma_pago": "01", "total": 50.00 }
                  ]
                }
                """.formatted(codigoNumerico, ID_SUCURSAL);

        webTestClient.post()
                .uri("/api/v1/comprobantes/facturas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestJson)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody()
                .jsonPath("$.id").isNotEmpty()
                .jsonPath("$.clave_acceso").value(v -> assertThat(v.toString()).hasSize(49))
                .jsonPath("$.estado").isEqualTo("AUTORIZADO")
                .jsonPath("$.tipo_comprobante").isEqualTo("01")
                .jsonPath("$.ambiente").isEqualTo("1")
                .jsonPath("$.id_compania").isEqualTo(ID_COMPANIA)
                .jsonPath("$.id_sucursal").isEqualTo(ID_SUCURSAL)
                .jsonPath("$.cod_establecimiento").isEqualTo("001")
                .jsonPath("$.cod_punto_emision").isEqualTo("001")
                .jsonPath("$.secuencial").value(v -> assertThat(v.toString())
                        .as("El servidor debe asignar un secuencial de 9 dígitos")
                        .matches("\\d{9}"))
                .jsonPath("$.tipo_id_receptor").isEqualTo("05")
                .jsonPath("$.id_receptor").isEqualTo("1712345678")
                .jsonPath("$.razon_social_receptor").isEqualTo("Cliente de Prueba")
                .jsonPath("$.total").isEqualTo(50.00)
                .jsonPath("$.subtotal_sin_impuesto").isEqualTo(50.00)
                .jsonPath("$.moneda").isEqualTo("DOLAR")
                .jsonPath("$.numero_autorizacion").value(v -> assertThat(v.toString())
                        .as("Debe venir número de autorización devuelto por el mock del SRI")
                        .startsWith("AUT-IT-"));

        StepVerifier.create(comprobanteRepository.findByEmpresa(ID_COMPANIA, ID_SUCURSAL, "AUTORIZADO", 50, 0))
                .recordWith(java.util.ArrayList::new)
                .thenConsumeWhile(c -> true)
                .consumeRecordedWith(items -> assertThat(items)
                        .as("Debe existir al menos un comprobante AUTORIZADO para la empresa de test")
                        .anyMatch(c -> "AUTORIZADO".equals(c.getEstado())
                                && ID_COMPANIA == c.getIdCompania()
                                && c.getSecuencial() != null
                                && c.getSecuencial().matches("\\d{9}")))
                .verifyComplete();
    }

    @Test
    @DisplayName("rechaza con 422 cuando el tipo_id_receptor no existe en sri.tipos_identificacion_comprador (G6)")
    void emitirFactura_tipoIdReceptorInvalido_devuelve422() {
        String bearer = staffBearer();
        String codigoNumerico = randomDigits(9);
        String requestJson = """
                {
                  "tipo_id_receptor": "99",
                  "id_receptor": "1712345678",
                  "razon_social_receptor": "Cliente Test",
                  "cod_establecimiento": "001",
                  "cod_punto_emision": "001",
                  "codigo_numerico": "%s",
                  "id_sucursal": %d,
                  "detalles": [
                    { "codigo_principal": "PROD001", "descripcion": "Membresía", "cantidad": 1.0, "precio_unitario": 50.00, "descuento": 0.00 }
                  ],
                  "pagos": [
                    { "forma_pago": "01", "total": 50.00 }
                  ]
                }
                """.formatted(codigoNumerico, ID_SUCURSAL);

        webTestClient.post()
                .uri("/api/v1/comprobantes/facturas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestJson)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
                .expectBody()
                .jsonPath("$.message").value(v -> assertThat(v.toString())
                        .as("mensaje debe indicar el tipo de identificación no reconocido")
                        .contains("Tipo de identificación"));
    }

    @Test
    @DisplayName("rechaza con 422 cuando alguna forma_pago no existe en sri.formas_pago (G6)")
    void emitirFactura_formaPagoInvalida_devuelve422() {
        String bearer = staffBearer();
        String codigoNumerico = randomDigits(9);
        String requestJson = """
                {
                  "tipo_id_receptor": "05",
                  "id_receptor": "1712345678",
                  "razon_social_receptor": "Cliente Test",
                  "cod_establecimiento": "001",
                  "cod_punto_emision": "001",
                  "codigo_numerico": "%s",
                  "id_sucursal": %d,
                  "detalles": [
                    { "codigo_principal": "PROD001", "descripcion": "Membresía", "cantidad": 1.0, "precio_unitario": 50.00, "descuento": 0.00 }
                  ],
                  "pagos": [
                    { "forma_pago": "99", "total": 50.00 }
                  ]
                }
                """.formatted(codigoNumerico, ID_SUCURSAL);

        webTestClient.post()
                .uri("/api/v1/comprobantes/facturas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestJson)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
                .expectBody()
                .jsonPath("$.message").value(v -> assertThat(v.toString())
                        .as("mensaje debe indicar la forma de pago no reconocida")
                        .contains("Forma de pago"));
    }

    private String staffBearer() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        return Jwts.builder()
                .subject("test-staff")
                .claim("tipo", "staff")
                .claim("id_compania", ID_COMPANIA)
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
