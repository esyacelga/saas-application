package com.gymadmin.core.unit;

import com.gymadmin.core.application.service.ClienteService;
import com.gymadmin.core.domain.model.Cliente;
import com.gymadmin.core.domain.model.Membresia;
import com.gymadmin.core.domain.model.TipoMembresia;
import com.gymadmin.core.domain.port.in.ClienteUseCase.RegistrarClienteCommand;
import com.gymadmin.core.domain.port.out.ClienteRepository;
import com.gymadmin.core.domain.port.out.CongelamientoRepository;
import com.gymadmin.core.domain.port.out.MembresiaRepository;
import com.gymadmin.core.domain.port.out.PersonaRepository;
import com.gymadmin.core.domain.port.out.TipoMembresiaRepository;
import com.gymadmin.core.infrastructure.adapter.out.http.PlatformServiceClient;
import com.gymadmin.core.infrastructure.exception.ConflictException;
import com.gymadmin.core.infrastructure.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClienteService — gestión de clientes del gimnasio")
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private PersonaRepository personaRepository;

    @Mock
    private MembresiaRepository membresiaRepository;

    @Mock
    private CongelamientoRepository congelamientoRepository;

    @Mock
    private TipoMembresiaRepository tipoMembresiaRepository;

    @Mock
    private PlatformServiceClient platformServiceClient;

    private ClienteService service;

    @BeforeEach
    void setUp() {
        // Construimos manualmente para poder inyectar `carnetPrefix` (el @Value no
        // lo resuelve @InjectMocks). Mantiene la misma configuración que producción.
        service = new ClienteService(
                clienteRepository, personaRepository, membresiaRepository,
                congelamientoRepository, tipoMembresiaRepository, platformServiceClient,
                "GYM"
        );
    }

    private Cliente buildCliente(Long id, Long idPersona, Long idCompania, Cliente.Estado estado) {
        Cliente c = new Cliente();
        c.setId(id);
        c.setIdPersona(idPersona);
        c.setIdCompania(idCompania);
        c.setEstado(estado);
        c.setFechaIngreso(LocalDate.now());
        return c;
    }

    private PersonaRepository.PersonaResult buildPersona(Long id, String ci, String nombre) {
        return new PersonaRepository.PersonaResult(id, ci, nombre, null, null, null);
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("buscarPorId")
    class BuscarPorId {

        @Test
        @DisplayName("retorna el cliente cuando existe y pertenece a la compañía")
        void retornaClienteCuandoExiste() {
            Cliente c = buildCliente(1L, 100L, 1L, Cliente.Estado.activo);
            when(clienteRepository.findByIdAndIdCompania(1L, 1L)).thenReturn(Mono.just(c));

            StepVerifier.create(service.buscarPorId(1L, 1L))
                    .expectNext(c)
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando el cliente no existe")
        void lanzaNotFoundCuandoNoExiste() {
            when(clienteRepository.findByIdAndIdCompania(99L, 1L)).thenReturn(Mono.empty());

            StepVerifier.create(service.buscarPorId(99L, 1L))
                    .expectError(NotFoundException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("buscarPorCi")
    class BuscarPorCi {

        @Test
        @DisplayName("retorna resultado con registrado=true cuando la persona ya es cliente")
        void retornaRegistradoCuandoYaEsCliente() {
            PersonaRepository.PersonaResult persona = buildPersona(100L, "1234567890", "Juan Pérez");
            Cliente c = buildCliente(1L, 100L, 1L, Cliente.Estado.activo);

            when(personaRepository.findByCi("1234567890")).thenReturn(Mono.just(persona));
            when(clienteRepository.findByIdPersonaAndIdCompania(100L, 1L)).thenReturn(Mono.just(c));

            StepVerifier.create(service.buscarPorCi("1234567890", 1L))
                    .assertNext(r -> {
                        assertThat(r.esClienteEnEsteGym()).isTrue();
                        assertThat(r.idCliente()).isEqualTo(1L);
                        assertThat(r.nombre()).isEqualTo("Juan Pérez");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna resultado con registrado=false cuando la persona existe pero no es cliente")
        void retornaNoRegistradoCuandoPersonaExisteSinCliente() {
            PersonaRepository.PersonaResult persona = buildPersona(100L, "1234567890", "Juan Pérez");

            when(personaRepository.findByCi("1234567890")).thenReturn(Mono.just(persona));
            when(clienteRepository.findByIdPersonaAndIdCompania(100L, 1L)).thenReturn(Mono.empty());

            StepVerifier.create(service.buscarPorCi("1234567890", 1L))
                    .assertNext(r -> {
                        assertThat(r.esClienteEnEsteGym()).isFalse();
                        assertThat(r.idCliente()).isNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando la persona no existe con esa CI")
        void lanzaNotFoundCuandoPersonaNoExiste() {
            when(personaRepository.findByCi("0000000000")).thenReturn(Mono.empty());

            StepVerifier.create(service.buscarPorCi("0000000000", 1L))
                    .expectError(NotFoundException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("registrar")
    class Registrar {

        private RegistrarClienteCommand buildCmd() {
            return new RegistrarClienteCommand(
                    "0987654321", "Ana García", "0999999999",
                    "ana@mail.com", LocalDate.of(1990, 1, 1),
                    null, null, null, null, 1L, "F"
            );
        }

        @Test
        @DisplayName("crea un nuevo cliente cuando la persona no existe aún")
        void creaClienteConNuevaPersona() {
            PersonaRepository.PersonaResult nuevaPersona = buildPersona(200L, "0987654321", "Ana García");
            Cliente clienteSaved = buildCliente(5L, 200L, 1L, Cliente.Estado.activo);

            when(platformServiceClient.requireLimite(eq(1L), eq("clientes_activos"))).thenReturn(Mono.empty());
            when(personaRepository.findByCi("0987654321")).thenReturn(Mono.empty());
            when(personaRepository.create(any())).thenReturn(Mono.just(nuevaPersona));
            when(clienteRepository.save(any())).thenAnswer(inv -> {
                Cliente c = inv.getArgument(0);
                c.setId(5L);
                return Mono.just(c);
            });

            StepVerifier.create(service.registrar(1L, buildCmd()))
                    .assertNext(c -> {
                        assertThat(c.getId()).isEqualTo(5L);
                        assertThat(c.getEstado()).isEqualTo(Cliente.Estado.activo);
                        assertThat(c.getCodigoCarnet()).contains("GYM");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ConflictException cuando la persona ya es cliente de este gym")
        void lanzaConflictCuandoYaEsCliente() {
            PersonaRepository.PersonaResult persona = buildPersona(100L, "0987654321", "Ana García");
            Cliente existente = buildCliente(1L, 100L, 1L, Cliente.Estado.activo);

            when(platformServiceClient.requireLimite(eq(1L), eq("clientes_activos"))).thenReturn(Mono.empty());
            when(personaRepository.findByCi("0987654321")).thenReturn(Mono.just(persona));
            when(clienteRepository.findByIdPersonaAndIdCompania(100L, 1L)).thenReturn(Mono.just(existente));
            // personaRepository.create(...) se evalúa eagerly como argumento a switchIfEmpty aunque
            // el flujo termine en error antes; sin este stub retornaría null → NPE.
            when(personaRepository.create(any())).thenReturn(Mono.empty());

            StepVerifier.create(service.registrar(1L, buildCmd()))
                    .expectErrorSatisfies(e -> assertThat(e).isInstanceOf(ConflictException.class))
                    .verify();

            verify(clienteRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("registrarDesdeApp")
    class RegistrarDesdeApp {

        @Test
        @DisplayName("registra el cliente cuando la persona aún no es cliente en esta compañía")
        void registraExitosamente() {
            when(clienteRepository.findByIdPersonaAndIdCompania(100L, 1L)).thenReturn(Mono.empty());
            when(clienteRepository.save(any())).thenAnswer(inv -> {
                Cliente c = inv.getArgument(0);
                c.setId(7L);
                return Mono.just(c);
            });

            StepVerifier.create(service.registrarDesdeApp(100L, 1L, 1L))
                    .assertNext(c -> {
                        assertThat(c.getId()).isEqualTo(7L);
                        assertThat(c.getCodigoCarnet()).contains("GYM");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ConflictException cuando la persona ya es cliente")
        void lanzaConflictCuandoYaEsCliente() {
            Cliente existente = buildCliente(1L, 100L, 1L, Cliente.Estado.activo);
            when(clienteRepository.findByIdPersonaAndIdCompania(100L, 1L)).thenReturn(Mono.just(existente));

            StepVerifier.create(service.registrarDesdeApp(100L, 1L, 1L))
                    .expectError(ConflictException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("eliminar")
    class Eliminar {

        @Test
        @DisplayName("elimina el cliente cuando existe")
        void eliminaExitosamente() {
            Cliente c = buildCliente(1L, 100L, 1L, Cliente.Estado.vencido);
            when(clienteRepository.findById(1L)).thenReturn(Mono.just(c));
            when(clienteRepository.deleteById(1L)).thenReturn(Mono.empty());

            StepVerifier.create(service.eliminar(1L))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando el cliente no existe")
        void lanzaNotFoundCuandoNoExiste() {
            when(clienteRepository.findById(99L)).thenReturn(Mono.empty());

            StepVerifier.create(service.eliminar(99L))
                    .expectError(NotFoundException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("listarPorPersona")
    class ListarPorPersona {

        @Test
        @DisplayName("retorna todos los clientes de una persona (multi-gym)")
        void retornaClientesDeLaPersona() {
            Cliente c1 = buildCliente(1L, 100L, 1L, Cliente.Estado.activo);
            Cliente c2 = buildCliente(2L, 100L, 2L, Cliente.Estado.vencido);
            when(clienteRepository.findByIdPersona(100L)).thenReturn(Flux.just(c1, c2));

            StepVerifier.create(service.listarPorPersona(100L))
                    .expectNext(c1)
                    .expectNext(c2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna Flux vacío cuando la persona no tiene clientes registrados")
        void retornaVacioCuandoSinClientes() {
            when(clienteRepository.findByIdPersona(100L)).thenReturn(Flux.empty());

            StepVerifier.create(service.listarPorPersona(100L))
                    .verifyComplete();
        }
    }
}
