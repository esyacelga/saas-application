package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.PlanService;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.port.in.PlanUseCase.ActualizarPlanCommand;
import com.gymadmin.platform.domain.port.in.PlanUseCase.CrearPlanCommand;
import com.gymadmin.platform.domain.port.out.PlanRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlanService — gestión de planes de suscripción")
class PlanServiceTest {

    @Mock
    private PlanRepository planRepository;

    @InjectMocks
    private PlanService service;

    private Plan buildPlan(Long id, String nombre, boolean activo) {
        Plan plan = new Plan();
        plan.setId(id);
        plan.setNombre(nombre);
        plan.setDescripcion("Descripción " + nombre);
        plan.setPrecioMensual(BigDecimal.valueOf(99.99));
        plan.setActivo(activo);
        return plan;
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("listarPlanes")
    class ListarPlanes {

        @Test
        @DisplayName("retorna todos los planes con sus características")
        void retornaTodosLosPlanes() {
            Plan p1 = buildPlan(1L, "Básico", true);
            Plan p2 = buildPlan(2L, "Premium", false);
            when(planRepository.findAllWithCaracteristicas()).thenReturn(Flux.just(p1, p2));

            StepVerifier.create(service.listarPlanes())
                    .expectNextMatches(p -> "Básico".equals(p.getNombre()))
                    .expectNextMatches(p -> "Premium".equals(p.getNombre()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna flux vacío cuando no existen planes")
        void retornaFluxVacioCuandoNoHayPlanes() {
            when(planRepository.findAllWithCaracteristicas()).thenReturn(Flux.empty());

            StepVerifier.create(service.listarPlanes())
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("listarPlanesPublicos")
    class ListarPlanesPublicos {

        @Test
        @DisplayName("retorna solo los planes activos")
        void retornaSoloPlaneActivos() {
            Plan activo = buildPlan(1L, "Básico", true);
            Plan inactivo = buildPlan(2L, "Archivado", false);
            when(planRepository.findAllWithCaracteristicas()).thenReturn(Flux.just(activo, inactivo));

            StepVerifier.create(service.listarPlanesPublicos())
                    .assertNext(p -> {
                        assertThat(p.getActivo()).isTrue();
                        assertThat(p.getNombre()).isEqualTo("Básico");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna flux vacío cuando todos los planes están inactivos")
        void retornaVacioCuandoNoPlanesActivos() {
            Plan inactivo = buildPlan(1L, "Archivado", false);
            when(planRepository.findAllWithCaracteristicas()).thenReturn(Flux.just(inactivo));

            StepVerifier.create(service.listarPlanesPublicos())
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("crearPlan")
    class CrearPlan {

        @Test
        @DisplayName("crea y retorna el plan con activo=true")
        void creaPlanExitosamente() {
            CrearPlanCommand command = new CrearPlanCommand("Pro", "Plan profesional", BigDecimal.valueOf(199.99));
            Plan guardado = buildPlan(5L, "Pro", true);
            when(planRepository.save(any(Plan.class))).thenReturn(Mono.just(guardado));

            StepVerifier.create(service.crearPlan(command))
                    .assertNext(p -> {
                        assertThat(p.getId()).isEqualTo(5L);
                        assertThat(p.getNombre()).isEqualTo("Pro");
                        assertThat(p.getActivo()).isTrue();
                    })
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("actualizarPlan")
    class ActualizarPlan {

        @Test
        @DisplayName("actualiza y retorna el plan cuando existe")
        void actualizaPlanCuandoExiste() {
            Plan existente = buildPlan(1L, "Básico", true);
            ActualizarPlanCommand command = new ActualizarPlanCommand("Básico Plus", "Descripción actualizada",
                    BigDecimal.valueOf(149.99));
            Plan actualizado = buildPlan(1L, "Básico Plus", true);

            when(planRepository.findById(1L)).thenReturn(Mono.just(existente));
            when(planRepository.update(any(Plan.class))).thenReturn(Mono.just(actualizado));

            StepVerifier.create(service.actualizarPlan(1L, command))
                    .assertNext(p -> {
                        assertThat(p.getNombre()).isEqualTo("Básico Plus");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando el plan no existe")
        void lanzaNotFoundCuandoPlanNoExiste() {
            ActualizarPlanCommand command = new ActualizarPlanCommand("Cualquier nombre", "desc",
                    BigDecimal.valueOf(100));
            when(planRepository.findById(99L)).thenReturn(Mono.empty());

            StepVerifier.create(service.actualizarPlan(99L, command))
                    .expectError(NotFoundException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("asignarCaracteristicas")
    class AsignarCaracteristicas {

        @Test
        @DisplayName("elimina características anteriores y guarda las nuevas")
        void asignaCaracteristicasExitosamente() {
            Plan existente = buildPlan(1L, "Básico", true);
            Plan conCaracteristicas = buildPlan(1L, "Básico", true);
            List<Long> caracteristicaIds = List.of(10L, 20L);

            when(planRepository.findById(1L)).thenReturn(Mono.just(existente));
            when(planRepository.deleteCaracteristicasByPlanId(1L)).thenReturn(Mono.empty());
            when(planRepository.saveCaracteristicaRelations(eq(1L), eq(caracteristicaIds))).thenReturn(Mono.empty());
            when(planRepository.findByIdWithCaracteristicas(1L)).thenReturn(Mono.just(conCaracteristicas));

            StepVerifier.create(service.asignarCaracteristicas(1L, caracteristicaIds))
                    .assertNext(p -> assertThat(p.getId()).isEqualTo(1L))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando el plan no existe")
        void lanzaNotFoundCuandoPlanNoExiste() {
            when(planRepository.findById(99L)).thenReturn(Mono.empty());

            StepVerifier.create(service.asignarCaracteristicas(99L, List.of(1L)))
                    .expectError(NotFoundException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("desactivarPlan")
    class DesactivarPlan {

        @Test
        @DisplayName("desactiva el plan y retorna Mono vacío")
        void desactivaPlanExitosamente() {
            Plan existente = buildPlan(1L, "Básico", true);
            Plan desactivado = buildPlan(1L, "Básico", false);

            when(planRepository.findById(1L)).thenReturn(Mono.just(existente));
            when(planRepository.update(any(Plan.class))).thenReturn(Mono.just(desactivado));

            StepVerifier.create(service.desactivarPlan(1L))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando el plan no existe")
        void lanzaNotFoundCuandoPlanNoExiste() {
            when(planRepository.findById(50L)).thenReturn(Mono.empty());

            StepVerifier.create(service.desactivarPlan(50L))
                    .expectError(NotFoundException.class)
                    .verify();
        }
    }
}
