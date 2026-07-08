package com.gymadmin.auth.infrastructure.adapter.out.google;

import com.gymadmin.auth.domain.exception.AuthException;
import com.gymadmin.auth.domain.port.out.GoogleTokenVerifierPort;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class GoogleTokenVerifierAdapter implements GoogleTokenVerifierPort {

    private final WebClient webClient;

    public GoogleTokenVerifierAdapter(WebClient.Builder builder) {
        this.webClient = builder.baseUrl("https://oauth2.googleapis.com").build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<String> verifyAndGetEmail(String idToken) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tokeninfo")
                        .queryParam("id_token", idToken)
                        .build())
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(),
                        response -> Mono.error(new AuthException("Token de Google inválido")))
                .bodyToMono(Map.class)
                .flatMap(map -> {
                    String email = (String) map.get("email");
                    String emailVerified = (String) map.get("email_verified");
                    if (email == null || !"true".equals(emailVerified)) {
                        return Mono.error(new AuthException("Token de Google inválido o email no verificado"));
                    }
                    return Mono.just(email);
                });
    }
}
