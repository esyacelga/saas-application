package com.gymadmin.attendance.infrastructure.adapter.out.core;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CoreServiceClient {

    /** Header del contrato interno service-to-service de core (no JWT). */
    public static final String HEADER_INTERNAL_CALL = "X-Internal-Call";

    private final WebClient coreWebClient;

    @Value("${services.core-service.internal-secret:${INTERNAL_SECRET:platform-secret-dev}}")
    private String internalSecret;

    public Mono<ValidarAccesoResponse> validarAcceso(Integer idCliente, Integer idCompania, String bearerToken) {
        boolean tokenPresente = bearerToken != null && !bearerToken.isBlank();
        log.info("[CoreService] validarAcceso → idCliente={} idCompania={} tokenPresente={}", idCliente, idCompania, tokenPresente);
        return coreWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/membresias/validar-acceso")
                        .queryParam("id_persona", idCliente)
                        .queryParam("id_compania", idCompania)
                        .build())
                .header("Authorization", bearerToken != null ? bearerToken : "")
                .exchangeToMono(response -> {
                    int status = response.statusCode().value();
                    log.info("[CoreService] validarAcceso ← HTTP {} idCliente={} idCompania={}", status, idCliente, idCompania);
                    if (status >= 400) {
                        log.warn("[CoreService] validarAcceso HTTP {} inesperado idCliente={} idCompania={}", status, idCliente, idCompania);
                    }
                    return response.bodyToMono(ValidarAccesoResponse.class);
                })
                .doOnNext(r -> {
                    if (r == null) {
                        log.error("[CoreService] validarAcceso respuesta NULL idCliente={} idCompania={}", idCliente, idCompania);
                    } else if (r.isPermitido()) {
                        log.info("[CoreService] acceso PERMITIDO idCliente={} idMembresia={} modo={} fechaFin={} accesosRestantes={}",
                                idCliente, r.getIdMembresia(), r.getModoControl(), r.getFechaFin(), r.getDiasAccesoRestantes());
                    } else {
                        log.warn("[CoreService] acceso DENEGADO razon='{}' idCliente={} idCompania={} idMembresia={} fechaFin={} tokenPresente={}",
                                r.getRazon(), idCliente, idCompania, r.getIdMembresia(), r.getFechaFin(), tokenPresente);
                    }
                })
                .onErrorResume(e -> {
                    log.error("[CoreService] validarAcceso FALLÓ idCliente={} idCompania={} tokenPresente={} causa='{}'",
                            idCliente, idCompania, tokenPresente, e.getMessage());
                    return Mono.error(new RuntimeException("Error consultando Core Service: " + e.getMessage()));
                });
    }

    public Mono<SucursalQrResponse> buscarSucursalPorQr(String qrToken, String bearerToken) {
        log.debug("[CoreService] buscarSucursalPorQr → qrToken={}...", truncate(qrToken));
        return coreWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/sucursales/by-qr")
                        .queryParam("qr_token", qrToken)
                        .build())
                .header("Authorization", bearerToken)
                .retrieve()
                .bodyToMono(SucursalQrResponse.class)
                .doOnNext(r -> log.debug("[CoreService] sucursal encontrada idSucursal={} idCompania={}", r.getIdSucursal(), r.getIdCompania()))
                .onErrorResume(e -> {
                    log.error("[CoreService] buscarSucursalPorQr FALLÓ qrToken={}... causa='{}'", truncate(qrToken), e.getMessage());
                    return Mono.error(new RuntimeException("Error consultando Core Service: " + e.getMessage()));
                });
    }

    public Mono<Integer> buscarIdClientePropio(String bearerToken) {
        log.debug("[CoreService] buscarIdClientePropio tokenPresente={}", bearerToken != null && !bearerToken.isBlank());
        return coreWebClient.get()
                .uri("/api/v1/clientes/my-id")
                .header("Authorization", bearerToken != null ? bearerToken : "")
                .retrieve()
                .bodyToMono(java.util.Map.class)
                .map(m -> ((Number) m.get("id_cliente")).intValue())
                .onErrorResume(e -> {
                    log.error("[CoreService] buscarIdClientePropio FALLÓ causa='{}'", e.getMessage());
                    return Mono.error(new RuntimeException("Error consultando Core Service: " + e.getMessage()));
                });
    }

    /**
     * REQ-SAAS-001 (Fase 5): consume el endpoint interno de core
     * {@code GET /internal/v1/companias/{id}/clientes-por-vencer} (Fase 4) para alimentar el
     * {@code MensajeriaJob}. Usa el header {@code X-Internal-Call} (no JWT).
     *
     * @param idCompania compañía a consultar.
     * @param dias       umbral de anticipación (calendario: días; accesos: entradas restantes).
     * @param modo       {@code calendario} | {@code accesos} | {@code todos}.
     */
    public Flux<ClientePorVencer> listarClientesPorVencer(Integer idCompania, int dias, String modo) {
        log.debug("[CoreService] clientesPorVencer → idCompania={} dias={} modo={}", idCompania, dias, modo);
        return coreWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/v1/companias/{id}/clientes-por-vencer")
                        .queryParam("dias", dias)
                        .queryParam("modo", modo)
                        .build(idCompania))
                .header(HEADER_INTERNAL_CALL, internalSecret)
                .retrieve()
                .bodyToMono(ClientesPorVencerResponse.class)
                .flatMapMany(r -> r == null || r.getClientes() == null
                        ? Flux.empty()
                        : Flux.fromIterable(r.getClientes()))
                .onErrorResume(e -> {
                    log.error("[CoreService] clientesPorVencer FALLÓ idCompania={} causa='{}'", idCompania, e.getMessage());
                    return Flux.error(new RuntimeException("Error consultando Core Service: " + e.getMessage()));
                });
    }

    private static String truncate(String s) {
        return s != null && s.length() > 8 ? s.substring(0, 8) : s;
    }

    @Getter
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ValidarAccesoResponse {
        private boolean permitido;
        private String razon;
        private Integer idCliente;
        private Integer idMembresia;
        private String modoControl;
        private Integer diasAccesoRestantes;
        private String fechaFin;
        private String tipoMembresia;
        private Integer accesosUsados;
    }

    @Getter
    public static class SucursalQrResponse {
        private Integer idSucursal;
        private Integer idCompania;
        private String nombre;
        private String qrTokenExpira;
    }

    /** Respuesta del endpoint interno de core (Fase 4): {@code companiaId + fechaCorte + clientes[]}. */
    @Getter
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ClientesPorVencerResponse {
        private Long companiaId;
        private String fechaCorte;
        private List<ClientePorVencer> clientes;
    }

    /** Proyección de un socio por vencer (contrato C3). El {@code telefono} viene SIN normalizar. */
    @Getter
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ClientePorVencer {
        private Integer idCliente;
        private Integer idPersona;
        private Integer idSucursal;
        private String nombre;
        private String telefono;
        private String correo;
        private String modoControl;
        private String fechaFin;
        private Integer diasParaVencer;
        private Integer accesosRestantes;
        private String estadoCliente;
        private boolean aceptaWhatsapp;
        private String fechaConsentimientoWa;
    }
}
