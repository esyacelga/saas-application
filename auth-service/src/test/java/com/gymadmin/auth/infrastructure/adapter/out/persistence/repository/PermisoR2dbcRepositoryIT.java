package com.gymadmin.auth.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.auth.IntegrationTestBase;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.PermisoEntity;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@DisplayName("PermisoR2dbcRepository")
class PermisoR2dbcRepositoryIT extends IntegrationTestBase {

    @Autowired
    private PermisoR2dbcRepository repository;

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("guarda un nuevo permiso en la base de datos")
        void save_nuevoPermiso_seGuardaCorrectamente() {
            PermisoEntity permiso = PermisoEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .nombre("Permiso-" + UUID.randomUUID().toString().substring(0, 8))
                    .descripcion("Descripción de test")
                    .modulo("test")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(permiso)
                    .flatMap(saved -> repository.findById(saved.getId())))
                    .assertNext(retrieved -> {
                        assert retrieved.getNombre().equals(permiso.getNombre());
                        assert retrieved.getModulo().equals("test");
                        assert retrieved.getIdCompania().equals(ID_COMPANIA);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByIdCompania")
    class FindByIdCompania {

        @Test
        @DisplayName("retorna todos los permisos de una compañía")
        void findByIdCompania_existenPermisos_retornaFlux() {
            PermisoEntity perm1 = PermisoEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .nombre("PermA-" + UUID.randomUUID().toString().substring(0, 8))
                    .modulo("modulo1")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();
            PermisoEntity perm2 = PermisoEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .nombre("PermB-" + UUID.randomUUID().toString().substring(0, 8))
                    .modulo("modulo2")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(perm1)
                    .then(repository.save(perm2))
                    .thenMany(repository.findByIdCompania(ID_COMPANIA)))
                    .recordWith(java.util.ArrayList::new)
                    .thenConsumeWhile(p -> true)
                    .consumeRecordedWith(permisos -> {
                        assert permisos.size() >= 2 : "Debe haber al menos 2 permisos";
                        assert permisos.stream().anyMatch(p -> p.getNombre().equals(perm1.getNombre()));
                        assert permisos.stream().anyMatch(p -> p.getNombre().equals(perm2.getNombre()));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna lista vacía para una compañía sin permisos")
        void findByIdCompania_sinPermisos_retornaFluxVacio() {
            int idCompaniaNoExistente = 88888;

            StepVerifier.create(repository.findByIdCompania(idCompaniaNoExistente))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByIdInAndIdCompania")
    class FindByIdInAndIdCompania {

        @Test
        @DisplayName("retorna permisos cuyos ids están en la colección")
        void findByIdInAndIdCompania_idsExisten_retornaFlux() {
            PermisoEntity perm1 = PermisoEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .nombre("Perm1-" + UUID.randomUUID().toString().substring(0, 8))
                    .modulo("m1")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();
            PermisoEntity perm2 = PermisoEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .nombre("Perm2-" + UUID.randomUUID().toString().substring(0, 8))
                    .modulo("m2")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(perm1)
                    .then(repository.save(perm2))
                    .flatMapMany(saved -> repository.findByIdInAndIdCompania(
                            List.of(perm1.getId(), perm2.getId()), ID_COMPANIA)))
                    .recordWith(java.util.ArrayList::new)
                    .thenConsumeWhile(p -> true)
                    .consumeRecordedWith(permisos -> {
                        assert permisos.size() >= 2 : "Debe retornar al menos 2 permisos";
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna lista vacía cuando no hay ids coincidentes")
        void findByIdInAndIdCompania_sinCoincidencias_retornaFluxVacio() {
            List<Integer> idsNoExistentes = List.of(88888, 88889);

            StepVerifier.create(repository.findByIdInAndIdCompania(idsNoExistentes, ID_COMPANIA))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByIdAndEliminadoFalse")
    class FindByIdAndEliminadoFalse {

        @Test
        @DisplayName("retorna el permiso cuando no está eliminado")
        void findByIdAndEliminadoFalse_noEliminado_retornaMono() {
            PermisoEntity permiso = PermisoEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .nombre("PermNoElim-" + UUID.randomUUID().toString().substring(0, 8))
                    .modulo("test")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(repository.save(permiso)
                    .flatMap(saved -> repository.findByIdAndEliminadoFalse(saved.getId())))
                    .assertNext(retrieved -> {
                        assert retrieved.getId().equals(permiso.getId());
                        assert retrieved.getEliminado() == false;
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna Mono vacío cuando el permiso está eliminado")
        void findByIdAndEliminadoFalse_eliminado_retornaMonoVacio() {
            PermisoEntity permiso = PermisoEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .nombre("PermElim-" + UUID.randomUUID().toString().substring(0, 8))
                    .modulo("test")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .eliminado(true)
                    .build();

            StepVerifier.create(repository.save(permiso)
                    .flatMap(saved -> repository.findByIdAndEliminadoFalse(saved.getId())))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("existsByIdCompaniaAndNombreAndEliminadoFalse")
    class ExistsByIdCompaniaAndNombreAndEliminadoFalse {

        @Test
        @DisplayName("retorna true cuando el permiso existe y no está eliminado")
        void existsByIdCompaniaAndNombreAndEliminadoFalse_noEliminado_retornaTrue() {
            String nombrePermiso = "Permiso-" + UUID.randomUUID().toString().substring(0, 8);
            PermisoEntity permiso = PermisoEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .nombre(nombrePermiso)
                    .modulo("test")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(repository.save(permiso)
                    .then(repository.existsByIdCompaniaAndNombreAndEliminadoFalse(ID_COMPANIA, nombrePermiso)))
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna false cuando el permiso está eliminado")
        void existsByIdCompaniaAndNombreAndEliminadoFalse_eliminado_retornaFalse() {
            String nombrePermiso = "PermElim-" + UUID.randomUUID().toString().substring(0, 8);
            PermisoEntity permiso = PermisoEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .nombre(nombrePermiso)
                    .modulo("test")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .eliminado(true)
                    .build();

            StepVerifier.create(repository.save(permiso)
                    .then(repository.existsByIdCompaniaAndNombreAndEliminadoFalse(ID_COMPANIA, nombrePermiso)))
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna false cuando el permiso no existe")
        void existsByIdCompaniaAndNombreAndEliminadoFalse_noExiste_retornaFalse() {
            String nombreNoExistente = "NoExiste-" + UUID.randomUUID().toString().substring(0, 8);

            StepVerifier.create(repository.existsByIdCompaniaAndNombreAndEliminadoFalse(ID_COMPANIA, nombreNoExistente))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("deleteById")
    class DeleteById {

        @Test
        @DisplayName("elimina un permiso por su id")
        void deleteById_permisoExiste_seElimina() {
            PermisoEntity permiso = PermisoEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .nombre("PermDel-" + UUID.randomUUID().toString().substring(0, 8))
                    .modulo("test")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(permiso)
                    .flatMap(saved -> repository.deleteById(saved.getId())
                            .then(repository.findById(saved.getId()))))
                    .verifyComplete();
        }
    }
}
