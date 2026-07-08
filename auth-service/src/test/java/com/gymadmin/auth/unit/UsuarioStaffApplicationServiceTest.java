package com.gymadmin.auth.unit;

import com.gymadmin.auth.application.service.UsuarioStaffApplicationService;
import com.gymadmin.auth.domain.exception.BadRequestException;
import com.gymadmin.auth.domain.exception.ConflictException;
import com.gymadmin.auth.domain.exception.ResourceNotFoundException;
import com.gymadmin.auth.domain.model.Persona;
import com.gymadmin.auth.domain.model.Rol;
import com.gymadmin.auth.domain.model.UsuarioStaff;
import com.gymadmin.auth.domain.port.out.PersonaPort;
import com.gymadmin.auth.domain.port.out.RolPermisoPort;
import com.gymadmin.auth.domain.port.out.RolPort;
import com.gymadmin.auth.domain.port.out.UsuarioStaffPort;
import com.gymadmin.auth.dto.request.CreateUsuarioStaffRequest;
import com.gymadmin.auth.dto.request.UpdateUsuarioStaffRequest;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsuarioStaffApplicationService")
class UsuarioStaffApplicationServiceTest {

    @Mock
    private UsuarioStaffPort staffPort;

    @Mock
    private RolPort rolPort;

    @Mock
    private RolPermisoPort rolPermisoPort;

    @Mock
    private PersonaPort personaPort;

    @Mock
    private PasswordEncoder encoder;

    @InjectMocks
    private UsuarioStaffApplicationService service;

    private UsuarioStaff staffFijura;
    private Persona personaFijura;
    private Rol rolFijura;

    @BeforeEach
    void setUp() {
        personaFijura = new Persona();
        personaFijura.setId(10);
        personaFijura.setCi("1234567890");
        personaFijura.setNombre("Ana Lopez");
        personaFijura.setCorreo("ana@example.com");

        rolFijura = Rol.builder()
                .id(5)
                .idCompania(1)
                .nombre("Admin")
                .descripcion("Administrador")
                .build();

        staffFijura = UsuarioStaff.builder()
                .id(100)
                .idCompania(1)
                .idSucursal(1)
                .idPersona(10)
                .nombrePersona("Ana Lopez")
                .correo("ana@gymadmin.com")
                .passwordHash("hashed")
                .idRol(5)
                .nombreRol("Admin")
                .activo(true)
                .build();
    }

    @Nested
    @DisplayName("listar usuarios staff por compania")
    class Listar {

        @Test
        @DisplayName("retorna flux de staff cuando existen usuarios")
        void retornaStaffCuandoExisten() {
            when(staffPort.findByIdCompania(1)).thenReturn(Flux.just(staffFijura));

            StepVerifier.create(service.listar(1))
                    .expectNextMatches(r -> r.id().equals(100) && r.correo().equals("ana@gymadmin.com"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna flux vacio cuando no hay usuarios")
        void retornaFluxVacioCuandoNoHayUsuarios() {
            when(staffPort.findByIdCompania(1)).thenReturn(Flux.empty());

            StepVerifier.create(service.listar(1))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("crear usuario staff")
    class Crear {

        @Test
        @DisplayName("crea usuario staff exitosamente")
        void creaStaffExitosamente() {
            CreateUsuarioStaffRequest req = new CreateUsuarioStaffRequest(
                    10, "ana@gymadmin.com", 5, 1, "Temp1234!", 1
            );
            when(personaPort.findById(10)).thenReturn(Mono.just(personaFijura));
            when(staffPort.existsByCorreoAndIdCompania("ana@gymadmin.com", 1)).thenReturn(Mono.just(false));
            when(rolPort.findByIdAndIdCompania(5, 1)).thenReturn(Mono.just(rolFijura));
            when(encoder.encode("Temp1234!")).thenReturn("hashed");
            when(staffPort.save(any(UsuarioStaff.class))).thenReturn(Mono.just(staffFijura));

            StepVerifier.create(service.crear(1, 1, req, "admin"))
                    .expectNextMatches(r -> r.id().equals(100))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ResourceNotFoundException cuando la persona no existe")
        void lanzaExcepcionCuandoPersonaNoExiste() {
            CreateUsuarioStaffRequest req = new CreateUsuarioStaffRequest(
                    99, "nadie@gymadmin.com", 5, 1, "Temp1234!", 1
            );
            when(personaPort.findById(99)).thenReturn(Mono.empty());

            StepVerifier.create(service.crear(1, 1, req, "admin"))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza ConflictException cuando el correo ya existe en la compania")
        void lanzaConflictCuandoCorreoDuplicado() {
            CreateUsuarioStaffRequest req = new CreateUsuarioStaffRequest(
                    10, "ana@gymadmin.com", 5, 1, "Temp1234!", 1
            );
            when(personaPort.findById(10)).thenReturn(Mono.just(personaFijura));
            when(staffPort.existsByCorreoAndIdCompania("ana@gymadmin.com", 1)).thenReturn(Mono.just(true));

            StepVerifier.create(service.crear(1, 1, req, "admin"))
                    .expectError(ConflictException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza BadRequestException cuando el rol no pertenece a la compania")
        void lanzaExcepcionCuandoRolNoPertenece() {
            CreateUsuarioStaffRequest req = new CreateUsuarioStaffRequest(
                    10, "ana@gymadmin.com", 999, 1, "Temp1234!", 1
            );
            when(personaPort.findById(10)).thenReturn(Mono.just(personaFijura));
            when(staffPort.existsByCorreoAndIdCompania("ana@gymadmin.com", 1)).thenReturn(Mono.just(false));
            when(rolPort.findByIdAndIdCompania(999, 1)).thenReturn(Mono.empty());

            StepVerifier.create(service.crear(1, 1, req, "admin"))
                    .expectError(BadRequestException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("editar usuario staff")
    class Editar {

        @Test
        @DisplayName("edita usuario staff exitosamente sin cambiar correo ni rol")
        void editaStaffExitosamente() {
            UpdateUsuarioStaffRequest req = new UpdateUsuarioStaffRequest(null, null);
            when(staffPort.findByIdAndIdCompania(100, 1)).thenReturn(Mono.just(staffFijura));
            when(staffPort.save(any(UsuarioStaff.class))).thenReturn(Mono.just(staffFijura));

            StepVerifier.create(service.editar(100, 1, req, "admin"))
                    .expectNextMatches(r -> r.id().equals(100))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ResourceNotFoundException cuando el usuario no existe")
        void lanzaExcepcionCuandoNoExiste() {
            UpdateUsuarioStaffRequest req = new UpdateUsuarioStaffRequest(null, null);
            when(staffPort.findByIdAndIdCompania(999, 1)).thenReturn(Mono.empty());

            StepVerifier.create(service.editar(999, 1, req, "admin"))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza ConflictException cuando el nuevo correo ya esta en uso")
        void lanzaConflictCuandoCorreoNuevoDuplicado() {
            UpdateUsuarioStaffRequest req = new UpdateUsuarioStaffRequest("otro@gymadmin.com", null);
            when(staffPort.findByIdAndIdCompania(100, 1)).thenReturn(Mono.just(staffFijura));
            when(staffPort.existsByCorreoAndIdCompania("otro@gymadmin.com", 1)).thenReturn(Mono.just(true));

            StepVerifier.create(service.editar(100, 1, req, "admin"))
                    .expectError(ConflictException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("ver permisos de usuario staff")
    class VerPermisos {

        @Test
        @DisplayName("retorna permisos del usuario cuando existe")
        void retornaPermisosExitosamente() {
            when(staffPort.findByIdAndIdCompania(100, 1)).thenReturn(Mono.just(staffFijura));
            when(rolPermisoPort.findNombresPermisoByIdRol(5))
                    .thenReturn(Flux.just("usuarios:crear", "usuarios:listar"));

            StepVerifier.create(service.verPermisos(100, 1))
                    .expectNextMatches(r ->
                            r.usuario().id().equals(100)
                                    && r.permisos().size() == 2
                    )
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ResourceNotFoundException cuando el usuario no existe")
        void lanzaExcepcionCuandoNoExiste() {
            when(staffPort.findByIdAndIdCompania(999, 1)).thenReturn(Mono.empty());

            StepVerifier.create(service.verPermisos(999, 1))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("desactivar usuario staff")
    class Desactivar {

        @Test
        @DisplayName("desactiva usuario staff exitosamente")
        void desactivaExitosamente() {
            when(staffPort.findByIdAndIdCompania(100, 1)).thenReturn(Mono.just(staffFijura));
            when(staffPort.save(any(UsuarioStaff.class))).thenReturn(Mono.just(staffFijura));

            StepVerifier.create(service.desactivar(100, 1, "admin"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ResourceNotFoundException cuando el usuario no existe")
        void lanzaExcepcionCuandoNoExiste() {
            when(staffPort.findByIdAndIdCompania(999, 1)).thenReturn(Mono.empty());

            StepVerifier.create(service.desactivar(999, 1, "admin"))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza ConflictException cuando intenta desactivar al unico dueno activo")
        void lanzaConflictCuandoUnicoDueno() {
            UsuarioStaff dueno = UsuarioStaff.builder()
                    .id(100)
                    .idCompania(1)
                    .correo("dueno@gym.com")
                    .nombreRol("Dueño")
                    .activo(true)
                    .build();
            when(staffPort.findByIdAndIdCompania(100, 1)).thenReturn(Mono.just(dueno));
            when(staffPort.countActiveDuenos(1)).thenReturn(Mono.just(1L));

            StepVerifier.create(service.desactivar(100, 1, "admin"))
                    .expectError(ConflictException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("activar usuario staff")
    class Activar {

        @Test
        @DisplayName("activa usuario staff exitosamente")
        void activaExitosamente() {
            when(staffPort.findByIdAndIdCompania(100, 1)).thenReturn(Mono.just(staffFijura));
            when(staffPort.save(any(UsuarioStaff.class))).thenReturn(Mono.just(staffFijura));

            StepVerifier.create(service.activar(100, 1, "admin"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ResourceNotFoundException cuando el usuario no existe")
        void lanzaExcepcionCuandoNoExiste() {
            when(staffPort.findByIdAndIdCompania(999, 1)).thenReturn(Mono.empty());

            StepVerifier.create(service.activar(999, 1, "admin"))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("resetear contrasena de usuario staff")
    class ResetPassword {

        @Test
        @DisplayName("resetea contrasena exitosamente")
        void resetPasswordExitosamente() {
            when(staffPort.findByIdAndIdCompania(100, 1)).thenReturn(Mono.just(staffFijura));
            when(encoder.encode("NuevaClave123!")).thenReturn("nuevohashed");
            when(staffPort.save(any(UsuarioStaff.class))).thenReturn(Mono.just(staffFijura));

            StepVerifier.create(service.resetPassword(100, 1, "NuevaClave123!", "admin"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ResourceNotFoundException cuando el usuario no existe")
        void lanzaExcepcionCuandoNoExiste() {
            when(staffPort.findByIdAndIdCompania(999, 1)).thenReturn(Mono.empty());

            StepVerifier.create(service.resetPassword(999, 1, "NuevaClave123!", "admin"))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }
    }
}
