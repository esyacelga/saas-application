package com.gymadmin.auth.unit;

import com.gymadmin.auth.application.service.PermisoApplicationService;
import com.gymadmin.auth.domain.model.Permiso;
import com.gymadmin.auth.domain.port.out.PermisoPort;
import com.gymadmin.auth.domain.port.out.RolPermisoPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PermisoApplicationService")
class PermisoApplicationServiceTest {

    @Mock
    private PermisoPort permisoPort;

    @Mock
    private RolPermisoPort rolPermisoPort;

    @InjectMocks
    private PermisoApplicationService service;

    private Permiso permisoUno;
    private Permiso permisoDos;

    @BeforeEach
    void setUp() {
        permisoUno = Permiso.builder()
                .id(1)
                .idCompania(10)
                .nombre("miembros:listar")
                .modulo("miembros")
                .descripcion("Listar miembros del gimnasio")
                .build();

        permisoDos = Permiso.builder()
                .id(2)
                .idCompania(10)
                .nombre("miembros:crear")
                .modulo("miembros")
                .descripcion("Crear miembros")
                .build();
    }

    @Nested
    @DisplayName("listar permisos por compania")
    class ListarPorCompania {

        @Test
        @DisplayName("retorna flux de permisos cuando la compania tiene permisos")
        void retornaPermisosExistentes() {
            when(permisoPort.findByIdCompania(10)).thenReturn(Flux.just(permisoUno, permisoDos));

            StepVerifier.create(service.listarPorCompania(10))
                    .expectNextMatches(r -> r.id().equals(1) && r.nombre().equals("miembros:listar"))
                    .expectNextMatches(r -> r.id().equals(2) && r.nombre().equals("miembros:crear"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna flux vacio cuando la compania no tiene permisos")
        void retornaFluxVacioCuandoNoHayPermisos() {
            when(permisoPort.findByIdCompania(10)).thenReturn(Flux.empty());

            StepVerifier.create(service.listarPorCompania(10))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("listar permisos por rol")
    class ListarPorRol {

        @Test
        @DisplayName("retorna permisos del rol filtrados por compania")
        void retornaPermisosDelRolEnCompania() {
            Permiso permisoOtraCompania = Permiso.builder()
                    .id(99).idCompania(99).nombre("otro:permiso").modulo("otro").build();

            when(rolPermisoPort.findPermisosWithDetailByIdRol(5))
                    .thenReturn(Flux.just(permisoUno, permisoDos, permisoOtraCompania));

            // Solo deben pasar los que tienen idCompania == 10
            StepVerifier.create(service.listarPorRol(5, 10))
                    .expectNextMatches(r -> r.id().equals(1))
                    .expectNextMatches(r -> r.id().equals(2))
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna flux vacio cuando el rol no tiene permisos")
        void retornaFluxVacioCuandoRolSinPermisos() {
            when(rolPermisoPort.findPermisosWithDetailByIdRol(5)).thenReturn(Flux.empty());

            StepVerifier.create(service.listarPorRol(5, 10))
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna flux vacio cuando ninguno de los permisos pertenece a la compania")
        void retornaVacioCuandoNingunPermisoDeCompania() {
            Permiso permisoOtraCompania = Permiso.builder()
                    .id(99).idCompania(99).nombre("otro:permiso").modulo("otro").build();

            when(rolPermisoPort.findPermisosWithDetailByIdRol(5))
                    .thenReturn(Flux.just(permisoOtraCompania));

            StepVerifier.create(service.listarPorRol(5, 10))
                    .verifyComplete();
        }
    }
}
