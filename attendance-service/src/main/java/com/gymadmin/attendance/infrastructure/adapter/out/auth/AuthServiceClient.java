package com.gymadmin.attendance.infrastructure.adapter.out.auth;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AuthServiceClient {

    private final WebClient authWebClient;

    public AuthServiceClient(@Qualifier("authWebClient") WebClient authWebClient) {
        this.authWebClient = authWebClient;
    }

    public Mono<GimnasioQrResponse> buscarSucursalPorQr(String qrToken) {
        log.debug("[AuthService] buscarSucursalPorQr → qrToken={}...", truncate(qrToken));
        return authWebClient.get()
                .uri("/api/v1/auth/gimnasio/by-qr/{qrToken}", qrToken)
                .retrieve()
                .bodyToMono(GimnasioQrResponse.class)
                .doOnNext(r -> log.debug("[AuthService] gimnasio encontrado idSucursal={} idCompania={} nombre='{}'",
                        r.getIdSucursal(), r.getIdCompania(), r.getNombreSucursal()))
                .onErrorResume(e -> {
                    log.error("[AuthService] buscarSucursalPorQr FALLÓ qrToken={}... causa='{}'", truncate(qrToken), e.getMessage());
                    return Mono.error(new RuntimeException("Error consultando Auth Service: " + e.getMessage()));
                });
    }

    private static String truncate(String s) {
        return s != null && s.length() > 8 ? s.substring(0, 8) : s;
    }

    @Getter
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class GimnasioQrResponse {
        private Integer idSucursal;
        private Integer idCompania;
        private String nombreSucursal;
        private String nombreCompania;
        private String logoUrl;
        private String qrTokenExpira;
    }
}
