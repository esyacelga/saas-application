package com.gymadmin.billing.infrastructure.adapter.in.web;

import com.gymadmin.billing.application.service.CatalogoSriService;
import com.gymadmin.billing.infrastructure.adapter.in.web.dto.MotivoAnulacionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * G3 · Endpoint público para el catálogo {@code sri.motivos_anulacion_nc}.
 * Se expone bajo el prefijo {@code /api/v1/sri} porque es lookup del schema SRI,
 * no un recurso del schema {@code facturacion}.
 */
@Tag(name = "Catálogos SRI", description = "Lookups de catálogos SRI Ecuador (motivos de anulación, etc.)")
@RestController
@RequestMapping("/api/v1/sri")
public class MotivosAnulacionController {

    private final CatalogoSriService catalogoSriService;

    public MotivosAnulacionController(CatalogoSriService catalogoSriService) {
        this.catalogoSriService = catalogoSriService;
    }

    @Operation(summary = "Listar motivos oficiales de anulación de NC",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Motivos disponibles"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @GetMapping("/motivos-anulacion")
    public Mono<ResponseEntity<List<MotivoAnulacionResponse>>> listar() {
        return catalogoSriService.listarMotivosAnulacion()
                .map(MotivoAnulacionResponse::from)
                .collectList()
                .map(ResponseEntity::ok);
    }
}
