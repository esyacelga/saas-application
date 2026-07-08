package com.gymadmin.finance.infrastructure.adapter.in.web;

import com.gymadmin.finance.application.service.AccessControlService;
import com.gymadmin.finance.domain.port.in.ReporteUseCase;
import com.gymadmin.finance.infrastructure.config.AppProperties;
import com.gymadmin.finance.infrastructure.config.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Tag(name = "Reportes Financieros", description = "Reportes de resumen, mensual y proyección")
@RestController
@RequestMapping("/api/v1/finanzas/reporte")
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteUseCase reporteUseCase;
    private final AccessControlService accessControl;

    @Value("${finance.proyeccion-meses-base:3}")
    private int proyeccionMesesBase;

    @Operation(summary = "Resumen financiero del período", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resumen financiero del período solicitado"),
        @ApiResponse(responseCode = "400", description = "Parámetros de fecha inválidos"),
        @ApiResponse(responseCode = "403", description = "Sin permiso")
    })
    @GetMapping("/resumen")
    public Mono<ResponseEntity<ReporteUseCase.ResumenResult>> resumen(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        return getJwtPrincipal()
                .flatMap(p -> accessControl.requireFinanzasReportes(p)
                        .then(Mono.defer(() -> reporteUseCase.resumen(p.getIdCompania().intValue(), desde, hasta)))
                        .map(result -> ResponseEntity.ok(result)));
    }

    @Operation(summary = "Reporte mensual anual", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Desglose mensual del año indicado"),
        @ApiResponse(responseCode = "400", description = "Parámetros inválidos"),
        @ApiResponse(responseCode = "403", description = "Sin permiso")
    })
    @GetMapping("/mensual")
    public Mono<ResponseEntity<ReporteUseCase.MensualResult>> mensual(
            @RequestParam(required = false) Integer anio) {

        int anioFinal = anio != null ? anio : LocalDate.now().getYear();

        return getJwtPrincipal()
                .flatMap(p -> accessControl.requireFinanzasReportes(p)
                        .then(Mono.defer(() -> reporteUseCase.mensual(p.getIdCompania().intValue(), anioFinal)))
                        .map(result -> ResponseEntity.ok(result)));
    }

    @Operation(summary = "Proyección de ingresos", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Proyección basada en los últimos N meses"),
        @ApiResponse(responseCode = "403", description = "Sin permiso")
    })
    @GetMapping("/proyeccion")
    public Mono<ResponseEntity<ReporteUseCase.ProyeccionResult>> proyeccion() {
        return getJwtPrincipal()
                .flatMap(p -> accessControl.requireFinanzasReportes(p)
                        .then(Mono.defer(() -> reporteUseCase.proyeccion(
                                p.getIdCompania().intValue(), proyeccionMesesBase)))
                        .map(result -> ResponseEntity.ok(result)));
    }

    private Mono<JwtPrincipal> getJwtPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(JwtPrincipal.class);
    }
}
