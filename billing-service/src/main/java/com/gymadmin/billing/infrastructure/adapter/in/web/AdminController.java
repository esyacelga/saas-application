package com.gymadmin.billing.infrastructure.adapter.in.web;

import com.gymadmin.billing.domain.model.AuditoriaEmision;
import com.gymadmin.billing.domain.port.in.AuditoriaUseCase;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.CertificadoEntity;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.repository.CertificadoR2dbcRepository;
import com.gymadmin.billing.infrastructure.config.JwtPrincipal;
import com.gymadmin.billing.infrastructure.config.SriAmbienteConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Administración", description = "Endpoints de diagnóstico y auditoría del servicio de facturación")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private static final Duration SRI_PING_TIMEOUT = Duration.ofSeconds(10);

    private static final String PING_ENVELOPE = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" \
            xmlns:ec="http://ec.gob.sri.ws.recepcion">
              <soapenv:Header/>
              <soapenv:Body>
                <ec:validarComprobante>
                  <xml></xml>
                </ec:validarComprobante>
              </soapenv:Body>
            </soapenv:Envelope>""";

    private final WebClient sriWebClient;
    private final SriAmbienteConfig sriAmbienteConfig;
    private final CertificadoR2dbcRepository certificadoRepository;
    private final AuditoriaUseCase auditoriaUseCase;

    @Operation(summary = "Verificar conectividad con endpoint SRI", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado del ping al SRI"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @GetMapping("/sri/ping")
    public Mono<ResponseEntity<Map<String, Object>>> pingSri() {
        return extractPrincipal()
                .flatMap(principal -> {
                    String ambiente = sriAmbienteConfig.getAmbiente();
                    String url = sriAmbienteConfig.getUrlRecepcionEfectiva();
                    long startMs = System.currentTimeMillis();

                    return sriWebClient.post()
                            .uri(url)
                            .contentType(MediaType.valueOf("text/xml; charset=UTF-8"))
                            .header("SOAPAction", "validarComprobante")
                            .bodyValue(PING_ENVELOPE)
                            .retrieve()
                            .toBodilessEntity()
                            .timeout(SRI_PING_TIMEOUT)
                            .map(response -> {
                                long latencia = System.currentTimeMillis() - startMs;
                                return buildPingResponse(ambiente, url, "DISPONIBLE", latencia);
                            })
                            .onErrorResume(e -> {
                                long latencia = System.currentTimeMillis() - startMs;
                                log.warn("SRI ping failed: {}", e.getMessage());
                                return Mono.just(buildPingResponse(ambiente, url, "NO_DISPONIBLE", latencia));
                            });
                })
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "Estado del certificado digital activo", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado del certificado"),
            @ApiResponse(responseCode = "404", description = "Certificado no encontrado"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @GetMapping("/certificado/estado")
    public Mono<ResponseEntity<Map<String, Object>>> estadoCertificado() {
        return extractPrincipal()
                .flatMap(principal -> {
                    Integer idCompania = toIntegerSafe(principal.getIdCompania());
                    return certificadoRepository.findActiveByEmpresaForAdmin(idCompania)
                            .switchIfEmpty(Mono.error(new com.gymadmin.billing.infrastructure.exception.NotFoundException(
                                    "Certificado activo no encontrado para la empresa " + idCompania)))
                            .map(entity -> buildCertificadoResponse(entity));
                })
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "Auditoría de emisión de comprobantes", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de auditoría"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @GetMapping("/auditoria")
    public Flux<AuditoriaEmision> listarAuditoria(
            @RequestParam String desde,
            @RequestParam String hasta,
            @RequestParam(required = false) String estado) {
        return extractPrincipal()
                .flatMapMany(principal -> {
                    Integer idCompania = toIntegerSafe(principal.getIdCompania());
                    LocalDate fechaDesde = LocalDate.parse(desde);
                    LocalDate fechaHasta = LocalDate.parse(hasta);
                    return auditoriaUseCase.listarAuditoria(idCompania, fechaDesde, fechaHasta, estado);
                });
    }

    private Map<String, Object> buildPingResponse(String ambiente, String url, String estado, long latenciaMs) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ambiente", ambiente);
        response.put("url", url);
        response.put("estado", estado);
        response.put("latenciaMs", latenciaMs);
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }

    private Map<String, Object> buildCertificadoResponse(CertificadoEntity entity) {
        LocalDate vencimiento = entity.getFechaVencimiento();
        LocalDate hoy = LocalDate.now();
        long diasRestantes = ChronoUnit.DAYS.between(hoy, vencimiento);

        String estadoCert;
        if (diasRestantes < 0) {
            estadoCert = "VENCIDO";
        } else if (diasRestantes <= 30) {
            estadoCert = "POR_VENCER";
        } else {
            estadoCert = "VIGENTE";
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("idCertificado", entity.getId());
        response.put("ruc", null);
        response.put("fechaVencimiento", vencimiento != null ? vencimiento.toString() : null);
        response.put("diasRestantes", diasRestantes);
        response.put("estado", estadoCert);
        return response;
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
