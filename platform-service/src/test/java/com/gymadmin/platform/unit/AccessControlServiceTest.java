package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import com.gymadmin.platform.infrastructure.exception.ForbiddenException;
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
@DisplayName("AccessControlService — control de acceso por roles")
class AccessControlServiceTest {

    @InjectMocks
    private AccessControlService service;

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("requirePlataforma")
    class RequirePlataforma {

        @Test
        @DisplayName("permite acceso cuando isPlataforma es true")
        void permiteAccesoCuandoEsPlataforma() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isPlataforma()).thenReturn(true);

            StepVerifier.create(service.requirePlataforma(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando isPlataforma es false")
        void lanzaForbiddenCuandoNoEsPlataforma() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isPlataforma()).thenReturn(false);

            StepVerifier.create(service.requirePlataforma(principal))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando el principal es null")
        void lanzaForbiddenCuandoPrincipalEsNull() {
            StepVerifier.create(service.requirePlataforma(null))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("requireSuperAdmin")
    class RequireSuperAdmin {

        @Test
        @DisplayName("permite acceso cuando isSuperAdmin es true")
        void permiteAccesoCuandoEsSuperAdmin() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isSuperAdmin()).thenReturn(true);

            StepVerifier.create(service.requireSuperAdmin(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando rol no es super_admin")
        void lanzaForbiddenCuandoRolNoEsSuperAdmin() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isSuperAdmin()).thenReturn(false);

            StepVerifier.create(service.requireSuperAdmin(principal))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando el principal es null")
        void lanzaForbiddenCuandoPrincipalEsNull() {
            StepVerifier.create(service.requireSuperAdmin(null))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("requireSuperAdminOrSoporte")
    class RequireSuperAdminOrSoporte {

        @Test
        @DisplayName("permite acceso cuando el rol es super_admin")
        void permiteAccesoParaSuperAdmin() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isSuperAdmin()).thenReturn(true);
            when(principal.isSoporte()).thenReturn(false);

            StepVerifier.create(service.requireSuperAdminOrSoporte(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("permite acceso cuando el rol es soporte")
        void permiteAccesoParaSoporte() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isSuperAdmin()).thenReturn(false);
            when(principal.isSoporte()).thenReturn(true);

            StepVerifier.create(service.requireSuperAdminOrSoporte(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando el rol no es super_admin ni soporte")
        void lanzaForbiddenCuandoRolNoAutorizado() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isSuperAdmin()).thenReturn(false);
            when(principal.isSoporte()).thenReturn(false);

            StepVerifier.create(service.requireSuperAdminOrSoporte(principal))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando el principal es null")
        void lanzaForbiddenCuandoPrincipalEsNull() {
            StepVerifier.create(service.requireSuperAdminOrSoporte(null))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("requireStaff")
    class RequireStaff {

        @Test
        @DisplayName("permite acceso cuando isStaff es true")
        void permiteAccesoCuandoEsStaff() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isStaff()).thenReturn(true);

            StepVerifier.create(service.requireStaff(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando el usuario es de plataforma (no staff)")
        void lanzaForbiddenParaUsuarioPlataforma() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isStaff()).thenReturn(false);

            StepVerifier.create(service.requireStaff(principal))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando el principal es null")
        void lanzaForbiddenCuandoPrincipalEsNull() {
            StepVerifier.create(service.requireStaff(null))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("requireAccessToCompania")
    class RequireAccessToCompania {

        @Test
        @DisplayName("permite acceso cuando el usuario es de plataforma (cualquier compania)")
        void permiteAccesoCuandoEsPlataforma() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isPlataforma()).thenReturn(true);

            StepVerifier.create(service.requireAccessToCompania(principal, 1L))
                    .verifyComplete();
        }

        @Test
        @DisplayName("permite acceso cuando es admin_compania y el idCompania coincide")
        void permiteAccesoParaAdminCompaniaConIdCorrecto() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isPlataforma()).thenReturn(false);
            when(principal.isAdminCompania()).thenReturn(true);
            when(principal.getIdCompania()).thenReturn(42L);

            StepVerifier.create(service.requireAccessToCompania(principal, 42L))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando es staff con idCompania diferente")
        void lanzaForbiddenParaStaffConIdComapniaDiferente() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isPlataforma()).thenReturn(false);
            when(principal.isAdminCompania()).thenReturn(true);
            when(principal.getIdCompania()).thenReturn(10L);

            StepVerifier.create(service.requireAccessToCompania(principal, 99L))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando el principal no es plataforma ni admin_compania")
        void lanzaForbiddenCuandoNoTieneAcceso() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isPlataforma()).thenReturn(false);
            when(principal.isAdminCompania()).thenReturn(false);

            StepVerifier.create(service.requireAccessToCompania(principal, 1L))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando el principal es null")
        void lanzaForbiddenCuandoPrincipalEsNull() {
            StepVerifier.create(service.requireAccessToCompania(null, 1L))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
    }
}
