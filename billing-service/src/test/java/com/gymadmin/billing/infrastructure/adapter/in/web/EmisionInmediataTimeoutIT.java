package com.gymadmin.billing.infrastructure.adapter.in.web;

import com.gymadmin.billing.IntegrationTestBase;
import com.gymadmin.billing.domain.model.sri.RespuestaAutorizacion;
import com.gymadmin.billing.domain.model.sri.RespuestaRecepcion;
import com.gymadmin.billing.domain.port.out.CertificadoRepository;
import com.gymadmin.billing.domain.port.out.SriSoapPort;
import com.gymadmin.billing.domain.port.out.XmlSignaturePort;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * G2 · Verifica que cuando el SRI no responde dentro del timeout
 * ({@code sri.timeout.envio-seconds}) el response llega con estado
 * {@code ERROR} y se encola en {@code facturacion.cola_envio} con
 * {@code proxima_ejecucion ≈ now()} para reintento inmediato.
 */
@DirtiesContext
@DisplayName("POST /api/v1/comprobantes/facturas — G2 timeout de emisión inmediata")
class EmisionInmediataTimeoutIT extends IntegrationTestBase {

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

    @DynamicPropertySource
    static void overrideTimeout(DynamicPropertyRegistry registry) {
        // Timeout corto para no bloquear el suite completo. Suficiente margen
        // para que el pipeline de firma+persist+update funcione en <2 s en el
        // happy path; en este IT forzamos que el mock del SRI tarde 6 s.
        registry.add("sri.timeout.envio-seconds", () -> 2);
    }

    @BeforeEach
    void seedYMocks() {
        databaseClient.sql("DELETE FROM facturacion.cola_envio WHERE id_compania = :idCompania")
                .bind("idCompania", ID_COMPANIA)
                .then()
                .block();
        databaseClient.sql("DELETE FROM facturacion.config_sri WHERE id_compania = :idCompania AND id_sucursal = :idSucursal")
                .bind("idCompania", ID_COMPANIA)
                .bind("idSucursal", ID_SUCURSAL)
                .then()
                .block();

        r2dbcEntityTemplate.insert(ConfigSriEntity.class)
                .using(ConfigSriEntity.builder()
                        .idCompania(ID_COMPANIA)
                        .idSucursal(ID_SUCURSAL)
                        .razonSocial("Gimnasio Test Timeout")
                        .ruc(RUC_TEST)
                        .obligadoContabilidad(false)
                        .ambiente("1")
                        .tipoEmision("1")
                        .facturacionActiva(true)
                        .updatedAt(OffsetDateTime.now())
                        .updatedBy("test")
                        .build())
                .block();

        when(certificadoRepository.getActiveCertificateContent(anyInt(), anyInt()))
                .thenReturn(Mono.just(new byte[]{1, 2, 3}));
        when(certificadoRepository.getActiveCertificatePassword(anyInt(), anyInt()))
                .thenReturn(Mono.just("password"));
        when(xmlSignaturePort.sign(anyString(), any(byte[].class), anyString()))
                .thenReturn(Mono.just("<signed>xml</signed>"));

        // Delay largo → dispara el timeout de 2 s.
        when(sriSoapPort.enviarComprobante(anyString(), anyString()))
                .thenReturn(Mono.delay(Duration.ofSeconds(6))
                        .then(Mono.just(RespuestaRecepcion.builder().estado("RECIBIDA").build())));
        // Ni siquiera se debería llegar aquí en el timeout, pero por seguridad:
        when(sriSoapPort.autorizarComprobante(anyString(), anyString()))
                .thenReturn(Mono.just(RespuestaAutorizacion.builder().estado("AUTORIZADO").build()));
    }

    @Test
    @DisplayName("timeout · devuelve HTTP 201 con estado ERROR y encola con proxima_ejecucion≈now()")
    void emitirFactura_timeoutSRI_devuelveErrorYEncolaInmediato() {
        String bearer = staffBearer();
        String codigoNumerico = randomDigits(9);
        String requestJson = """
                {
                  "tipo_id_receptor": "05",
                  "id_receptor": "1712345678",
                  "razon_social_receptor": "Cliente Timeout",
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

        AtomicReference<Long> idComprobanteRef = new AtomicReference<>();

        webTestClient.post()
                .uri("/api/v1/comprobantes/facturas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestJson)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody()
                .jsonPath("$.estado").isEqualTo("ERROR")
                .jsonPath("$.id").value(v -> idComprobanteRef.set(Long.valueOf(v.toString())));

        assertThat(idComprobanteRef.get())
                .as("El id del comprobante debe venir en el response para poder verificar la cola")
                .isNotNull();

        // Verificar cola_envio: PENDIENTE, proxima_ejecucion ≈ now() (dentro de 30 s)
        List<ColaRow> filas = databaseClient.sql(
                "SELECT estado, proxima_ejecucion, ultimo_error FROM facturacion.cola_envio WHERE id_comprobante = :idComprobante")
                .bind("idComprobante", idComprobanteRef.get())
                .map((row, meta) -> new ColaRow(
                        row.get("estado", String.class),
                        row.get("proxima_ejecucion", OffsetDateTime.class),
                        row.get("ultimo_error", String.class)))
                .all()
                .collectList()
                .block();

        assertThat(filas).as("Debe existir una fila en cola_envio para el comprobante").hasSize(1);
        ColaRow row = filas.get(0);
        assertThat(row.estado).isEqualTo("PENDIENTE");
        assertThat(row.proximaEjecucion)
                .as("proxima_ejecucion debe estar cerca de now() (no backoff de 1 min)")
                .isBefore(OffsetDateTime.now().plusSeconds(30));
        assertThat(row.ultimoError).as("ultimo_error debe indicar timeout").contains("Timeout");
    }

    private record ColaRow(String estado, OffsetDateTime proximaEjecucion, String ultimoError) {}

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
