package com.gymadmin.auth.infrastructure.adapter.out.facebook;

import com.gymadmin.auth.domain.exception.AuthException;
import com.gymadmin.auth.domain.model.OAuthProfile;
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
    public Mono<String> verifyAndGetEmail(String accessToken) {
        return fetchProfile(accessToken).map(OAuthProfile::email);
    }

    @Override
    public Mono<OAuthProfile> verifyAndGetProfile(String accessToken) {
        return fetchProfile(accessToken);
    }

    @SuppressWarnings("unchecked")
    private Mono<OAuthProfile> fetchProfile(String accessToken) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/me")
                        // picture.width(200) devuelve una URL de mayor calidad que el default 50x50.
                        .queryParam("fields", "id,email,name,picture.width(200).height(200)")
                        .queryParam("access_token", accessToken)
                        .build())
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(),
                        response -> Mono.error(new AuthException("Token de Facebook invalido")))
                .bodyToMono(Map.class)
                .flatMap(map -> {
                    String email = (String) map.get("email");
                    if (email == null) {
                        return Mono.error(new AuthException(
                                "No se pudo obtener el email de Facebook; verifica que hayas concedido el permiso de email"));
                    }
                    String nombre = (String) map.get("name");
                    String fotoUrl = extractPictureUrl(map.get("picture"));
                    return Mono.just(new OAuthProfile(email, nombre, fotoUrl));
                });
    }

    // Graph API devuelve picture como { data: { url, width, height, is_silhouette } }.
    // is_silhouette=true es el avatar por defecto de FB — lo tratamos como "sin foto".
    @SuppressWarnings("unchecked")
    private String extractPictureUrl(Object pictureNode) {
        if (!(pictureNode instanceof Map<?, ?> picture)) return null;
        Object dataObj = picture.get("data");
        if (!(dataObj instanceof Map<?, ?> data)) return null;
        if (Boolean.TRUE.equals(data.get("is_silhouette"))) return null;
        Object url = data.get("url");
        return url instanceof String s ? s : null;
    }
}
