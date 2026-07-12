package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.model.PagoPendienteValidacion;
import com.gymadmin.platform.domain.port.in.ReportarPagoUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaRepository;
import com.gymadmin.platform.infrastructure.adapter.in.web.PagoOwnerController;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import com.gymadmin.platform.infrastructure.ratelimit.PostgresRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-SAAS-001 (Sub-fase 1.6, item #4): tests del {@link PagoOwnerController}
 * verificando que la parte multipart {@code comprobante} pasa a ser opcional
 * y que la respuesta 201 incluye {@code nombreCompania} y
 * {@code comprobanteUrl = null} cuando no se envía el archivo.
 */
@DisplayName("PagoOwnerController — POST /api/v1/companias/{id}/pagos/reportar")
class PagoOwnerControllerTest {

    private final ReportarPagoUseCase reportarPagoUseCase = mock(ReportarPagoUseCase.class);
    private final AccessControlService accessControl = new AccessControlService();
    private final PostgresRateLimiter rateLimiter = mock(PostgresRateLimiter.class);
    private final CompaniaRepository companiaRepository = mock(CompaniaRepository.class);
    private final PagoOwnerController controller = new PagoOwnerController(
            reportarPagoUseCase, accessControl, rateLimiter, companiaRepository);

    private WebTestClient clientForPrincipal(JwtPrincipal principal) {
        return WebTestClient.bindToController(controller)
                .webFilter((exchange, chain) -> chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                                Mono.just(new SecurityContextImpl(
                                        new UsernamePasswordAuthenticationToken(
                                                principal, "n/a",
                                                List.of(new SimpleGrantedAuthority("ROLE_USER")))))
                        )))
                .build();
    }

    private JwtPrincipal ownerOf(Long idCompania) {
        return new JwtPrincipal("1", "plataforma", "admin_compania", idCompania, null);
    }

    private Compania compania(Long id, String nombre) {
        Compania c = new Compania();
        c.setId(id);
        c.setNombre(nombre);
        return c;
    }

    private PagoPendienteValidacion pagoPersistido(String comprobanteUrl) {
        PagoPendienteValidacion p = new PagoPendienteValidacion();
        p.setId(77L);
        p.setIdCompania(42L);
        p.setIdPlanDestino(300L);
        p.setMonto(new BigDecimal("29.99"));
        p.setMoneda("USD");
        p.setFechaReporte(Instant.parse("2026-07-10T10:00:00Z"));
        p.setFechaTransferencia(LocalDate.of(2026, 7, 9));
        p.setComprobanteUrl(comprobanteUrl);
        p.setReferencia("REF-001");
        p.setEstado(PagoPendienteValidacion.Estado.PENDIENTE);
        return p;
    }

    @Test
    @DisplayName("201 Created cuando NO se envía la parte 'comprobante' → comprobanteUrl null y nombreCompania poblado")
    void created201SinComprobante() {
        when(rateLimiter.checkRateLimit(any(), anyLong(), anyInt(), any())).thenReturn(Mono.empty());
        when(reportarPagoUseCase.reportar(any())).thenReturn(Mono.just(pagoPersistido(null)));
        when(companiaRepository.findById(42L)).thenReturn(Mono.just(compania(42L, "Gym Titan")));

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("id_plan_destino", "300");
        builder.part("monto", "29.99");
        builder.part("fecha_transferencia", "2026-07-09");
        builder.part("referencia", "REF-001");

        clientForPrincipal(ownerOf(42L))
                .post()
                .uri("/api/v1/companias/42/pagos/reportar")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(77)
                .jsonPath("$.idCompania").isEqualTo(42)
                .jsonPath("$.nombreCompania").isEqualTo("Gym Titan")
                .jsonPath("$.comprobanteUrl").isEmpty()
                .jsonPath("$.estado").isEqualTo("PENDIENTE");

        ArgumentCaptor<ReportarPagoUseCase.ReportarPagoCommand> cmdCaptor =
                ArgumentCaptor.forClass(ReportarPagoUseCase.ReportarPagoCommand.class);
        verify(reportarPagoUseCase).reportar(cmdCaptor.capture());
        ReportarPagoUseCase.ReportarPagoCommand cmd = cmdCaptor.getValue();
        assertThat(cmd.comprobanteBytes()).isNull();
        assertThat(cmd.nombreArchivo()).isNull();
        assertThat(cmd.idCompania()).isEqualTo(42L);
        assertThat(cmd.idPlanDestino()).isEqualTo(300L);
        assertThat(cmd.monto()).isEqualByComparingTo(new BigDecimal("29.99"));
        assertThat(cmd.referencia()).isEqualTo("REF-001");
    }

    @Test
    @DisplayName("201 Created cuando SÍ se envía comprobante — comportamiento previo intacto")
    void created201ConComprobante() {
        when(rateLimiter.checkRateLimit(any(), anyLong(), anyInt(), any())).thenReturn(Mono.empty());
        when(reportarPagoUseCase.reportar(any()))
                .thenReturn(Mono.just(pagoPersistido("https://cdn/test/comprobante.pdf")));
        when(companiaRepository.findById(42L)).thenReturn(Mono.just(compania(42L, "Gym Titan")));

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("id_plan_destino", "300");
        builder.part("monto", "29.99");
        builder.part("fecha_transferencia", "2026-07-09");
        builder.part("referencia", "REF-001");
        builder.part("comprobante", new ByteArrayResource("PDFCONTENT".getBytes()) {
            @Override
            public String getFilename() {
                return "comprobante.pdf";
            }
        }).contentType(MediaType.APPLICATION_PDF);

        clientForPrincipal(ownerOf(42L))
                .post()
                .uri("/api/v1/companias/42/pagos/reportar")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(77)
                .jsonPath("$.comprobanteUrl").isEqualTo("https://cdn/test/comprobante.pdf")
                .jsonPath("$.nombreCompania").isEqualTo("Gym Titan");

        ArgumentCaptor<ReportarPagoUseCase.ReportarPagoCommand> cmdCaptor =
                ArgumentCaptor.forClass(ReportarPagoUseCase.ReportarPagoCommand.class);
        verify(reportarPagoUseCase).reportar(cmdCaptor.capture());
        ReportarPagoUseCase.ReportarPagoCommand cmd = cmdCaptor.getValue();
        assertThat(cmd.comprobanteBytes()).isNotNull();
        assertThat(new String(cmd.comprobanteBytes())).isEqualTo("PDFCONTENT");
        assertThat(cmd.nombreArchivo()).isEqualTo("comprobante.pdf");
    }

    @Test
    @DisplayName("compañía borrada → comprobanteUrl y nombreCompania null, respuesta se emite igual")
    void companiaNoExisteNoRompeRespuesta() {
        when(rateLimiter.checkRateLimit(any(), anyLong(), anyInt(), any())).thenReturn(Mono.empty());
        when(reportarPagoUseCase.reportar(any())).thenReturn(Mono.just(pagoPersistido(null)));
        when(companiaRepository.findById(eq(42L))).thenReturn(Mono.empty());

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("id_plan_destino", "300");
        builder.part("monto", "29.99");
        builder.part("fecha_transferencia", "2026-07-09");
        builder.part("referencia", "REF-001");

        clientForPrincipal(ownerOf(42L))
                .post()
                .uri("/api/v1/companias/42/pagos/reportar")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(77)
                .jsonPath("$.nombreCompania").isEmpty()
                .jsonPath("$.comprobanteUrl").isEmpty();
    }
}
