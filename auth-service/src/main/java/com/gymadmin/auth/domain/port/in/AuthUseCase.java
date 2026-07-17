package com.gymadmin.auth.domain.port.in;

import com.gymadmin.auth.dto.request.*;
import com.gymadmin.auth.dto.response.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AuthUseCase {
    Mono<LoginPlatformResponse> loginPlatform(LoginPlatformRequest req);
    Mono<LoginStaffResponse> loginStaff(LoginStaffRequest req);
    Mono<LoginAppResponse> loginApp(LoginAppRequest req);
    Mono<TokenRefreshResponse> refresh(RefreshTokenRequest req);
    Mono<Void> logout(Integer idUsuario, String tipoUsuario);
    Mono<Void> requestPasswordReset(PasswordResetRequestDto req);
    Mono<Void> applyPasswordReset(PasswordResetApplyRequest req);
    Flux<CompaniaBasicaResponse> getCompaniesByCorreo(String correo);
    Mono<OAuthLoginResponse> loginWithGoogle(OAuthGoogleRequest req);
    Mono<OAuthLoginResponse> loginWithFacebook(OAuthFacebookRequest req);
    Mono<LoginAppResponse> completarRegistroOauth(CompletarRegistroOauthRequest req);
    Mono<LoginAppResponse> registrar(RegistroAppRequest req);
}
