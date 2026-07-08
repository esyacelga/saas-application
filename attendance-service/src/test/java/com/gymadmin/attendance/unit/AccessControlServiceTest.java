package com.gymadmin.attendance.unit;

import com.gymadmin.attendance.application.service.AccessControlService;
import com.gymadmin.attendance.infrastructure.config.JwtPrincipal;
import com.gymadmin.attendance.infrastructure.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccessControlService — Pruebas unitarias")
class AccessControlServiceTest {

    private AccessControlService service;

    @BeforeEach
    void setUp() {
        service = new AccessControlService();
    }

    // -------------------------------------------------------------------------
    // requireCliente
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("requireCliente — solo clientes tienen acceso")
    class RequireCliente {

        @Test
        @DisplayName("debe completar vacío cuando el principal es cliente")
        void pasaCuandoEsCliente() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isCliente()).thenReturn(true);

            StepVerifier.create(service.requireCliente(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("debe lanzar ForbiddenException cuando el principal es staff")
        void fallaParaStaff() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isCliente()).thenReturn(false);

            StepVerifier.create(service.requireCliente(principal))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("debe lanzar ForbiddenException cuando el principal es null")
        void fallaParaNull() {
            StepVerifier.create(service.requireCliente(null))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    // requireStaff
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("requireStaff — solo personal del gym tiene acceso")
    class RequireStaff {

        @Test
        @DisplayName("debe completar vacío cuando el principal es staff")
        void pasaCuandoEsStaff() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isStaff()).thenReturn(true);

            StepVerifier.create(service.requireStaff(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("debe lanzar ForbiddenException cuando el principal es cliente")
        void fallaParaCliente() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isStaff()).thenReturn(false);

            StepVerifier.create(service.requireStaff(principal))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("debe lanzar ForbiddenException cuando el principal es null")
        void fallaParaNull() {
            StepVerifier.create(service.requireStaff(null))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    // requireStaffOrPlataforma
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("requireStaffOrPlataforma — staff o plataforma tienen acceso")
    class RequireStaffOrPlataforma {

        @Test
        @DisplayName("debe completar vacío cuando el principal es staff")
        void pasaCuandoEsStaff() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isStaff()).thenReturn(true);
            when(principal.isPlataforma()).thenReturn(false);

            StepVerifier.create(service.requireStaffOrPlataforma(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("debe completar vacío cuando el principal es plataforma")
        void pasaCuandoEsPlataforma() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isStaff()).thenReturn(false);
            when(principal.isPlataforma()).thenReturn(true);

            StepVerifier.create(service.requireStaffOrPlataforma(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("debe lanzar ForbiddenException cuando el principal es cliente")
        void fallaParaCliente() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isStaff()).thenReturn(false);
            when(principal.isPlataforma()).thenReturn(false);

            StepVerifier.create(service.requireStaffOrPlataforma(principal))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("debe lanzar ForbiddenException cuando el principal es null")
        void fallaParaNull() {
            StepVerifier.create(service.requireStaffOrPlataforma(null))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    // requireDueno
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("requireDueno — solo dueño o admin_compania tienen acceso")
    class RequireDueno {

        @Test
        @DisplayName("debe completar vacío cuando isDueno retorna true (rol dueno)")
        void pasaCuandoRolEsDueno() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isDueno()).thenReturn(true);

            StepVerifier.create(service.requireDueno(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("debe completar vacío cuando isDueno retorna true (rol admin_compania)")
        void pasaCuandoRolEsAdminCompania() {
            // isDueno() devuelve true para ambos: "dueno" y "admin_compania"
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isDueno()).thenReturn(true);

            StepVerifier.create(service.requireDueno(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("debe lanzar ForbiddenException cuando el rol es recepcion")
        void fallaParaRecepcion() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isDueno()).thenReturn(false);

            StepVerifier.create(service.requireDueno(principal))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("debe lanzar ForbiddenException cuando el principal es null")
        void fallaParaNull() {
            StepVerifier.create(service.requireDueno(null))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    // requireDuenoOrPlataforma
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("requireDuenoOrPlataforma — dueño o plataforma tienen acceso")
    class RequireDuenoOrPlataforma {

        @Test
        @DisplayName("debe completar vacío cuando el principal es dueño")
        void pasaCuandoEsDueno() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isDueno()).thenReturn(true);
            when(principal.isPlataforma()).thenReturn(false);

            StepVerifier.create(service.requireDuenoOrPlataforma(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("debe completar vacío cuando el principal es plataforma")
        void pasaCuandoEsPlataforma() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isDueno()).thenReturn(false);
            when(principal.isPlataforma()).thenReturn(true);

            StepVerifier.create(service.requireDuenoOrPlataforma(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("debe lanzar ForbiddenException cuando el rol es recepcion")
        void fallaParaRecepcion() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isDueno()).thenReturn(false);
            when(principal.isPlataforma()).thenReturn(false);

            StepVerifier.create(service.requireDuenoOrPlataforma(principal))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("debe lanzar ForbiddenException cuando el principal es null")
        void fallaParaNull() {
            StepVerifier.create(service.requireDuenoOrPlataforma(null))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    // requireNotEntrenador
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("requireNotEntrenador — los entrenadores no pueden registrar asistencia manual")
    class RequireNotEntrenador {

        @Test
        @DisplayName("debe completar vacío cuando el rol es recepcion")
        void pasaCuandoEsRecepcion() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isEntrenador()).thenReturn(false);

            StepVerifier.create(service.requireNotEntrenador(principal))
                    .verifyComplete();
        }

        @Test
        @DisplayName("debe completar vacío cuando el principal es null (no se puede determinar si es entrenador)")
        void pasaCuandoEsNull() {
            // El método solo bloquea cuando principal != null && isEntrenador()
            StepVerifier.create(service.requireNotEntrenador(null))
                    .verifyComplete();
        }

        @Test
        @DisplayName("debe lanzar ForbiddenException cuando el rol es entrenador")
        void fallaParaEntrenador() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isEntrenador()).thenReturn(true);

            StepVerifier.create(service.requireNotEntrenador(principal))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    // requireAccessToCompania
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("requireAccessToCompania — control de acceso multi-tenant")
    class RequireAccessToCompania {

        @Test
        @DisplayName("debe completar vacío cuando el principal es plataforma (acceso a cualquier compañía)")
        void pasaParaPlataformaCualquierCompania() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isPlataforma()).thenReturn(true);

            StepVerifier.create(service.requireAccessToCompania(principal, 99))
                    .verifyComplete();
        }

        @Test
        @DisplayName("debe completar vacío cuando el staff pertenece a la misma compañía")
        void pasaParaStaffDeLaMismaCompania() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isPlataforma()).thenReturn(false);
            when(principal.getIdCompania()).thenReturn(10L);

            StepVerifier.create(service.requireAccessToCompania(principal, 10))
                    .verifyComplete();
        }

        @Test
        @DisplayName("debe lanzar ForbiddenException cuando el staff pertenece a una compañía diferente")
        void fallaParaStaffDeOtraCompania() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isPlataforma()).thenReturn(false);
            when(principal.getIdCompania()).thenReturn(5L);

            StepVerifier.create(service.requireAccessToCompania(principal, 99))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("debe lanzar ForbiddenException cuando el principal es null")
        void fallaParaNull() {
            StepVerifier.create(service.requireAccessToCompania(null, 10))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("debe lanzar ForbiddenException cuando idCompania del principal es null")
        void fallaParaIdCompaniaNull() {
            JwtPrincipal principal = mock(JwtPrincipal.class);
            when(principal.isPlataforma()).thenReturn(false);
            when(principal.getIdCompania()).thenReturn(null);

            StepVerifier.create(service.requireAccessToCompania(principal, 10))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
    }
}
