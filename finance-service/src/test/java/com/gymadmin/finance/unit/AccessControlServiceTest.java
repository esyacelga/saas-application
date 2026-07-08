package com.gymadmin.finance.unit;

import com.gymadmin.finance.application.service.AccessControlService;
import com.gymadmin.finance.infrastructure.config.JwtPrincipal;
import com.gymadmin.finance.infrastructure.exception.ForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccessControlService — control de acceso por permisos y roles")
class AccessControlServiceTest {

    @InjectMocks
    private AccessControlService service;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private JwtPrincipal principalConPermiso(String permiso) {
        JwtPrincipal p = mock(JwtPrincipal.class);
        when(p.hasPermiso(permiso)).thenReturn(true);
        when(p.isDueno()).thenReturn(false);
        when(p.isPlataforma()).thenReturn(false);
        when(p.isRecepcion()).thenReturn(false);
        return p;
    }

    private JwtPrincipal principalDueno() {
        JwtPrincipal p = mock(JwtPrincipal.class);
        when(p.isDueno()).thenReturn(true);
        when(p.isPlataforma()).thenReturn(false);
        return p;
    }

    private JwtPrincipal principalPlataforma() {
        JwtPrincipal p = mock(JwtPrincipal.class);
        when(p.isDueno()).thenReturn(false);
        when(p.isPlataforma()).thenReturn(true);
        return p;
    }

    private JwtPrincipal principalSinPermisos() {
        JwtPrincipal p = mock(JwtPrincipal.class);
        when(p.isDueno()).thenReturn(false);
        when(p.isPlataforma()).thenReturn(false);
        when(p.hasPermiso("finanzas:leer")).thenReturn(false);
        when(p.hasPermiso("finanzas:crear")).thenReturn(false);
        when(p.hasPermiso("finanzas:exportar")).thenReturn(false);
        when(p.isRecepcion()).thenReturn(false);
        return p;
    }

    // =========================================================================

    @Nested
    @DisplayName("requireFinanzasLeer")
    class RequireFinanzasLeer {

        @Test
        @DisplayName("permite acceso cuando el principal tiene permiso 'finanzas:leer'")
        void permiteCuandoTienePermisoLeer() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.hasPermiso("finanzas:leer")).thenReturn(true);

            StepVerifier.create(service.requireFinanzasLeer(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("permite acceso cuando el principal es dueño")
        void permiteCuandoEsDueno() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.hasPermiso("finanzas:leer")).thenReturn(false);
            when(principal.isDueno()).thenReturn(true);

            StepVerifier.create(service.requireFinanzasLeer(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("permite acceso cuando el principal es plataforma")
        void permiteCuandoEsPlataforma() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.hasPermiso("finanzas:leer")).thenReturn(false);
            when(principal.isDueno()).thenReturn(false);
            when(principal.isPlataforma()).thenReturn(true);

            StepVerifier.create(service.requireFinanzasLeer(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("niega acceso cuando staff sin permisos relevantes")
        void deniegalCuandoStaffSinPermisos() {
            JwtPrincipal principal = principalSinPermisos();

            StepVerifier.create(service.requireFinanzasLeer(principal))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("niega acceso cuando principal es null")
        void deniegalCuandoPrincipalEsNull() {
            StepVerifier.create(service.requireFinanzasLeer(null))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("requireFinanzasCrear")
    class RequireFinanzasCrear {

        @Test
        @DisplayName("permite acceso cuando el principal tiene permiso 'finanzas:crear'")
        void permiteCuandoTienePermisoCrear() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.hasPermiso("finanzas:crear")).thenReturn(true);

            StepVerifier.create(service.requireFinanzasCrear(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("permite acceso cuando el principal es dueño")
        void permiteCuandoEsDueno() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.hasPermiso("finanzas:crear")).thenReturn(false);
            when(principal.isDueno()).thenReturn(true);

            StepVerifier.create(service.requireFinanzasCrear(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("permite acceso cuando el principal es plataforma")
        void permiteCuandoEsPlataforma() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.hasPermiso("finanzas:crear")).thenReturn(false);
            when(principal.isDueno()).thenReturn(false);
            when(principal.isPlataforma()).thenReturn(true);

            StepVerifier.create(service.requireFinanzasCrear(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("niega acceso cuando recepcion sin permiso finanzas:crear")
        void deniegalCuandoRecepcionSinPermiso() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.hasPermiso("finanzas:crear")).thenReturn(false);
            when(principal.isDueno()).thenReturn(false);
            when(principal.isPlataforma()).thenReturn(false);

            StepVerifier.create(service.requireFinanzasCrear(principal))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("requireFinanzasCrearORecepcion")
    class RequireFinanzasCrearORecepcion {

        @Test
        @DisplayName("permite acceso cuando el principal tiene rol recepcion sin permiso explícito")
        void permiteCuandoEsRecepcion() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.hasPermiso("finanzas:crear")).thenReturn(false);
            when(principal.isDueno()).thenReturn(false);
            when(principal.isRecepcion()).thenReturn(true);
            when(principal.isPlataforma()).thenReturn(false);

            StepVerifier.create(service.requireFinanzasCrearORecepcion(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("permite acceso cuando el principal es dueño")
        void permiteCuandoEsDueno() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.hasPermiso("finanzas:crear")).thenReturn(false);
            when(principal.isDueno()).thenReturn(true);

            StepVerifier.create(service.requireFinanzasCrearORecepcion(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("permite acceso cuando el principal tiene permiso 'finanzas:crear'")
        void permiteCuandoTienePermiso() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.hasPermiso("finanzas:crear")).thenReturn(true);

            StepVerifier.create(service.requireFinanzasCrearORecepcion(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("niega acceso cuando el principal es entrenador sin permisos")
        void deniegalCuandoEsEntrenador() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.hasPermiso("finanzas:crear")).thenReturn(false);
            when(principal.isDueno()).thenReturn(false);
            when(principal.isRecepcion()).thenReturn(false);
            when(principal.isPlataforma()).thenReturn(false);

            StepVerifier.create(service.requireFinanzasCrearORecepcion(principal))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("requireFinanzasReportes")
    class RequireFinanzasReportes {

        @Test
        @DisplayName("permite acceso cuando el principal tiene permiso 'finanzas:exportar'")
        void permiteCuandoTienePermisoExportar() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.hasPermiso("finanzas:exportar")).thenReturn(true);

            StepVerifier.create(service.requireFinanzasReportes(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("permite acceso cuando el principal tiene permiso 'finanzas:leer'")
        void permiteCuandoTienePermisoLeer() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.hasPermiso("finanzas:exportar")).thenReturn(false);
            when(principal.hasPermiso("finanzas:leer")).thenReturn(true);

            StepVerifier.create(service.requireFinanzasReportes(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("permite acceso cuando el principal es dueño")
        void permiteCuandoEsDueno() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.hasPermiso("finanzas:exportar")).thenReturn(false);
            when(principal.hasPermiso("finanzas:leer")).thenReturn(false);
            when(principal.isDueno()).thenReturn(true);

            StepVerifier.create(service.requireFinanzasReportes(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("niega acceso cuando recepcion sin permisos de reportes")
        void deniegalCuandoRecepcionSinPermisos() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.hasPermiso("finanzas:exportar")).thenReturn(false);
            when(principal.hasPermiso("finanzas:leer")).thenReturn(false);
            when(principal.isDueno()).thenReturn(false);
            when(principal.isPlataforma()).thenReturn(false);

            StepVerifier.create(service.requireFinanzasReportes(principal))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
    }
}
