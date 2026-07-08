package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.SuscripcionService;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.port.in.SuscripcionUseCase.DowngradeCommand;
import com.gymadmin.platform.domain.port.in.SuscripcionUseCase.RenovarCommand;
import com.gymadmin.platform.domain.port.in.SuscripcionUseCase.UpgradeCommand;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import com.gymadmin.platform.infrastructure.exception.BusinessException;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SuscripcionService — gestión de suscripciones de compañías")
class SuscripcionServiceTest {

    @Mock
    private CompaniaPlanRepository companiaPlanRepository;

    @Mock
    private PlanRepository planRepository;

    @InjectMocks
    private SuscripcionService service;

    private CompaniaPlan buildPlanActivo(Long id, Long idCompania, Long idPlan,
                                          LocalDate inicio, LocalDate fin, CompaniaPlan.Estado estado) {
        CompaniaPlan cp = new CompaniaPlan();
        cp.setId(id);
        cp.setIdCompania(idCompania);
        cp.setIdPlan(idPlan);
        cp.setFechaInicio(inicio);
        cp.setFechaFin(fin);
        cp.setDiasGracia(7);
        cp.setEstado(estado);
        return cp;
    }

    private Plan buildPlan(Long id, String nombre, BigDecimal precioMensual) {
        Plan plan = new Plan();
        plan.setId(id);
        plan.setNombre(nombre);
        plan.setPrecioMensual(precioMensual);
        plan.setActivo(true);
        return plan;
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("getSuscripcionActiva")
    class GetSuscripcionActiva {

        @Test
        @DisplayName("retorna la suscripción activa de la compañía")
        void retornaSuscripcionActiva() {
            CompaniaPlan cp = buildPlanActivo(1L, 1L, 2L,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1), CompaniaPlan.Estado.ACTIVO);
            when(companiaPlanRepository.findHistorialByIdCompania(1L)).thenReturn(Flux.just(cp));

            StepVerifier.create(service.getSuscripcionActiva(1L))
                    .expectNext(cp)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna suscripción EN_GRACIA como suscripción activa")
        void retornaSuscripcionEnGracia() {
            CompaniaPlan cp = buildPlanActivo(1L, 1L, 2L,
                    LocalDate.now().minusMonths(2), LocalDate.now().minusDays(3), CompaniaPlan.Estado.EN_GRACIA);
            when(companiaPlanRepository.findHistorialByIdCompania(1L)).thenReturn(Flux.just(cp));

            StepVerifier.create(service.getSuscripcionActiva(1L))
                    .expectNext(cp)
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando no existe suscripción activa")
        void lanzaNotFoundCuandoNoExisteSuscripcion() {
            CompaniaPlan vencida = buildPlanActivo(1L, 1L, 2L,
                    LocalDate.now().minusMonths(3), LocalDate.now().minusMonths(1), CompaniaPlan.Estado.VENCIDO);
            when(companiaPlanRepository.findHistorialByIdCompania(1L)).thenReturn(Flux.just(vencida));

            StepVerifier.create(service.getSuscripcionActiva(1L))
                    .expectError(NotFoundException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando el historial está vacío")
        void lanzaNotFoundCuandoHistorialVacio() {
            when(companiaPlanRepository.findHistorialByIdCompania(1L)).thenReturn(Flux.empty());

            StepVerifier.create(service.getSuscripcionActiva(1L))
                    .expectError(NotFoundException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("getHistorial")
    class GetHistorial {

        @Test
        @DisplayName("retorna el historial completo de suscripciones de la compañía")
        void retornaHistorialCompleto() {
            CompaniaPlan cp1 = buildPlanActivo(1L, 1L, 2L,
                    LocalDate.now().minusMonths(3), LocalDate.now().minusMonths(2), CompaniaPlan.Estado.VENCIDO);
            CompaniaPlan cp2 = buildPlanActivo(2L, 1L, 3L,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1), CompaniaPlan.Estado.ACTIVO);
            when(companiaPlanRepository.findHistorialByIdCompania(1L)).thenReturn(Flux.just(cp1, cp2));

            StepVerifier.create(service.getHistorial(1L))
                    .expectNext(cp1)
                    .expectNext(cp2)
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("renovar")
    class Renovar {

        @Test
        @DisplayName("renueva usando el mismo plan con 1 mes cuando no se especifica plan ni meses")
        void renuevaConMismoPlanPorDefecto() {
            CompaniaPlan actual = buildPlanActivo(1L, 1L, 2L,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusDays(5), CompaniaPlan.Estado.ACTIVO);
            Plan plan = buildPlan(2L, "Básico", BigDecimal.valueOf(100));
            CompaniaPlan renovada = buildPlanActivo(2L, 1L, 2L,
                    LocalDate.now().plusDays(6), LocalDate.now().plusDays(6).plusMonths(1), CompaniaPlan.Estado.ACTIVO);

            when(companiaPlanRepository.findActivoByIdCompania(1L)).thenReturn(Mono.just(actual));
            when(planRepository.findById(2L)).thenReturn(Mono.just(plan));
            when(companiaPlanRepository.save(any())).thenReturn(Mono.just(renovada));

            RenovarCommand cmd = new RenovarCommand(null, null);

            StepVerifier.create(service.renovar(1L, cmd))
                    .assertNext(cp -> {
                        assertThat(cp.getEstado()).isEqualTo(CompaniaPlan.Estado.ACTIVO);
                        assertThat(cp.getTipoCambio()).isNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("renueva con el plan e intervalo especificados en el comando")
        void renuevaConPlanYMesesEspecificos() {
            CompaniaPlan actual = buildPlanActivo(1L, 1L, 2L,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusDays(5), CompaniaPlan.Estado.ACTIVO);
            Plan planNuevo = buildPlan(3L, "Pro", BigDecimal.valueOf(200));
            CompaniaPlan renovada = buildPlanActivo(2L, 1L, 3L,
                    LocalDate.now().plusDays(6), LocalDate.now().plusDays(6).plusMonths(3), CompaniaPlan.Estado.ACTIVO);
            renovada.setTipoCambio(CompaniaPlan.TipoCambio.RENOVACION);

            when(companiaPlanRepository.findActivoByIdCompania(1L)).thenReturn(Mono.just(actual));
            when(planRepository.findById(3L)).thenReturn(Mono.just(planNuevo));
            when(companiaPlanRepository.save(any())).thenReturn(Mono.just(renovada));

            RenovarCommand cmd = new RenovarCommand(3L, 3);

            StepVerifier.create(service.renovar(1L, cmd))
                    .assertNext(cp -> assertThat(cp.getIdPlan()).isEqualTo(3L))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando no existe suscripción activa para renovar")
        void lanzaNotFoundCuandoNoExisteSuscripcionActiva() {
            when(companiaPlanRepository.findActivoByIdCompania(1L)).thenReturn(Mono.empty());

            StepVerifier.create(service.renovar(1L, new RenovarCommand(null, null)))
                    .expectError(NotFoundException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("upgrade")
    class Upgrade {

        @Test
        @DisplayName("realiza upgrade cancelando el plan actual y creando uno nuevo de mayor precio")
        void realizaUpgradeExitosamente() {
            CompaniaPlan actual = buildPlanActivo(1L, 1L, 2L,
                    LocalDate.now().minusDays(15), LocalDate.now().plusDays(15), CompaniaPlan.Estado.ACTIVO);
            Plan planActual = buildPlan(2L, "Básico", BigDecimal.valueOf(100));
            Plan planNuevo  = buildPlan(3L, "Pro",    BigDecimal.valueOf(300));

            CompaniaPlan cancelado = buildPlanActivo(10L, 1L, 2L,
                    actual.getFechaInicio(), LocalDate.now(), CompaniaPlan.Estado.CANCELADO);
            CompaniaPlan nuevo = buildPlanActivo(11L, 1L, 3L,
                    LocalDate.now(), LocalDate.now().plusMonths(1), CompaniaPlan.Estado.ACTIVO);
            nuevo.setTipoCambio(CompaniaPlan.TipoCambio.UPGRADE);

            when(companiaPlanRepository.findActivoByIdCompania(1L)).thenReturn(Mono.just(actual));
            when(planRepository.findById(2L)).thenReturn(Mono.just(planActual));
            when(planRepository.findById(3L)).thenReturn(Mono.just(planNuevo));
            when(companiaPlanRepository.save(any()))
                    .thenReturn(Mono.just(cancelado))
                    .thenReturn(Mono.just(nuevo));

            StepVerifier.create(service.upgrade(1L, new UpgradeCommand(3L)))
                    .assertNext(r -> {
                        assertThat(r.idCompaniaPlanNuevo()).isEqualTo(11L);
                        assertThat(r.planAnteriorCancelado()).isTrue();
                        assertThat(r.montoAPagar()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza BusinessException cuando el plan nuevo no es de mayor precio")
        void lanzaBusinessExceptionCuandoPlanNoEsMayorPrecio() {
            CompaniaPlan actual = buildPlanActivo(1L, 1L, 2L,
                    LocalDate.now().minusDays(15), LocalDate.now().plusDays(15), CompaniaPlan.Estado.ACTIVO);
            Plan planActual = buildPlan(2L, "Pro",   BigDecimal.valueOf(300));
            Plan planBarato = buildPlan(3L, "Básico", BigDecimal.valueOf(100));

            when(companiaPlanRepository.findActivoByIdCompania(1L)).thenReturn(Mono.just(actual));
            when(planRepository.findById(2L)).thenReturn(Mono.just(planActual));
            when(planRepository.findById(3L)).thenReturn(Mono.just(planBarato));

            StepVerifier.create(service.upgrade(1L, new UpgradeCommand(3L)))
                    .expectErrorSatisfies(e -> {
                        assertThat(e).isInstanceOf(BusinessException.class);
                        assertThat(e.getMessage()).containsIgnoringCase("higher price");
                    })
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("downgrade")
    class Downgrade {

        @Test
        @DisplayName("programa un downgrade para después del fin del plan actual")
        void programaDowngradeExitosamente() {
            CompaniaPlan actual = buildPlanActivo(1L, 1L, 3L,
                    LocalDate.now().minusDays(15), LocalDate.now().plusDays(15), CompaniaPlan.Estado.ACTIVO);
            Plan planActual   = buildPlan(3L, "Pro",    BigDecimal.valueOf(300));
            Plan planEconomico = buildPlan(2L, "Básico", BigDecimal.valueOf(100));

            CompaniaPlan programado = buildPlanActivo(20L, 1L, 2L,
                    actual.getFechaFin().plusDays(1),
                    actual.getFechaFin().plusDays(1).plusMonths(1),
                    CompaniaPlan.Estado.PROGRAMADO);
            programado.setTipoCambio(CompaniaPlan.TipoCambio.DOWNGRADE);

            when(companiaPlanRepository.findActivoByIdCompania(1L)).thenReturn(Mono.just(actual));
            when(planRepository.findById(3L)).thenReturn(Mono.just(planActual));
            when(planRepository.findById(2L)).thenReturn(Mono.just(planEconomico));
            when(companiaPlanRepository.save(any())).thenReturn(Mono.just(programado));

            StepVerifier.create(service.downgrade(1L, new DowngradeCommand(2L)))
                    .assertNext(r -> {
                        assertThat(r.idCompaniaPlanNuevo()).isEqualTo(20L);
                        assertThat(r.estado()).isEqualTo("PROGRAMADO");
                        assertThat(r.efectivoDe()).isAfter(LocalDate.now());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza BusinessException cuando el plan nuevo no es de menor precio")
        void lanzaBusinessExceptionCuandoPlanNoEsMenorPrecio() {
            CompaniaPlan actual = buildPlanActivo(1L, 1L, 2L,
                    LocalDate.now().minusDays(15), LocalDate.now().plusDays(15), CompaniaPlan.Estado.ACTIVO);
            Plan planActual = buildPlan(2L, "Básico", BigDecimal.valueOf(100));
            Plan planCaro   = buildPlan(3L, "Pro",    BigDecimal.valueOf(300));

            when(companiaPlanRepository.findActivoByIdCompania(1L)).thenReturn(Mono.just(actual));
            when(planRepository.findById(2L)).thenReturn(Mono.just(planActual));
            when(planRepository.findById(3L)).thenReturn(Mono.just(planCaro));

            StepVerifier.create(service.downgrade(1L, new DowngradeCommand(3L)))
                    .expectErrorSatisfies(e -> {
                        assertThat(e).isInstanceOf(BusinessException.class);
                        assertThat(e.getMessage()).containsIgnoringCase("lower price");
                    })
                    .verify();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando no existe suscripción activa para downgrade")
        void lanzaNotFoundCuandoNoExisteSuscripcionActiva() {
            when(companiaPlanRepository.findActivoByIdCompania(1L)).thenReturn(Mono.empty());

            StepVerifier.create(service.downgrade(1L, new DowngradeCommand(2L)))
                    .expectError(NotFoundException.class)
                    .verify();
        }
    }
}
