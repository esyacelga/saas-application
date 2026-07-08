package com.gymadmin.core.infrastructure.adapter.in.web;

import com.gymadmin.core.application.service.AccessControlService;
import com.gymadmin.core.domain.port.in.MembresiaUseCase;
import com.gymadmin.core.infrastructure.adapter.in.web.dto.ActualizarAsistenciasPreviasRequest;
import com.gymadmin.core.infrastructure.adapter.in.web.dto.AnularRequest;
import com.gymadmin.core.infrastructure.adapter.in.web.dto.MembresiaResponse;
import com.gymadmin.core.infrastructure.adapter.in.web.dto.VenderMembresiaRequest;
import com.gymadmin.core.infrastructure.config.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Tag(name = "Membresías", description = "Gestión de membresías de clientes")
@RestController
@RequestMapping("/api/v1")
public class MembresiaController {

    private final MembresiaUseCase membresiaUseCase;
    private final AccessControlService accessControl;

    public MembresiaController(MembresiaUseCase membresiaUseCase, AccessControlService accessControl) {
        this.membresiaUseCase = membresiaUseCase;
        this.accessControl = accessControl;
    }

    @Operation(summary = "Listar membresías del cliente", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "404"),
            @ApiResponse(responseCode = "403")
    })
    @GetMapping("/clientes/{id}/membresias")
    public Flux<MembresiaResponse> historial(@PathVariable Long id) {
        return extractPrincipal()
                .flatMapMany(principal -> membresiaUseCase.historialPorCliente(id, principal.getIdCompania())
                        .map(MembresiaResponse::from));
    }

    @Operation(summary = "Crear membresía", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201"),
            @ApiResponse(responseCode = "404"),
            @ApiResponse(responseCode = "403")
    })
    @PostMapping("/clientes/{id}/membresias")
    public Mono<ResponseEntity<MembresiaResponse>> vender(@PathVariable Long id,
                                                          @Valid @RequestBody VenderMembresiaRequest request) {
        return extractPrincipal()
                .flatMap(principal -> accessControl.requireRecepcionOrAbove(principal, principal.getIdCompania())
                        .then(membresiaUseCase.vender(
                                id,
                                principal.getIdCompania(),
                                principal.getIdCompania(),
                                Long.parseLong(principal.getUserId()),
                                new MembresiaUseCase.VenderCommand(
                                        request.idTipoMembresia(), request.fechaInicio(),
                                        request.idMetodoPago(), request.descuentoAplicado()
                                )
                        ))
                )
                .map(m -> ResponseEntity.status(HttpStatus.CREATED).body(MembresiaResponse.from(m)));
    }

    @Operation(summary = "Obtener membresía por ID", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "404"),
            @ApiResponse(responseCode = "403")
    })
    @GetMapping("/membresias/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> detalle(@PathVariable Long id) {
        return extractPrincipal()
                .flatMap(principal -> membresiaUseCase.detalle(id, principal.getIdCompania()))
                .map(result -> {
                    var body = new java.util.LinkedHashMap<String, Object>();
                    body.put("id", result.membresia().getId());
                    body.put("tipo", result.tipoNombre());
                    body.put("modo_control", result.modoControl());
                    body.put("fecha_inicio", result.membresia().getFechaInicio());
                    body.put("fecha_fin", result.membresia().getFechaFin());
                    body.put("dias_acceso_total", result.membresia().getDiasAccesoTotal());
                    body.put("dias_acceso_usados", result.diasAccesoUsados());
                    body.put("dias_acceso_restantes", result.diasAccesoRestantes());
                    body.put("asistencias_previas", result.membresia().getAsistenciasPrevias());
                    body.put("precio_pagado", result.membresia().getPrecioPagado());
                    body.put("estado", result.membresia().getEstado());
                    return ResponseEntity.ok(body);
                });
    }

    @Operation(summary = "Actualizar asistencias previas", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "404"),
            @ApiResponse(responseCode = "403")
    })
    @PatchMapping("/membresias/{id}/asistencias-previas")
    public Mono<ResponseEntity<Map<String, Object>>> actualizarAsistenciasPrevias(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarAsistenciasPreviasRequest request) {
        return extractPrincipal()
                .flatMap(principal -> accessControl.requireRecepcionOrAbove(principal, principal.getIdCompania())
                        .then(membresiaUseCase.actualizarAsistenciasPrevias(id, principal.getIdCompania(), request.cantidad()))
                )
                .map(mem -> {
                    var body = new java.util.LinkedHashMap<String, Object>();
                    body.put("id", mem.getId());
                    body.put("asistencias_previas", mem.getAsistenciasPrevias());
                    return ResponseEntity.ok(body);
                });
    }

    @Operation(summary = "Anular membresía", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "404"),
            @ApiResponse(responseCode = "403")
    })
    @PutMapping("/membresias/{id}/anular")
    public Mono<ResponseEntity<Void>> anular(@PathVariable Long id, @RequestBody AnularRequest request) {
        return extractPrincipal()
                .flatMap(principal -> accessControl.requireAdminOrDueno(principal, principal.getIdCompania())
                        .then(membresiaUseCase.anular(id, principal.getIdCompania(), request.motivo()))
                )
                .thenReturn(ResponseEntity.<Void>ok().build());
    }

    @Operation(summary = "Validar acceso del cliente (público)")
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "403")
    })
    @GetMapping("/membresias/validar-acceso")
    public Mono<ResponseEntity<Map<String, Object>>> validarAcceso(
            @RequestParam Long id_persona,
            @RequestParam Long id_compania) {
        return membresiaUseCase.validarAcceso(id_persona, id_compania)
                .map(result -> {
                    if (result.permitido()) {
                        var body = new java.util.LinkedHashMap<String, Object>();
                        body.put("permitido", true);
                        body.put("id_cliente", result.idCliente());
                        body.put("id_membresia", result.idMembresia());
                        body.put("modo_control", result.modoControl());
                        body.put("tipo_membresia", result.tipoNombre());
                        body.put("dias_acceso_restantes", result.diasAccesoRestantes() != null ? result.diasAccesoRestantes() : 0);
                        body.put("fecha_fin", result.fechaFin());
                        if (result.accesosUsados() != null) body.put("accesos_usados", result.accesosUsados());
                        return ResponseEntity.<Map<String, Object>>ok(body);
                    } else {
                        var body = new java.util.LinkedHashMap<String, Object>();
                        body.put("permitido", false);
                        body.put("razon", result.razon());
                        if (result.tipoNombre() != null) body.put("tipo_membresia", result.tipoNombre());
                        if (result.fechaFin() != null) body.put("ultima_membresia_fin", result.fechaFin());
                        if (result.accesosUsados() != null) body.put("accesos_usados", result.accesosUsados());
                        if (result.accesosTotal() != null) body.put("accesos_total", result.accesosTotal());
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<Map<String, Object>>body(body);
                    }
                });
    }

    private Mono<JwtPrincipal> extractPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getPrincipal())
                .cast(JwtPrincipal.class);
    }
}
