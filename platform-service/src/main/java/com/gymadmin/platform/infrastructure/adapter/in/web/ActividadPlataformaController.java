package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.domain.model.ActividadPlataforma;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.ActividadPlataformaResponse;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.PaginadoActividadResponse;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/actividad")
public class ActividadPlataformaController {

    private static final int POR_PAGINA = 25;

    private final ActividadPlataformaUseCase actividadUseCase;
    private final AccessControlService accessControl;

    public ActividadPlataformaController(ActividadPlataformaUseCase actividadUseCase,
                                          AccessControlService accessControl) {
        this.actividadUseCase = actividadUseCase;
        this.accessControl = accessControl;
    }

    @GetMapping
    public Mono<ResponseEntity<PaginadoActividadResponse>> listar(
            @RequestParam(required = false) String modulo,
            @RequestParam(required = false) String tipoEvento,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(defaultValue = "1") int pagina) {

        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requirePlataforma(principal)
                        .then(Mono.defer(() -> {
                            ActividadPlataformaUseCase.ListarQuery query =
                                    new ActividadPlataformaUseCase.ListarQuery(
                                            modulo, tipoEvento, desde, hasta,
                                            Math.max(1, pagina), POR_PAGINA);

                            return actividadUseCase.contar(query)
                                    .flatMap(total -> actividadUseCase.listar(query)
                                            .map(this::toResponse)
                                            .collectList()
                                            .map(datos -> ResponseEntity.ok(
                                                    new PaginadoActividadResponse(total, pagina, datos))));
                        })));
    }

    private Mono<JwtPrincipal> getJwtPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(JwtPrincipal.class);
    }

    private ActividadPlataformaResponse toResponse(ActividadPlataforma a) {
        return new ActividadPlataformaResponse(
                a.getId(),
                a.getTipoEvento(),
                a.getModulo(),
                a.getEntidadId(),
                a.getEntidadNombre(),
                a.getDetalle(),
                a.getUsuario(),
                a.getIp(),
                a.getFecha()
        );
    }
}
