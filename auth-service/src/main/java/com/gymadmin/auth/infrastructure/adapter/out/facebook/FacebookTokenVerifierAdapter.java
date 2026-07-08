package com.gymadmin.auth.infrastructure.adapter.out.facebook;

import com.gymadmin.auth.domain.exception.AuthException;
import com.gymadmin.auth.domain.port.out.FacebookTokenVerifierPort;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class FacebookTokenVerifierAdapter implements FacebookTokenVerifierPort {

    private final WebClient webClient;

    public FacebookTokenVerifierAdapter(WebClient.Builder builder) {
        this.webClient = builder.baseUrl("https://graph.facebook.com").build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<String> verifyAndGetEmail(String accessToken) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/me")
                        .queryParam("fields", "id,email")
                        .queryParam("access_token", accessToken)
                        .build())
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(),
                        response -> Mono.error(new AuthException("Token de Facebook inválido")))
                .bodyToMono(Map.class)
                .flatMap(map -> {
                    String email = (String) map.get("email");
                    if (email == null) {
                        return Mono.error(new AuthException(
                                "No se pudo obtener el email de Facebook; verifica que hayas concedido el permiso de email"));
                    }
                    return Mono.just(email);
                });
    }
}
