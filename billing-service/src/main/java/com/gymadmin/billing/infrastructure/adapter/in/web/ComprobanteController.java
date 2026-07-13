package com.gymadmin.billing.infrastructure.adapter.in.web;

import com.gymadmin.billing.application.command.EmitirFacturaCommand;
import com.gymadmin.billing.domain.port.in.ComprobanteUseCase;
import com.gymadmin.billing.infrastructure.adapter.in.web.dto.ComprobanteResponse;
import com.gymadmin.billing.infrastructure.adapter.in.web.dto.EmitirFacturaRequest;
import com.gymadmin.billing.infrastructure.config.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Map;

@Tag(name = "Comprobantes", description = "Facturación electrónica SRI Ecuador")
@RestController
@RequestMapping("/api/v1/comprobantes")
public class ComprobanteController {

    private static final Logger log = LoggerFactory.getLogger(ComprobanteController.class);

    private final ComprobanteUseCase comprobanteUseCase;

    public ComprobanteController(ComprobanteUseCase comprobanteUseCase) {
        this.comprobanteUseCase = comprobanteUseCase;
    }

    @Operation(summary = "Emitir factura electrónica", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Factura emitida exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "422", description = "Error de negocio SRI")
    })
    @PostMapping("/facturas")
    public Mono<ResponseEntity<ComprobanteResponse>> emitirFactura(
            @Valid @RequestBody EmitirFacturaRequest request) {
        return extractPrincipal()
                .flatMap(principal -> {
                    Integer idCompania = toIntegerSafe(principal.getIdCompania());
                    if (request.secuencial() != null && !request.secuencial().isBlank()) {
                        log.warn("Cliente envió 'secuencial' deprecado (G5); se ignora. Combinación: {}:{}:{}:{}",
                                idCompania,
                                request.idSucursal(),
                                request.codEstablecimiento(),
                                request.codPuntoEmision());
                    }
                    EmitirFacturaCommand command = new EmitirFacturaCommand(
                            idCompania,
                            request.idSucursal(),
                            LocalDate.now(),
                            request.codEstablecimiento(),
                            request.codPuntoEmision(),
                            request.codigoNumerico(),
                            request.tipoIdReceptor(),
                            request.idReceptor(),
                            request.razonSocialReceptor(),
                            request.emailReceptor(),
                            request.direccionReceptor(),
                            request.telefonoReceptor(),
                            request.detalles().stream()
                                    .map(d -> {
                                        java.math.BigDecimal precioTotal = d.cantidad()
                                                .multiply(d.precioUnitario())
                                                .subtract(d.descuento() != null ? d.descuento() : java.math.BigDecimal.ZERO)
                                                .setScale(2, java.math.RoundingMode.HALF_UP);
                                        return new EmitirFacturaCommand.DetalleFacturaItem(
                                                d.codigoPrincipal(),
                                                d.codigoAuxiliar(),
                                                d.descripcion(),
                                                d.cantidad(),
                                                d.precioUnitario(),
                                                d.descuento() != null ? d.descuento() : java.math.BigDecimal.ZERO,
                                                precioTotal,
                                                null
                                        );
                                    })
                                    .toList(),
                            request.pagos().stream()
                                    .map(p -> new EmitirFacturaCommand.PagoItem(p.formaPago(), p.total()))
                                    .toList(),
                            request.pagos().isEmpty() ? null : request.pagos().get(0).formaPago(),
                            request.idMembresia(),
                            request.idVenta(),
                            toIntegerSafe(principal.getIdPersona())
                    );
                    return comprobanteUseCase.emitirFactura(command);
                })
                .map(c -> ResponseEntity.status(HttpStatus.CREATED).body(ComprobanteResponse.from(c)));
    }

    @Operation(summary = "Consultar comprobante por ID", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comprobante encontrado"),
            @ApiResponse(responseCode = "404", description = "Comprobante no encontrado"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @GetMapping("/{id}")
    public Mono<ResponseEntity<ComprobanteResponse>> buscarPorId(@PathVariable Long id) {
        return extractPrincipal()
                .flatMap(principal -> comprobanteUseCase.buscarPorId(id, toIntegerSafe(principal.getIdCompania())))
                .map(c -> ResponseEntity.ok(ComprobanteResponse.from(c)));
    }

    @Operation(summary = "Listar comprobantes por empresa", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista paginada de comprobantes"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer idSucursal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return extractPrincipal()
                .flatMap(principal -> {
                    Integer idCompania = toIntegerSafe(principal.getIdCompania());
                    return comprobanteUseCase.contarPorEmpresa(idCompania, idSucursal, estado)
                            .flatMap(total -> comprobanteUseCase.listarPorEmpresa(idCompania, idSucursal, estado, page, limit)
                                    .map(ComprobanteResponse::from)
                                    .collectList()
                                    .map(datos -> ResponseEntity.ok(Map.<String, Object>of(
                                            "total", total,
                                            "pagina", page,
                                            "datos", datos
                                    )))
                            );
                });
    }

    @Operation(summary = "Enviar comprobante al SRI para autorización", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comprobante procesado"),
            @ApiResponse(responseCode = "404", description = "Comprobante no encontrado"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @PostMapping("/{id}/enviar")
    public Mono<ResponseEntity<ComprobanteResponse>> enviarAlSri(
            @PathVariable Long id,
            @RequestParam Integer idSucursal) {
        return extractPrincipal()
                .flatMap(principal -> comprobanteUseCase.procesarEnvioSri(
                        id,
                        toIntegerSafe(principal.getIdCompania()),
                        idSucursal))
                .map(c -> ResponseEntity.ok(ComprobanteResponse.from(c)));
    }

    @Operation(summary = "Descargar XML firmado del comprobante", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "XML firmado"),
            @ApiResponse(responseCode = "404", description = "Comprobante o XML no encontrado"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @GetMapping("/{id}/xml-firmado")
    public Mono<ResponseEntity<byte[]>> descargarXmlFirmado(@PathVariable Long id) {
        return extractPrincipal()
                .flatMap(principal -> comprobanteUseCase.buscarPorId(id, toIntegerSafe(principal.getIdCompania()))
                        .flatMap(comprobante -> comprobanteUseCase.leerXmlFirmado(id, toIntegerSafe(principal.getIdCompania()))
                                .map(xmlContent -> {
                                    byte[] bytes = xmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                                    HttpHeaders headers = new HttpHeaders();
                                    headers.setContentType(MediaType.APPLICATION_XML);
                                    headers.setContentDisposition(ContentDisposition.attachment()
                                            .filename("factura_" + comprobante.getClaveAcceso() + ".xml")
                                            .build());
                                    return ResponseEntity.ok().headers(headers).body(bytes);
                                })));
    }

    @Operation(summary = "Descargar RIDE PDF del comprobante", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "RIDE PDF"),
            @ApiResponse(responseCode = "404", description = "Comprobante o PDF no encontrado"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @GetMapping("/{id}/ride")
    public Mono<ResponseEntity<byte[]>> descargarRide(@PathVariable Long id) {
        return extractPrincipal()
                .flatMap(principal -> comprobanteUseCase.buscarPorId(id, toIntegerSafe(principal.getIdCompania()))
                        .flatMap(comprobante -> comprobanteUseCase.leerRidePdf(id, toIntegerSafe(principal.getIdCompania()))
                                .map(pdfBytes -> {
                                    HttpHeaders headers = new HttpHeaders();
                                    headers.setContentType(MediaType.APPLICATION_PDF);
                                    headers.setContentDisposition(ContentDisposition.attachment()
                                            .filename("ride_" + comprobante.getClaveAcceso() + ".pdf")
                                            .build());
                                    return ResponseEntity.ok().headers(headers).body(pdfBytes);
                                })));
    }

    @Operation(summary = "Anular comprobante", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comprobante anulado"),
            @ApiResponse(responseCode = "404", description = "Comprobante no encontrado"),
            @ApiResponse(responseCode = "422", description = "Estado no permite anulación"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @PostMapping("/{id}/anular")
    public Mono<ResponseEntity<ComprobanteResponse>> anularComprobante(@PathVariable Long id) {
        return extractPrincipal()
                .flatMap(principal -> comprobanteUseCase.anularComprobante(id, toIntegerSafe(principal.getIdCompania())))
                .map(c -> ResponseEntity.ok(ComprobanteResponse.from(c)));
    }

    @Operation(summary = "Reenviar RIDE por email", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email enviado"),
            @ApiResponse(responseCode = "404", description = "Comprobante o PDF no encontrado"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @PostMapping("/{id}/reenviar-email")
    public Mono<ResponseEntity<Map<String, String>>> reenviarEmail(@PathVariable Long id) {
        return extractPrincipal()
                .flatMap(principal -> comprobanteUseCase.reenviarEmail(id, toIntegerSafe(principal.getIdCompania())))
                .then(Mono.just(ResponseEntity.ok(Map.of("mensaje", "Email enviado"))));
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
