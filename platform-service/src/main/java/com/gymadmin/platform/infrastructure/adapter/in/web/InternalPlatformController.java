package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.application.service.LimiteRecursoService;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.model.RecursoLimitable;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import com.gymadmin.platform.infrastructure.adapter.out.http.CoreServiceClient;
import com.gymadmin.platform.infrastructure.exception.ForbiddenException;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REQ-SAAS-001 (RN-06, Sub-fase 1.4): endpoint interno usado por otros servicios
 * (core-service, auth-service) para verificar si un tenant puede crear un
 * recurso adicional.
 * <p>
 * Autenticación inter-service: header {@code X-Internal-Call} con el secreto
 * compartido {@code services.internal.secret} (default {@code platform-secret-dev}).
 * Está fuera del filtro JWT — la ruta {@code /internal/**} debe ser {@code permitAll()}.
 */
@Hidden
@RestController
@RequestMapping("/internal/v1")
public class InternalPlatformController {

    private final CompaniaPlanRepository companiaPlanRepository;
    private final PlanRepository planRepository;
    private final LimiteRecursoService limiteRecursoService;
    private final String internalSecret;

    public InternalPlatformController(CompaniaPlanRepository companiaPlanRepository,
                                       PlanRepository planRepository,
                                       LimiteRecursoService limiteRecursoService,
                                       @Value("${services.internal.secret:platform-secret-dev}") String internalSecret) {
        this.companiaPlanRepository = companiaPlanRepository;
        this.planRepository = planRepository;
        this.limiteRecursoService = limiteRecursoService;
        this.internalSecret = internalSecret;
    }

    @GetMapping("/companias/{id}/uso-limites/{recurso}")
    public Mono<ResponseEntity<Map<String, Object>>> checkLimite(
            @PathVariable("id") Long idCompania,
            @PathVariable("recurso") String recurso,
            @RequestHeader(value = CoreServiceClient.HEADER_INTERNAL_CALL, required = false) String internalCall) {

        if (internalCall == null || !internalCall.equals(internalSecret)) {
            return Mono.error(new ForbiddenException("Invalid internal call"));
        }

        RecursoLimitable recursoEnum = parseRecurso(recurso);
        if (recursoEnum == null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("codigo", "recurso_invalido");
            body.put("mensaje", "Recurso desconocido: " + recurso);
            return Mono.just(ResponseEntity.badRequest().body(body));
        }

        return companiaPlanRepository.findActivoByIdCompania(idCompania)
                .flatMap(cp -> planRepository.findById(cp.getIdPlan())
                        .flatMap(plan -> buildRespuesta(idCompania, recursoEnum, cp, plan)))
                .defaultIfEmpty(sinSuscripcion(recursoEnum))
                .map(ResponseEntity::ok);
    }

    private Mono<Map<String, Object>> buildRespuesta(Long idCompania,
                                                       RecursoLimitable recurso,
                                                       CompaniaPlan cp,
                                                       Plan plan) {
        Integer maximoInt = maximoParaRecurso(plan, recurso);
        return limiteRecursoService.contarUsoActual(idCompania, recurso)
                .map(actual -> {
                    long maximo = maximoInt == null ? Long.MAX_VALUE : maximoInt.longValue();
                    boolean permite = actual < maximo;
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("permite", permite);
                    body.put("actual", actual);
                    body.put("maximo", maximoInt);
                    body.put("planCodigo", plan.getCodigo());
                    return body;
                });
    }

    private Map<String, Object> sinSuscripcion(RecursoLimitable recurso) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("permite", false);
        body.put("actual", 0);
        body.put("maximo", 0);
        body.put("planCodigo", null);
        return body;
    }

    private Integer maximoParaRecurso(Plan plan, RecursoLimitable recurso) {
        return switch (recurso) {
            case SUCURSALES -> plan.getMaxSucursales();
            case CLIENTES_ACTIVOS -> plan.getMaxClientesActivos();
            case STAFF -> plan.getMaxStaff();
        };
    }

    private RecursoLimitable parseRecurso(String recurso) {
        if (recurso == null) return null;
        return switch (recurso.toLowerCase()) {
            case "sucursales" -> RecursoLimitable.SUCURSALES;
            case "clientes_activos", "clientes-activos" -> RecursoLimitable.CLIENTES_ACTIVOS;
            case "staff" -> RecursoLimitable.STAFF;
            default -> null;
        };
    }
}
