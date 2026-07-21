package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.CompaniaService;
import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.model.CompaniaConPlan;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.CompaniaRepository;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompaniaService.listarCompanias — enriquecimiento con plan activo")
class CompaniaServiceListarTest {

    @Mock
    private CompaniaRepository companiaRepository;

    @Mock
    private CompaniaPlanRepository companiaPlanRepository;

    @Mock
    private PlanRepository planRepository;

    private CompaniaService service;

    private final JwtPrincipal plataforma =
            new JwtPrincipal("1", "plataforma", "super_admin", null, null);

    @BeforeEach
    void setUp() {
        service = new CompaniaService(
                companiaRepository, companiaPlanRepository, null, null, planRepository,
                null, null, null, null, null, null);
    }

    private Compania compania(Long id, String nombre) {
        Compania c = new Compania();
        c.setId(id);
        c.setNombre(nombre);
        c.setActivo(true);
        return c;
    }

    private CompaniaPlan planActivo(Long idCompania, Long idPlan, CompaniaPlan.Estado estado, LocalDate fin) {
        CompaniaPlan cp = new CompaniaPlan();
        cp.setIdCompania(idCompania);
        cp.setIdPlan(idPlan);
        cp.setEstado(estado);
        cp.setFechaFin(fin);
        return cp;
    }

    @Test
    @DisplayName("puebla planActivo cuando la compañía tiene suscripción activa")
    void poblaPlanActivo() {
        Compania c = compania(10L, "Gym A");
        Plan plan = new Plan();
        plan.setId(5L);
        plan.setNombre("Premium");
        LocalDate fin = LocalDate.now().plusDays(20);

        when(companiaRepository.findAll()).thenReturn(Flux.just(c));
        when(companiaPlanRepository.findActivoByIdCompania(10L))
                .thenReturn(Mono.just(planActivo(10L, 5L, CompaniaPlan.Estado.ACTIVO, fin)));
        when(planRepository.findById(5L)).thenReturn(Mono.just(plan));

        StepVerifier.create(service.listarCompanias(plataforma))
                .assertNext(cp -> {
                    org.assertj.core.api.Assertions.assertThat(cp.compania().getId()).isEqualTo(10L);
                    CompaniaConPlan.PlanActivo pa = cp.planActivo();
                    org.assertj.core.api.Assertions.assertThat(pa).isNotNull();
                    org.assertj.core.api.Assertions.assertThat(pa.nombre()).isEqualTo("Premium");
                    org.assertj.core.api.Assertions.assertThat(pa.estado()).isEqualTo(CompaniaPlan.Estado.ACTIVO);
                    org.assertj.core.api.Assertions.assertThat(pa.fechaFin()).isEqualTo(fin);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("deja planActivo null cuando no hay suscripción activa")
    void planActivoNullSinSuscripcion() {
        Compania c = compania(11L, "Gym B");

        when(companiaRepository.findAll()).thenReturn(Flux.just(c));
        when(companiaPlanRepository.findActivoByIdCompania(11L)).thenReturn(Mono.empty());

        StepVerifier.create(service.listarCompanias(plataforma))
                .assertNext(cp -> {
                    org.assertj.core.api.Assertions.assertThat(cp.compania().getId()).isEqualTo(11L);
                    org.assertj.core.api.Assertions.assertThat(cp.planActivo()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("no rompe el stream si una compañía no tiene plan y otra sí")
    void mezclaConYSinPlan() {
        Compania sinPlan = compania(1L, "Sin");
        Compania conPlan = compania(2L, "Con");
        Plan plan = new Plan();
        plan.setId(9L);
        plan.setNombre("Free");

        when(companiaRepository.findAll()).thenReturn(Flux.just(sinPlan, conPlan));
        when(companiaPlanRepository.findActivoByIdCompania(1L)).thenReturn(Mono.empty());
        when(companiaPlanRepository.findActivoByIdCompania(2L))
                .thenReturn(Mono.just(planActivo(2L, 9L, CompaniaPlan.Estado.EN_GRACIA, LocalDate.now().plusDays(3))));
        when(planRepository.findById(9L)).thenReturn(Mono.just(plan));

        StepVerifier.create(service.listarCompanias(plataforma))
                .expectNextCount(2)
                .verifyComplete();
    }
}
