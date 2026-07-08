package com.gymadmin.attendance.infrastructure.adapter.in.web;

import com.gymadmin.attendance.application.service.AccessControlService;
import com.gymadmin.attendance.domain.model.MensajeLog;
import com.gymadmin.attendance.domain.port.in.MensajeLogUseCase;
import com.gymadmin.attendance.infrastructure.adapter.in.web.dto.EnviarMensajeRequest;
import com.gymadmin.attendance.infrastructure.config.JwtPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mensajes")
@RequiredArgsConstructor
public class MensajeController {

    private final MensajeLogUseCase mensajeLogUseCase;
    private final AccessControlService accessControl;

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> listar(
            @RequestParam(required = false) Integer idCliente,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde) {

        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireStaffOrPlataforma(principal)
                        .thenMany(mensajeLogUseCase.listar(
                                principal.getIdCompania().intValue(), idCliente, tipo, estado, desde))
                        .collectList()
                        .map(lista -> ResponseEntity.ok(Map.of(
                                "total", lista.size(),
                                "datos", lista
                        ))));
    }

    @PostMapping("/enviar")
    public Mono<ResponseEntity<MensajeLog>> enviarManual(@Valid @RequestBody EnviarMensajeRequest request) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireStaffOrPlataforma(principal)
                        .then(mensajeLogUseCase.enviarManual(new MensajeLogUseCase.EnviarMensajeCommand(
                                request.idCliente(),
                                request.canal(),
                                request.idPlantilla(),
                                principal.getIdCompania().intValue(),
                                1,
                                principal.getUserId()
                        )))
                        .map(m -> ResponseEntity.status(HttpStatus.CREATED).body(m)));
    }

    @PostMapping("/reenviar/{id}")
    public Mono<ResponseEntity<MensajeLog>> reenviar(@PathVariable Long id) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireStaffOrPlataforma(principal)
                        .then(mensajeLogUseCase.reenviar(id, principal.getIdCompania().intValue()))
                        .map(ResponseEntity::ok));
    }

    private Mono<JwtPrincipal> getJwtPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(JwtPrincipal.class);
    }
}
