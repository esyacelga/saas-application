package com.gymadmin.billing.infrastructure.adapter.in.web;

import com.gymadmin.billing.domain.model.PeriodoResumen;
import com.gymadmin.billing.domain.port.in.ReporteUseCase;
import com.gymadmin.billing.infrastructure.config.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Tag(name = "Reportes", description = "Reportes y estadísticas de facturación electrónica")
@RestController
@RequestMapping("/api/v1/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteUseCase reporteUseCase;

    @Operation(summary = "Generar ATS mensual (Anexo Transaccional Simplificado)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "XML ATS generado"),
            @ApiResponse(responseCode = "404", description = "Configuración SRI no encontrada"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @GetMapping("/ats")
    public Mono<ResponseEntity<byte[]>> generarAts(
            @RequestParam Integer anio,
            @RequestParam Integer mes) {
        return extractPrincipal()
                .flatMap(principal -> {
                    Integer idCompania = toIntegerSafe(principal.getIdCompania());
                    return reporteUseCase.generarAts(idCompania, anio, mes)
                            .map(xmlBytes -> {
                                String filename = String.format("ATS_%d_%02d.xml", anio, mes);
                                HttpHeaders headers = new HttpHeaders();
                                headers.setContentType(MediaType.APPLICATION_XML);
                                headers.setContentDisposition(
                                        ContentDisposition.attachment().filename(filename).build());
                                return ResponseEntity.ok()
                                        .headers(headers)
                                        .body(xmlBytes);
                            });
                });
    }

    @Operation(summary = "Resumen de facturación por período",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumen del período"),
            @ApiResponse(responseCode = "400", description = "Parámetros inválidos"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @GetMapping("/resumen")
    public Mono<ResponseEntity<PeriodoResumen>> resumenPeriodo(
            @RequestParam String desde,
            @RequestParam String hasta) {
        LocalDate fechaDesde = LocalDate.parse(desde, DateTimeFormatter.ISO_LOCAL_DATE);
        LocalDate fechaHasta = LocalDate.parse(hasta, DateTimeFormatter.ISO_LOCAL_DATE);
        return extractPrincipal()
                .flatMap(principal -> {
                    Integer idCompania = toIntegerSafe(principal.getIdCompania());
                    return reporteUseCase.resumenPeriodo(idCompania, fechaDesde, fechaHasta);
                })
                .map(ResponseEntity::ok);
    }

    private Mono<JwtPrincipal> extractPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getPrincipal())
                .cast(JwtPrincipal.class);
    }

    private Integer toIntegerSafe(Long value) {
        return value != null ? value.intValue() : null;
    }
}
