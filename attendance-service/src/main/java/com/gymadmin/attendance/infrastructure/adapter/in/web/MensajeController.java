package com.gymadmin.attendance.infrastructure.adapter.in.web;

import com.gymadmin.attendance.application.service.AccessControlService;
import com.gymadmin.attendance.domain.model.MensajeLog;
import com.gymadmin.attendance.domain.port.in.MensajeLogUseCase;
import com.gymadmin.attendance.infrastructure.adapter.in.web.dto.EnviarMensajeRequest;
import com.gymadmin.attendance.infrastructure.config.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Mensajes", description = "Envío y reenvío de mensajes a clientes")
@RestController
@RequestMapping("/api/v1/mensajes")
@RequiredArgsConstructor
public class MensajeController {

    private final MensajeLogUseCase mensajeLogUseCase;
    private final AccessControlService accessControl;

    @Operation(summary = "Listar mensajes enviados", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de mensajes enviados filtrada por los parámetros"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado — se requiere rol staff o plataforma")
    })
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

    @Operation(summary = "Enviar mensaje", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Mensaje enviado correctamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado — se requiere rol staff o plataforma"),
            @ApiResponse(responseCode = "404", description = "Cliente o plantilla no encontrada")
    })
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

    @Operation(summary = "Reenviar mensaje", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mensaje reenviado correctamente"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado — se requiere rol staff o plataforma"),
            @ApiResponse(responseCode = "404", description = "Mensaje no encontrado"),
            @ApiResponse(responseCode = "409", description = "El mensaje no está en estado fallido y no puede reenviarse")
    })
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
