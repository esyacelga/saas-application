package com.gymadmin.auth.unit;

import com.gymadmin.auth.application.service.AuthApplicationService;
import com.gymadmin.auth.domain.exception.AuthException;
import com.gymadmin.auth.domain.exception.ConflictException;
import com.gymadmin.auth.domain.exception.ForbiddenException;
import com.gymadmin.auth.domain.model.OAuthProfile;
import com.gymadmin.auth.domain.model.Persona;
import com.gymadmin.auth.domain.model.UsuarioApp;
import com.gymadmin.auth.domain.port.out.*;
import com.gymadmin.auth.dto.request.CompletarRegistroOauthRequest;
import com.gymadmin.auth.dto.request.OAuthFacebookRequest;
import com.gymadmin.auth.dto.request.OAuthGoogleRequest;
import com.gymadmin.auth.dto.response.GimnasioPublicoResponse;
import com.gymadmin.auth.dto.response.OAuthLoginResponse;
import com.gymadmin.auth.infrastructure.config.AppProperties;
import com.gymadmin.auth.infrastructure.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthApplicationService — flujo OAuth")
class AuthApplicationServiceTest {

    @Mock private UsuarioPlataformaPort plataformaPort;
    @Mock private UsuarioStaffPort staffPort;
    @Mock private UsuarioAppPort appPort;
    @Mock private PersonaPort personaPort;
    @Mock private RolPermisoPort rolPermisoPort;
    @Mock private RefreshTokenPort refreshTokenPort;
    @Mock private TokenGeneratorPort tokenGenerator;
    @Mock private EmailPort emailPort;
    @Mock private JwtProperties jwtProps;
    @Mock private PasswordEncoder encoder;
    @Mock private RateLimiterPort rateLimiter;
    @Mock private AppProperties appProps;
    @Mock private GimnasioPort gimnasioPort;
    @Mock private GoogleTokenVerifierPort googleTokenVerifier;
    @Mock private FacebookTokenVerifierPort facebookTokenVerifier;

    @InjectMocks
    private AuthApplicationService service;

    private static final Integer ID_COMPANIA = 1;
    private static final String EMAIL = "maria@gmail.com";
    private static final String NOMBRE_GOOGLE = "Maria Lopez";

    @BeforeEach
    void setUp() {
        lenient().when(rateLimiter.checkAndRecord(anyString())).thenReturn(Mono.empty());
        lenient().when(rateLimiter.reset(anyString())).thenReturn(Mono.empty());
        lenient().when(refreshTokenPort.deleteByIdUsuarioAndTipoUsuario(anyInt(), anyString()))
                .thenReturn(Mono.empty());
        lenient().when(refreshTokenPort.save(any())).thenReturn(Mono.empty());
        lenient().when(tokenGenerator.generateRefreshToken()).thenReturn("refresh-token-abc");
        lenient().when(tokenGenerator.generateClienteToken(anyInt(), anyInt(), anyInt(),
                anyString(), any(), any(), any())).thenReturn("access-token-xyz");
        lenient().when(jwtProps.getExpiryClienteSeconds()).thenReturn(604800L);
        lenient().when(gimnasioPort.findByIdCompania(ID_COMPANIA))
                .thenReturn(Mono.just(new GimnasioPublicoResponse(ID_COMPANIA, null, "Gym Elite", null, "https://logo")));
    }

    private UsuarioApp existingActiveUser() {
        return UsuarioApp.builder()
                .id(50)
                .idPersona(10)
                .idCompania(ID_COMPANIA)
                .login(EMAIL)
                .nombrePersona("Maria Lopez")
                .sexoPersona("F")
                .fotoUrlPersona(null)
                .passwordHash("hashed")
                .activo(true)
                .build();
    }

    @Nested
    @DisplayName("loginWithGoogle")
    class LoginWithGoogle {

        @Test
        @DisplayName("devuelve status=logged_in cuando el usuario ya existe en la compania")
        void devuelveLoggedInCuandoUsuarioExiste() {
            UsuarioApp existing = existingActiveUser();
            when(googleTokenVerifier.verifyAndGetProfile("id-token"))
                    .thenReturn(Mono.just(new OAuthProfile(EMAIL, NOMBRE_GOOGLE, null)));
            when(appPort.findByLoginAndIdCompania(EMAIL, ID_COMPANIA))
                    .thenReturn(Mono.just(existing));
            when(appPort.save(any(UsuarioApp.class))).thenReturn(Mono.just(existing));

            StepVerifier.create(service.loginWithGoogle(new OAuthGoogleRequest("id-token", ID_COMPANIA)))
                    .expectNextMatches(r -> OAuthLoginResponse.STATUS_LOGGED_IN.equals(r.status())
                            && r.accessToken() != null
                            && r.refreshToken() != null
                            && r.persona() != null
                            && r.persona().id().equals(10)
                            && r.email() == null
                            && r.nombre() == null)
                    .verifyComplete();
        }

        @Test
        @DisplayName("devuelve status=registro_pendiente cuando el usuario no existe, con email y nombre del proveedor")
        void devuelveRegistroPendienteCuandoNoExiste() {
            when(googleTokenVerifier.verifyAndGetProfile("id-token"))
                    .thenReturn(Mono.just(new OAuthProfile(EMAIL, NOMBRE_GOOGLE, null)));
            when(appPort.findByLoginAndIdCompania(EMAIL, ID_COMPANIA))
                    .thenReturn(Mono.empty());

            StepVerifier.create(service.loginWithGoogle(new OAuthGoogleRequest("id-token", ID_COMPANIA)))
                    .expectNextMatches(r -> OAuthLoginResponse.STATUS_REGISTRO_PENDIENTE.equals(r.status())
                            && EMAIL.equals(r.email())
                            && NOMBRE_GOOGLE.equals(r.nombre())
                            && r.accessToken() == null
                            && r.refreshToken() == null
                            && r.persona() == null
                            && r.compania() == null)
                    .verifyComplete();
        }

        @Test
        @DisplayName("propaga ForbiddenException cuando el usuario existe pero esta inactivo")
        void lanzaForbiddenCuandoInactivo() {
            UsuarioApp inactive = existingActiveUser();
            inactive.setActivo(false);
            when(googleTokenVerifier.verifyAndGetProfile("id-token"))
                    .thenReturn(Mono.just(new OAuthProfile(EMAIL, NOMBRE_GOOGLE, null)));
            when(appPort.findByLoginAndIdCompania(EMAIL, ID_COMPANIA))
                    .thenReturn(Mono.just(inactive));

            StepVerifier.create(service.loginWithGoogle(new OAuthGoogleRequest("id-token", ID_COMPANIA)))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("propaga AuthException cuando el token de Google es invalido")
        void lanzaAuthCuandoTokenInvalido() {
            when(googleTokenVerifier.verifyAndGetProfile("bad-token"))
                    .thenReturn(Mono.error(new AuthException("Token de Google invalido")));

            StepVerifier.create(service.loginWithGoogle(new OAuthGoogleRequest("bad-token", ID_COMPANIA)))
                    .expectError(AuthException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("loginWithFacebook")
    class LoginWithFacebook {

        @Test
        @DisplayName("devuelve status=registro_pendiente cuando el usuario no existe")
        void devuelveRegistroPendienteCuandoNoExiste() {
            when(facebookTokenVerifier.verifyAndGetProfile("access-token"))
                    .thenReturn(Mono.just(new OAuthProfile(EMAIL, NOMBRE_GOOGLE, null)));
            when(appPort.findByLoginAndIdCompania(EMAIL, ID_COMPANIA))
                    .thenReturn(Mono.empty());

            StepVerifier.create(service.loginWithFacebook(new OAuthFacebookRequest("access-token", ID_COMPANIA)))
                    .expectNextMatches(r -> OAuthLoginResponse.STATUS_REGISTRO_PENDIENTE.equals(r.status())
                            && EMAIL.equals(r.email()))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("completarRegistroOauth")
    class CompletarRegistroOauth {

        @BeforeEach
        void stubOauthDefaults() {
            // Estos ports se consultan siempre dentro del flujo OAuth; por defecto
            // devuelven vacio para que cada test solo tenga que redefinir lo especifico.
            lenient().when(appPort.findByPersonaCiAndIdCompania(anyString(), anyInt()))
                    .thenReturn(Mono.empty());
            lenient().when(personaPort.findByCi(anyString())).thenReturn(Mono.empty());
        }

        private CompletarRegistroOauthRequest requestGoogle() {
            return new CompletarRegistroOauthRequest(
                    "google", "id-token", ID_COMPANIA,
                    "1234567890", "Maria Lopez", "0991234567");
        }

        private CompletarRegistroOauthRequest requestFacebook() {
            return new CompletarRegistroOauthRequest(
                    "facebook", "fb-token", ID_COMPANIA,
                    "1234567890", "Maria Lopez", "0991234567");
        }

        private Persona personaGuardada() {
            Persona p = new Persona();
            p.setId(10);
            p.setCi("1234567890");
            p.setNombre("Maria Lopez");
            p.setCorreo(EMAIL);
            return p;
        }

        @Test
        @DisplayName("happy path Google: crea persona + usuario_app y devuelve LoginAppResponse")
        void happyPathGoogle() {
            when(googleTokenVerifier.verifyAndGetProfile("id-token"))
                    .thenReturn(Mono.just(new OAuthProfile(EMAIL, NOMBRE_GOOGLE, null)));
            when(appPort.findByLoginAndIdCompania(EMAIL, ID_COMPANIA)).thenReturn(Mono.empty());
            when(personaPort.findByCorreo(EMAIL)).thenReturn(Mono.empty());
            when(personaPort.save(any(Persona.class))).thenReturn(Mono.just(personaGuardada()));
            when(appPort.existsByIdPersonaAndIdCompania(10, ID_COMPANIA)).thenReturn(Mono.just(false));
            when(encoder.encode(anyString())).thenReturn("random-hash");
            UsuarioApp guardado = existingActiveUser();
            when(appPort.save(any(UsuarioApp.class))).thenReturn(Mono.just(guardado));

            StepVerifier.create(service.completarRegistroOauth(requestGoogle()))
                    .expectNextMatches(r -> r.accessToken() != null
                            && r.refreshToken() != null
                            && r.persona() != null
                            && r.persona().id().equals(10)
                            && r.compania() != null)
                    .verifyComplete();
        }

        @Test
        @DisplayName("happy path Facebook: reusa persona existente por correo y crea usuario_app")
        void happyPathFacebookReusandoPersona() {
            when(facebookTokenVerifier.verifyAndGetProfile("fb-token"))
                    .thenReturn(Mono.just(new OAuthProfile(EMAIL, NOMBRE_GOOGLE, null)));
            when(appPort.findByLoginAndIdCompania(EMAIL, ID_COMPANIA)).thenReturn(Mono.empty());
            when(personaPort.findByCorreo(EMAIL)).thenReturn(Mono.just(personaGuardada()));
            when(appPort.existsByIdPersonaAndIdCompania(10, ID_COMPANIA)).thenReturn(Mono.just(false));
            when(encoder.encode(anyString())).thenReturn("random-hash");
            UsuarioApp guardado = existingActiveUser();
            when(appPort.save(any(UsuarioApp.class))).thenReturn(Mono.just(guardado));

            StepVerifier.create(service.completarRegistroOauth(requestFacebook()))
                    .expectNextMatches(r -> r.accessToken() != null && r.persona().id().equals(10))
                    .verifyComplete();
        }

        @Test
        @DisplayName("propaga ConflictException cuando ya existe usuario_app para ese correo en la compania")
        void conflictoCuandoUsuarioYaExiste() {
            when(googleTokenVerifier.verifyAndGetProfile("id-token"))
                    .thenReturn(Mono.just(new OAuthProfile(EMAIL, NOMBRE_GOOGLE, null)));
            when(appPort.findByLoginAndIdCompania(EMAIL, ID_COMPANIA))
                    .thenReturn(Mono.just(existingActiveUser()));

            StepVerifier.create(service.completarRegistroOauth(requestGoogle()))
                    .expectError(ConflictException.class)
                    .verify();
        }

        @Test
        @DisplayName("propaga ConflictException cuando la persona ya tiene cuenta en el gimnasio")
        void conflictoCuandoPersonaYaTieneCuenta() {
            when(googleTokenVerifier.verifyAndGetProfile("id-token"))
                    .thenReturn(Mono.just(new OAuthProfile(EMAIL, NOMBRE_GOOGLE, null)));
            when(appPort.findByLoginAndIdCompania(EMAIL, ID_COMPANIA)).thenReturn(Mono.empty());
            when(personaPort.findByCorreo(EMAIL)).thenReturn(Mono.just(personaGuardada()));
            when(appPort.existsByIdPersonaAndIdCompania(10, ID_COMPANIA)).thenReturn(Mono.just(true));

            StepVerifier.create(service.completarRegistroOauth(requestGoogle()))
                    .expectError(ConflictException.class)
                    .verify();
        }

        @Test
        @DisplayName("propaga AuthException cuando el token OAuth es invalido")
        void tokenInvalido() {
            when(googleTokenVerifier.verifyAndGetProfile("bad-token"))
                    .thenReturn(Mono.error(new AuthException("Token de Google invalido")));

            CompletarRegistroOauthRequest req = new CompletarRegistroOauthRequest(
                    "google", "bad-token", ID_COMPANIA,
                    "1234567890", "Maria Lopez", null);

            StepVerifier.create(service.completarRegistroOauth(req))
                    .expectError(AuthException.class)
                    .verify();
        }

        @Test
        @DisplayName("propaga AuthException cuando el provider no es soportado")
        void providerNoSoportado() {
            CompletarRegistroOauthRequest req = new CompletarRegistroOauthRequest(
                    "twitter", "token", ID_COMPANIA,
                    "1234567890", "Maria Lopez", null);

            StepVerifier.create(service.completarRegistroOauth(req))
                    .expectError(AuthException.class)
                    .verify();
        }
    }
}
