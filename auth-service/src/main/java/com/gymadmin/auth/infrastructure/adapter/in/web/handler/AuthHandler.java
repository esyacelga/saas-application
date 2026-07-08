package com.gymadmin.auth.infrastructure.adapter.in.web.handler;

import com.gymadmin.auth.domain.port.in.AuthUseCase;
import com.gymadmin.auth.dto.request.*;
import com.gymadmin.auth.dto.response.MessageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Autenticación", description = "Login, logout, refresh y recuperación de contraseña")
public class AuthHandler {

    private final AuthUseCase authUseCase;
    private final RequestValidator validator;

    @Operation(summary = "Login de usuario de plataforma SaaS")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login exitoso"),
        @ApiResponse(responseCode = "401", description = "Credenciales inválidas"),
        @ApiResponse(responseCode = "429", description = "Demasiados intentos")
    })
    public Mono<ServerResponse> loginPlatform(ServerRequest request) {
        return request.bodyToMono(LoginPlatformRequest.class)
                .flatMap(validator::validate)
                .flatMap(authUseCase::loginPlatform)
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Login de usuario staff del gimnasio")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login exitoso"),
        @ApiResponse(responseCode = "401", description = "Credenciales inválidas"),
        @ApiResponse(responseCode = "429", description = "Demasiados intentos")
    })
    public Mono<ServerResponse> loginStaff(ServerRequest request) {return request.bodyToMono(LoginStaffRequest.class)
                .flatMap(validator::validate)
                .flatMap(authUseCase::loginStaff)
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Login de usuario de la app móvil")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login exitoso"),
        @ApiResponse(responseCode = "401", description = "Credenciales inválidas"),
        @ApiResponse(responseCode = "429", description = "Demasiados intentos")
    })
    public Mono<ServerResponse> loginApp(ServerRequest request) {
        return request.bodyToMono(LoginAppRequest.class)
                .flatMap(validator::validate)
                .flatMap(authUseCase::loginApp)
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Renovar access token usando refresh token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token renovado"),
        @ApiResponse(responseCode = "401", description = "Refresh token inválido o expirado")
    })
    public Mono<ServerResponse> refresh(ServerRequest request) {
        return request.bodyToMono(RefreshTokenRequest.class)
                .flatMap(validator::validate)
                .flatMap(authUseCase::refresh)
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Cerrar sesión e invalidar refresh token", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Sesión cerrada"),
        @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    public Mono<ServerResponse> logout(ServerRequest request) {
        return SecurityUtils.currentUser()
                .flatMap(p -> authUseCase.logout(p.getId(), p.getTipo()))
                .then(ServerResponse.noContent().build());
    }

    @Operation(summary = "Solicitar restablecimiento de contraseña por correo")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Solicitud procesada (respuesta genérica)"),
        @ApiResponse(responseCode = "429", description = "Demasiados intentos")
    })
    public Mono<ServerResponse> resetRequest(ServerRequest request) {
        return request.bodyToMono(PasswordResetRequestDto.class)
                .flatMap(validator::validate)
                .flatMap(authUseCase::requestPasswordReset)
                .then(ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new MessageResponse("Si el correo existe, recibirás las instrucciones")));
    }

    @Operation(summary = "Aplicar nueva contraseña con token de restablecimiento")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contraseña actualizada"),
        @ApiResponse(responseCode = "400", description = "Token inválido o expirado")
    })
    public Mono<ServerResponse> resetApply(ServerRequest request) {
        return request.bodyToMono(PasswordResetApplyRequest.class)
                .flatMap(validator::validate)
                .flatMap(authUseCase::applyPasswordReset)
                .then(ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new MessageResponse("Contraseña actualizada correctamente")));
    }

    @Operation(summary = "Login de app móvil con Google OAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login exitoso"),
        @ApiResponse(responseCode = "401", description = "Token de Google inválido")
    })
    public Mono<ServerResponse> oauthGoogle(ServerRequest request) {
        return request.bodyToMono(OAuthGoogleRequest.class)
                .flatMap(validator::validate)
                .flatMap(authUseCase::loginWithGoogle)
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Login de app móvil con Facebook OAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login exitoso"),
        @ApiResponse(responseCode = "401", description = "Token de Facebook inválido")
    })
    public Mono<ServerResponse> oauthFacebook(ServerRequest request) {
        return request.bodyToMono(OAuthFacebookRequest.class)
                .flatMap(validator::validate)
                .flatMap(authUseCase::loginWithFacebook)
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Registro de nuevo usuario de la app móvil")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Usuario registrado"),
        @ApiResponse(responseCode = "409", description = "Correo o CI ya registrado")
    })
    public Mono<ServerResponse> registro(ServerRequest request) {
        return request.bodyToMono(RegistroAppRequest.class)
                .flatMap(validator::validate)
                .flatMap(authUseCase::registrar)
                .flatMap(r -> ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Listar compañías asociadas a un correo (para login staff multi-compañía)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de compañías")
    })
    public Mono<ServerResponse> getCompaniesByCorreo(ServerRequest request) {
        String correo = request.queryParam("correo").orElse("");
        return authUseCase.getCompaniesByCorreo(correo)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(list));
    }
}
