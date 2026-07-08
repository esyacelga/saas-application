package com.gymadmin.auth.unit;

import com.gymadmin.auth.application.service.AppUsuarioApplicationService;
import com.gymadmin.auth.domain.exception.ConflictException;
import com.gymadmin.auth.domain.exception.ResourceNotFoundException;
import com.gymadmin.auth.domain.model.Persona;
import com.gymadmin.auth.domain.model.UsuarioApp;
import com.gymadmin.auth.domain.port.out.PersonaPort;
import com.gymadmin.auth.domain.port.out.UsuarioAppPort;
import com.gymadmin.auth.dto.request.CreateAppUsuarioRequest;
import com.gymadmin.auth.dto.request.UpdateAppUsuarioRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppUsuarioApplicationService")
class AppUsuarioApplicationServiceTest {

    @Mock
    private UsuarioAppPort usuarioAppPort;

    @Mock
    private PersonaPort personaPort;

    @Mock
    private PasswordEncoder encoder;

    @InjectMocks
    private AppUsuarioApplicationService service;

    private UsuarioApp usuarioFijura;
    private Persona personaFijura;

    @BeforeEach
    void setUp() {
        personaFijura = new Persona();
        personaFijura.setId(10);
        personaFijura.setCi("1234567890");
        personaFijura.setNombre("Maria Torres");
        personaFijura.setCorreo("maria@example.com");

        usuarioFijura = UsuarioApp.builder()
                .id(50)
                .idPersona(10)
                .nombrePersona("Maria Torres")
                .idCompania(1)
                .login("maria.torres")
                .passwordHash("hashed")
                .activo(true)
                .build();
    }

    @Nested
    @DisplayName("crear usuario app")
    class Crear {

        @Test
        @DisplayName("crea usuario app exitosamente cuando la persona existe y no tiene cuenta")
        void creaUsuarioAppExitosamente() {
            CreateAppUsuarioRequest req = new CreateAppUsuarioRequest(10, "maria.torres", "Clave1234!", 1);

            when(personaPort.findById(10)).thenReturn(Mono.just(personaFijura));
            when(usuarioAppPort.existsByIdPersonaAndIdCompania(10, 1)).thenReturn(Mono.just(false));
            when(encoder.encode("Clave1234!")).thenReturn("hashed");
            when(usuarioAppPort.save(any(UsuarioApp.class))).thenReturn(Mono.just(usuarioFijura));

            StepVerifier.create(service.crear(1, req, "admin"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ResourceNotFoundException cuando la persona no existe")
        void lanzaExcepcionCuandoPersonaNoExiste() {
            CreateAppUsuarioRequest req = new CreateAppUsuarioRequest(99, "nadie", "Clave1234!", 1);
            when(personaPort.findById(99)).thenReturn(Mono.empty());

            StepVerifier.create(service.crear(1, req, "admin"))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza ConflictException cuando la persona ya tiene cuenta en la compania")
        void lanzaConflictCuandoYaTieneCuenta() {
            CreateAppUsuarioRequest req = new CreateAppUsuarioRequest(10, "maria.torres", "Clave1234!", 1);

            when(personaPort.findById(10)).thenReturn(Mono.just(personaFijura));
            when(usuarioAppPort.existsByIdPersonaAndIdCompania(10, 1)).thenReturn(Mono.just(true));

            StepVerifier.create(service.crear(1, req, "admin"))
                    .expectError(ConflictException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("desactivar usuario app")
    class Desactivar {

        @Test
        @DisplayName("desactiva usuario app exitosamente")
        void desactivaExitosamente() {
            when(usuarioAppPort.findById(50)).thenReturn(Mono.just(usuarioFijura));
            when(usuarioAppPort.save(any(UsuarioApp.class))).thenReturn(Mono.just(usuarioFijura));

            StepVerifier.create(service.desactivar(50, "admin"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ResourceNotFoundException cuando el usuario no existe")
        void lanzaExcepcionCuandoNoExiste() {
            when(usuarioAppPort.findById(999)).thenReturn(Mono.empty());

            StepVerifier.create(service.desactivar(999, "admin"))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("activar usuario app")
    class Activar {

        @Test
        @DisplayName("activa usuario app exitosamente")
        void activaExitosamente() {
            UsuarioApp inactivo = UsuarioApp.builder()
                    .id(50).idPersona(10).idCompania(1).login("maria.torres")
                    .activo(false).build();
            when(usuarioAppPort.findById(50)).thenReturn(Mono.just(inactivo));
            when(usuarioAppPort.save(any(UsuarioApp.class))).thenReturn(Mono.just(usuarioFijura));

            StepVerifier.create(service.activar(50, "admin"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ResourceNotFoundException cuando el usuario no existe")
        void lanzaExcepcionCuandoNoExiste() {
            when(usuarioAppPort.findById(999)).thenReturn(Mono.empty());

            StepVerifier.create(service.activar(999, "admin"))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("obtener usuario app por CI")
    class ObtenerPorCi {

        @Test
        @DisplayName("retorna usuario cuando existe el CI en la compania")
        void retornaUsuarioCuandoExiste() {
            when(usuarioAppPort.findByPersonaCiAndIdCompania("1234567890", 1))
                    .thenReturn(Mono.just(usuarioFijura));

            StepVerifier.create(service.obtenerPorCi("1234567890", 1))
                    .expectNextMatches(r -> r.id().equals(50) && r.login().equals("maria.torres"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna mono vacio cuando no existe el CI en la compania")
        void retornaVacioCuandoNoExiste() {
            when(usuarioAppPort.findByPersonaCiAndIdCompania("0000000000", 1))
                    .thenReturn(Mono.empty());

            StepVerifier.create(service.obtenerPorCi("0000000000", 1))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("actualizar usuario app")
    class Actualizar {

        @Test
        @DisplayName("actualiza login exitosamente cuando el nuevo login no esta en uso")
        void actualizaLoginExitosamente() {
            UpdateAppUsuarioRequest req = new UpdateAppUsuarioRequest("nuevo.login", null);
            when(usuarioAppPort.findById(50)).thenReturn(Mono.just(usuarioFijura));
            // El nuevo login no existe en la compania => Mono.empty()
            when(usuarioAppPort.findByLoginAndIdCompania("nuevo.login", 1)).thenReturn(Mono.empty());
            when(usuarioAppPort.save(any(UsuarioApp.class))).thenReturn(Mono.just(usuarioFijura));

            StepVerifier.create(service.actualizar(50, req, "admin"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ResourceNotFoundException cuando el usuario no existe")
        void lanzaExcepcionCuandoNoExiste() {
            UpdateAppUsuarioRequest req = new UpdateAppUsuarioRequest("nuevo.login", null);
            when(usuarioAppPort.findById(999)).thenReturn(Mono.empty());

            StepVerifier.create(service.actualizar(999, req, "admin"))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza ConflictException cuando el nuevo login ya esta en uso")
        void lanzaConflictCuandoLoginDuplicado() {
            UpdateAppUsuarioRequest req = new UpdateAppUsuarioRequest("login.duplicado", null);
            UsuarioApp otroUsuario = UsuarioApp.builder()
                    .id(99).idCompania(1).login("login.duplicado").build();

            when(usuarioAppPort.findById(50)).thenReturn(Mono.just(usuarioFijura));
            when(usuarioAppPort.findByLoginAndIdCompania("login.duplicado", 1))
                    .thenReturn(Mono.just(otroUsuario));

            StepVerifier.create(service.actualizar(50, req, "admin"))
                    .expectError(ConflictException.class)
                    .verify();
        }

        @Test
        @DisplayName("actualiza solo la contrasena sin cambiar login")
        void actualizaSoloContrasena() {
            UpdateAppUsuarioRequest req = new UpdateAppUsuarioRequest(null, "NuevaClave123!");
            when(usuarioAppPort.findById(50)).thenReturn(Mono.just(usuarioFijura));
            when(encoder.encode("NuevaClave123!")).thenReturn("nuevohashed");
            when(usuarioAppPort.save(any(UsuarioApp.class))).thenReturn(Mono.just(usuarioFijura));

            StepVerifier.create(service.actualizar(50, req, "admin"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("listar usuarios app por persona")
    class ListarPorPersona {

        @Test
        @DisplayName("retorna flux de usuarios app de la persona")
        void retornaUsuariosDePersona() {
            when(usuarioAppPort.findByIdPersona(10)).thenReturn(Flux.just(usuarioFijura));

            StepVerifier.create(service.listarPorPersona(10))
                    .expectNextMatches(r -> r.id().equals(50) && r.login().equals("maria.torres"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna flux vacio cuando la persona no tiene usuarios app")
        void retornaFluxVacioCuandoNoTiene() {
            when(usuarioAppPort.findByIdPersona(10)).thenReturn(Flux.empty());

            StepVerifier.create(service.listarPorPersona(10))
                    .verifyComplete();
        }
    }
}
