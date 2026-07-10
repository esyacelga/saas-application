package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.domain.port.in.BannerUseCase;
import com.gymadmin.platform.domain.port.in.BannerUseCase.BannerActivoView;
import com.gymadmin.platform.infrastructure.adapter.in.web.BannerController;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-SAAS-001 (Sub-fase 1.5): tests del {@link BannerController} usando
 * {@link WebTestClient} standalone — inyectamos el {@code JwtPrincipal} vía
 * {@code SecurityContext} sin arrancar todo el ApplicationContext.
 */
@DisplayName("BannerController — GET /banners-activos + POST /descartar")
class BannerControllerTest {

    private final BannerUseCase bannerUseCase = mock(BannerUseCase.class);
    private final AccessControlService accessControl = new AccessControlService();
    private final BannerController controller = new BannerController(bannerUseCase, accessControl);

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

    @Test
    @DisplayName("GET /banners-activos → 200 y lista para el tenant del principal")
    void listaBannersDelTenant() {
        BannerActivoView v = new BannerActivoView(
                1L, "VENCIMIENTO_TRIAL", 7,
                "Faltan 7 dias", "https://x.test/planes", "Renovar");
        when(bannerUseCase.listarBannersActivos(eq(42L))).thenReturn(Flux.just(v));

        clientForPrincipal(ownerOf(42L))
                .get()
                .uri("/api/v1/companias/42/banners-activos")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(1)
                .jsonPath("$[0].tipo").isEqualTo("VENCIMIENTO_TRIAL");
    }

    @Test
    @DisplayName("GET /banners-activos → 403 si el principal es de otro tenant")
    void tenantMismatchProhibido() {
        clientForPrincipal(ownerOf(99L))
                .get()
                .uri("/api/v1/companias/42/banners-activos")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("POST /banners/{idBanner}/descartar → 204 cuando se descarta")
    void descartaBannerRetorna204() {
        when(bannerUseCase.descartarBanner(eq(1L), eq(42L))).thenReturn(Mono.just(true));

        clientForPrincipal(ownerOf(42L))
                .post()
                .uri("/api/v1/companias/42/banners/1/descartar")
                .exchange()
                .expectStatus().isNoContent();

        verify(bannerUseCase).descartarBanner(1L, 42L);
    }

    @Test
    @DisplayName("POST /descartar → 404 cuando el banner no pertenece al tenant o no existe")
    void descartarInexistenteRetorna404() {
        when(bannerUseCase.descartarBanner(eq(999L), eq(42L))).thenReturn(Mono.just(false));

        clientForPrincipal(ownerOf(42L))
                .post()
                .uri("/api/v1/companias/42/banners/999/descartar")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("POST /descartar → 403 cuando tenant mismatch")
    void descartarTenantMismatchProhibido() {
        clientForPrincipal(ownerOf(99L))
                .post()
                .uri("/api/v1/companias/42/banners/1/descartar")
                .exchange()
                .expectStatus().isForbidden();
    }
}
