package com.gymadmin.auth.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.auth.IntegrationTestBase;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.RolEntity;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@DisplayName("RolR2dbcRepository")
class RolR2dbcRepositoryIT extends IntegrationTestBase {

    @Autowired
    private RolR2dbcRepository repository;

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("guarda un nuevo rol en la base de datos")
        void save_nuevoRol_seGuardaCorrectamente() {
            RolEntity rol = RolEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .nombre("Rol-" + UUID.randomUUID().toString().substring(0, 8))
                    .descripcion("Descripción de test")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(rol)
                    .flatMap(saved -> repository.findByIdAndIdCompania(saved.getId(), ID_COMPANIA)))
                    .assertNext(retrieved -> {
                        assert retrieved.getNombre().equals(rol.getNombre());
                        assert retrieved.getDescripcion().equals(rol.getDescripcion());
                        assert retrieved.getIdCompania().equals(ID_COMPANIA);
                        assert retrieved.getIdSucursal().equals(ID_SUCURSAL);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByIdCompania")
    class FindByIdCompania {

        @Test
        @DisplayName("retorna todos los roles de una compañía")
        void findByIdCompania_existenRoles_retornaFlux() {
            RolEntity rol1 = RolEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .nombre("RolA-" + UUID.randomUUID().toString().substring(0, 8))
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();
            RolEntity rol2 = RolEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .nombre("RolB-" + UUID.randomUUID().toString().substring(0, 8))
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(rol1)
                    .then(repository.save(rol2))
                    .thenMany(repository.findByIdCompania(ID_COMPANIA)))
                    .recordWith(java.util.ArrayList::new)
                    .thenConsumeWhile(r -> true)
                    .consumeRecordedWith(roles -> {
                        assert roles.size() >= 2 : "Debe haber al menos 2 roles";
                        assert roles.stream().anyMatch(r -> r.getNombre().equals(rol1.getNombre()));
                        assert roles.stream().anyMatch(r -> r.getNombre().equals(rol2.getNombre()));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna lista vacía para una compañía sin roles")
        void findByIdCompania_sinRoles_retornaFluxVacio() {
            int idCompaniaNoExistente = 88888;

            StepVerifier.create(repository.findByIdCompania(idCompaniaNoExistente))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByIdAndIdCompania")
    class FindByIdAndIdCompania {

        @Test
        @DisplayName("retorna el rol cuando existe para esa compañía")
        void findByIdAndIdCompania_rolExiste_retornaMono() {
            RolEntity rol = RolEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .nombre("RolBuscar-" + UUID.randomUUID().toString().substring(0, 8))
                    .descripcion("Para buscar por id")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(rol)
                    .flatMap(saved -> repository.findByIdAndIdCompania(saved.getId(), ID_COMPANIA)))
                    .assertNext(retrieved -> {
                        assert retrieved.getId().equals(rol.getId());
                        assert retrieved.getNombre().equals(rol.getNombre());
                        assert retrieved.getDescripcion().equals("Para buscar por id");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna Mono vacío cuando el rol no existe para esa compañía")
        void findByIdAndIdCompania_rolNoExiste_retornaMonoVacio() {
            int idRolNoExistente = 99999;

            StepVerifier.create(repository.findByIdAndIdCompania(idRolNoExistente, ID_COMPANIA))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("existsByIdCompaniaAndNombre")
    class ExistsByIdCompaniaAndNombre {

        @Test
        @DisplayName("retorna true cuando el rol existe con ese nombre")
        void existsByIdCompaniaAndNombre_rolExiste_retornaTrue() {
            String nombreRol = "RolExistente-" + UUID.randomUUID().toString().substring(0, 8);
            RolEntity rol = RolEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .nombre(nombreRol)
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(rol)
                    .then(repository.existsByIdCompaniaAndNombre(ID_COMPANIA, nombreRol)))
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna false cuando el rol no existe con ese nombre")
        void existsByIdCompaniaAndNombre_rolNoExiste_retornaFalse() {
            String nombreNoExistente = "NoExiste-" + UUID.randomUUID().toString().substring(0, 8);

            StepVerifier.create(repository.existsByIdCompaniaAndNombre(ID_COMPANIA, nombreNoExistente))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("deleteById")
    class DeleteById {

        @Test
        @DisplayName("elimina un rol por su id")
        void deleteById_rolExiste_seElimina() {
            RolEntity rol = RolEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .nombre("RolEliminar-" + UUID.randomUUID().toString().substring(0, 8))
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(rol)
                    .flatMap(saved -> repository.deleteById(saved.getId())
                            .then(repository.findById(saved.getId()))))
                    .verifyComplete();
        }
    }
}
