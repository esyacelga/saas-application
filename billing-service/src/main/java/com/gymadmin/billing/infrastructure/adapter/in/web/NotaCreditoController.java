package com.gymadmin.billing.infrastructure.adapter.in.web;

import com.gymadmin.billing.application.command.EmitirFacturaCommand;
import com.gymadmin.billing.application.command.EmitirNotaCreditoCommand;
import com.gymadmin.billing.domain.port.in.NotaCreditoUseCase;
import com.gymadmin.billing.infrastructure.adapter.in.web.dto.ComprobanteResponse;
import com.gymadmin.billing.infrastructure.adapter.in.web.dto.EmitirNotaCreditoRequest;
import com.gymadmin.billing.infrastructure.config.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;

/**
 * G4 · Notas de crédito electrónicas SRI Ecuador (tipo {@code "04"}).
 * Requiere JWT {@code tipo=staff}; el {@code id_compania} sale del token para
 * enforcement multi-tenant.
 */
@Tag(name = "Notas de crédito", description = "Emisión y consulta de notas de crédito electrónicas SRI (tipo 04)")
@RestController
@RequestMapping("/api/v1/notas-credito")
public class NotaCreditoController {

    private final NotaCreditoUseCase notaCreditoUseCase;

    public NotaCreditoController(NotaCreditoUseCase notaCreditoUseCase) {
        this.notaCreditoUseCase = notaCreditoUseCase;
    }

    @Operation(summary = "Emitir nota de crédito electrónica",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "NC emitida (ver estado en body)"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "404", description = "Factura original no encontrada"),
            @ApiResponse(responseCode = "422", description = "Regla de negocio violada (estado, motivo, valor)")
    })
    @PostMapping
    public Mono<ResponseEntity<ComprobanteResponse>> emitir(
            @Valid @RequestBody EmitirNotaCreditoRequest request) {
        return extractPrincipal()
                .flatMap(principal -> {
                    Integer idCompania = toIntegerSafe(principal.getIdCompania());
                    EmitirNotaCreditoCommand command = new EmitirNotaCreditoCommand(
                            idCompania,
                            request.idSucursal(),
                            LocalDate.now(),
                            request.codEstablecimiento(),
                            request.codPuntoEmision(),
                            request.codigoNumerico(),
                            request.idFacturaOriginal(),
                            request.codigoMotivo(),
                            request.razon(),
                            request.valorModificacion(),
                            request.detalles().stream()
                                    .map(d -> {
                                        BigDecimal precioTotal = d.cantidad()
                                                .multiply(d.precioUnitario())
                                                .subtract(d.descuento() != null ? d.descuento() : BigDecimal.ZERO)
                                                .setScale(2, RoundingMode.HALF_UP);
                                        return new EmitirFacturaCommand.DetalleFacturaItem(
                                                d.codigoPrincipal(),
                                                d.codigoAuxiliar(),
                                                d.descripcion(),
                                                d.cantidad(),
                                                d.precioUnitario(),
                                                d.descuento() != null ? d.descuento() : BigDecimal.ZERO,
                                                precioTotal,
                                                null
                                        );
                                    })
                                    .toList(),
                            toIntegerSafe(principal.getIdPersona())
                    );
                    return notaCreditoUseCase.emitirNotaCredito(command);
                })
                .map(nc -> ResponseEntity.status(HttpStatus.CREATED).body(ComprobanteResponse.from(nc)));
    }

    @Operation(summary = "Consultar nota de crédito por ID",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "NC encontrada"),
            @ApiResponse(responseCode = "404", description = "NC no encontrada"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @GetMapping("/{id}")
    public Mono<ResponseEntity<ComprobanteResponse>> buscarPorId(@PathVariable Long id) {
        return extractPrincipal()
                .flatMap(principal -> notaCreditoUseCase.buscarPorId(id, toIntegerSafe(principal.getIdCompania())))
                .map(nc -> ResponseEntity.ok(ComprobanteResponse.from(nc)));
    }

    @Operation(summary = "Listar notas de crédito de la empresa",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista paginada"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer idSucursal,
            @RequestParam(required = false) Long idFacturaOriginal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return extractPrincipal()
                .flatMap(principal -> {
                    Integer idCompania = toIntegerSafe(principal.getIdCompania());
                    return notaCreditoUseCase.contar(idCompania, idSucursal, estado, idFacturaOriginal)
                            .flatMap(total -> notaCreditoUseCase.listar(
                                            idCompania, idSucursal, estado, idFacturaOriginal, page, limit)
                                    .map(ComprobanteResponse::from)
                                    .collectList()
                                    .map(datos -> ResponseEntity.ok(Map.<String, Object>of(
                                            "total", total,
                                            "pagina", page,
                                            "datos", datos
                                    ))));
                });
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
