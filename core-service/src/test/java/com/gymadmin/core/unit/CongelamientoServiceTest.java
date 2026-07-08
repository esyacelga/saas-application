package com.gymadmin.core.unit;

import com.gymadmin.core.application.service.CongelamientoService;
import com.gymadmin.core.domain.model.Cliente;
import com.gymadmin.core.domain.model.Congelamiento;
import com.gymadmin.core.domain.model.Congelamiento.Motivo;
import com.gymadmin.core.domain.model.Membresia;
import com.gymadmin.core.domain.port.in.CongelamientoUseCase.CongelarCommand;
import com.gymadmin.core.domain.port.in.CongelamientoUseCase.ReactivarResult;
import com.gymadmin.core.domain.port.out.ClienteRepository;
import com.gymadmin.core.domain.port.out.CongelamientoRepository;
import com.gymadmin.core.domain.port.out.MembresiaRepository;
import com.gymadmin.core.infrastructure.exception.BusinessException;
import com.gymadmin.core.infrastructure.exception.ConflictException;
import com.gymadmin.core.infrastructure.exception.ForbiddenException;
import com.gymadmin.core.infrastructure.exception.NotFoundException;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CongelamientoService — congelar y reactivar membresías")
class CongelamientoServiceTest {

    @Mock
    private CongelamientoRepository congelamientoRepository;

    @Mock
    private MembresiaRepository membresiaRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @InjectMocks
    private CongelamientoService service;

    private Membresia buildMembresia(Long id, Long idCliente, Membresia.Estado estado, LocalDate inicio, LocalDate fin) {
        Membresia m = new Membresia();
        m.setId(id);
        m.setIdCliente(idCliente);
        m.setIdCompania(1L);
        m.setEstado(estado);
        m.setFechaInicio(inicio);
        m.setFechaFin(fin);
        return m;
    }

    private Congelamiento buildCongelamiento(Long id, Long idMembresia, LocalDate fechaInicio) {
        Congelamiento c = new Congelamiento();
        c.setId(id);
        c.setIdMembresia(idMembresia);
        c.setIdCompania(1L);
        c.setFechaInicio(fechaInicio);
        c.setMotivo(Motivo.voluntario);
        return c;
    }

    private Cliente buildCliente(Long id, Long idPersona, Cliente.Estado estado) {
        Cliente cl = new Cliente();
        cl.setId(id);
        cl.setIdPersona(idPersona);
        cl.setIdCompania(1L);
        cl.setEstado(estado);
        return cl;
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("congelar")
    class Congelar {

        private CongelarCommand cmd(boolean retroactivo, String doc, Long aprobado) {
            return new CongelarCommand(LocalDate.now(), Motivo.voluntario, "Detalle",
                    retroactivo, doc, aprobado);
        }

        @Test
        @DisplayName("congela una membresía activa y actualiza estado cliente a congelado")
        void congelaExitosamente() {
            Membresia mem = buildMembresia(1L, 10L, Membresia.Estado.activa,
                    LocalDate.now().minusDays(5), LocalDate.now().plusDays(25));
            Cliente cliente = buildCliente(10L, 100L, Cliente.Estado.activo);
            Congelamiento saved = buildCongelamiento(1L, 1L, LocalDate.now());

            when(membresiaRepository.findById(1L)).thenReturn(Mono.just(mem));
            when(congelamientoRepository.save(any())).thenReturn(Mono.just(saved));
            when(membresiaRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(clienteRepository.findById(10L)).thenReturn(Mono.just(cliente));
            when(clienteRepository.save(any())).thenReturn(Mono.just(cliente));

            StepVerifier.create(service.congelar(1L, 1L, 1L, 5L, cmd(false, null, null)))
                    .assertNext(c -> {
                        assertThat(c.getId()).isEqualTo(1L);
                        assertThat(mem.getEstado()).isEqualTo(Membresia.Estado.congelada);
                        assertThat(cliente.getEstado()).isEqualTo(Cliente.Estado.congelado);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ConflictException cuando la membresía ya está congelada")
        void lanzaConflictCuandoYaCongelada() {
            Membresia mem = buildMembresia(1L, 10L, Membresia.Estado.congelada,
                    LocalDate.now().minusDays(5), LocalDate.now().plusDays(25));
            when(membresiaRepository.findById(1L)).thenReturn(Mono.just(mem));

            StepVerifier.create(service.congelar(1L, 1L, 1L, 5L, cmd(false, null, null)))
                    .expectError(ConflictException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza BusinessException cuando la membresía no está activa")
        void lanzaBusinessExceptionCuandoNoActiva() {
            Membresia mem = buildMembresia(1L, 10L, Membresia.Estado.vencida,
                    LocalDate.now().minusMonths(2), LocalDate.now().minusDays(1));
            when(membresiaRepository.findById(1L)).thenReturn(Mono.just(mem));

            StepVerifier.create(service.congelar(1L, 1L, 1L, 5L, cmd(false, null, null)))
                    .expectError(BusinessException.class)
                    .verify();
        }

        @Test
        @DisplayName("lanza BusinessException cuando retroactivo sin documento_respaldo ni aprobado_por")
        void lanzaBusinessExceptionCuandoRetroactivoSinDocumento() {
            Membresia mem = buildMembresia(1L, 10L, Membresia.Estado.activa,
                    LocalDate.now().minusDays(5), LocalDate.now().plusDays(25));
            when(membresiaRepository.findById(1L)).thenReturn(Mono.just(mem));

            StepVerifier.create(service.congelar(1L, 1L, 1L, 5L, cmd(true, null, null)))
                    .expectErrorSatisfies(e -> assertThat(e).isInstanceOf(BusinessException.class))
                    .verify();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando la membresía no existe")
        void lanzaNotFoundCuandoMembresiaNoExiste() {
            when(membresiaRepository.findById(99L)).thenReturn(Mono.empty());

            StepVerifier.create(service.congelar(99L, 1L, 1L, 5L, cmd(false, null, null)))
                    .expectError(NotFoundException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("reactivar")
    class Reactivar {

        @Test
        @DisplayName("reactiva y extiende la fecha de fin por los días congelados")
        void reactivaYExtiendeFechaFin() {
            LocalDate fechaCongelacion = LocalDate.now().minusDays(10);
            LocalDate fechaFinOriginal = LocalDate.now().plusDays(20);
            LocalDate fechaFinEsperada = fechaFinOriginal.plusDays(
                    java.time.temporal.ChronoUnit.DAYS.between(fechaCongelacion, LocalDate.now()));

            Congelamiento cong = buildCongelamiento(1L, 1L, fechaCongelacion);
            Membresia mem = buildMembresia(1L, 10L, Membresia.Estado.congelada,
                    LocalDate.now().minusMonths(1), fechaFinOriginal);
            Cliente cliente = buildCliente(10L, 100L, Cliente.Estado.congelado);

            when(congelamientoRepository.findById(1L)).thenReturn(Mono.just(cong));
            when(membresiaRepository.findById(1L)).thenReturn(Mono.just(mem));
            when(congelamientoRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(membresiaRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(clienteRepository.findById(10L)).thenReturn(Mono.just(cliente));
            when(clienteRepository.save(any())).thenReturn(Mono.just(cliente));

            StepVerifier.create(service.reactivar(1L, 1L))
                    .assertNext(r -> {
                        assertThat(r.fechaFinNueva()).isEqualTo(fechaFinEsperada);
                        assertThat(r.diasCompensados()).isGreaterThan(0);
                        assertThat(mem.getEstado()).isEqualTo(Membresia.Estado.activa);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando el congelamiento no existe")
        void lanzaNotFoundCuandoCongelamientoNoExiste() {
            when(congelamientoRepository.findById(99L)).thenReturn(Mono.empty());

            StepVerifier.create(service.reactivar(99L, 1L))
                    .expectError(NotFoundException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("reactivarPorCliente")
    class ReactivarPorCliente {

        @Test
        @DisplayName("reactiva cuando la persona corresponde al cliente de la membresía")
        void reactivaExitosamente() {
            LocalDate fechaCongelacion = LocalDate.now().minusDays(5);
            Congelamiento cong = buildCongelamiento(1L, 1L, fechaCongelacion);
            Membresia mem = buildMembresia(1L, 10L, Membresia.Estado.congelada,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusDays(30));
            Cliente cliente = buildCliente(10L, 100L, Cliente.Estado.congelado);

            when(congelamientoRepository.findById(1L)).thenReturn(Mono.just(cong));
            when(membresiaRepository.findById(1L)).thenReturn(Mono.just(mem));
            when(clienteRepository.findById(10L)).thenReturn(Mono.just(cliente));
            when(congelamientoRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(membresiaRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(clienteRepository.save(any())).thenReturn(Mono.just(cliente));

            StepVerifier.create(service.reactivarPorCliente(1L, 100L, 1L))
                    .assertNext(r -> assertThat(r.diasCompensados()).isGreaterThanOrEqualTo(5))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando la persona no es la propietaria de la membresía")
        void lanzaForbiddenCuandoPersonaNoCorresponde() {
            Congelamiento cong = buildCongelamiento(1L, 1L, LocalDate.now().minusDays(5));
            Membresia mem = buildMembresia(1L, 10L, Membresia.Estado.congelada,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusDays(30));
            Cliente cliente = buildCliente(10L, 100L, Cliente.Estado.congelado);

            when(congelamientoRepository.findById(1L)).thenReturn(Mono.just(cong));
            when(membresiaRepository.findById(1L)).thenReturn(Mono.just(mem));
            when(clienteRepository.findById(10L)).thenReturn(Mono.just(cliente));

            StepVerifier.create(service.reactivarPorCliente(1L, 999L, 1L))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("historialPorMembresia")
    class HistorialPorMembresia {

        @Test
        @DisplayName("retorna el historial de congelamientos de la membresía")
        void retornaHistorial() {
            Congelamiento c1 = buildCongelamiento(1L, 1L, LocalDate.now().minusMonths(2));
            Congelamiento c2 = buildCongelamiento(2L, 1L, LocalDate.now().minusMonths(1));
            when(congelamientoRepository.findByIdMembresia(1L)).thenReturn(Flux.just(c1, c2));

            StepVerifier.create(service.historialPorMembresia(1L, 1L))
                    .expectNext(c1)
                    .expectNext(c2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna Flux vacío cuando no hay congelamientos")
        void retornaVacioCuandoSinHistorial() {
            when(congelamientoRepository.findByIdMembresia(1L)).thenReturn(Flux.empty());

            StepVerifier.create(service.historialPorMembresia(1L, 1L))
                    .verifyComplete();
        }
    }
}
