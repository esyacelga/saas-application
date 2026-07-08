package com.gymadmin.auth.unit;

import com.gymadmin.auth.application.service.PersonaApplicationService;
import com.gymadmin.auth.domain.exception.ConflictException;
import com.gymadmin.auth.domain.exception.ResourceNotFoundException;
import com.gymadmin.auth.domain.model.Persona;
import com.gymadmin.auth.domain.port.out.PersonaPort;
import com.gymadmin.auth.dto.request.CreatePersonaRequest;
import com.gymadmin.auth.dto.request.UpdatePersonaRequest;
import com.gymadmin.auth.dto.response.PersonaPageResponse;
import com.gymadmin.auth.dto.response.PersonaResponse;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PersonaApplicationService")
class PersonaApplicationServiceTest {

    @Mock
    private PersonaPort personaPort;

    @InjectMocks
    private PersonaApplicationService service;

    private Persona personaFijura;

    @BeforeEach
    void setUp() {
        personaFijura = new Persona();
        personaFijura.setId(1);
        personaFijura.setCi("1234567890");
        personaFijura.setNombre("Juan Perez");
        personaFijura.setCorreo("juan@example.com");
        personaFijura.setTelefono("0991234567");
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("retorna persona cuando existe")
        void retornaPersonaCuandoExiste() {
            when(personaPort.findById(1)).thenReturn(Mono.just(personaFijura));

            StepVerifier.create(service.findById(1))
                    .expectNextMatches(r -> r.id().equals(1) && r.ci().equals("1234567890"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ResourceNotFoundException cuando no existe")
        void lanzaExcepcionCuandoNoExiste() {
            when(personaPort.findById(99)).thenReturn(Mono.empty());

            StepVerifier.create(service.findById(99))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("findByCi")
    class FindByCi {

        @Test
        @DisplayName("retorna persona cuando el CI existe")
        void retornaPersonaCuandoCiExiste() {
            when(personaPort.findByCi("1234567890")).thenReturn(Mono.just(personaFijura));

            StepVerifier.create(service.findByCi("1234567890"))
                    .expectNextMatches(r -> r.ci().equals("1234567890"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ResourceNotFoundException cuando el CI no existe")
        void lanzaExcepcionCuandoCiNoExiste() {
            when(personaPort.findByCi("0000000000")).thenReturn(Mono.empty());

            StepVerifier.create(service.findByCi("0000000000"))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("findByCorreo")
    class FindByCorreo {

        @Test
        @DisplayName("retorna persona cuando el correo existe")
        void retornaPersonaCuandoCorreoExiste() {
            when(personaPort.findByCorreo("juan@example.com")).thenReturn(Mono.just(personaFijura));

            StepVerifier.create(service.findByCorreo("juan@example.com"))
                    .expectNextMatches(r -> r.correo().equals("juan@example.com"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ResourceNotFoundException cuando el correo no existe")
        void lanzaExcepcionCuandoCorreoNoExiste() {
            when(personaPort.findByCorreo("noexiste@example.com")).thenReturn(Mono.empty());

            StepVerifier.create(service.findByCorreo("noexiste@example.com"))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("crear persona")
    class Crear {

        @Test
        @DisplayName("crea y retorna persona cuando el CI no existe")
        void creaPersonaCuandoCiNoDuplicado() {
            CreatePersonaRequest req = new CreatePersonaRequest(
                    "1234567890", "Juan Perez", "0991234567",
                    "juan@example.com", "M", null, null
            );
            when(personaPort.existsByCi("1234567890")).thenReturn(Mono.just(false));
            when(personaPort.save(any(Persona.class))).thenReturn(Mono.just(personaFijura));

            StepVerifier.create(service.create(req, "admin"))
                    .expectNextMatches(r -> r.ci().equals("1234567890"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ConflictException cuando el CI ya existe")
        void lanzaConflictCuandoCiDuplicado() {
            CreatePersonaRequest req = new CreatePersonaRequest(
                    "1234567890", "Juan Perez", "0991234567",
                    "juan@example.com", "M", null, null
            );
            when(personaPort.existsByCi("1234567890")).thenReturn(Mono.just(true));

            StepVerifier.create(service.create(req, "admin"))
                    .expectError(ConflictException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("actualizar persona")
    class Update {

        @Test
        @DisplayName("actualiza persona exitosamente cuando existe y no hay conflicto de CI")
        void actualizaExitosamenteSinCambioCi() {
            UpdatePersonaRequest req = new UpdatePersonaRequest(
                    "Juan Nuevo", "0999999999", null, null, null, null, null
            );
            when(personaPort.findById(1)).thenReturn(Mono.just(personaFijura));
            when(personaPort.save(any(Persona.class))).thenReturn(Mono.just(personaFijura));

            StepVerifier.create(service.update(1, req, "admin"))
                    .expectNextMatches(r -> r.id().equals(1))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ResourceNotFoundException cuando la persona no existe")
        void lanzaExcepcionCuandoPersonaNoExiste() {
            UpdatePersonaRequest req = new UpdatePersonaRequest(
                    "Juan Nuevo", null, null, null, null, null, null
            );
            when(personaPort.findById(99)).thenReturn(Mono.empty());

            StepVerifier.create(service.update(99, req, "admin"))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza ConflictException cuando el nuevo CI ya pertenece a otra persona")
        void lanzaConflictCuandoCiTomadoPorOtra() {
            UpdatePersonaRequest req = new UpdatePersonaRequest(
                    null, null, null, null, null, "9999999999", null
            );
            when(personaPort.findById(1)).thenReturn(Mono.just(personaFijura));
            when(personaPort.existsByCiAndIdNot("9999999999", 1)).thenReturn(Mono.just(true));

            StepVerifier.create(service.update(1, req, "admin"))
                    .expectError(ConflictException.class)
                    .verify();
        }

        @Test
        @DisplayName("actualiza persona con nuevo CI cuando no hay conflicto")
        void actualizaConNuevoCiSinConflicto() {
            UpdatePersonaRequest req = new UpdatePersonaRequest(
                    null, null, null, null, null, "9999999999", null
            );
            Persona personaActualizada = new Persona();
            personaActualizada.setId(1);
            personaActualizada.setCi("9999999999");
            personaActualizada.setNombre("Juan Perez");
            personaActualizada.setCorreo("juan@example.com");
            personaActualizada.setTelefono("0991234567");

            when(personaPort.findById(1)).thenReturn(Mono.just(personaFijura));
            when(personaPort.existsByCiAndIdNot("9999999999", 1)).thenReturn(Mono.just(false));
            when(personaPort.save(any(Persona.class))).thenReturn(Mono.just(personaActualizada));

            StepVerifier.create(service.update(1, req, "admin"))
                    .expectNextMatches(r -> r.ci().equals("9999999999"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("listar personas paginadas")
    class Listar {

        @Test
        @DisplayName("retorna pagina con personas y conteo total")
        void retornaPaginaConPersonas() {
            when(personaPort.findAll(null, null, null, null, 0, 10))
                    .thenReturn(Flux.just(personaFijura));
            when(personaPort.countAll(null, null, null, null))
                    .thenReturn(Mono.just(1L));

            StepVerifier.create(service.listar(null, null, null, null, 0, 10))
                    .expectNextMatches(page ->
                            page.content().size() == 1
                                    && page.totalElements() == 1L
                                    && page.totalPages() == 1
                                    && page.number() == 0
                                    && page.size() == 10
                    )
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna pagina vacia cuando no hay personas")
        void retornaPaginaVaciaCuandoNoHayPersonas() {
            when(personaPort.findAll(null, null, null, null, 0, 10))
                    .thenReturn(Flux.empty());
            when(personaPort.countAll(null, null, null, null))
                    .thenReturn(Mono.just(0L));

            StepVerifier.create(service.listar(null, null, null, null, 0, 10))
                    .expectNextMatches(page ->
                            page.content().isEmpty()
                                    && page.totalElements() == 0L
                                    && page.totalPages() == 0
                    )
                    .verifyComplete();
        }
    }
}
