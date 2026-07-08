package com.gymadmin.auth.unit;

import com.gymadmin.auth.application.service.RolApplicationService;
import com.gymadmin.auth.domain.exception.ConflictException;
import com.gymadmin.auth.domain.exception.ResourceNotFoundException;
import com.gymadmin.auth.domain.model.Permiso;
import com.gymadmin.auth.domain.model.Rol;
import com.gymadmin.auth.domain.port.out.PermisoPort;
import com.gymadmin.auth.domain.port.out.RolPermisoPort;
import com.gymadmin.auth.domain.port.out.RolPort;
import com.gymadmin.auth.domain.port.out.UsuarioStaffPort;
import com.gymadmin.auth.dto.request.CreateRolRequest;
import com.gymadmin.auth.dto.request.UpdateRolPermisosRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RolApplicationService")
class RolApplicationServiceTest {

    @Mock
    private RolPort rolPort;

    @Mock
    private PermisoPort permisoPort;

    @Mock
    private RolPermisoPort rolPermisoPort;

    @Mock
    private UsuarioStaffPort staffPort;

    @InjectMocks
    private RolApplicationService service;

    private Rol rolFijura;
    private Permiso permisoFijura;

    @BeforeEach
    void setUp() {
        rolFijura = Rol.builder()
                .id(1)
                .idCompania(10)
                .nombre("Entrenador")
                .descripcion("Rol de entrenador")
                .build();

        permisoFijura = Permiso.builder()
                .id(1)
                .idCompania(10)
                .nombre("miembros:listar")
                .modulo("miembros")
                .descripcion("Listar miembros")
                .build();
    }

    @Nested
    @DisplayName("listar roles por compania")
    class ListarPorCompania {

        @Test
        @DisplayName("retorna flux de roles cuando existen")
        void retornaRolesCuandoExisten() {
            when(rolPort.findByIdCompania(10)).thenReturn(Flux.just(rolFijura));

            StepVerifier.create(service.listarPorCompania(10))
                    .expectNextMatches(r -> r.id().equals(1) && r.nombre().equals("Entrenador"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna flux vacio cuando no hay roles")
        void retornaFluxVacioCuandoNoHayRoles() {
            when(rolPort.findByIdCompania(10)).thenReturn(Flux.empty());

            StepVerifier.create(service.listarPorCompania(10))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("buscar rol por id")
    class BuscarPorId {

        @Test
        @DisplayName("retorna rol cuando existe en la compania")
        void retornaRolCuandoExiste() {
            when(rolPort.findByIdAndIdCompania(1, 10)).thenReturn(Mono.just(rolFijura));

            StepVerifier.create(service.buscarPorId(1, 10))
                    .expectNextMatches(r -> r.id().equals(1) && r.nombre().equals("Entrenador"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ResourceNotFoundException cuando el rol no existe")
        void lanzaExcepcionCuandoNoExiste() {
            when(rolPort.findByIdAndIdCompania(999, 10)).thenReturn(Mono.empty());

            StepVerifier.create(service.buscarPorId(999, 10))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("crear rol")
    class Crear {

        @Test
        @DisplayName("crea rol exitosamente cuando el nombre no existe")
        void creaRolExitosamente() {
            CreateRolRequest req = new CreateRolRequest("Nutricionista", "Rol nutricionista", 10, 1);
            Rol rolNuevo = Rol.builder()
                    .id(2)
                    .idCompania(10)
                    .nombre("Nutricionista")
                    .descripcion("Rol nutricionista")
                    .build();

            when(rolPort.existsByIdCompaniaAndNombre(10, "Nutricionista")).thenReturn(Mono.just(false));
            when(rolPort.save(any(Rol.class))).thenReturn(Mono.just(rolNuevo));

            StepVerifier.create(service.crear(10, 1, req, "admin"))
                    .expectNextMatches(r -> r.nombre().equals("Nutricionista"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ConflictException cuando el nombre del rol ya existe en la compania")
        void lanzaConflictCuandoNombreDuplicado() {
            CreateRolRequest req = new CreateRolRequest("Entrenador", "Rol existente", 10, 1);
            when(rolPort.existsByIdCompaniaAndNombre(10, "Entrenador")).thenReturn(Mono.just(true));

            StepVerifier.create(service.crear(10, 1, req, "admin"))
                    .expectError(ConflictException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("ver permisos de rol")
    class VerPermisos {

        @Test
        @DisplayName("retorna rol con sus permisos cuando existe")
        void retornaRolConPermisosExitosamente() {
            when(rolPort.findByIdAndIdCompania(1, 10)).thenReturn(Mono.just(rolFijura));
            when(rolPermisoPort.findPermisosWithDetailByIdRol(1)).thenReturn(Flux.just(permisoFijura));

            StepVerifier.create(service.verPermisos(1, 10))
                    .expectNextMatches(r ->
                            r.rol().id().equals(1)
                                    && r.permisos().size() == 1
                                    && r.permisos().get(0).nombre().equals("miembros:listar")
                    )
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ResourceNotFoundException cuando el rol no existe")
        void lanzaExcepcionCuandoRolNoExiste() {
            when(rolPort.findByIdAndIdCompania(999, 10)).thenReturn(Mono.empty());

            StepVerifier.create(service.verPermisos(999, 10))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("actualizar permisos de rol")
    class ActualizarPermisos {

        @Test
        @DisplayName("actualiza permisos exitosamente cuando todos pertenecen a la compania")
        void actualizaPermisosExitosamente() {
            UpdateRolPermisosRequest req = new UpdateRolPermisosRequest(List.of(1, 2));
            Permiso permiso2 = Permiso.builder()
                    .id(2).idCompania(10).nombre("miembros:editar").modulo("miembros").build();

            when(rolPort.findByIdAndIdCompania(1, 10)).thenReturn(Mono.just(rolFijura));
            when(permisoPort.findByIdInAndIdCompania(List.of(1, 2), 10))
                    .thenReturn(Flux.just(permisoFijura, permiso2));
            when(rolPermisoPort.deleteByIdRol(1)).thenReturn(Mono.empty());
            when(rolPermisoPort.saveAll(1, List.of(1, 2), "admin")).thenReturn(Mono.empty());

            StepVerifier.create(service.actualizarPermisos(1, 10, req, "admin"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ResourceNotFoundException cuando el rol no existe")
        void lanzaExcepcionCuandoRolNoExiste() {
            UpdateRolPermisosRequest req = new UpdateRolPermisosRequest(List.of(1));
            when(rolPort.findByIdAndIdCompania(999, 10)).thenReturn(Mono.empty());

            StepVerifier.create(service.actualizarPermisos(999, 10, req, "admin"))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza IllegalArgumentException cuando algun permiso no pertenece a la compania")
        void lanzaExcepcionCuandoPermisosFueraDeCompania() {
            UpdateRolPermisosRequest req = new UpdateRolPermisosRequest(List.of(1, 2));
            when(rolPort.findByIdAndIdCompania(1, 10)).thenReturn(Mono.just(rolFijura));
            // Solo devuelve 1 permiso en vez de 2
            when(permisoPort.findByIdInAndIdCompania(List.of(1, 2), 10))
                    .thenReturn(Flux.just(permisoFijura));

            StepVerifier.create(service.actualizarPermisos(1, 10, req, "admin"))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("eliminar rol")
    class Eliminar {

        @Test
        @DisplayName("elimina rol exitosamente cuando no tiene usuarios asignados")
        void eliminaRolExitosamente() {
            when(rolPort.findByIdAndIdCompania(1, 10)).thenReturn(Mono.just(rolFijura));
            when(staffPort.existsByIdRolInCompania(1, 10)).thenReturn(Mono.just(false));
            when(rolPermisoPort.deleteByIdRol(1)).thenReturn(Mono.empty());
            when(rolPort.deleteById(1)).thenReturn(Mono.empty());

            StepVerifier.create(service.eliminar(1, 10))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ConflictException cuando el rol tiene usuarios asignados")
        void lanzaConflictCuandoTieneUsuarios() {
            when(rolPort.findByIdAndIdCompania(1, 10)).thenReturn(Mono.just(rolFijura));
            when(staffPort.existsByIdRolInCompania(1, 10)).thenReturn(Mono.just(true));

            StepVerifier.create(service.eliminar(1, 10))
                    .expectError(ConflictException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza ResourceNotFoundException cuando el rol no existe")
        void lanzaExcepcionCuandoRolNoExiste() {
            when(rolPort.findByIdAndIdCompania(999, 10)).thenReturn(Mono.empty());

            StepVerifier.create(service.eliminar(999, 10))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }
    }
}
