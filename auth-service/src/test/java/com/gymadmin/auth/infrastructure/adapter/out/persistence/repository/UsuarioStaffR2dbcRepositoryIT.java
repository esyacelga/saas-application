package com.gymadmin.auth.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.auth.IntegrationTestBase;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.PersonaEntity;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.RolEntity;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.UsuarioStaffEntity;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@DisplayName("UsuarioStaffR2dbcRepository")
class UsuarioStaffR2dbcRepositoryIT extends IntegrationTestBase {

    @Autowired
    private UsuarioStaffR2dbcRepository repository;

    @Autowired
    private PersonaR2dbcRepository personaRepo;

    @Autowired
    private RolR2dbcRepository rolRepo;

    private PersonaEntity createTestPersona() {
        PersonaEntity persona = PersonaEntity.builder()
                .ci(UUID.randomUUID().toString().substring(0, 10))
                .nombre("Test Person")
                .correo("test" + UUID.randomUUID().toString().substring(0, 8) + "@example.com")
                .creacionFecha(OffsetDateTime.now())
                .creacionUsuario("test")
                .build();
        return personaRepo.save(persona).block();
    }

    private RolEntity createTestRol() {
        RolEntity rol = RolEntity.builder()
                .idCompania(ID_COMPANIA)
                .idSucursal(ID_SUCURSAL)
                .nombre("Rol-" + UUID.randomUUID().toString().substring(0, 8))
                .creacionFecha(OffsetDateTime.now())
                .creacionUsuario("test")
                .build();
        return rolRepo.save(rol).block();
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("guarda un nuevo usuario staff en la base de datos")
        void save_nuevoUsuarioStaff_seGuardaCorrectamente() {
            PersonaEntity persona = createTestPersona();
            RolEntity rol = createTestRol();
            String correo = "staff" + UUID.randomUUID().toString().substring(0, 8) + "@company.com";

            UsuarioStaffEntity usuario = UsuarioStaffEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idPersona(persona.getId())
                    .idRol(rol.getId())
                    .correo(correo)
                    .passwordHash("hashedpassword")
                    .requiereCambioPwd(false)
                    .activo(true)
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(usuario)
                    .flatMap(saved -> repository.findById(saved.getId())))
                    .assertNext(retrieved -> {
                        assert retrieved.getCorreo().equals(correo);
                        assert retrieved.getIdPersona().equals(persona.getId());
                        assert retrieved.getIdRol().equals(rol.getId());
                        assert retrieved.getActivo().equals(true);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByCorreoAndIdCompania")
    class FindByCorreoAndIdCompania {

        @Test
        @DisplayName("retorna el usuario cuando existe con ese correo en la compañía")
        void findByCorreoAndIdCompania_usuarioExiste_retornaMono() {
            PersonaEntity persona = createTestPersona();
            RolEntity rol = createTestRol();
            String correo = "find" + UUID.randomUUID().toString().substring(0, 8) + "@company.com";

            UsuarioStaffEntity usuario = UsuarioStaffEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idPersona(persona.getId())
                    .idRol(rol.getId())
                    .correo(correo)
                    .passwordHash("hashedpassword")
                    .activo(true)
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(usuario)
                    .then(repository.findByCorreoAndIdCompania(correo, ID_COMPANIA)))
                    .assertNext(retrieved -> {
                        assert retrieved.getCorreo().equals(correo);
                        assert retrieved.getIdCompania().equals(ID_COMPANIA);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna Mono vacío cuando no existe usuario con ese correo")
        void findByCorreoAndIdCompania_usuarioNoExiste_retornaMonoVacio() {
            String correoNoExistente = "noexiste" + UUID.randomUUID().toString().substring(0, 8) + "@company.com";

            StepVerifier.create(repository.findByCorreoAndIdCompania(correoNoExistente, ID_COMPANIA))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("existsByCorreoAndIdCompania")
    class ExistsByCorreoAndIdCompania {

        @Test
        @DisplayName("retorna true cuando existe usuario con ese correo en la compañía")
        void existsByCorreoAndIdCompania_usuarioExiste_retornaTrue() {
            PersonaEntity persona = createTestPersona();
            RolEntity rol = createTestRol();
            String correo = "exists" + UUID.randomUUID().toString().substring(0, 8) + "@company.com";

            UsuarioStaffEntity usuario = UsuarioStaffEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idPersona(persona.getId())
                    .idRol(rol.getId())
                    .correo(correo)
                    .passwordHash("hashedpassword")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(usuario)
                    .then(repository.existsByCorreoAndIdCompania(correo, ID_COMPANIA)))
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna false cuando no existe usuario con ese correo")
        void existsByCorreoAndIdCompania_usuarioNoExiste_retornaFalse() {
            String correoNoExistente = "nothere" + UUID.randomUUID().toString().substring(0, 8) + "@company.com";

            StepVerifier.create(repository.existsByCorreoAndIdCompania(correoNoExistente, ID_COMPANIA))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByIdCompania")
    class FindByIdCompania {

        @Test
        @DisplayName("retorna todos los usuarios staff de una compañía")
        void findByIdCompania_existenUsuarios_retornaFlux() {
            PersonaEntity persona1 = createTestPersona();
            PersonaEntity persona2 = createTestPersona();
            RolEntity rol = createTestRol();

            UsuarioStaffEntity usuario1 = UsuarioStaffEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idPersona(persona1.getId())
                    .idRol(rol.getId())
                    .correo("user1" + UUID.randomUUID().toString().substring(0, 8) + "@company.com")
                    .passwordHash("hash1")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();
            UsuarioStaffEntity usuario2 = UsuarioStaffEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idPersona(persona2.getId())
                    .idRol(rol.getId())
                    .correo("user2" + UUID.randomUUID().toString().substring(0, 8) + "@company.com")
                    .passwordHash("hash2")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(usuario1)
                    .then(repository.save(usuario2))
                    .thenMany(repository.findByIdCompania(ID_COMPANIA)))
                    .recordWith(java.util.ArrayList::new)
                    .thenConsumeWhile(u -> true)
                    .consumeRecordedWith(usuarios -> {
                        assert usuarios.size() >= 2 : "Debe haber al menos 2 usuarios";
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByIdAndIdCompania")
    class FindByIdAndIdCompania {

        @Test
        @DisplayName("retorna el usuario cuando existe para esa compañía")
        void findByIdAndIdCompania_usuarioExiste_retornaMono() {
            PersonaEntity persona = createTestPersona();
            RolEntity rol = createTestRol();
            String correo = "findbyid" + UUID.randomUUID().toString().substring(0, 8) + "@company.com";

            UsuarioStaffEntity usuario = UsuarioStaffEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idPersona(persona.getId())
                    .idRol(rol.getId())
                    .correo(correo)
                    .passwordHash("hashedpassword")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(usuario)
                    .flatMap(saved -> repository.findByIdAndIdCompania(saved.getId(), ID_COMPANIA)))
                    .assertNext(retrieved -> {
                        assert retrieved.getCorreo().equals(correo);
                        assert retrieved.getIdCompania().equals(ID_COMPANIA);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna Mono vacío cuando el usuario no existe para esa compañía")
        void findByIdAndIdCompania_usuarioNoExiste_retornaMonoVacio() {
            int idUsuarioNoExistente = 88888;

            StepVerifier.create(repository.findByIdAndIdCompania(idUsuarioNoExistente, ID_COMPANIA))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByIdPersona")
    class FindByIdPersona {

        @Test
        @DisplayName("retorna los usuarios staff de una persona")
        void findByIdPersona_usuariosExisten_retornaFlux() {
            PersonaEntity persona = createTestPersona();
            RolEntity rol = createTestRol();

            UsuarioStaffEntity usuario1 = UsuarioStaffEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idPersona(persona.getId())
                    .idRol(rol.getId())
                    .correo("persona1" + UUID.randomUUID().toString().substring(0, 8) + "@company.com")
                    .passwordHash("hash")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();
            UsuarioStaffEntity usuario2 = UsuarioStaffEntity.builder()
                    .idCompania(ID_COMPANIA + 1)
                    .idSucursal(ID_SUCURSAL)
                    .idPersona(persona.getId())
                    .idRol(rol.getId())
                    .correo("persona2" + UUID.randomUUID().toString().substring(0, 8) + "@company.com")
                    .passwordHash("hash")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(usuario1)
                    .then(repository.save(usuario2))
                    .thenMany(repository.findByIdPersona(persona.getId())))
                    .recordWith(java.util.ArrayList::new)
                    .thenConsumeWhile(u -> true)
                    .consumeRecordedWith(usuarios -> {
                        assert usuarios.size() >= 1 : "Debe haber al menos 1 usuario";
                        assert usuarios.stream().allMatch(u -> u.getIdPersona().equals(persona.getId()));
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("countActiveDuenos")
    class CountActiveDuenos {

        @Test
        @DisplayName("retorna 0 cuando no hay dueños activos en la compañía")
        void countActiveDuenos_sinDuenos_retorna0() {
            StepVerifier.create(repository.countActiveDuenos(ID_COMPANIA))
                    .expectNext(0L)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("existsByIdRolAndIdCompania")
    class ExistsByIdRolAndIdCompania {

        @Test
        @DisplayName("retorna true cuando existe usuario con ese rol en la compañía")
        void existsByIdRolAndIdCompania_usuarioExiste_retornaTrue() {
            PersonaEntity persona = createTestPersona();
            RolEntity rol = createTestRol();

            UsuarioStaffEntity usuario = UsuarioStaffEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idPersona(persona.getId())
                    .idRol(rol.getId())
                    .correo("roltest" + UUID.randomUUID().toString().substring(0, 8) + "@company.com")
                    .passwordHash("hashedpassword")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(usuario)
                    .then(repository.existsByIdRolAndIdCompania(rol.getId(), ID_COMPANIA)))
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna false cuando no existe usuario con ese rol")
        void existsByIdRolAndIdCompania_usuarioNoExiste_retornaFalse() {
            int idRolNoExistente = 88888;

            StepVerifier.create(repository.existsByIdRolAndIdCompania(idRolNoExistente, ID_COMPANIA))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("deleteById")
    class DeleteById {

        @Test
        @DisplayName("elimina un usuario staff por su id")
        void deleteById_usuarioExiste_seElimina() {
            PersonaEntity persona = createTestPersona();
            RolEntity rol = createTestRol();

            UsuarioStaffEntity usuario = UsuarioStaffEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idPersona(persona.getId())
                    .idRol(rol.getId())
                    .correo("delete" + UUID.randomUUID().toString().substring(0, 8) + "@company.com")
                    .passwordHash("hashedpassword")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(usuario)
                    .flatMap(saved -> repository.deleteById(saved.getId())
                            .then(repository.findById(saved.getId()))))
                    .verifyComplete();
        }
    }
}
