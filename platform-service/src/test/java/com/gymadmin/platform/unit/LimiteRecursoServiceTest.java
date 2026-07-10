package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.LimiteRecursoService;
import com.gymadmin.platform.domain.exception.LimiteAlcanzadoException;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.model.RecursoLimitable;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import com.gymadmin.platform.infrastructure.adapter.out.http.CoreServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.FetchSpec;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * REQ-SAAS-001 (RN-05): tests unitarios de {@link LimiteRecursoService}.
 * <p>
 * Se cubren los dos flujos de decisión de negocio principales:
 * <ol>
 *   <li>Plan sin límite duro para el recurso → OK, no lanza.</li>
 *   <li>Sin suscripción activa → OK, no lanza (el caller decide).</li>
 * </ol>
 * El path "límite alcanzado" requiere mockear el chain SQL de {@code DatabaseClient}
 * para el COUNT; se prueba a nivel de integración en Sub-fase 1.4.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LimiteRecursoService — validación de cuotas RN-05")
class LimiteRecursoServiceTest {

    @Mock DatabaseClient databaseClient;
    @Mock CompaniaPlanRepository companiaPlanRepository;
    @Mock PlanRepository planRepository;
    @Mock ActividadPlataformaUseCase actividadPlataformaUseCase;
    @Mock CoreServiceClient coreServiceClient;

    @Mock DatabaseClient.GenericExecuteSpec advisorySpec;
    @Mock FetchSpec<Map<String, Object>> advisoryFetch;

    private CompaniaPlan buildCompaniaPlan(Long idCompania, Long idPlan) {
        CompaniaPlan cp = new CompaniaPlan();
        cp.setId(10L);
        cp.setIdCompania(idCompania);
        cp.setIdPlan(idPlan);
        cp.setEstado(CompaniaPlan.Estado.ACTIVO);
        return cp;
    }

    private Plan buildPlan(String codigo, Integer maxSucursales) {
        Plan p = new Plan();
        p.setId(2L);
        p.setCodigo(codigo);
        p.setMaxSucursales(maxSucursales);
        return p;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void configurarAdvisoryLockNoOp() {
        lenient().when(databaseClient.sql(anyString())).thenReturn(advisorySpec);
        lenient().when(advisorySpec.bind(anyString(), any())).thenReturn(advisorySpec);
        lenient().when(advisorySpec.fetch()).thenReturn(advisoryFetch);
        lenient().when(advisoryFetch.rowsUpdated()).thenReturn((Mono) Mono.just(0L));
    }

    @Test
    @DisplayName("plan sin max_sucursales → OK, no lanza excepción")
    void planSinLimiteOk() {
        configurarAdvisoryLockNoOp();
        Long idCompania = 1L;
        Plan plan = buildPlan("PREMIUM", null);
        when(companiaPlanRepository.findActivoByIdCompania(eq(idCompania)))
                .thenReturn(Mono.just(buildCompaniaPlan(idCompania, plan.getId())));
        when(planRepository.findById(eq(plan.getId()))).thenReturn(Mono.just(plan));

        LimiteRecursoService service = new LimiteRecursoService(
                databaseClient, companiaPlanRepository, planRepository, actividadPlataformaUseCase, coreServiceClient);

        StepVerifier.create(service.validarPuedeCrear(idCompania, RecursoLimitable.SUCURSALES))
                .verifyComplete();
    }

    @Test
    @DisplayName("sin suscripción activa → OK (caller decide)")
    void sinSuscripcionActivaOk() {
        configurarAdvisoryLockNoOp();
        Long idCompania = 99L;
        when(companiaPlanRepository.findActivoByIdCompania(eq(idCompania)))
                .thenReturn(Mono.empty());

        LimiteRecursoService service = new LimiteRecursoService(
                databaseClient, companiaPlanRepository, planRepository, actividadPlataformaUseCase, coreServiceClient);

        StepVerifier.create(service.validarPuedeCrear(idCompania, RecursoLimitable.SUCURSALES))
                .verifyComplete();
    }

    @Test
    @DisplayName("plan con maxClientesActivos=50 y stub retorna 0 → OK")
    void clientesActivosPorDebajoLimite() {
        configurarAdvisoryLockNoOp();
        Long idCompania = 5L;
        Plan planFree = buildPlan("FREE", null);
        planFree.setMaxClientesActivos(50);
        when(companiaPlanRepository.findActivoByIdCompania(eq(idCompania)))
                .thenReturn(Mono.just(buildCompaniaPlan(idCompania, planFree.getId())));
        when(planRepository.findById(eq(planFree.getId()))).thenReturn(Mono.just(planFree));
        when(coreServiceClient.contarClientesActivos(eq(idCompania))).thenReturn(Mono.just(0L));

        LimiteRecursoService service = new LimiteRecursoService(
                databaseClient, companiaPlanRepository, planRepository, actividadPlataformaUseCase, coreServiceClient);

        // El stub cross-service retorna 0 → no supera el máximo, no lanza.
        StepVerifier.create(service.validarPuedeCrear(idCompania, RecursoLimitable.CLIENTES_ACTIVOS))
                .verifyComplete();
    }

    /** Aserto documental: la excepción existe y sus getters funcionan como se espera. */
    @Test
    @DisplayName("LimiteAlcanzadoException conserva recurso/actual/máximo/planCodigo")
    void limiteAlcanzadoExceptionEsInmutable() {
        LimiteAlcanzadoException ex = new LimiteAlcanzadoException(
                RecursoLimitable.SUCURSALES, 3, 1, "FREE");
        org.assertj.core.api.Assertions.assertThat(ex.getRecurso()).isEqualTo(RecursoLimitable.SUCURSALES);
        org.assertj.core.api.Assertions.assertThat(ex.getActual()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(ex.getMaximo()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(ex.getPlanCodigo()).isEqualTo("FREE");
    }
}
