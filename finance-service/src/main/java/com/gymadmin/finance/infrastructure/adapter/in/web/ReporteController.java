package com.gymadmin.finance.infrastructure.adapter.in.web;

import com.gymadmin.finance.application.service.AccessControlService;
import com.gymadmin.finance.domain.port.in.ReporteUseCase;
import com.gymadmin.finance.infrastructure.config.AppProperties;
import com.gymadmin.finance.infrastructure.config.JwtPrincipal;
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

@RestController
@RequestMapping("/api/v1/finanzas/reporte")
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteUseCase reporteUseCase;
    private final AccessControlService accessControl;

    @Value("${finance.proyeccion-meses-base:3}")
    private int proyeccionMesesBase;

    @GetMapping("/resumen")
    public Mono<ResponseEntity<ReporteUseCase.ResumenResult>> resumen(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        return getJwtPrincipal()
                .flatMap(p -> accessControl.requireFinanzasReportes(p)
                        .then(Mono.defer(() -> reporteUseCase.resumen(p.getIdCompania().intValue(), desde, hasta)))
                        .map(result -> ResponseEntity.ok(result)));
    }

    @GetMapping("/mensual")
    public Mono<ResponseEntity<ReporteUseCase.MensualResult>> mensual(
            @RequestParam(required = false) Integer anio) {

        int anioFinal = anio != null ? anio : LocalDate.now().getYear();

        return getJwtPrincipal()
                .flatMap(p -> accessControl.requireFinanzasReportes(p)
                        .then(Mono.defer(() -> reporteUseCase.mensual(p.getIdCompania().intValue(), anioFinal)))
                        .map(result -> ResponseEntity.ok(result)));
    }

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
