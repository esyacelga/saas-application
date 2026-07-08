package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.domain.port.in.ModuloCheckUseCase;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.ModuloCheckResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/modulos")
@Tag(name = "Módulos", description = "Verificación de módulos habilitados")
public class ModuloCheckController {

    private final ModuloCheckUseCase moduloCheckUseCase;

    public ModuloCheckController(ModuloCheckUseCase moduloCheckUseCase) {
        this.moduloCheckUseCase = moduloCheckUseCase;
    }

    @Operation(summary = "Verificar acceso a un módulo vía QR (público, sin auth)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Módulo habilitado"),
        @ApiResponse(responseCode = "402", description = "Suscripción vencida"),
        @ApiResponse(responseCode = "403", description = "Módulo no incluido en el plan")
    })
    @GetMapping("/check")
    public Mono<ResponseEntity<ModuloCheckResponse>> check(
            @RequestParam("id_compania") Long idCompania,
            @RequestParam("codigo") String codigo) {
        return moduloCheckUseCase.checkAcceso(idCompania, codigo)
                .map(result -> {
                    ModuloCheckResponse body = new ModuloCheckResponse(
                            result.getPermitido(), result.getPlan(), result.getRazon());
                    if (Boolean.TRUE.equals(result.getPermitido())) {
                        return ResponseEntity.ok(body);
                    }
                    if ("modulo_no_incluido".equals(result.getRazon())) {
                        return ResponseEntity.status(403).<ModuloCheckResponse>body(body);
                    }
                    return ResponseEntity.status(402).<ModuloCheckResponse>body(body);
                });
    }
}
