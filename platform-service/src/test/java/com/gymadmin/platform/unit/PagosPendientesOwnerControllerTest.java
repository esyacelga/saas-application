package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.domain.model.PagoPendienteValidacion;
import com.gymadmin.platform.domain.port.in.ListarPagosPendientesOwnerUseCase;
import com.gymadmin.platform.infrastructure.adapter.in.web.PagosPendientesOwnerController;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import com.gymadmin.platform.infrastructure.exception.ForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-SAAS-001 (Sub-fase 1.6, item #3): tests del
 * {@link PagosPendientesOwnerController}. Usamos {@link WebTestClient} en modo
 * standalone e inyectamos el {@code JwtPrincipal} vía {@code SecurityContext},
 * siguiendo el mismo patrón que {@code BannerControllerTest}.
 */
@DisplayName("PagosPendientesOwnerController — GET /api/v1/companias/{idCompania}/pagos-pendientes")
class PagosPendientesOwnerControllerTest {

    private final ListarPagosPendientesOwnerUseCase useCase = mock(ListarPagosPendientesOwnerUseCase.class);
    private final AccessControlService accessControl = new AccessControlService();
    private final PagosPendientesOwnerController controller =
            new PagosPendientesOwnerController(useCase, accessControl);

    private WebTestClient clientForPrincipal(JwtPrincipal principal) {
        return WebTestClient.bindToController(controller)
                .controllerAdvice(new ForbiddenExceptionAdvice())
                .webFilter((exchange, chain) -> chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                                Mono.just(new SecurityContextImpl(
                                        new UsernamePasswordAuthenticationToken(
                                                principal, "n/a",
                                                List.of(new SimpleGrantedAuthority("ROLE_USER")))))
                        )))
                .build();
    }

    @RestControllerAdvice
    static class ForbiddenExceptionAdvice {
        @ExceptionHandler(ForbiddenException.class)
        public ResponseEntity<String> handle(ForbiddenException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
        }
    }

    private JwtPrincipal ownerOf(Long idCompania) {
        return new JwtPrincipal("u-1", "plataforma", "admin_compania", idCompania, null);
    }

    private JwtPrincipal staffOf(Long idCompania) {
        return new JwtPrincipal("u-2", "staff", null, idCompania, null);
    }

    private PagoPendienteValidacion pago(Long id,
                                         PagoPendienteValidacion.Estado estado,
                                         String motivoRechazo) {
        PagoPendienteValidacion p = new PagoPendienteValidacion();
        p.setId(id);
        p.setIdCompania(42L);
        p.setIdPlanDestino(300L);
        p.setMonto(new BigDecimal("29.99"));
        p.setMoneda("USD");
        p.setFechaReporte(Instant.parse("2026-07-10T10:00:00Z"));
        p.setFechaTransferencia(LocalDate.of(2026, 7, 9));
        p.setEstado(estado);
        p.setMotivoRechazo(motivoRechazo);
        return p;
    }

    @Test
    @DisplayName("200 OK cuando pathVar coincide con jwt.id_compania (owner)")
    void ok200ParaOwnerDelTenant() {
        PagoPendienteValidacion pendiente = pago(2L, PagoPendienteValidacion.Estado.PENDIENTE, null);
        PagoPendienteValidacion rechazado = pago(1L, PagoPendienteValidacion.Estado.RECHAZADO,
                "Comprobante ilegible");
        when(useCase.listarPorCompania(eq(42L), anyInt()))
                .thenReturn(Flux.just(pendiente, rechazado));

        clientForPrincipal(ownerOf(42L))
                .get()
                .uri("/api/v1/companias/42/pagos-pendientes")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(2)
                .jsonPath("$[0].estado").isEqualTo("PENDIENTE")
                .jsonPath("$[1].id").isEqualTo(1)
                .jsonPath("$[1].estado").isEqualTo("RECHAZADO")
                .jsonPath("$[1].motivoRechazo").isEqualTo("Comprobante ilegible");
    }

    @Test
    @DisplayName("200 OK también para staff (sin rol_plataforma) del mismo tenant")
    void ok200ParaStaffDelTenant() {
        when(useCase.listarPorCompania(eq(42L), anyInt())).thenReturn(Flux.empty());

        clientForPrincipal(staffOf(42L))
                .get()
                .uri("/api/v1/companias/42/pagos-pendientes")
                .exchange()
                .expectStatus().isOk();

        verify(useCase).listarPorCompania(eq(42L), anyInt());
    }

    @Test
    @DisplayName("403 Forbidden cuando pathVar != jwt.id_compania")
    void forbid403SiTenantMismatch() {
        // El use case NO debe emitir datos: aunque el metodo se construye
        // dentro de thenMany(...), el guard corta la cadena antes de subscribirse.
        when(useCase.listarPorCompania(eq(42L), anyInt())).thenReturn(Flux.empty());

        clientForPrincipal(ownerOf(99L))
                .get()
                .uri("/api/v1/companias/42/pagos-pendientes")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body)
                        .contains("Access denied to company 42"));
    }

    @Test
    @DisplayName("respeta el parámetro limit y lo propaga al use case")
    void propagaParametroLimit() {
        when(useCase.listarPorCompania(eq(42L), eq(5))).thenReturn(Flux.empty());

        clientForPrincipal(ownerOf(42L))
                .get()
                .uri("/api/v1/companias/42/pagos-pendientes?limit=5")
                .exchange()
                .expectStatus().isOk();

        verify(useCase).listarPorCompania(42L, 5);
    }
}
