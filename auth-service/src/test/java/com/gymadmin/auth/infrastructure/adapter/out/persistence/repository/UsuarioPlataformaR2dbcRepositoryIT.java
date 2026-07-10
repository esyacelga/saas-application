package com.gymadmin.auth.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.auth.IntegrationTestBase;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.PersonaEntity;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.UsuarioPlataformaEntity;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@DisplayName("UsuarioPlataformaR2dbcRepository")
class UsuarioPlataformaR2dbcRepositoryIT extends IntegrationTestBase {

    @Autowired
    private UsuarioPlataformaR2dbcRepository repository;

    @Autowired
    private PersonaR2dbcRepository personaRepo;

    private PersonaEntity createTestPersona() {
        PersonaEntity persona = PersonaEntity.builder()
                .ci(UUID.randomUUID().toString().substring(0, 10))
                .nombre("Platform User Person")
                .correo("plat" + UUID.randomUUID().toString().substring(0, 8) + "@example.com")
                .creacionFecha(OffsetDateTime.now())
                .creacionUsuario("test")
                .build();
        return personaRepo.save(persona).block();
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("guarda un nuevo usuario plataforma en la base de datos")
        void save_nuevoUsuarioPlataforma_seGuardaCorrectamente() {
            PersonaEntity persona = createTestPersona();
            String correo = "platform" + UUID.randomUUID().toString().substring(0, 8) + "@platform.com";

            UsuarioPlataformaEntity usuario = UsuarioPlataformaEntity.builder()
                    .idPersona(persona.getId())
                    .correo(correo)
                    .passwordHash("hashedpassword")
                    .rol("super_admin")
                    .activo(true)
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(usuario)
                    .flatMap(saved -> repository.findById(saved.getId())))
                    .assertNext(retrieved -> {
                        assert retrieved.getCorreo().equals(correo);
                        assert retrieved.getIdPersona().equals(persona.getId());
                        assert retrieved.getRol().equals("super_admin");
                        assert retrieved.getActivo().equals(true);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByCorreo")
    class FindByCorreo {

        @Test
        @DisplayName("retorna el usuario cuando existe con ese correo")
        void findByCorreo_usuarioExiste_retornaMono() {
            PersonaEntity persona = createTestPersona();
            String correo = "find" + UUID.randomUUID().toString().substring(0, 8) + "@platform.com";

            UsuarioPlataformaEntity usuario = UsuarioPlataformaEntity.builder()
                    .idPersona(persona.getId())
                    .correo(correo)
                    .passwordHash("hashedpassword")
                    .rol("soporte")
                    .activo(true)
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(usuario)
                    .then(repository.findByCorreo(correo)))
                    .assertNext(retrieved -> {
                        assert retrieved.getCorreo().equals(correo);
                        assert retrieved.getIdPersona().equals(persona.getId());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna Mono vacío cuando no existe usuario con ese correo")
        void findByCorreo_usuarioNoExiste_retornaMonoVacio() {
            String correoNoExistente = "notfound" + UUID.randomUUID().toString().substring(0, 8) + "@platform.com";

            StepVerifier.create(repository.findByCorreo(correoNoExistente))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("existsByCorreo")
    class ExistsByCorreo {

        @Test
        @DisplayName("retorna true cuando existe usuario con ese correo")
        void existsByCorreo_usuarioExiste_retornaTrue() {
            PersonaEntity persona = createTestPersona();
            String correo = "exists" + UUID.randomUUID().toString().substring(0, 8) + "@platform.com";

            UsuarioPlataformaEntity usuario = UsuarioPlataformaEntity.builder()
                    .idPersona(persona.getId())
                    .correo(correo)
                    .passwordHash("hashedpassword")
                    .rol("viewer")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(usuario)
                    .then(repository.existsByCorreo(correo)))
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna false cuando no existe usuario con ese correo")
        void existsByCorreo_usuarioNoExiste_retornaFalse() {
            String correoNoExistente = "notexists" + UUID.randomUUID().toString().substring(0, 8) + "@platform.com";

            StepVerifier.create(repository.existsByCorreo(correoNoExistente))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByIdPersona")
    class FindByIdPersona {

        @Test
        @DisplayName("retorna el usuario plataforma de una persona")
        void findByIdPersona_usuariosExisten_retornaFlux() {
            PersonaEntity persona = createTestPersona();

            UsuarioPlataformaEntity usuario = UsuarioPlataformaEntity.builder()
                    .idPersona(persona.getId())
                    .correo("persona" + UUID.randomUUID().toString().substring(0, 8) + "@platform.com")
                    .passwordHash("hash")
                    .rol("super_admin")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(usuario)
                    .thenMany(repository.findByIdPersona(persona.getId())))
                    .recordWith(java.util.ArrayList::new)
                    .thenConsumeWhile(u -> true)
                    .consumeRecordedWith(usuarios -> {
                        assert usuarios.size() >= 1 : "Debe haber al menos 1 usuario plataforma";
                        assert usuarios.stream().allMatch(u -> u.getIdPersona().equals(persona.getId()));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna lista vacía cuando la persona no tiene usuarios plataforma")
        void findByIdPersona_sinUsuarios_retornaFluxVacio() {
            int idPersonaNoExistente = 88888;

            StepVerifier.create(repository.findByIdPersona(idPersonaNoExistente))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("countByRolAndActivoTrue")
    class CountByRolAndActivoTrue {

        @Test
        @DisplayName("retorna el conteo de usuarios activos con un rol específico")
        void countByRolAndActivoTrue_usuariosActivos_retornaLong() {
            PersonaEntity persona1 = createTestPersona();
            PersonaEntity persona2 = createTestPersona();
            String rol = "super_admin";

            UsuarioPlataformaEntity usuario1 = UsuarioPlataformaEntity.builder()
                    .idPersona(persona1.getId())
                    .correo("active1" + UUID.randomUUID().toString().substring(0, 8) + "@platform.com")
                    .passwordHash("hash1")
                    .rol(rol)
                    .activo(true)
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();
            UsuarioPlataformaEntity usuario2 = UsuarioPlataformaEntity.builder()
                    .idPersona(persona2.getId())
                    .correo("active2" + UUID.randomUUID().toString().substring(0, 8) + "@platform.com")
                    .passwordHash("hash2")
                    .rol(rol)
                    .activo(true)
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(usuario1)
                    .then(repository.save(usuario2))
                    .then(repository.countByRolAndActivoTrue(rol)))
                    .assertNext(count -> {
                        assert count >= 2 : "Debe contar al menos 2 usuarios activos";
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna 0 cuando no hay usuarios activos con ese rol")
        void countByRolAndActivoTrue_noHayActivos_retorna0() {
            StepVerifier.create(repository.countByRolAndActivoTrue("inexistentrol"))
                    .expectNext(0L)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("deleteById")
    class DeleteById {

        @Test
        @DisplayName("elimina un usuario plataforma por su id")
        void deleteById_usuarioExiste_seElimina() {
            PersonaEntity persona = createTestPersona();

            UsuarioPlataformaEntity usuario = UsuarioPlataformaEntity.builder()
                    .idPersona(persona.getId())
                    .correo("delete" + UUID.randomUUID().toString().substring(0, 8) + "@platform.com")
                    .passwordHash("hashedpassword")
                    .rol("viewer")
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
