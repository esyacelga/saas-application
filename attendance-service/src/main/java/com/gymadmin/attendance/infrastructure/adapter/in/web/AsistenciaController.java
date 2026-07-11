package com.gymadmin.attendance.infrastructure.adapter.in.web;

import com.gymadmin.attendance.application.service.AccessControlService;
import com.gymadmin.attendance.domain.model.Asistencia;
import com.gymadmin.attendance.domain.port.in.AsistenciaUseCase;
import com.gymadmin.attendance.infrastructure.adapter.in.web.dto.RegistrarAppRequest;
import com.gymadmin.attendance.infrastructure.adapter.in.web.dto.RegistrarManualRequest;
import com.gymadmin.attendance.infrastructure.adapter.in.web.dto.RegistrarOverrideRequest;
import com.gymadmin.attendance.infrastructure.adapter.in.web.dto.RegistrarQrRequest;
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

@Tag(name = "Asistencias", description = "Registro y consulta de asistencias")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AsistenciaController {

    private final AsistenciaUseCase asistenciaUseCase;
    private final AccessControlService accessControl;

    // ── Registro por QR (solo cliente) ─────────────────────────────────────────

    @Operation(summary = "Registrar asistencia por QR", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Asistencia registrada correctamente"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
            @ApiResponse(responseCode = "403", description = "El token no pertenece a un cliente"),
            @ApiResponse(responseCode = "409", description = "Ya existe una asistencia registrada hoy"),
            @ApiResponse(responseCode = "410", description = "QR token expirado o inválido")
    })
    @PostMapping("/asistencias/qr")
    public Mono<ResponseEntity<AsistenciaUseCase.AsistenciaQrResult>> registrarPorQr(
            @Valid @RequestBody RegistrarQrRequest request,
            @RequestHeader("Authorization") String authorization) {

        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireCliente(principal)
                        .then(Mono.defer(() -> asistenciaUseCase.registrarPorQr(new AsistenciaUseCase.RegistrarQrCommand(
                                request.qrToken(),
                                Integer.parseInt(principal.getUserId()),
                                principal.getIdCompania(),
                                authorization,
                                principal.getIdPersona() != null ? principal.getIdPersona().intValue() : Integer.parseInt(principal.getUserId())
                        ))))
                        .map(result -> ResponseEntity.status(HttpStatus.CREATED).body(result)));
    }

    // ── Registro desde app (solo cliente, sin QR) ──────────────────────────────

    @Operation(summary = "Registrar asistencia desde la app", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Asistencia registrada correctamente"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
            @ApiResponse(responseCode = "403", description = "El token no pertenece a un cliente"),
            @ApiResponse(responseCode = "409", description = "Ya existe una asistencia registrada hoy")
    })
    @PostMapping("/asistencias/app")
    public Mono<ResponseEntity<AsistenciaUseCase.AsistenciaQrResult>> registrarPorApp(
            @Valid @RequestBody RegistrarAppRequest request,
            @RequestHeader("Authorization") String authorization) {

        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireCliente(principal)
                        .then(Mono.defer(() -> asistenciaUseCase.registrarPorApp(new AsistenciaUseCase.RegistrarAppCommand(
                                Integer.parseInt(principal.getUserId()),
                                principal.getIdCompania(),
                                request.idSucursal(),
                                request.nombreSucursal(),
                                authorization,
                                principal.getIdPersona() != null ? principal.getIdPersona().intValue() : Integer.parseInt(principal.getUserId())
                        ))))
                        .map(result -> ResponseEntity.status(HttpStatus.CREATED).body(result)));
    }

    // ── Registro manual (staff: dueño, admin, recepción) ───────────────────────

    @Operation(summary = "Registrar asistencia manual", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Asistencia registrada correctamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado — se requiere rol staff (no entrenador)")
    })
    @PostMapping("/asistencias/manual")
    public Mono<ResponseEntity<Asistencia>> registrarManual(
            @Valid @RequestBody RegistrarManualRequest request) {

        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireStaff(principal)
                        .then(accessControl.requireNotEntrenador(principal))
                        .then(asistenciaUseCase.registrarManual(new AsistenciaUseCase.RegistrarManualCommand(
                                request.idCliente(),
                                request.fecha(),
                                request.horaEntrada(),
                                principal.getIdCompania().intValue(),
                                null,
                                principal.getUserId()
                        )))
                        .map(a -> ResponseEntity.status(HttpStatus.CREATED).body(a)));
    }

    // ── Override (solo Dueño / admin_compania) ──────────────────────────────────

    @Operation(summary = "Registrar asistencia con override (admin)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Asistencia registrada con override correctamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado — se requiere rol dueño o admin_compania")
    })
    @PostMapping("/asistencias/manual/override")
    public Mono<ResponseEntity<Asistencia>> registrarOverride(
            @Valid @RequestBody RegistrarOverrideRequest request) {

        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireDueno(principal)
                        .then(asistenciaUseCase.registrarOverride(new AsistenciaUseCase.RegistrarOverrideCommand(
                                request.idCliente(),
                                request.fecha(),
                                request.horaEntrada(),
                                principal.getIdCompania().intValue(),
                                request.idSucursal() != null ? request.idSucursal() : 1,
                                request.motivoOverride(),
                                principal.getUserId()
                        )))
                        .map(a -> ResponseEntity.status(HttpStatus.CREATED).body(a)));
    }

    // ── Historial de asistencias (cliente propio — sin ID en path) ─────────────

    @Operation(summary = "Ver mis asistencias", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de asistencias del cliente autenticado"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
            @ApiResponse(responseCode = "403", description = "El token no pertenece a un cliente")
    })
    @GetMapping("/asistencias/me")
    public Mono<ResponseEntity<Map<String, Object>>> historialMe(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireCliente(principal)
                        .then(asistenciaUseCase.listarPorClienteMe(
                                principal.getIdPersona() != null ? principal.getIdPersona().intValue() : Integer.parseInt(principal.getUserId()),
                                principal.getIdCompania() != null ? principal.getIdCompania().intValue() : null,
                                authorization, desde, hasta))
                        .map(lista -> ResponseEntity.ok(Map.of(
                                "total_en_periodo", lista.size(),
                                "asistencias", lista
                        ))));
    }

    // ── Historial de un cliente (legacy con {id}) ───────────────────────────────

    @Operation(summary = "Ver asistencias de un cliente", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de asistencias del cliente especificado"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
            @ApiResponse(responseCode = "403", description = "Un cliente solo puede ver sus propias asistencias")
    })
    @GetMapping("/clientes/{id}/asistencias")
    public Mono<ResponseEntity<Map<String, Object>>> listarPorCliente(
            @PathVariable Integer id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer idMembresia) {

        return getJwtPrincipal()
                .flatMap(principal -> {
                    if (principal.isCliente() && !principal.getUserId().equals(String.valueOf(id))) {
                        return Mono.error(new com.gymadmin.attendance.infrastructure.exception.ForbiddenException(
                                "Solo puedes ver tus propias asistencias"));
                    }
                    Integer idCompania = principal.getIdCompania() != null
                            ? principal.getIdCompania().intValue() : null;

                    return asistenciaUseCase.listarPorCliente(id, idCompania, desde, hasta, idMembresia)
                            .collectList()
                            .map(lista -> ResponseEntity.ok(Map.of(
                                    "cliente", Map.of("id", id),
                                    "total_en_periodo", lista.size(),
                                    "asistencias", lista
                            )));
                });
    }

    // ── Últimos 30 días / mapa de calor (cliente propio — sin ID en path) ──────

    @Operation(summary = "Mis asistencias últimos 30 días", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mapa de calor de los últimos 30 días del cliente autenticado"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
            @ApiResponse(responseCode = "403", description = "El token no pertenece a un cliente")
    })
    @GetMapping("/asistencias/me/ultimos-30")
    public Mono<ResponseEntity<AsistenciaUseCase.Ultimos30DiasResult>> ultimos30DiasMe(
            @RequestHeader("Authorization") String authorization) {

        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireCliente(principal)
                        .then(asistenciaUseCase.ultimos30DiasMe(
                                principal.getIdPersona() != null ? principal.getIdPersona().intValue() : Integer.parseInt(principal.getUserId()),
                                principal.getIdCompania() != null ? principal.getIdCompania().intValue() : null,
                                authorization))
                        .map(ResponseEntity::ok));
    }

    // ── Últimos 30 días / mapa de calor (legacy con {id}) ─────────────────────

    @Operation(summary = "Asistencias del cliente últimos 30 días", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mapa de calor de los últimos 30 días del cliente especificado"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
            @ApiResponse(responseCode = "403", description = "Un cliente solo puede ver sus propias asistencias")
    })
    @GetMapping("/clientes/{id}/asistencias/ultimos-30")
    public Mono<ResponseEntity<AsistenciaUseCase.Ultimos30DiasResult>> ultimos30Dias(
            @PathVariable Integer id) {

        return getJwtPrincipal()
                .flatMap(principal -> {
                    if (principal.isCliente() && !principal.getUserId().equals(String.valueOf(id))) {
                        return Mono.error(new com.gymadmin.attendance.infrastructure.exception.ForbiddenException(
                                "Solo puedes ver tus propias asistencias"));
                    }
                    Integer idCompania = principal.getIdCompania() != null
                            ? principal.getIdCompania().intValue() : null;
                    return asistenciaUseCase.ultimos30Dias(id, idCompania)
                            .map(ResponseEntity::ok);
                });
    }

    // ── Racha perfecta (consumido por Marketing Service) ───────────────────────

    @Operation(summary = "Verificar racha perfecta del cliente", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado de la racha perfecta del cliente"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
            @ApiResponse(responseCode = "404", description = "Cliente no encontrado")
    })
    @GetMapping("/clientes/{id}/asistencias/racha-perfecta")
    public Mono<ResponseEntity<AsistenciaUseCase.RachaPerfectaResult>> rachaPerfecta(
            @PathVariable Integer id,
            @RequestParam(required = false, defaultValue = "1") Integer meses) {

        return getJwtPrincipal()
                .flatMap(principal -> {
                    Integer idCompania = principal.getIdCompania() != null
                            ? principal.getIdCompania().intValue() : null;
                    return asistenciaUseCase.rachaPerfecta(id, idCompania, meses)
                            .map(ResponseEntity::ok);
                });
    }

    // ── Dashboard del día ───────────────────────────────────────────────────────

    @Operation(summary = "Asistencias de hoy", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asistencias registradas en el día de hoy"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado — se requiere rol staff o plataforma")
    })
    @GetMapping("/asistencias/hoy")
    public Mono<ResponseEntity<AsistenciaUseCase.AsistenciasHoyResult>> asistenciasHoy(
            @RequestParam(required = false) Integer idSucursal) {

        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireStaffOrPlataforma(principal)
                        .then(asistenciaUseCase.asistenciasHoy(
                                principal.getIdCompania().intValue(), idSucursal))
                        .map(ResponseEntity::ok));
    }

    // ── Estadísticas KPI ────────────────────────────────────────────────────────

    @Operation(summary = "Estadísticas de asistencias", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estadísticas KPI de asistencias del periodo solicitado"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado — se requiere rol staff o plataforma")
    })
    @GetMapping("/asistencias/estadisticas")
    public Mono<ResponseEntity<AsistenciaUseCase.EstadisticasResult>> estadisticas(
            @RequestParam(required = false, defaultValue = "mes") String periodo,
            @RequestParam(required = false) Integer anio,
            @RequestParam(required = false) Integer mes) {

        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireStaffOrPlataforma(principal)
                        .then(asistenciaUseCase.estadisticas(
                                principal.getIdCompania().intValue(),
                                periodo,
                                anio != null ? anio : LocalDate.now().getYear(),
                                mes != null ? mes : LocalDate.now().getMonthValue()))
                        .map(ResponseEntity::ok));
    }

    // ── Helper ──────────────────────────────────────────────────────────────────

    private Mono<JwtPrincipal> getJwtPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(JwtPrincipal.class);
    }
}
