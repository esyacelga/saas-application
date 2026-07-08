package com.gymadmin.core.unit;

import com.gymadmin.core.application.service.AccessControlService;
import com.gymadmin.core.infrastructure.config.JwtPrincipal;
import com.gymadmin.core.infrastructure.exception.ForbiddenException;
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
@DisplayName("AccessControlService — control de acceso basado en JWT")
class AccessControlServiceTest {

    @InjectMocks
    private AccessControlService accessControlService;

    // -------------------------------------------------------------------------
    // requireStaff
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("requireStaff")
    class RequireStaff {

        @Test
        @DisplayName("permite acceso cuando el principal es staff")
        void permiteAccesoCuandoEsStaff() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.getTipo()).thenReturn("staff");

            StepVerifier.create(accessControlService.requireStaff(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("permite acceso cuando el principal es plataforma")
        void permiteAccesoCuandoEsPlataforma() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.getTipo()).thenReturn("plataforma");

            StepVerifier.create(accessControlService.requireStaff(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("permite acceso cuando el principal es cliente (cualquier tipo no nulo)")
        void permiteAccesoCuandoEsCliente() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.getTipo()).thenReturn("cliente");

            // requireStaff solo verifica que el principal no sea null
            StepVerifier.create(accessControlService.requireStaff(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando el principal es null")
        void lanzaForbiddenCuandoPrincipalEsNull() {
            StepVerifier.create(accessControlService.requireStaff(null))
                    .expectErrorSatisfies(error -> {
                        assert error instanceof ForbiddenException;
                        assert error.getMessage().contains("Authentication required");
                    })
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    // requireGymStaff
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("requireGymStaff")
    class RequireGymStaff {

        @Test
        @DisplayName("permite acceso al super_admin independientemente de la compañía")
        void permiteAccesoAlSuperAdmin() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isSuperAdmin()).thenReturn(true);

            StepVerifier.create(accessControlService.requireGymStaff(principal, 1L))
                    .verifyComplete();
        }

        @Test
        @DisplayName("permite acceso cuando idCompania coincide con la del principal")
        void permiteAccesoCuandoIdCompaniaCoincide() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isSuperAdmin()).thenReturn(false);
            when(principal.getIdCompania()).thenReturn(5L);

            StepVerifier.create(accessControlService.requireGymStaff(principal, 5L))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando idCompania es diferente")
        void lanzaForbiddenCuandoIdCompaniaDiferente() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isSuperAdmin()).thenReturn(false);
            when(principal.getIdCompania()).thenReturn(5L);

            StepVerifier.create(accessControlService.requireGymStaff(principal, 99L))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando el principal es null")
        void lanzaForbiddenCuandoPrincipalEsNull() {
            StepVerifier.create(accessControlService.requireGymStaff(null, 1L))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando idCompania del request es null")
        void lanzaForbiddenCuandoIdCompaniaDelRequestEsNull() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isSuperAdmin()).thenReturn(false);
            when(principal.getIdCompania()).thenReturn(5L);

            StepVerifier.create(accessControlService.requireGymStaff(principal, null))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    // requireAdminOrDueno
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("requireAdminOrDueno")
    class RequireAdminOrDueno {

        @Test
        @DisplayName("permite acceso al super_admin")
        void permiteAccesoAlSuperAdmin() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isSuperAdmin()).thenReturn(true);

            StepVerifier.create(accessControlService.requireAdminOrDueno(principal, 1L))
                    .verifyComplete();
        }

        @Test
        @DisplayName("permite acceso a token staff real (sin rol_plataforma) con compañía correcta")
        void permiteAccesoAStaffRealSinRolPlataforma() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isSuperAdmin()).thenReturn(false);
            when(principal.isStaff()).thenReturn(true);
            when(principal.getRolPlataforma()).thenReturn(null);
            when(principal.getIdCompania()).thenReturn(3L);

            StepVerifier.create(accessControlService.requireAdminOrDueno(principal, 3L))
                    .verifyComplete();
        }

        @Test
        @DisplayName("permite acceso con rol admin_compania y compañía correcta")
        void permiteAccesoConRolAdminCompania() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isSuperAdmin()).thenReturn(false);
            when(principal.isStaff()).thenReturn(false);
            when(principal.getRolPlataforma()).thenReturn("admin_compania");
            when(principal.getIdCompania()).thenReturn(7L);

            StepVerifier.create(accessControlService.requireAdminOrDueno(principal, 7L))
                    .verifyComplete();
        }

        @Test
        @DisplayName("permite acceso con rol Dueño y compañía correcta")
        void permiteAccesoConRolDueno() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isSuperAdmin()).thenReturn(false);
            when(principal.isStaff()).thenReturn(false);
            when(principal.getRolPlataforma()).thenReturn("Dueño");
            when(principal.getIdCompania()).thenReturn(7L);

            StepVerifier.create(accessControlService.requireAdminOrDueno(principal, 7L))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ForbiddenException para rol Recepción")
        void lanzaForbiddenParaRolRecepcion() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isSuperAdmin()).thenReturn(false);
            when(principal.isStaff()).thenReturn(false);
            when(principal.getRolPlataforma()).thenReturn("Recepción");
            when(principal.getIdCompania()).thenReturn(7L);

            StepVerifier.create(accessControlService.requireAdminOrDueno(principal, 7L))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando la compañía no coincide aunque tenga rol admin")
        void lanzaForbiddenCuandoCompaniaNoCoincide() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isSuperAdmin()).thenReturn(false);
            when(principal.isStaff()).thenReturn(false);
            when(principal.getRolPlataforma()).thenReturn("Dueño");
            when(principal.getIdCompania()).thenReturn(1L);

            StepVerifier.create(accessControlService.requireAdminOrDueno(principal, 99L))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando el principal es null")
        void lanzaForbiddenCuandoPrincipalEsNull() {
            StepVerifier.create(accessControlService.requireAdminOrDueno(null, 1L))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    // requireCliente
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("requireCliente")
    class RequireCliente {

        @Test
        @DisplayName("permite acceso cuando el tipo es cliente y la compañía coincide")
        void permiteAccesoCuandoEsClienteConCompaniaCorrecta() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isCliente()).thenReturn(true);
            when(principal.getIdCompania()).thenReturn(2L);

            StepVerifier.create(accessControlService.requireCliente(principal, 2L))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando el tipo es staff")
        void lanzaForbiddenParaStaff() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isCliente()).thenReturn(false);

            StepVerifier.create(accessControlService.requireCliente(principal, 2L))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando la compañía del cliente no coincide")
        void lanzaForbiddenCuandoCompaniaNoCoincide() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isCliente()).thenReturn(true);
            when(principal.getIdCompania()).thenReturn(2L);

            StepVerifier.create(accessControlService.requireCliente(principal, 99L))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando el principal es null")
        void lanzaForbiddenCuandoPrincipalEsNull() {
            StepVerifier.create(accessControlService.requireCliente(null, 1L))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    // requireRecepcionOrAbove
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("requireRecepcionOrAbove")
    class RequireRecepcionOrAbove {

        @Test
        @DisplayName("permite acceso al super_admin")
        void permiteAccesoAlSuperAdmin() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isSuperAdmin()).thenReturn(true);

            StepVerifier.create(accessControlService.requireRecepcionOrAbove(principal, 1L))
                    .verifyComplete();
        }

        @Test
        @DisplayName("permite acceso a token staff real sin rol_plataforma con compañía correcta")
        void permiteAccesoAStaffRealSinRolPlataforma() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isSuperAdmin()).thenReturn(false);
            when(principal.isStaff()).thenReturn(true);
            when(principal.getRolPlataforma()).thenReturn(null);
            when(principal.getIdCompania()).thenReturn(4L);

            StepVerifier.create(accessControlService.requireRecepcionOrAbove(principal, 4L))
                    .verifyComplete();
        }

        @Test
        @DisplayName("permite acceso con rol Dueño")
        void permiteAccesoConRolDueno() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isSuperAdmin()).thenReturn(false);
            when(principal.isStaff()).thenReturn(false);
            when(principal.getRolPlataforma()).thenReturn("Dueño");
            when(principal.getIdCompania()).thenReturn(4L);

            StepVerifier.create(accessControlService.requireRecepcionOrAbove(principal, 4L))
                    .verifyComplete();
        }

        @Test
        @DisplayName("permite acceso con rol Recepción y compañía correcta")
        void permiteAccesoConRolRecepcion() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isSuperAdmin()).thenReturn(false);
            when(principal.isStaff()).thenReturn(false);
            when(principal.getRolPlataforma()).thenReturn("Recepción");
            when(principal.getIdCompania()).thenReturn(4L);

            StepVerifier.create(accessControlService.requireRecepcionOrAbove(principal, 4L))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ForbiddenException para rol Entrenador")
        void lanzaForbiddenParaEntrenador() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isSuperAdmin()).thenReturn(false);
            when(principal.isStaff()).thenReturn(false);
            when(principal.getRolPlataforma()).thenReturn("Entrenador");
            when(principal.getIdCompania()).thenReturn(4L);

            StepVerifier.create(accessControlService.requireRecepcionOrAbove(principal, 4L))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando la compañía no coincide aunque tenga rol válido")
        void lanzaForbiddenCuandoCompaniaNoCoincide() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isSuperAdmin()).thenReturn(false);
            when(principal.isStaff()).thenReturn(false);
            when(principal.getRolPlataforma()).thenReturn("Recepción");
            when(principal.getIdCompania()).thenReturn(1L);

            StepVerifier.create(accessControlService.requireRecepcionOrAbove(principal, 999L))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando el principal es null")
        void lanzaForbiddenCuandoPrincipalEsNull() {
            StepVerifier.create(accessControlService.requireRecepcionOrAbove(null, 1L))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
    }
}
