package com.gymadmin.auth.application.service;

import com.gymadmin.auth.domain.exception.AuthException;
import com.gymadmin.auth.domain.exception.ConflictException;
import com.gymadmin.auth.domain.exception.ForbiddenException;
import org.springframework.dao.DataIntegrityViolationException;
import com.gymadmin.auth.domain.model.OAuthProfile;
import com.gymadmin.auth.domain.model.Persona;
import com.gymadmin.auth.domain.model.RefreshToken;
import com.gymadmin.auth.domain.model.UsuarioApp;
import com.gymadmin.auth.domain.model.UsuarioPlataforma;
import com.gymadmin.auth.domain.model.UsuarioStaff;
import reactor.util.function.Tuple2;
import com.gymadmin.auth.domain.port.in.AuthUseCase;
import com.gymadmin.auth.domain.port.out.*;
import com.gymadmin.auth.dto.response.GimnasioPublicoResponse;
import com.gymadmin.auth.dto.request.*;
import com.gymadmin.auth.dto.response.*;
import reactor.core.publisher.Flux;
import com.gymadmin.auth.infrastructure.config.AppProperties;
import com.gymadmin.auth.infrastructure.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthApplicationService implements AuthUseCase {

    private final UsuarioPlataformaPort plataformaPort;
    private final UsuarioStaffPort staffPort;
    private final UsuarioAppPort appPort;
    private final PersonaPort personaPort;
    private final RolPermisoPort rolPermisoPort;
    private final RefreshTokenPort refreshTokenPort;
    private final TokenGeneratorPort tokenGenerator;
    private final EmailPort emailPort;
    private final JwtProperties jwtProps;
    private final PasswordEncoder encoder;
    private final RateLimiterPort rateLimiter;
    private final AppProperties appProps;
    private final GimnasioPort gimnasioPort;
    private final GoogleTokenVerifierPort googleTokenVerifier;
    private final FacebookTokenVerifierPort facebookTokenVerifier;

    @Override
    @Transactional
    public Mono<LoginPlatformResponse> loginPlatform(LoginPlatformRequest req) {
        String key = "platform:" + req.correo();
        return rateLimiter.checkAndRecord(key)
                .then(plataformaPort.findByCorreo(req.correo()))
                .switchIfEmpty(Mono.error(new AuthException("Credenciales inválidas")))
                .flatMap(u -> validatePlatformUser(u, req.password()))
                .flatMap(u -> rateLimiter.reset(key).thenReturn(u))
                .flatMap(plataformaPort::save)
                .flatMap(this::buildPlatformLoginResponse);
    }

    private Mono<UsuarioPlataforma> validatePlatformUser(UsuarioPlataforma u, String password) {
        if (!encoder.matches(password, u.getPasswordHash()))
            return Mono.error(new AuthException("Credenciales inválidas"));
        if (!Boolean.TRUE.equals(u.getActivo()))
            return Mono.error(new ForbiddenException("Usuario inactivo"));
        u.setUltimoAcceso(OffsetDateTime.now());
        u.setModificaUsuario("plataforma:" + u.getId());
        return Mono.just(u);
    }

    private Mono<LoginPlatformResponse> buildPlatformLoginResponse(UsuarioPlataforma saved) {
        return createRefreshToken(saved.getId(), "plataforma", null)
                .map(rt -> new LoginPlatformResponse(
                        tokenGenerator.generatePlatformToken(
                                saved.getId(), saved.getNombrePersona(), saved.getRol()),
                        rt,
                        jwtProps.getExpiryStaffSeconds(),
                        new LoginPlatformResponse.UsuarioPlataformaInfo(
                                saved.getId(), saved.getNombrePersona(), saved.getRol())));
    }

    @Override
    @Transactional
    public Mono<LoginStaffResponse> loginStaff(LoginStaffRequest req) {
        String key = "staff:" + req.idCompania() + ":" + req.correo();
        return rateLimiter.checkAndRecord(key)
                .then(staffPort.findByCorreoAndIdCompania(req.correo(), req.idCompania()))
                .switchIfEmpty(Mono.error(new AuthException("Credenciales inválidas")))
                .flatMap(u -> validateStaffUser(u, req.password()))
                .flatMap(u -> rateLimiter.reset(key).thenReturn(u))
                .flatMap(u -> staffPort.save(u).zipWith(loadPermisos(u)))
                .flatMap(this::buildStaffLoginResponse);
    }

    private Mono<UsuarioStaff> validateStaffUser(UsuarioStaff u, String password) {
        if (!encoder.matches(password, u.getPasswordHash()))
            return Mono.error(new AuthException("Credenciales inválidas"));
        if (!Boolean.TRUE.equals(u.getActivo()))
            return Mono.error(new ForbiddenException("Usuario inactivo"));
        u.setUltimoAcceso(OffsetDateTime.now());
        u.setModificaUsuario("staff:" + u.getId());
        return Mono.just(u);
    }

    private Mono<java.util.List<String>> loadPermisos(UsuarioStaff u) {
        return u.getIdRol() != null
                ? rolPermisoPort.findNombresPermisoByIdRol(u.getIdRol()).collectList()
                : Mono.just(java.util.List.of());
    }

    private Mono<LoginStaffResponse> buildStaffLoginResponse(
            Tuple2<UsuarioStaff, java.util.List<String>> tuple) {
        UsuarioStaff saved = tuple.getT1();
        java.util.List<String> permisos = tuple.getT2();
        return createRefreshToken(saved.getId(), "staff", saved.getIdCompania())
                .map(rt -> new LoginStaffResponse(
                        tokenGenerator.generateStaffToken(
                                saved.getId(), saved.getIdCompania(), saved.getIdSucursal(),
                                saved.getIdRol(), saved.getNombrePersona(), permisos),
                        rt,
                        jwtProps.getExpiryStaffSeconds(),
                        Boolean.TRUE.equals(saved.getRequiereCambioPwd()),
                        new LoginStaffResponse.UsuarioStaffInfo(
                                saved.getId(), saved.getNombrePersona(), saved.getCorreo(),
                                saved.getIdRol(), saved.getNombreRol())));
    }

    @Override
    @Transactional
    public Mono<LoginAppResponse> loginApp(LoginAppRequest req) {
        String key = "app:" + req.idCompania() + ":" + req.login();
        return rateLimiter.checkAndRecord(key)
                .then(appPort.findByLoginAndIdCompania(req.login(), req.idCompania()))
                .switchIfEmpty(Mono.error(new AuthException("Credenciales inválidas")))
                .flatMap(u -> validateAppUser(u, req.password()))
                .flatMap(u -> rateLimiter.reset(key).thenReturn(u))
                .flatMap(appPort::save)
                .flatMap(this::buildAppLoginResponse);
    }

    private Mono<UsuarioApp> validateAppUser(UsuarioApp u, String password) {
        if (!encoder.matches(password, u.getPasswordHash()))
            return Mono.error(new AuthException("Credenciales inválidas"));
        if (!Boolean.TRUE.equals(u.getActivo()))
            return Mono.error(new ForbiddenException("Usuario inactivo"));
        u.setUltimoAcceso(OffsetDateTime.now());
        u.setModificaUsuario("cliente:" + u.getId());
        return Mono.just(u);
    }

    private Mono<LoginAppResponse> buildAppLoginResponse(UsuarioApp saved) {
        return gimnasioPort.findByIdCompania(saved.getIdCompania())
                .defaultIfEmpty(new GimnasioPublicoResponse(saved.getIdCompania(), null, null, null, null))
                .flatMap(compania -> createRefreshToken(saved.getId(), "cliente", saved.getIdCompania())
                        .map(rt -> new LoginAppResponse(
                                tokenGenerator.generateClienteToken(
                                        saved.getId(), saved.getIdCompania(),
                                        saved.getIdPersona(), saved.getNombrePersona(),
                                        compania.nombreCompania(), compania.logoUrl(),
                                        saved.getSexoPersona()),
                                rt,
                                jwtProps.getExpiryClienteSeconds(),
                                new LoginAppResponse.PersonaInfo(
                                        saved.getIdPersona(), saved.getNombrePersona(),
                                        saved.getFotoUrlPersona(), saved.getSexoPersona()),
                                new LoginAppResponse.CompaniaInfo(
                                        saved.getIdCompania(), compania.nombreCompania(),
                                        compania.logoUrl()))));
    }

    @Override
    @Transactional
    public Mono<TokenRefreshResponse> refresh(RefreshTokenRequest req) {
        return refreshTokenPort.findByToken(req.refreshToken())
                .switchIfEmpty(Mono.error(new AuthException("Refresh token inválido o expirado")))
                .flatMap(rt -> {
                    if (rt.isExpired())
                        return refreshTokenPort.delete(rt)
                                .then(Mono.error(new AuthException("Refresh token expirado")));

                    Mono<String> newAccessTokenMono = switch (rt.getTipoUsuario()) {
                        case "plataforma" -> plataformaPort.findById(rt.getIdUsuario())
                                .switchIfEmpty(Mono.error(new AuthException("Usuario no encontrado")))
                                .map(u -> tokenGenerator.generatePlatformToken(u.getId(), u.getNombrePersona(), u.getRol()));
                        case "staff" -> staffPort.findById(rt.getIdUsuario())
                                .switchIfEmpty(Mono.error(new AuthException("Usuario no encontrado")))
                                .flatMap(u -> {
                                    Mono<java.util.List<String>> pm = u.getIdRol() != null
                                            ? rolPermisoPort.findNombresPermisoByIdRol(u.getIdRol()).collectList()
                                            : Mono.just(java.util.List.<String>of());
                                    return pm.map(p -> tokenGenerator.generateStaffToken(
                                            u.getId(), u.getIdCompania(), u.getIdSucursal(),
                                            u.getIdRol(), u.getNombrePersona(), p));
                                });
                        case "cliente" -> appPort.findById(rt.getIdUsuario())
                                .switchIfEmpty(Mono.error(new AuthException("Usuario no encontrado")))
                                .flatMap(u -> gimnasioPort.findByIdCompania(u.getIdCompania())
                                        .defaultIfEmpty(new GimnasioPublicoResponse(u.getIdCompania(), null, null, null, null))
                                        .map(c -> tokenGenerator.generateClienteToken(
                                                u.getId(), u.getIdCompania(), u.getIdPersona(),
                                                u.getNombrePersona(), c.nombreCompania(), c.logoUrl(),
                                                u.getSexoPersona())));
                        default -> Mono.error(new AuthException("Tipo de usuario desconocido"));
                    };

                    long expiresIn = "cliente".equals(rt.getTipoUsuario())
                            ? jwtProps.getExpiryClienteSeconds() : jwtProps.getExpiryStaffSeconds();

                    return newAccessTokenMono.map(token -> new TokenRefreshResponse(token, expiresIn));
                });
    }

    @Override
    @Transactional
    public Mono<Void> logout(Integer idUsuario, String tipoUsuario) {
        return refreshTokenPort.deleteByIdUsuarioAndTipoUsuario(idUsuario, tipoUsuario);
    }

    @Override
    @Transactional
    public Mono<Void> requestPasswordReset(PasswordResetRequestDto req) {
        if ("cliente".equals(req.tipo())) {
            return appPort.findByLoginAndIdCompania(req.correo(), req.idCompania())
                    .flatMap(u -> {
                        String token = java.util.UUID.randomUUID().toString().replace("-", "");
                        u.setTokenRecuperacion(token);
                        u.setTokenExpira(OffsetDateTime.now().plusHours(appProps.getPwdResetTokenExpiryHours()));
                        u.setModificaUsuario("sistema");
                        return appPort.save(u)
                                .flatMap(saved -> personaPort.findById(saved.getIdPersona()))
                                .flatMap(persona -> {
                                    String link = appProps.getFrontendUrl()
                                            + "/reset-password?token=" + token;
                                    return emailPort.sendPasswordResetEmail(
                                            persona.getCorreo(), persona.getNombre(), link);
                                });
                    })
                    .then();
        }
        return Mono.empty();
    }

    @Override
    @Transactional
    public Mono<Void> applyPasswordReset(PasswordResetApplyRequest req) {
        return appPort.findByTokenRecuperacion(req.token())
                .switchIfEmpty(Mono.error(new AuthException("Token inválido o expirado")))
                .flatMap(u -> {
                    if (u.getTokenExpira() == null || OffsetDateTime.now().isAfter(u.getTokenExpira()))
                        return Mono.error(new AuthException("Token inválido o expirado"));
                    u.setPasswordHash(encoder.encode(req.nuevaPassword()));
                    u.setTokenRecuperacion(null);
                    u.setTokenExpira(null);
                    u.setModificaUsuario("sistema");
                    return appPort.save(u);
                })
                .then();
    }

    @Override
    @Transactional
    public Mono<OAuthLoginResponse> loginWithGoogle(OAuthGoogleRequest req) {
        return googleTokenVerifier.verifyAndGetProfile(req.idToken())
                .flatMap(profile -> loginWithOAuthProfile(req.idCompania(), profile, "google"));
    }

    @Override
    @Transactional
    public Mono<OAuthLoginResponse> loginWithFacebook(OAuthFacebookRequest req) {
        return facebookTokenVerifier.verifyAndGetProfile(req.accessToken())
                .flatMap(profile -> loginWithOAuthProfile(req.idCompania(), profile, "facebook"));
    }

    private Mono<OAuthLoginResponse> loginWithOAuthProfile(
            Integer idCompania, OAuthProfile profile, String provider) {
        String key = "app-oauth:" + idCompania + ":" + profile.email();
        return rateLimiter.checkAndRecord(key)
                .then(appPort.findByLoginAndIdCompania(profile.email(), idCompania))
                .flatMap(u -> {
                    if (!Boolean.TRUE.equals(u.getActivo()))
                        return Mono.<OAuthLoginResponse>error(new ForbiddenException("Usuario inactivo"));
                    u.setUltimoAcceso(OffsetDateTime.now());
                    u.setModificaUsuario("oauth:" + provider + ":" + u.getId());
                    return rateLimiter.reset(key)
                            .then(appPort.save(u))
                            .flatMap(this::buildAppLoginResponse)
                            .map(OAuthLoginResponse::loggedIn);
                })
                .switchIfEmpty(Mono.defer(() -> rateLimiter.reset(key)
                        .thenReturn(OAuthLoginResponse.registroPendiente(profile.email(), profile.nombre()))));
    }

    @Override
    @Transactional
    public Mono<LoginAppResponse> completarRegistroOauth(CompletarRegistroOauthRequest req) {
        Mono<OAuthProfile> profileMono = switch (req.provider()) {
            case "google"   -> googleTokenVerifier.verifyAndGetProfile(req.token());
            case "facebook" -> facebookTokenVerifier.verifyAndGetProfile(req.token());
            default -> Mono.error(new AuthException("Proveedor OAuth no soportado: " + req.provider()));
        };

        return profileMono.flatMap(profile -> {
            String email = profile.email();
            String key = "oauth-completar:" + req.idCompania() + ":" + email;
            return rateLimiter.checkAndRecord(key)
                    .then(appPort.findByLoginAndIdCompania(email, req.idCompania()))
                    .flatMap(existing -> Mono.<LoginAppResponse>error(
                            new ConflictException("Ya existe una cuenta con ese correo en este gimnasio")))
                    .switchIfEmpty(Mono.defer(() -> appPort.findByPersonaCiAndIdCompania(req.ci(), req.idCompania())
                            .flatMap(cuentaExistente -> Mono.<LoginAppResponse>error(new ConflictException(
                                    "Ya tienes una cuenta en este gimnasio registrada con el correo "
                                            + maskEmail(cuentaExistente.getLogin())
                                            + ". Ingresa con ese método (Google, Facebook o correo/contraseña).")))
                            .switchIfEmpty(Mono.defer(() -> personaPort.findByCi(req.ci())
                                    .switchIfEmpty(Mono.defer(() -> personaPort.findByCorreo(email)))
                                    // Si la Persona ya existe (por CI o correo) NO se toca su foto_url:
                                    // respetamos lo que el admin o el propio usuario ya haya cargado.
                                    // La foto del proveedor OAuth solo se persiste al crear una Persona nueva.
                                    .switchIfEmpty(Mono.defer(() -> personaPort.save(Persona.builder()
                                            .ci(req.ci())
                                            .nombre(req.nombre())
                                            .correo(email)
                                            .telefono(req.telefono())
                                            .fotoUrl(truncateFotoUrl(profile.fotoUrl()))
                                            .creacionUsuario("oauth:" + req.provider())
                                            .build())
                                            .onErrorMap(DataIntegrityViolationException.class, e -> {
                                                String msg = e.getMostSpecificCause() != null
                                                        ? e.getMostSpecificCause().getMessage() : e.getMessage();
                                                if (msg != null && (msg.toLowerCase().contains("unique")
                                                        || msg.toLowerCase().contains("duplicate"))) {
                                                    return new ConflictException(
                                                            "Ya existe una persona con esos datos. Detalle: " + msg);
                                                }
                                                return new ConflictException(
                                                        "No se pudo crear la persona: " + msg);
                                            })))
                                    .flatMap(persona -> appPort.existsByIdPersonaAndIdCompania(persona.getId(), req.idCompania())
                                            .flatMap(exists -> {
                                                if (Boolean.TRUE.equals(exists))
                                                    return Mono.<UsuarioApp>error(new ConflictException(
                                                            "La persona ya tiene una cuenta en este gimnasio"));
                                                // Password inutilizable: usuario OAuth-only, no debe poder loguear con contrasena.
                                                String randomHash = encoder.encode(UUID.randomUUID().toString());
                                                return appPort.save(UsuarioApp.builder()
                                                        .idPersona(persona.getId())
                                                        .nombrePersona(persona.getNombre())
                                                        .idCompania(req.idCompania())
                                                        .login(email)
                                                        .passwordHash(randomHash)
                                                        .requiereCambioPwd(false)
                                                        .activo(true)
                                                        .creacionUsuario("oauth:" + req.provider())
                                                        .build());
                                            }))
                                    .flatMap(saved -> rateLimiter.reset(key).thenReturn(saved))
                                    .flatMap(this::buildAppLoginResponse)))));
        });
    }

    @Override
    public Flux<CompaniaBasicaResponse> getCompaniesByCorreo(String correo) {
        return staffPort.findCompaniesByCorreo(correo);
    }

    @Override
    @Transactional
    public Mono<LoginAppResponse> registrar(RegistroAppRequest req) {
        return appPort.findByLoginAndIdCompania(req.correo(), req.idCompania())
                .flatMap(u -> Mono.<LoginAppResponse>error(
                        new ConflictException("Ya existe una cuenta con ese correo en este gimnasio")))
                .switchIfEmpty(Mono.defer(() ->
                        personaPort.findByCorreo(req.correo())
                                // Mono.defer es obligatorio: sin él, `personaPort.save(...)` se evalúa al
                                // construir la cadena y se INSERTA una persona nueva (con CI temporal
                                // aleatorio) aunque el correo ya exista y el flujo tome la otra rama.
                                .switchIfEmpty(Mono.defer(() -> personaPort.save(Persona.builder()
                                        .ci(generarCiTemporal())
                                        .nombre(req.nombre())
                                        .correo(req.correo())
                                        .telefono(req.telefono())
                                        .creacionUsuario("auto-registro")
                                        .build())
                                        .onErrorMap(DataIntegrityViolationException.class, e -> {
                                            String msg = e.getMostSpecificCause() != null
                                                    ? e.getMostSpecificCause().getMessage() : e.getMessage();
                                            if (msg != null && (msg.toLowerCase().contains("unique") || msg.toLowerCase().contains("duplicate"))) {
                                                return new ConflictException(
                                                        "Ya existe una persona con esos datos. Detalle: " + msg);
                                            }
                                            return new ConflictException(
                                                    "No se pudo crear la persona: " + msg);
                                        })))
                                .flatMap(persona ->
                                        appPort.existsByIdPersonaAndIdCompania(persona.getId(), req.idCompania())
                                                .flatMap(exists -> {
                                                    if (Boolean.TRUE.equals(exists))
                                                        return Mono.<UsuarioApp>error(
                                                                new ConflictException("La persona ya tiene una cuenta en este gimnasio"));
                                                    return appPort.save(UsuarioApp.builder()
                                                            .idPersona(persona.getId())
                                                            .nombrePersona(persona.getNombre())
                                                            .idCompania(req.idCompania())
                                                            .login(req.correo())
                                                            .passwordHash(encoder.encode(req.password()))
                                                            .requiereCambioPwd(false)
                                                            .activo(true)
                                                            .creacionUsuario("auto-registro")
                                                            .build());
                                                })
                                )
                                .flatMap(this::buildAppLoginResponse)
                ));
    }

    private String generarCiTemporal() {
        long valor = Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 1_000_000_000L;
        return String.format("1%09d0", valor);
    }

    private Mono<String> createRefreshToken(Integer idUsuario, String tipo, Integer idCompania) {
        String token = tokenGenerator.generateRefreshToken();
        RefreshToken rt = RefreshToken.builder()
                .token(token).tipoUsuario(tipo).idUsuario(idUsuario).idCompania(idCompania)
                .expiraEn(OffsetDateTime.now().plusSeconds(jwtProps.getRefreshExpirySeconds()))
                .build();
        return refreshTokenPort.deleteByIdUsuarioAndTipoUsuario(idUsuario, tipo)
                .then(refreshTokenPort.save(rt))
                .thenReturn(token);
    }

    private static String maskEmail(String email) {
        if (email == null) return "";
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "***" + email.substring(at);
    }

    // identidad.personas.foto_url es VARCHAR(255). Las URLs de proveedores OAuth
    // (sobre todo Facebook Graph con tokens firmados) suelen superarlo — en ese caso
    // preferimos no guardar nada antes que persistir una URL truncada e invalida.
    private static String truncateFotoUrl(String url) {
        if (url == null || url.isBlank()) return null;
        return url.length() > 255 ? null : url;
    }
}
