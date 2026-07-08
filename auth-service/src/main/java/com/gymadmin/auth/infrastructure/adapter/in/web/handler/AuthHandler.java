package com.gymadmin.auth.infrastructure.adapter.in.web.handler;

import com.gymadmin.auth.domain.port.in.AuthUseCase;
import com.gymadmin.auth.dto.request.*;
import com.gymadmin.auth.dto.response.MessageResponse;
import org.springframework.http.HttpStatus;
import com.gymadmin.auth.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AuthHandler {

    private final AuthUseCase authUseCase;
    private final RequestValidator validator;

    public Mono<ServerResponse> loginPlatform(ServerRequest request) {
        return request.bodyToMono(LoginPlatformRequest.class)
                .flatMap(validator::validate)
                .flatMap(authUseCase::loginPlatform)
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> loginStaff(ServerRequest request) {return request.bodyToMono(LoginStaffRequest.class)
                .flatMap(validator::validate)
                .flatMap(authUseCase::loginStaff)
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> loginApp(ServerRequest request) {
        return request.bodyToMono(LoginAppRequest.class)
                .flatMap(validator::validate)
                .flatMap(authUseCase::loginApp)
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> refresh(ServerRequest request) {
        return request.bodyToMono(RefreshTokenRequest.class)
                .flatMap(validator::validate)
                .flatMap(authUseCase::refresh)
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> logout(ServerRequest request) {
        return SecurityUtils.currentUser()
                .flatMap(p -> authUseCase.logout(p.getId(), p.getTipo()))
                .then(ServerResponse.noContent().build());
    }

    public Mono<ServerResponse> resetRequest(ServerRequest request) {
        return request.bodyToMono(PasswordResetRequestDto.class)
                .flatMap(validator::validate)
                .flatMap(authUseCase::requestPasswordReset)
                .then(ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new MessageResponse("Si el correo existe, recibirás las instrucciones")));
    }

    public Mono<ServerResponse> resetApply(ServerRequest request) {
        return request.bodyToMono(PasswordResetApplyRequest.class)
                .flatMap(validator::validate)
                .flatMap(authUseCase::applyPasswordReset)
                .then(ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new MessageResponse("Contraseña actualizada correctamente")));
    }

    public Mono<ServerResponse> oauthGoogle(ServerRequest request) {
        return request.bodyToMono(OAuthGoogleRequest.class)
                .flatMap(validator::validate)
                .flatMap(authUseCase::loginWithGoogle)
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> oauthFacebook(ServerRequest request) {
        return request.bodyToMono(OAuthFacebookRequest.class)
                .flatMap(validator::validate)
                .flatMap(authUseCase::loginWithFacebook)
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> registro(ServerRequest request) {
        return request.bodyToMono(RegistroAppRequest.class)
                .flatMap(validator::validate)
                .flatMap(authUseCase::registrar)
                .flatMap(r -> ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> getCompaniesByCorreo(ServerRequest request) {
        String correo = request.queryParam("correo").orElse("");
        return authUseCase.getCompaniesByCorreo(correo)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(list));
    }
}
