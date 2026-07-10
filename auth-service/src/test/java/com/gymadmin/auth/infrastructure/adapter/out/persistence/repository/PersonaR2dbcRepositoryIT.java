package com.gymadmin.auth.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.auth.IntegrationTestBase;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.PersonaEntity;
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
@DisplayName("PersonaR2dbcRepository")
class PersonaR2dbcRepositoryIT extends IntegrationTestBase {

    @Autowired
    private PersonaR2dbcRepository repository;

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("guarda una nueva persona en la base de datos")
        void save_nuevaPersona_seGuardaCorrectamente() {
            String ci = UUID.randomUUID().toString().substring(0, 10);
            PersonaEntity persona = PersonaEntity.builder()
                    .ci(ci)
                    .nombre("Juan Pérez")
                    .telefono("0987654321")
                    .correo("juan@example.com")
                    .sexo("M")
                    .fechaNacimiento(LocalDate.of(1990, 5, 15))
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(persona)
                    .flatMap(saved -> repository.findById(saved.getId())))
                    .assertNext(retrieved -> {
                        assert retrieved.getCi().equals(ci);
                        assert retrieved.getNombre().equals("Juan Pérez");
                        assert retrieved.getTelefono().equals("0987654321");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByCi")
    class FindByCi {

        @Test
        @DisplayName("retorna la persona cuando existe con ese CI")
        void findByCi_personaExiste_retornaMono() {
            String ci = UUID.randomUUID().toString().substring(0, 10);
            PersonaEntity persona = PersonaEntity.builder()
                    .ci(ci)
                    .nombre("María García")
                    .correo("maria@example.com")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(persona)
                    .then(repository.findByCi(ci)))
                    .assertNext(retrieved -> {
                        assert retrieved.getCi().equals(ci);
                        assert retrieved.getNombre().equals("María García");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna Mono vacío cuando no existe persona con ese CI")
        void findByCi_personaNoExiste_retornaMonoVacio() {
            String ciNoExistente = "9999999999";

            StepVerifier.create(repository.findByCi(ciNoExistente))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("existsByCi")
    class ExistsByCi {

        @Test
        @DisplayName("retorna true cuando existe persona con ese CI")
        void existsByCi_personaExiste_retornaTrue() {
            String ci = UUID.randomUUID().toString().substring(0, 10);
            PersonaEntity persona = PersonaEntity.builder()
                    .ci(ci)
                    .nombre("Carlos López")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(persona)
                    .then(repository.existsByCi(ci)))
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna false cuando no existe persona con ese CI")
        void existsByCi_personaNoExiste_retornaFalse() {
            String ciNoExistente = "8888888888";

            StepVerifier.create(repository.existsByCi(ciNoExistente))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("existsByCiAndIdNot")
    class ExistsByCiAndIdNot {

        @Test
        @DisplayName("retorna false cuando el CI pertenece a la misma persona")
        void existsByCiAndIdNot_mismaPersona_retornaFalse() {
            String ci = UUID.randomUUID().toString().substring(0, 10);
            PersonaEntity persona = PersonaEntity.builder()
                    .ci(ci)
                    .nombre("Ana Rodríguez")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(persona)
                    .flatMap(saved -> repository.existsByCiAndIdNot(ci, saved.getId())))
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna true cuando el CI pertenece a otra persona")
        void existsByCiAndIdNot_otraPersona_retornaTrue() {
            String ci = UUID.randomUUID().toString().substring(0, 10);
            PersonaEntity persona1 = PersonaEntity.builder()
                    .ci(ci)
                    .nombre("Persona 1")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();
            PersonaEntity persona2 = PersonaEntity.builder()
                    .ci(UUID.randomUUID().toString().substring(0, 10))
                    .nombre("Persona 2")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(persona1)
                    .then(repository.save(persona2))
                    .flatMap(p2 -> repository.existsByCiAndIdNot(ci, p2.getId())))
                    .expectNext(true)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByCorreo")
    class FindByCorreo {

        @Test
        @DisplayName("retorna la persona cuando existe con ese correo")
        void findByCorreo_personaExiste_retornaMono() {
            String correo = UUID.randomUUID().toString().substring(0, 10) + "@example.com";
            PersonaEntity persona = PersonaEntity.builder()
                    .ci(UUID.randomUUID().toString().substring(0, 10))
                    .nombre("Pablo Sánchez")
                    .correo(correo)
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(persona)
                    .then(repository.findByCorreo(correo)))
                    .assertNext(retrieved -> {
                        assert retrieved.getCorreo().equals(correo);
                        assert retrieved.getNombre().equals("Pablo Sánchez");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna Mono vacío cuando no existe persona con ese correo")
        void findByCorreo_personaNoExiste_retornaMonoVacio() {
            String correoNoExistente = "noexiste@example.com";

            StepVerifier.create(repository.findByCorreo(correoNoExistente))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findAllFiltered")
    class FindAllFiltered {

        @Test
        @DisplayName("retorna personas filtrando por nombre")
        void findAllFiltered_filtrarPorNombre_retornaFlux() {
            PersonaEntity persona1 = PersonaEntity.builder()
                    .ci(UUID.randomUUID().toString().substring(0, 10))
                    .nombre("Juan Perez")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();
            PersonaEntity persona2 = PersonaEntity.builder()
                    .ci(UUID.randomUUID().toString().substring(0, 10))
                    .nombre("María García")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(persona1)
                    .then(repository.save(persona2))
                    .thenMany(repository.findAllFiltered("Juan", null, null, null, 10, 0)))
                    .recordWith(java.util.ArrayList::new)
                    .thenConsumeWhile(p -> true)
                    .consumeRecordedWith(personas -> {
                        assert personas.stream().anyMatch(p -> p.getNombre().equals("Juan Perez"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna personas filtrando por CI")
        void findAllFiltered_filtrarPorCi_retornaFlux() {
            String ci = "1234567890";
            PersonaEntity persona = PersonaEntity.builder()
                    .ci(ci)
                    .nombre("Roberto López")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(persona)
                    .thenMany(repository.findAllFiltered(null, ci, null, null, 10, 0)))
                    .recordWith(java.util.ArrayList::new)
                    .thenConsumeWhile(p -> true)
                    .consumeRecordedWith(personas -> {
                        assert personas.stream().anyMatch(p -> p.getCi().equals(ci));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna personas filtrando por correo")
        void findAllFiltered_filtrarPorCorreo_retornaFlux() {
            String correo = "elena@example.com";
            PersonaEntity persona = PersonaEntity.builder()
                    .ci(UUID.randomUUID().toString().substring(0, 10))
                    .nombre("Elena Martínez")
                    .correo(correo)
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(persona)
                    .thenMany(repository.findAllFiltered(null, null, correo, null, 10, 0)))
                    .recordWith(java.util.ArrayList::new)
                    .thenConsumeWhile(p -> true)
                    .consumeRecordedWith(personas -> {
                        assert personas.stream().anyMatch(p -> p.getCorreo().equals(correo));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna personas filtrando por sexo")
        void findAllFiltered_filtrarPorSexo_retornaFlux() {
            PersonaEntity persona = PersonaEntity.builder()
                    .ci(UUID.randomUUID().toString().substring(0, 10))
                    .nombre("Francisco Ruiz")
                    .sexo("M")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(persona)
                    .thenMany(repository.findAllFiltered(null, null, null, "M", 10, 0)))
                    .recordWith(java.util.ArrayList::new)
                    .thenConsumeWhile(p -> true)
                    .consumeRecordedWith(personas -> {
                        assert personas.stream().anyMatch(p -> p.getSexo().equals("M"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna lista vacía cuando no hay coincidencias")
        void findAllFiltered_sinCoincidencias_retornaFluxVacio() {
            StepVerifier.create(repository.findAllFiltered("NoExiste123456789", null, null, null, 10, 0))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("countAllFiltered")
    class CountAllFiltered {

        @Test
        @DisplayName("retorna el conteo de personas filtrando por nombre")
        void countAllFiltered_filtrarPorNombre_retornaLong() {
            PersonaEntity persona = PersonaEntity.builder()
                    .ci(UUID.randomUUID().toString().substring(0, 10))
                    .nombre("Diego Fernández")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(persona)
                    .then(repository.countAllFiltered("Diego", null, null, null)))
                    .assertNext(count -> {
                        assert count >= 1 : "Debe contar al menos 1 persona";
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna 0 cuando no hay coincidencias")
        void countAllFiltered_sinCoincidencias_retorna0() {
            StepVerifier.create(repository.countAllFiltered("NoExiste999999999", null, null, null))
                    .expectNext(0L)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("deleteById")
    class DeleteById {

        @Test
        @DisplayName("elimina una persona por su id")
        void deleteById_personaExiste_seElimina() {
            PersonaEntity persona = PersonaEntity.builder()
                    .ci(UUID.randomUUID().toString().substring(0, 10))
                    .nombre("Graciela Torres")
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(persona)
                    .flatMap(saved -> repository.deleteById(saved.getId())
                            .then(repository.findById(saved.getId()))))
                    .verifyComplete();
        }
    }
}
