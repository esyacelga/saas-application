package com.gymadmin.auth.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.auth.IntegrationTestBase;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.PersonaEntity;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.UsuarioAppEntity;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@DisplayName("UsuarioAppR2dbcRepository")
class UsuarioAppR2dbcRepositoryIT extends IntegrationTestBase {

    @Autowired
    private UsuarioAppR2dbcRepository repository;

    @Autowired
    private PersonaR2dbcRepository personaRepo;

    private PersonaEntity createTestPersona() {
        PersonaEntity persona = PersonaEntity.builder()
                .ci(UUID.randomUUID().toString().substring(0, 10))
                .nombre("App User Person")
                .correo("appuser" + UUID.randomUUID().toString().substring(0, 8) + "@example.com")
                .creacionFecha(OffsetDateTime.now())
                .creacionUsuario("test")
                .build();
        return personaRepo.save(persona).block();
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("guarda un nuevo usuario app en la base de datos")
        void save_nuevoUsuarioApp_seGuardaCorrectamente() {
            PersonaEntity persona = createTestPersona();
            String login = "appuser" + UUID.randomUUID().toString().substring(0, 8);

            UsuarioAppEntity usuario = UsuarioAppEntity.builder()
                    .idPersona(persona.getId())
                    .idCompania(ID_COMPANIA)
                    .login(login)
                    .passwordHash("hashedpassword")
                    .requiereCambioPwd(false)
                    .activo(true)
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(usuario)
                    .flatMap(saved -> repository.findById(saved.getId())))
                    .assertNext(retrieved -> {
                        assert retrieved.getLogin().equals(login);
                        assert retrieved.getIdPersona().equals(persona.getId());
                        assert retrieved.getIdCompania().equals(ID_COMPANIA);
                        assert retrieved.getActivo().equals(true);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByLoginAndIdCompania")
    class FindByLoginAndIdCompania {

        @Test
        @DisplayName("retorna el usuario cuando existe con ese login en la compañía")
        void findByLoginAndIdCompania_usuarioExiste_retornaMono() {
            PersonaEntity persona = createTestPersona();
            String login = "findlogin" + UUID.randomUUID().toString().substring(0, 8);

            UsuarioAppEntity usuario = UsuarioAppEntity.builder()
                    .idPersona(persona.getId())
                    .idCompania(ID_COMPANIA)
                    .login(login)
                    .passwordHash("hashedpassword")
                    .activo(true)
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(usuario)
                    .then(repository.findByLoginAndIdCompania(login, ID_COMPANIA)))
                    .assertNext(retrieved -> {
                        assert retrieved.getLogin().equals(login);
                        assert retrieved.getIdCompania().equals(ID_COMPANIA);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna Mono vacío cuando no existe usuario con ese login")
        void findByLoginAndIdCompania_usuarioNoExiste_retornaMonoVacio() {
            String loginNoExistente = "noexists" + UUID.randomUUID().toString().substring(0, 8);

            StepVerifier.create(repository.findByLoginAndIdCompania(loginNoExistente, ID_COMPANIA))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("existsByIdPersonaAndIdCompania")
    class ExistsByIdPersonaAndIdCompania {

        @Test
        @DisplayName("retorna true cuando existe usuario app para esa persona en la compañía")
        void existsByIdPersonaAndIdCompania_usuarioExiste_retornaTrue() {
            PersonaEntity persona = createTestPersona();

            UsuarioAppEntity usuario = UsuarioAppEntity.builder()
                    .idPersona(persona.getId())
                    .idCompania(ID_COMPANIA)
                    .login("exists" + UUID.randomUUID().toString().substring(0, 8))
                    .passwordHash("hashedpassword")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(usuario)
                    .then(repository.existsByIdPersonaAndIdCompania(persona.getId(), ID_COMPANIA)))
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna false cuando no existe usuario app para esa persona")
        void existsByIdPersonaAndIdCompania_usuarioNoExiste_retornaFalse() {
            int idPersonaNoExistente = 88888;

            StepVerifier.create(repository.existsByIdPersonaAndIdCompania(idPersonaNoExistente, ID_COMPANIA))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByTokenRecuperacion")
    class FindByTokenRecuperacion {

        @Test
        @DisplayName("retorna el usuario cuando existe con ese token de recuperación")
        void findByTokenRecuperacion_tokenExiste_retornaMono() {
            PersonaEntity persona = createTestPersona();
            String tokenRecuperacion = UUID.randomUUID().toString();

            UsuarioAppEntity usuario = UsuarioAppEntity.builder()
                    .idPersona(persona.getId())
                    .idCompania(ID_COMPANIA)
                    .login("token" + UUID.randomUUID().toString().substring(0, 8))
                    .passwordHash("hashedpassword")
                    .tokenRecuperacion(tokenRecuperacion)
                    .tokenExpira(OffsetDateTime.now().plusDays(1))
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(usuario)
                    .then(repository.findByTokenRecuperacion(tokenRecuperacion)))
                    .assertNext(retrieved -> {
                        assert retrieved.getTokenRecuperacion().equals(tokenRecuperacion);
                        assert retrieved.getIdPersona().equals(persona.getId());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna Mono vacío cuando no existe usuario con ese token")
        void findByTokenRecuperacion_tokenNoExiste_retornaMonoVacio() {
            String tokenNoExistente = UUID.randomUUID().toString();

            StepVerifier.create(repository.findByTokenRecuperacion(tokenNoExistente))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByIdPersona")
    class FindByIdPersona {

        @Test
        @DisplayName("retorna los usuarios app de una persona")
        void findByIdPersona_usuariosExisten_retornaFlux() {
            PersonaEntity persona = createTestPersona();

            UsuarioAppEntity usuario1 = UsuarioAppEntity.builder()
                    .idPersona(persona.getId())
                    .idCompania(ID_COMPANIA)
                    .login("persona1" + UUID.randomUUID().toString().substring(0, 8))
                    .passwordHash("hash1")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();
            UsuarioAppEntity usuario2 = UsuarioAppEntity.builder()
                    .idPersona(persona.getId())
                    .idCompania(ID_COMPANIA + 1)
                    .login("persona2" + UUID.randomUUID().toString().substring(0, 8))
                    .passwordHash("hash2")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(usuario1)
                    .then(repository.save(usuario2))
                    .thenMany(repository.findByIdPersona(persona.getId())))
                    .recordWith(java.util.ArrayList::new)
                    .thenConsumeWhile(u -> true)
                    .consumeRecordedWith(usuarios -> {
                        assert usuarios.size() >= 2 : "Debe haber al menos 2 usuarios app";
                        assert usuarios.stream().allMatch(u -> u.getIdPersona().equals(persona.getId()));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna lista vacía cuando la persona no tiene usuarios app")
        void findByIdPersona_sinUsuarios_retornaFluxVacio() {
            int idPersonaNoExistente = 88888;

            StepVerifier.create(repository.findByIdPersona(idPersonaNoExistente))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("deleteById")
    class DeleteById {

        @Test
        @DisplayName("elimina un usuario app por su id")
        void deleteById_usuarioExiste_seElimina() {
            PersonaEntity persona = createTestPersona();

            UsuarioAppEntity usuario = UsuarioAppEntity.builder()
                    .idPersona(persona.getId())
                    .idCompania(ID_COMPANIA)
                    .login("delete" + UUID.randomUUID().toString().substring(0, 8))
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
