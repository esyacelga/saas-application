package com.gymadmin.platform.unit;

import com.gymadmin.platform.domain.model.Plan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-SAAS-001 (Sub-fase 1.2) — {@link Plan#tieneLimites()} decide si
 * {@code LimiteRecursoService} (Sub-fase 1.3) debe evaluar cuotas contra este plan.
 * <p>
 * El contrato: true si al menos uno de {@code maxSucursales}, {@code maxClientesActivos}
 * o {@code maxStaff} está seteado; false si los tres son null (plan ilimitado).
 */
@DisplayName("Plan.tieneLimites() — detecta al menos un límite duro seteado")
class PlanTieneLimitesTest {

    private Plan buildBasePlan() {
        Plan p = new Plan();
        p.setId(1L);
        p.setNombre("Test");
        p.setCodigo("TEST");
        return p;
    }

    @Test
    @DisplayName("todos los max_* NULL → sin límites (retorna false)")
    void sinLimitesRetornaFalse() {
        Plan p = buildBasePlan();
        assertThat(p.getMaxSucursales()).isNull();
        assertThat(p.getMaxClientesActivos()).isNull();
        assertThat(p.getMaxStaff()).isNull();

        assertThat(p.tieneLimites()).isFalse();
    }

    @Test
    @DisplayName("solo maxSucursales seteado → tiene límites (retorna true)")
    void soloMaxSucursalesRetornaTrue() {
        Plan p = buildBasePlan();
        p.setMaxSucursales(1);

        assertThat(p.tieneLimites()).isTrue();
    }

    @Test
    @DisplayName("solo maxClientesActivos seteado → tiene límites (retorna true)")
    void soloMaxClientesActivosRetornaTrue() {
        Plan p = buildBasePlan();
        p.setMaxClientesActivos(50);

        assertThat(p.tieneLimites()).isTrue();
    }

    @Test
    @DisplayName("solo maxStaff seteado → tiene límites (retorna true)")
    void soloMaxStaffRetornaTrue() {
        Plan p = buildBasePlan();
        p.setMaxStaff(2);

        assertThat(p.tieneLimites()).isTrue();
    }

    @Test
    @DisplayName("los tres límites seteados → tiene límites (retorna true)")
    void todosLosLimitesRetornaTrue() {
        Plan p = buildBasePlan();
        p.setMaxSucursales(1);
        p.setMaxClientesActivos(50);
        p.setMaxStaff(2);

        assertThat(p.tieneLimites()).isTrue();
    }

    @Test
    @DisplayName("un plan Premium típico sin max_* (ilimitado) → sin límites")
    void planPremiumIlimitadoRetornaFalse() {
        Plan premium = buildBasePlan();
        premium.setCodigo("PREMIUM");
        premium.setEsGratuito(false);
        premium.setMoneda("USD");
        // Premium en el spec es ilimitado por default en cuotas.
        assertThat(premium.tieneLimites()).isFalse();
    }
}
