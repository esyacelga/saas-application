package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.model.PagoPendienteValidacion;
import com.gymadmin.platform.domain.port.in.AprobarPagoUseCase;
import com.gymadmin.platform.domain.port.in.ListarPagosPendientesUseCase;
import com.gymadmin.platform.domain.port.in.RechazarPagoUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaRepository;
import com.gymadmin.platform.infrastructure.adapter.in.web.PagoPlataformaController;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * REQ-SAAS-001 (Sub-fase 1.6, item #4): tests del {@link PagoPlataformaController}
 * verificando el enriquecimiento con {@code nombreCompania} en la bandeja
 * root/soporte via batch fetch.
 */
@DisplayName("PagoPlataformaController — GET /api/v1/plataforma/pagos-pendientes")
class PagoPlataformaControllerTest {

    private final ListarPagosPendientesUseCase listarUseCase = mock(ListarPagosPendientesUseCase.class);
    private final AprobarPagoUseCase aprobarUseCase = mock(AprobarPagoUseCase.class);
    private final RechazarPagoUseCase rechazarUseCase = mock(RechazarPagoUseCase.class);
    private final AccessControlService accessControl = new AccessControlService();
    private final CompaniaRepository companiaRepository = mock(CompaniaRepository.class);
    private final PagoPlataformaController controller = new PagoPlataformaController(
            listarUseCase, aprobarUseCase, rechazarUseCase, accessControl, companiaRepository);

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

    private JwtPrincipal soporte() {
        return new JwtPrincipal("100", "plataforma", "soporte", null, null);
    }

    private Compania compania(Long id, String nombre) {
        Compania c = new Compania();
        c.setId(id);
        c.setNombre(nombre);
        return c;
    }

    private PagoPendienteValidacion pago(Long id, Long idCompania) {
        PagoPendienteValidacion p = new PagoPendienteValidacion();
        p.setId(id);
        p.setIdCompania(idCompania);
        p.setIdPlanDestino(300L);
        p.setMonto(new BigDecimal("29.99"));
        p.setMoneda("USD");
        p.setFechaReporte(Instant.parse("2026-07-10T10:00:00Z"));
        p.setFechaTransferencia(LocalDate.of(2026, 7, 9));
        p.setEstado(PagoPendienteValidacion.Estado.PENDIENTE);
        return p;
    }

    @Test
    @DisplayName("200 OK enriquece cada pago con nombreCompania via batch fetch")
    void listarEnriqueceConNombreCompania() {
        when(listarUseCase.contar(any())).thenReturn(Mono.just(2L));
        when(listarUseCase.listar(any())).thenReturn(Flux.just(
                pago(10L, 42L),
                pago(11L, 43L)
        ));
        when(companiaRepository.findAllByIds(any())).thenReturn(Flux.just(
                compania(42L, "Gym Titan"),
                compania(43L, "Iron Palace")
        ));

        clientForPrincipal(soporte())
                .get()
                .uri("/api/v1/plataforma/pagos-pendientes")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.total").isEqualTo(2)
                .jsonPath("$.datos[0].id").isEqualTo(10)
                .jsonPath("$.datos[0].idCompania").isEqualTo(42)
                .jsonPath("$.datos[0].nombreCompania").isEqualTo("Gym Titan")
                .jsonPath("$.datos[1].id").isEqualTo(11)
                .jsonPath("$.datos[1].nombreCompania").isEqualTo("Iron Palace");
    }

    @Test
    @DisplayName("compañía borrada (findAllByIds no la devuelve) → nombreCompania null, no falla")
    void companiaBorradaDevuelveNombreNull() {
        when(listarUseCase.contar(any())).thenReturn(Mono.just(1L));
        when(listarUseCase.listar(any())).thenReturn(Flux.just(pago(10L, 42L)));
        // La compañía 42 no aparece en el batch (borrada).
        when(companiaRepository.findAllByIds(any())).thenReturn(Flux.empty());

        clientForPrincipal(soporte())
                .get()
                .uri("/api/v1/plataforma/pagos-pendientes")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.total").isEqualTo(1)
                .jsonPath("$.datos[0].id").isEqualTo(10)
                .jsonPath("$.datos[0].nombreCompania").isEmpty();
    }

    @Test
    @DisplayName("lista vacía → responde sin invocar findAllByIds")
    void listaVaciaNoInvocaBatchFetch() {
        when(listarUseCase.contar(any())).thenReturn(Mono.just(0L));
        when(listarUseCase.listar(any())).thenReturn(Flux.empty());

        clientForPrincipal(soporte())
                .get()
                .uri("/api/v1/plataforma/pagos-pendientes")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.total").isEqualTo(0)
                .jsonPath("$.datos").isArray()
                .jsonPath("$.datos.length()").isEqualTo(0);
    }
}
