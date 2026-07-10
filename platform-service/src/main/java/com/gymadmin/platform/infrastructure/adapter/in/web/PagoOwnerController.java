package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.domain.port.in.ReportarPagoUseCase;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.PagoPendienteResponse;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import com.gymadmin.platform.infrastructure.ratelimit.PostgresRateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;

/**
 * REQ-SAAS-001 (RN-08, Sub-fase 1.4): endpoint del owner/admin del tenant para
 * reportar un pago por transferencia. Distinto de {@link PagoController}, que
 * gestiona pagos legacy tramitados por super_admin/soporte.
 * <p>
 * Aplica rate-limit contra Postgres (máx 3 reportes por hora por tenant) antes
 * de invocar el caso de uso.
 */
@RestController
@Tag(name = "Pagos owner", description = "Reporte de pagos por transferencia del owner")
public class PagoOwnerController {

    private static final int MAX_REPORTES_POR_HORA = 3;

    private final ReportarPagoUseCase reportarPagoUseCase;
    private final AccessControlService accessControl;
    private final PostgresRateLimiter rateLimiter;

    public PagoOwnerController(ReportarPagoUseCase reportarPagoUseCase,
                                AccessControlService accessControl,
                                PostgresRateLimiter rateLimiter) {
        this.reportarPagoUseCase = reportarPagoUseCase;
        this.accessControl = accessControl;
        this.rateLimiter = rateLimiter;
    }

    @Operation(summary = "Reportar pago por transferencia (owner/admin)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Pago reportado en estado pendiente"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "409", description = "Pago duplicado (idempotencia)"),
        @ApiResponse(responseCode = "429", description = "Rate limit excedido")
    })
    @PostMapping(value = "/api/v1/companias/{id}/pagos/reportar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<PagoPendienteResponse>> reportarPago(
            @PathVariable Long id,
            @RequestPart("comprobante") FilePart comprobante,
            @RequestPart("id_plan_destino") String idPlanDestino,
            @RequestPart("monto") String monto,
            @RequestPart("fecha_transferencia") String fechaTransferencia,
            @RequestPart(value = "banco_origen", required = false) String bancoOrigen,
            @RequestPart(value = "referencia", required = false) String referencia,
            ServerWebExchange exchange) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireOwnerOrAdminOfCompania(principal, id)
                        .then(rateLimiter.checkRateLimit(
                                PostgresRateLimiter.BUCKET_PAGOS_REPORTADOS,
                                id,
                                MAX_REPORTES_POR_HORA,
                                Duration.ofHours(1)))
                        .then(readBytes(comprobante))
                        .flatMap(bytes -> reportarPagoUseCase.reportar(new ReportarPagoUseCase.ReportarPagoCommand(
                                id,
                                Long.parseLong(idPlanDestino),
                                new BigDecimal(monto),
                                LocalDate.parse(fechaTransferencia),
                                bancoOrigen,
                                referencia,
                                bytes,
                                comprobante.filename(),
                                toLongOrNull(principal.getUserId()),
                                clientIp(exchange)
                        )))
                        .map(pago -> ResponseEntity.status(HttpStatus.CREATED)
                                .body(PagoPendienteResponse.from(pago))));
    }

    private Mono<byte[]> readBytes(FilePart filePart) {
        return DataBufferUtils.join(filePart.content())
                .map(buf -> {
                    byte[] bytes = new byte[buf.readableByteCount()];
                    buf.read(bytes);
                    DataBufferUtils.release(buf);
                    return bytes;
                });
    }

    private String clientIp(ServerWebExchange exchange) {
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return null;
    }

    private Long toLongOrNull(String userId) {
        if (userId == null || userId.isBlank()) return null;
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Mono<JwtPrincipal> getJwtPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(JwtPrincipal.class);
    }
}
