package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.in.PlanUseCase;
import com.gymadmin.platform.infrastructure.adapter.in.web.PlanController;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import com.gymadmin.platform.infrastructure.exception.ForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * REQ-SAAS-001 Sub-fase 1.6: verifica que el campo {@code codigo} del plan
 * se propague desde el modelo de dominio al DTO de respuesta HTTP en los
 * endpoints protegidos de {@code /api/v1/planes}.
 */
@DisplayName("PlanController — propagación de codigo en PlanResponse")
class PlanControllerTest {

    private final PlanUseCase planUseCase = mock(PlanUseCase.class);
    private final ActividadPlataformaUseCase actividadUseCase = mock(ActividadPlataformaUseCase.class);
    private final AccessControlService accessControl = new AccessControlService();
    private final PlanController controller = new PlanController(planUseCase, accessControl, actividadUseCase);

    private WebTestClient clientForPrincipal(JwtPrincipal principal) {
        return WebTestClient.bindToController(controller)
                .controllerAdvice(new ForbiddenExceptionAdvice())
                .webFilter((exchange, chain) -> chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                                Mono.just(new SecurityContextImpl(
                                        new UsernamePasswordAuthenticationToken(
                                                principal, "n/a",
                                                List.of(new SimpleGrantedAuthority("ROLE_USER")))))
                        )))
                .build();
    }

    @RestControllerAdvice
    static class ForbiddenExceptionAdvice {
        @ExceptionHandler(ForbiddenException.class)
        public ResponseEntity<String> handle(ForbiddenException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
        }
    }

    private JwtPrincipal superAdmin() {
        return new JwtPrincipal("root-1", "plataforma", "super_admin", null, null);
    }

    private Plan buildPlan(Long id, String codigo, String nombre) {
        Plan plan = new Plan();
        plan.setId(id);
        plan.setCodigo(codigo);
        plan.setNombre(nombre);
        plan.setDescripcion("desc " + codigo);
        plan.setPrecioMensual(BigDecimal.valueOf(29.99));
        plan.setActivo(true);
        return plan;
    }

    @Test
    @DisplayName("GET /planes expone el campo codigo en cada elemento de la lista")
    void listarPlanesExponeCodigo() {
        Plan trial = buildPlan(10L, "TRIAL", "Prueba gratuita");
        Plan premium = buildPlan(11L, "PREMIUM", "Premium mensual");
        when(planUseCase.listarPlanes()).thenReturn(Flux.just(trial, premium));

        clientForPrincipal(superAdmin())
                .get()
                .uri("/api/v1/planes")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(10)
                .jsonPath("$[0].codigo").isEqualTo("TRIAL")
                .jsonPath("$[0].nombre").isEqualTo("Prueba gratuita")
                .jsonPath("$[1].codigo").isEqualTo("PREMIUM");
    }

    @Test
    @DisplayName("POST /planes retorna el codigo del plan recien creado")
    void crearPlanExponeCodigo() {
        Plan creado = buildPlan(20L, "PREMIUM", "Premium");
        when(planUseCase.crearPlan(any(PlanUseCase.CrearPlanCommand.class))).thenReturn(Mono.just(creado));
        when(actividadUseCase.registrar(any(ActividadPlataformaUseCase.RegistrarCommand.class)))
                .thenReturn(Mono.empty());

        clientForPrincipal(superAdmin())
                .post()
                .uri("/api/v1/planes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", "Premium",
                        "descripcion", "Premium mensual",
                        "precioMensual", 29.99
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(20)
                .jsonPath("$.codigo").isEqualTo("PREMIUM");
    }

    @Test
    @DisplayName("PUT /planes/{id} retorna el codigo del plan actualizado")
    void actualizarPlanExponeCodigo() {
        Plan actualizado = buildPlan(30L, "PREMIUM", "Premium v2");
        when(planUseCase.actualizarPlan(org.mockito.ArgumentMatchers.eq(30L),
                any(PlanUseCase.ActualizarPlanCommand.class)))
                .thenReturn(Mono.just(actualizado));
        when(actividadUseCase.registrar(any(ActividadPlataformaUseCase.RegistrarCommand.class)))
                .thenReturn(Mono.empty());

        clientForPrincipal(superAdmin())
                .put()
                .uri("/api/v1/planes/30")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", "Premium v2",
                        "descripcion", "actualizado",
                        "precioMensual", 39.99
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("PREMIUM")
                .jsonPath("$.nombre").isEqualTo("Premium v2");
    }
}
