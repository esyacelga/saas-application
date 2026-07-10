package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import com.gymadmin.platform.infrastructure.exception.ForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * REQ-SAAS-001 (Sub-fase 1.4): guard tenant mismatch — el owner de una compañía
 * no debe poder operar sobre otra distinta. Sirve como test conductor de los
 * endpoints owner/admin del tenant (activar trial, cancelar, reportar pago,
 * consultar uso-límites).
 */
@DisplayName("AccessControlService.requireOwnerOrAdminOfCompania — tenant guard")
class TenantMismatchGuardTest {

    private final AccessControlService service = new AccessControlService();

    @Test
    @DisplayName("permite cuando idCompania del principal coincide con el path")
    void permiteCuandoIdCompaniaCoincide() {
        JwtPrincipal principal = mock(JwtPrincipal.class);
        when(principal.isSuperAdmin()).thenReturn(false);
        when(principal.isSoporte()).thenReturn(false);
        when(principal.isStaff()).thenReturn(false);
        when(principal.getRolPlataforma()).thenReturn("admin_compania");
        when(principal.getIdCompania()).thenReturn(42L);

        StepVerifier.create(service.requireOwnerOrAdminOfCompania(principal, 42L))
                .verifyComplete();
    }

    @Test
    @DisplayName("bloquea con ForbiddenException cuando idCompania no coincide (403)")
    void bloqueaCuandoTenantMismatch() {
        JwtPrincipal principal = mock(JwtPrincipal.class);
        when(principal.isSuperAdmin()).thenReturn(false);
        when(principal.isSoporte()).thenReturn(false);
        when(principal.isStaff()).thenReturn(false);
        when(principal.getRolPlataforma()).thenReturn("admin_compania");
        when(principal.getIdCompania()).thenReturn(42L);

        StepVerifier.create(service.requireOwnerOrAdminOfCompania(principal, 99L))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    @DisplayName("permite super_admin sobre cualquier compañía")
    void permiteSuperAdminSobreCualquierCompania() {
        JwtPrincipal principal = mock(JwtPrincipal.class);
        when(principal.isSuperAdmin()).thenReturn(true);

        StepVerifier.create(service.requireOwnerOrAdminOfCompania(principal, 99L))
                .verifyComplete();
    }

    @Test
    @DisplayName("permite soporte sobre cualquier compañía")
    void permiteSoporteSobreCualquierCompania() {
        JwtPrincipal principal = mock(JwtPrincipal.class);
        when(principal.isSuperAdmin()).thenReturn(false);
        when(principal.isSoporte()).thenReturn(true);

        StepVerifier.create(service.requireOwnerOrAdminOfCompania(principal, 99L))
                .verifyComplete();
    }

    @Test
    @DisplayName("permite token staff (sin rol_plataforma) del mismo tenant")
    void permiteStaffDelMismoTenant() {
        JwtPrincipal principal = mock(JwtPrincipal.class);
        when(principal.isSuperAdmin()).thenReturn(false);
        when(principal.isSoporte()).thenReturn(false);
        when(principal.isStaff()).thenReturn(true);
        when(principal.getRolPlataforma()).thenReturn(null);
        when(principal.getIdCompania()).thenReturn(42L);

        StepVerifier.create(service.requireOwnerOrAdminOfCompania(principal, 42L))
                .verifyComplete();
    }

    @Test
    @DisplayName("bloquea principal null")
    void bloqueaPrincipalNull() {
        StepVerifier.create(service.requireOwnerOrAdminOfCompania(null, 42L))
                .expectError(ForbiddenException.class)
                .verify();
    }
}
