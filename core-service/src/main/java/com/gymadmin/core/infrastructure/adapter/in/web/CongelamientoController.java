package com.gymadmin.core.infrastructure.adapter.in.web;

import com.gymadmin.core.application.service.AccessControlService;
import com.gymadmin.core.domain.model.Congelamiento;
import com.gymadmin.core.domain.port.in.CongelamientoUseCase;
import com.gymadmin.core.infrastructure.adapter.in.web.dto.CongelarRequest;
import com.gymadmin.core.infrastructure.config.JwtPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class CongelamientoController {

    private final CongelamientoUseCase congelamientoUseCase;
    private final AccessControlService accessControl;

    public CongelamientoController(CongelamientoUseCase congelamientoUseCase,
                                   AccessControlService accessControl) {
        this.congelamientoUseCase = congelamientoUseCase;
        this.accessControl = accessControl;
    }

    @PostMapping("/membresias/{id}/congelar")
    public Mono<ResponseEntity<Map<String, Object>>> congelar(@PathVariable Long id,
                                                              @Valid @RequestBody CongelarRequest request) {
        return extractPrincipal()
                .flatMap(principal -> {
                    if (request.retroactivo()) {
                        return accessControl.requireAdminOrDueno(principal, principal.getIdCompania());
                    }
                    return accessControl.requireRecepcionOrAbove(principal, principal.getIdCompania());
                })
                .then(extractPrincipal())
                .flatMap(principal -> congelamientoUseCase.congelar(
                        id,
                        principal.getIdCompania(),
                        principal.getIdCompania(),
                        Long.parseLong(principal.getUserId()),
                        new CongelamientoUseCase.CongelarCommand(
                                request.fechaInicio(),
                                Congelamiento.Motivo.valueOf(request.motivo()),
                                request.detalle(),
                                request.retroactivo(),
                                request.documentoRespaldo(),
                                request.aprobadoPor()
                        )
                ))
                .map(cong -> ResponseEntity.status(HttpStatus.CREATED).<Map<String, Object>>body(Map.of(
                        "id_congelamiento", cong.getId(),
                        "fecha_inicio", cong.getFechaInicio(),
                        "fecha_fin", cong.getFechaFin() != null ? cong.getFechaFin().toString() : ""
                )));
    }

    @PutMapping("/congelamientos/{id}/reactivar")
    public Mono<ResponseEntity<Map<String, Object>>> reactivar(@PathVariable Long id) {
        return extractPrincipal()
                .flatMap(principal -> accessControl.requireRecepcionOrAbove(principal, principal.getIdCompania())
                        .then(congelamientoUseCase.reactivar(id, principal.getIdCompania()))
                )
                .map(result -> ResponseEntity.ok(Map.<String, Object>of(
                        "fecha_fin_anterior", result.fechaFinAnterior(),
                        "dias_compensados", result.diasCompensados(),
                        "fecha_fin_nueva", result.fechaFinNueva()
                )));
    }

    @PutMapping("/mis-congelamientos/{id}/reactivar")
    public Mono<ResponseEntity<Map<String, Object>>> reactivarCliente(@PathVariable Long id) {
        return extractPrincipal()
                .flatMap(principal -> accessControl.requireCliente(principal, principal.getIdCompania())
                        .then(congelamientoUseCase.reactivarPorCliente(id, principal.getIdPersona(), principal.getIdCompania()))
                )
                .map(result -> ResponseEntity.ok(Map.<String, Object>of(
                        "fecha_fin_anterior", result.fechaFinAnterior(),
                        "dias_compensados", result.diasCompensados(),
                        "fecha_fin_nueva", result.fechaFinNueva()
                )));
    }

    @GetMapping("/membresias/{id}/congelamientos")
    public Flux<Map<String, Object>> historial(@PathVariable Long id) {
        return extractPrincipal()
                .flatMapMany(principal -> congelamientoUseCase.historialPorMembresia(id, principal.getIdCompania())
                        .map(cong -> {
                            var m = new java.util.LinkedHashMap<String, Object>();
                            m.put("id", cong.getId());
                            m.put("fecha_inicio", cong.getFechaInicio());
                            m.put("fecha_fin", cong.getFechaFin());
                            m.put("motivo", cong.getMotivo());
                            m.put("retroactivo", cong.getRetroactivo());
                            return (Map<String, Object>) m;
                        })
                );
    }

    private Mono<JwtPrincipal> extractPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getPrincipal())
                .cast(JwtPrincipal.class);
    }
}
