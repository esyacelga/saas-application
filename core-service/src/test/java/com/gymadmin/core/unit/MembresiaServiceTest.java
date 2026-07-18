package com.gymadmin.core.unit;

import com.gymadmin.core.application.service.MembresiaService;
import com.gymadmin.core.domain.model.Cliente;
import com.gymadmin.core.domain.model.Membresia;
import com.gymadmin.core.domain.model.TipoMembresia;
import com.gymadmin.core.domain.port.in.MembresiaUseCase;
import com.gymadmin.core.domain.port.out.ClienteRepository;
import com.gymadmin.core.domain.port.out.MembresiaRepository;
import com.gymadmin.core.domain.port.out.PersonaRepository;
import com.gymadmin.core.domain.port.out.TipoMembresiaRepository;
import com.gymadmin.core.infrastructure.exception.BusinessException;
import com.gymadmin.core.infrastructure.exception.ConflictException;
import com.gymadmin.core.infrastructure.exception.NotFoundException;
import com.gymadmin.core.domain.event.MembresiaPagadaEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MembresiaService — gestión de membresías")
class MembresiaServiceTest {

    @Mock
    private MembresiaRepository membresiaRepository;

    @Mock
    private TipoMembresiaRepository tipoMembresiaRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private PersonaRepository personaRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MembresiaService membresiaService;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Membresia buildMembresia(Long id, Long idCliente, Long idCompania,
                                     Long idTipoMembresia, Membresia.Estado estado,
                                     LocalDate inicio, LocalDate fin) {
        Membresia m = new Membresia();
        m.setId(id);
        m.setIdCliente(idCliente);
        m.setIdCompania(idCompania);
        m.setIdTipoMembresia(idTipoMembresia);
        m.setEstado(estado);
        m.setFechaInicio(inicio);
        m.setFechaFin(fin);
        m.setPrecioPagado(BigDecimal.valueOf(50));
        m.setDescuentoAplicado(BigDecimal.ZERO);
        return m;
    }

    private TipoMembresia buildTipo(Long id, TipoMembresia.ModoControl modo,
                                    TipoMembresia.DuracionTipo duracionTipo, int duracionValor,
                                    Integer diasAcceso, BigDecimal precio) {
        TipoMembresia t = new TipoMembresia();
        t.setId(id);
        t.setNombre("Tipo " + id);
        t.setModoControl(modo);
        t.setDuracionTipo(duracionTipo);
        t.setDuracionValor(duracionValor);
        t.setDiasAcceso(diasAcceso);
        t.setPrecio(precio);
        t.setActivo(true);
        return t;
    }

    private Cliente buildCliente(Long id, Long idPersona, Long idCompania, Cliente.Estado estado) {
        Cliente c = new Cliente();
        c.setId(id);
        c.setIdPersona(idPersona);
        c.setIdCompania(idCompania);
        c.setEstado(estado);
        return c;
    }

    // -------------------------------------------------------------------------
    // historialPorCliente
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("historialPorCliente")
    class HistorialPorCliente {

        @Test
        @DisplayName("retorna el historial de membresías del cliente")
        void retornaHistorial() {
            Membresia m1 = buildMembresia(1L, 10L, 1L, 2L, Membresia.Estado.activa,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1));
            Membresia m2 = buildMembresia(2L, 10L, 1L, 2L, Membresia.Estado.vencida,
                    LocalDate.now().minusMonths(3), LocalDate.now().minusMonths(2));
            when(membresiaRepository.findByIdCliente(10L)).thenReturn(Flux.just(m1, m2));

            StepVerifier.create(membresiaService.historialPorCliente(10L, 1L))
                    .expectNext(m1)
                    .expectNext(m2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna Flux vacío cuando el cliente no tiene membresías")
        void retornaVacioCuandoSinMembresias() {
            when(membresiaRepository.findByIdCliente(10L)).thenReturn(Flux.empty());

            StepVerifier.create(membresiaService.historialPorCliente(10L, 1L))
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    // detalle
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("detalle")
    class Detalle {

        @Test
        @DisplayName("retorna el detalle con información del tipo calendario (sin accesos)")
        void retornaDetalleConTipoCalendario() {
            Membresia mem = buildMembresia(1L, 10L, 1L, 2L, Membresia.Estado.activa,
                    LocalDate.now(), LocalDate.now().plusMonths(1));
            TipoMembresia tipo = buildTipo(2L, TipoMembresia.ModoControl.calendario,
                    TipoMembresia.DuracionTipo.meses, 1, null, BigDecimal.valueOf(50));

            when(membresiaRepository.findById(1L)).thenReturn(Mono.just(mem));
            when(tipoMembresiaRepository.findById(2L)).thenReturn(Mono.just(tipo));

            StepVerifier.create(membresiaService.detalle(1L, 1L))
                    .expectNextMatches(r -> r.tipoNombre().equals("Tipo 2")
                            && r.modoControl().equals("calendario")
                            && r.diasAccesoUsados() == null
                            && r.diasAccesoRestantes() == null)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna el detalle con accesos usados y restantes para tipo accesos")
        void retornaDetalleConTipoAccesos() {
            Membresia mem = buildMembresia(1L, 10L, 1L, 3L, Membresia.Estado.activa,
                    LocalDate.now(), LocalDate.now().plusMonths(1));
            mem.setDiasAccesoTotal(20);

            TipoMembresia tipo = buildTipo(3L, TipoMembresia.ModoControl.accesos,
                    TipoMembresia.DuracionTipo.meses, 1, 20, BigDecimal.valueOf(60));

            when(membresiaRepository.findById(1L)).thenReturn(Mono.just(mem));
            when(tipoMembresiaRepository.findById(3L)).thenReturn(Mono.just(tipo));
            when(membresiaRepository.countAsistenciasByIdMembresia(1L)).thenReturn(Mono.just(8L));

            StepVerifier.create(membresiaService.detalle(1L, 1L))
                    .expectNextMatches(r -> r.diasAccesoUsados() == 8
                            && r.diasAccesoRestantes() == 12)
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando la membresía no existe")
        void lanzaNotFoundCuandoMembresiaNoExiste() {
            when(membresiaRepository.findById(99L)).thenReturn(Mono.empty());

            StepVerifier.create(membresiaService.detalle(99L, 1L))
                    .expectErrorSatisfies(err -> {
                        assertThat(err).isInstanceOf(NotFoundException.class);
                        assertThat(err.getMessage()).contains("99");
                    })
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    // vender
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("vender")
    class Vender {

        private MembresiaUseCase.VenderCommand buildCmd(Long idTipo, LocalDate inicio, BigDecimal descuento) {
            return new MembresiaUseCase.VenderCommand(idTipo, inicio, 1L, descuento, null);
        }

        private MembresiaUseCase.VenderCommand buildCmd(Long idTipo, LocalDate inicio, BigDecimal descuento,
                                                        Membresia.EstadoPago estadoPago) {
            return new MembresiaUseCase.VenderCommand(idTipo, inicio, 1L, descuento, estadoPago);
        }

        @Test
        @DisplayName("venta exitosa con tipo calendario: calcula fechaFin en días")
        void ventaExitosaCalendarioDias() {
            LocalDate inicio = LocalDate.of(2026, 1, 1);
            TipoMembresia tipo = buildTipo(2L, TipoMembresia.ModoControl.calendario,
                    TipoMembresia.DuracionTipo.dias, 30, null, BigDecimal.valueOf(50));
            Cliente cliente = buildCliente(10L, 20L, 1L, Cliente.Estado.vencido);

            when(membresiaRepository.findActivaByIdClienteAndIdCompania(10L, 1L)).thenReturn(Mono.empty());
            when(tipoMembresiaRepository.findById(2L)).thenReturn(Mono.just(tipo));
            when(membresiaRepository.save(any())).thenAnswer(inv -> {
                Membresia m = inv.getArgument(0);
                m.setId(1L);
                return Mono.just(m);
            });
            when(clienteRepository.findById(10L)).thenReturn(Mono.just(cliente));
            when(clienteRepository.save(any())).thenReturn(Mono.just(cliente));

            StepVerifier.create(membresiaService.vender(10L, 1L, 1L, 5L, buildCmd(2L, inicio, null)))
                    .expectNextMatches(m -> m.getFechaFin().equals(inicio.plusDays(30))
                            && m.getEstado() == Membresia.Estado.activa
                            && m.getDiasAccesoTotal() == null)
                    .verifyComplete();
        }

        @Test
        @DisplayName("venta exitosa con tipo calendario: calcula fechaFin en semanas")
        void ventaExitosaCalendarioSemanas() {
            LocalDate inicio = LocalDate.of(2026, 1, 1);
            TipoMembresia tipo = buildTipo(2L, TipoMembresia.ModoControl.calendario,
                    TipoMembresia.DuracionTipo.semanas, 4, null, BigDecimal.valueOf(50));
            Cliente cliente = buildCliente(10L, 20L, 1L, Cliente.Estado.vencido);

            when(membresiaRepository.findActivaByIdClienteAndIdCompania(10L, 1L)).thenReturn(Mono.empty());
            when(tipoMembresiaRepository.findById(2L)).thenReturn(Mono.just(tipo));
            when(membresiaRepository.save(any())).thenAnswer(inv -> {
                Membresia m = inv.getArgument(0);
                m.setId(2L);
                return Mono.just(m);
            });
            when(clienteRepository.findById(10L)).thenReturn(Mono.just(cliente));
            when(clienteRepository.save(any())).thenReturn(Mono.just(cliente));

            StepVerifier.create(membresiaService.vender(10L, 1L, 1L, 5L, buildCmd(2L, inicio, null)))
                    .expectNextMatches(m -> m.getFechaFin().equals(inicio.plusWeeks(4)))
                    .verifyComplete();
        }

        @Test
        @DisplayName("venta exitosa con tipo calendario: calcula fechaFin en meses")
        void ventaExitosaCalendarioMeses() {
            LocalDate inicio = LocalDate.of(2026, 1, 15);
            TipoMembresia tipo = buildTipo(2L, TipoMembresia.ModoControl.calendario,
                    TipoMembresia.DuracionTipo.meses, 3, null, BigDecimal.valueOf(50));
            Cliente cliente = buildCliente(10L, 20L, 1L, Cliente.Estado.vencido);

            when(membresiaRepository.findActivaByIdClienteAndIdCompania(10L, 1L)).thenReturn(Mono.empty());
            when(tipoMembresiaRepository.findById(2L)).thenReturn(Mono.just(tipo));
            when(membresiaRepository.save(any())).thenAnswer(inv -> {
                Membresia m = inv.getArgument(0);
                m.setId(3L);
                return Mono.just(m);
            });
            when(clienteRepository.findById(10L)).thenReturn(Mono.just(cliente));
            when(clienteRepository.save(any())).thenReturn(Mono.just(cliente));

            StepVerifier.create(membresiaService.vender(10L, 1L, 1L, 5L, buildCmd(2L, inicio, null)))
                    .expectNextMatches(m -> m.getFechaFin().equals(inicio.plusMonths(3)))
                    .verifyComplete();
        }

        @Test
        @DisplayName("venta exitosa con tipo calendario: calcula fechaFin en años")
        void ventaExitosaCalendarioAnios() {
            LocalDate inicio = LocalDate.of(2026, 3, 10);
            TipoMembresia tipo = buildTipo(2L, TipoMembresia.ModoControl.calendario,
                    TipoMembresia.DuracionTipo.años, 1, null, BigDecimal.valueOf(50));
            Cliente cliente = buildCliente(10L, 20L, 1L, Cliente.Estado.vencido);

            when(membresiaRepository.findActivaByIdClienteAndIdCompania(10L, 1L)).thenReturn(Mono.empty());
            when(tipoMembresiaRepository.findById(2L)).thenReturn(Mono.just(tipo));
            when(membresiaRepository.save(any())).thenAnswer(inv -> {
                Membresia m = inv.getArgument(0);
                m.setId(4L);
                return Mono.just(m);
            });
            when(clienteRepository.findById(10L)).thenReturn(Mono.just(cliente));
            when(clienteRepository.save(any())).thenReturn(Mono.just(cliente));

            StepVerifier.create(membresiaService.vender(10L, 1L, 1L, 5L, buildCmd(2L, inicio, null)))
                    .expectNextMatches(m -> m.getFechaFin().equals(inicio.plusYears(1)))
                    .verifyComplete();
        }

        @Test
        @DisplayName("venta exitosa con tipo accesos: establece diasAccesoTotal según el tipo")
        void ventaExitosaAccesos() {
            LocalDate inicio = LocalDate.of(2026, 1, 1);
            TipoMembresia tipo = buildTipo(3L, TipoMembresia.ModoControl.accesos,
                    TipoMembresia.DuracionTipo.meses, 1, 15, BigDecimal.valueOf(60));
            Cliente cliente = buildCliente(10L, 20L, 1L, Cliente.Estado.vencido);

            when(membresiaRepository.findActivaByIdClienteAndIdCompania(10L, 1L)).thenReturn(Mono.empty());
            when(tipoMembresiaRepository.findById(3L)).thenReturn(Mono.just(tipo));
            when(membresiaRepository.save(any())).thenAnswer(inv -> {
                Membresia m = inv.getArgument(0);
                m.setId(5L);
                return Mono.just(m);
            });
            when(clienteRepository.findById(10L)).thenReturn(Mono.just(cliente));
            when(clienteRepository.save(any())).thenReturn(Mono.just(cliente));

            StepVerifier.create(membresiaService.vender(10L, 1L, 1L, 5L, buildCmd(3L, inicio, null)))
                    .expectNextMatches(m -> m.getDiasAccesoTotal() != null
                            && m.getDiasAccesoTotal().equals(15))
                    .verifyComplete();
        }

        @Test
        @DisplayName("aplica descuento al precio pagado cuando se indica un descuento")
        void aplicaDescuento() {
            LocalDate inicio = LocalDate.of(2026, 1, 1);
            TipoMembresia tipo = buildTipo(2L, TipoMembresia.ModoControl.calendario,
                    TipoMembresia.DuracionTipo.meses, 1, null, BigDecimal.valueOf(100));
            Cliente cliente = buildCliente(10L, 20L, 1L, Cliente.Estado.vencido);

            when(membresiaRepository.findActivaByIdClienteAndIdCompania(10L, 1L)).thenReturn(Mono.empty());
            when(tipoMembresiaRepository.findById(2L)).thenReturn(Mono.just(tipo));
            when(membresiaRepository.save(any())).thenAnswer(inv -> {
                Membresia m = inv.getArgument(0);
                m.setId(6L);
                return Mono.just(m);
            });
            when(clienteRepository.findById(10L)).thenReturn(Mono.just(cliente));
            when(clienteRepository.save(any())).thenReturn(Mono.just(cliente));

            StepVerifier.create(membresiaService.vender(10L, 1L, 1L, 5L,
                    buildCmd(2L, inicio, BigDecimal.valueOf(20))))
                    .expectNextMatches(m -> m.getPrecioPagado().compareTo(BigDecimal.valueOf(80)) == 0
                            && m.getDescuentoAplicado().compareTo(BigDecimal.valueOf(20)) == 0)
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando el tipo de membresía no existe")
        void lanzaNotFoundSiTipoNoExiste() {
            LocalDate inicio = LocalDate.of(2026, 1, 1);
            when(membresiaRepository.findActivaByIdClienteAndIdCompania(10L, 1L)).thenReturn(Mono.empty());
            when(tipoMembresiaRepository.findById(99L)).thenReturn(Mono.empty());

            StepVerifier.create(membresiaService.vender(10L, 1L, 1L, 5L, buildCmd(99L, inicio, null)))
                    .expectErrorSatisfies(err -> {
                        assertThat(err).isInstanceOf(NotFoundException.class);
                        assertThat(err.getMessage()).contains("99");
                    })
                    .verify();
        }

        @Test
        @DisplayName("lanza ConflictException cuando el cliente ya tiene una membresía activa")
        void lanzaConflictSiClienteYaTieneMembresia() {
            LocalDate inicio = LocalDate.of(2026, 1, 1);
            Membresia activa = buildMembresia(1L, 10L, 1L, 2L, Membresia.Estado.activa,
                    LocalDate.now(), LocalDate.now().plusMonths(1));

            when(membresiaRepository.findActivaByIdClienteAndIdCompania(10L, 1L))
                    .thenReturn(Mono.just(activa));

            StepVerifier.create(membresiaService.vender(10L, 1L, 1L, 5L, buildCmd(2L, inicio, null)))
                    .expectErrorSatisfies(err -> {
                        assertThat(err).isInstanceOf(ConflictException.class);
                        assertThat(err.getMessage()).contains("activa");
                    })
                    .verify();

            verify(tipoMembresiaRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("PENDIENTE — vende sin fechas cuando estadoPago=PENDIENTE (N8)")
        void ventaPendienteSinFechas() {
            LocalDate inicio = LocalDate.of(2026, 1, 1);
            TipoMembresia tipo = buildTipo(2L, TipoMembresia.ModoControl.calendario,
                    TipoMembresia.DuracionTipo.meses, 1, null, BigDecimal.valueOf(50));

            when(membresiaRepository.findPendienteVivaByIdCliente(10L, 1L)).thenReturn(Mono.empty());
            when(tipoMembresiaRepository.findById(2L)).thenReturn(Mono.just(tipo));
            when(membresiaRepository.save(any())).thenAnswer(inv -> {
                Membresia m = inv.getArgument(0);
                m.setId(1L);
                return Mono.just(m);
            });

            StepVerifier.create(membresiaService.vender(10L, 1L, 1L, 5L,
                    buildCmd(2L, inicio, null, Membresia.EstadoPago.PENDIENTE)))
                    .expectNextMatches(m -> m.getFechaInicio() == null
                            && m.getFechaFin() == null
                            && m.getEstadoPago() == Membresia.EstadoPago.PENDIENTE)
                    .verifyComplete();

            // No debe consultar findActiva ni tocar cliente ni disparar evento en PENDIENTE
            verify(membresiaRepository, never()).findActivaByIdClienteAndIdCompania(anyLong(), anyLong());
            verify(clienteRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("PENDIENTE — 409 cuando ya hay otra PENDIENTE viva (N8)")
        void pendienteConflictSiYaHayPendienteViva() {
            LocalDate inicio = LocalDate.of(2026, 1, 1);
            Membresia otraPendiente = buildMembresia(9L, 10L, 1L, 2L, Membresia.Estado.activa, null, null);
            otraPendiente.setEstadoPago(Membresia.EstadoPago.PENDIENTE);
            otraPendiente.setEliminado(false);

            when(membresiaRepository.findPendienteVivaByIdCliente(10L, 1L))
                    .thenReturn(Mono.just(otraPendiente));

            StepVerifier.create(membresiaService.vender(10L, 1L, 1L, 5L,
                    buildCmd(2L, inicio, null, Membresia.EstadoPago.PENDIENTE)))
                    .expectErrorSatisfies(err -> {
                        assertThat(err).isInstanceOf(ConflictException.class);
                        assertThat(err.getMessage()).contains("pendiente");
                    })
                    .verify();

            verify(tipoMembresiaRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("PENDIENTE — ignora existencia de PAGADA activa (renovación anticipada)")
        void pendientePermiteAunConPagadaActiva() {
            LocalDate inicio = LocalDate.of(2026, 1, 1);
            TipoMembresia tipo = buildTipo(2L, TipoMembresia.ModoControl.calendario,
                    TipoMembresia.DuracionTipo.meses, 1, null, BigDecimal.valueOf(50));

            // Solo mockeamos la query de PENDIENTE — la de PAGADA no debe consultarse.
            when(membresiaRepository.findPendienteVivaByIdCliente(10L, 1L)).thenReturn(Mono.empty());
            when(tipoMembresiaRepository.findById(2L)).thenReturn(Mono.just(tipo));
            when(membresiaRepository.save(any())).thenAnswer(inv -> {
                Membresia m = inv.getArgument(0);
                m.setId(99L);
                return Mono.just(m);
            });

            StepVerifier.create(membresiaService.vender(10L, 1L, 1L, 5L,
                    buildCmd(2L, inicio, null, Membresia.EstadoPago.PENDIENTE)))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(membresiaRepository, never()).findActivaByIdClienteAndIdCompania(anyLong(), anyLong());
        }

        @Test
        @DisplayName("PAGADO — publica MembresiaPagadaEvent al vender directamente en PAGADO")
        void ventaPagadaEmiteEvento() {
            LocalDate inicio = LocalDate.of(2026, 1, 1);
            TipoMembresia tipo = buildTipo(2L, TipoMembresia.ModoControl.calendario,
                    TipoMembresia.DuracionTipo.meses, 1, null, BigDecimal.valueOf(50));
            Cliente cliente = buildCliente(10L, 20L, 1L, Cliente.Estado.vencido);

            when(membresiaRepository.findActivaByIdClienteAndIdCompania(10L, 1L)).thenReturn(Mono.empty());
            when(tipoMembresiaRepository.findById(2L)).thenReturn(Mono.just(tipo));
            when(membresiaRepository.save(any())).thenAnswer(inv -> {
                Membresia m = inv.getArgument(0);
                m.setId(42L);
                return Mono.just(m);
            });
            when(clienteRepository.findById(10L)).thenReturn(Mono.just(cliente));
            when(clienteRepository.save(any())).thenReturn(Mono.just(cliente));

            StepVerifier.create(membresiaService.vender(10L, 1L, 1L, 5L,
                    buildCmd(2L, inicio, null, Membresia.EstadoPago.PAGADO)))
                    .expectNextCount(1)
                    .verifyComplete();

            ArgumentCaptor<MembresiaPagadaEvent> captor = ArgumentCaptor.forClass(MembresiaPagadaEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().idMembresia()).isEqualTo(42L);
            assertThat(captor.getValue().idCliente()).isEqualTo(10L);
        }
    }

    // -------------------------------------------------------------------------
    // anular
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("anular")
    class Anular {

        @Test
        @DisplayName("anula la membresía y actualiza el estado del cliente a vencido")
        void anulaExitosamente() {
            Membresia mem = buildMembresia(1L, 10L, 1L, 2L, Membresia.Estado.activa,
                    LocalDate.now(), LocalDate.now().plusMonths(1));
            Cliente cliente = buildCliente(10L, 20L, 1L, Cliente.Estado.activo);

            when(membresiaRepository.findById(1L)).thenReturn(Mono.just(mem));
            when(membresiaRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(clienteRepository.findById(10L)).thenReturn(Mono.just(cliente));
            when(clienteRepository.save(any())).thenReturn(Mono.just(cliente));

            StepVerifier.create(membresiaService.anular(1L, 1L, "Motivo de prueba"))
                    .verifyComplete();

            assertThat(mem.getEstado()).isEqualTo(Membresia.Estado.anulada);
        }

        @Test
        @DisplayName("lanza NotFoundException cuando la membresía no existe")
        void lanzaNotFoundSiMembresiaNoExiste() {
            when(membresiaRepository.findById(99L)).thenReturn(Mono.empty());

            StepVerifier.create(membresiaService.anular(99L, 1L, "motivo"))
                    .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(NotFoundException.class))
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    // validarAcceso
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("validarAcceso")
    class ValidarAcceso {

        @Test
        @DisplayName("permite acceso con membresía calendario activa con días restantes")
        void permiteAccesoConCalendarioActivo() {
            Cliente cliente = buildCliente(10L, 100L, 1L, Cliente.Estado.activo);
            Membresia mem = buildMembresia(1L, 10L, 1L, 2L, Membresia.Estado.activa,
                    LocalDate.now().minusDays(5), LocalDate.now().plusDays(25));
            TipoMembresia tipo = buildTipo(2L, TipoMembresia.ModoControl.calendario,
                    TipoMembresia.DuracionTipo.meses, 1, null, BigDecimal.valueOf(50));

            when(clienteRepository.findByIdPersonaAndIdCompania(100L, 1L)).thenReturn(Mono.just(cliente));
            when(membresiaRepository.findActivaByIdClienteAndIdCompania(10L, 1L)).thenReturn(Mono.just(mem));
            when(tipoMembresiaRepository.findById(2L)).thenReturn(Mono.just(tipo));

            StepVerifier.create(membresiaService.validarAcceso(100L, 1L))
                    .expectNextMatches(r -> r.permitido() && r.idCliente().equals(10L))
                    .verifyComplete();
        }

        @Test
        @DisplayName("deniega acceso cuando la membresía está vencida por fecha")
        void denegaAccesoConMembresiaVencidaPorFecha() {
            Cliente cliente = buildCliente(10L, 100L, 1L, Cliente.Estado.vencido);
            Membresia mem = buildMembresia(1L, 10L, 1L, 2L, Membresia.Estado.activa,
                    LocalDate.now().minusMonths(2), LocalDate.now().minusDays(1)); // vencida ayer
            TipoMembresia tipo = buildTipo(2L, TipoMembresia.ModoControl.calendario,
                    TipoMembresia.DuracionTipo.meses, 1, null, BigDecimal.valueOf(50));

            when(clienteRepository.findByIdPersonaAndIdCompania(100L, 1L)).thenReturn(Mono.just(cliente));
            when(membresiaRepository.findActivaByIdClienteAndIdCompania(10L, 1L)).thenReturn(Mono.just(mem));
            when(tipoMembresiaRepository.findById(2L)).thenReturn(Mono.just(tipo));

            StepVerifier.create(membresiaService.validarAcceso(100L, 1L))
                    .expectNextMatches(r -> !r.permitido() && "membresia_vencida".equals(r.razon()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("deniega acceso cuando la membresía está congelada")
        void denegaAccesoConMembresiaCongelada() {
            Cliente cliente = buildCliente(10L, 100L, 1L, Cliente.Estado.congelado);
            Membresia mem = buildMembresia(1L, 10L, 1L, 2L, Membresia.Estado.congelada,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1));

            when(clienteRepository.findByIdPersonaAndIdCompania(100L, 1L)).thenReturn(Mono.just(cliente));
            when(membresiaRepository.findActivaByIdClienteAndIdCompania(10L, 1L)).thenReturn(Mono.just(mem));

            StepVerifier.create(membresiaService.validarAcceso(100L, 1L))
                    .expectNextMatches(r -> !r.permitido() && "membresia_congelada".equals(r.razon()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("deniega acceso cuando los accesos del tipo accesos están agotados")
        void denegaAccesoCuandoAccesosAgotados() {
            Cliente cliente = buildCliente(10L, 100L, 1L, Cliente.Estado.activo);
            Membresia mem = buildMembresia(1L, 10L, 1L, 3L, Membresia.Estado.activa,
                    LocalDate.now().minusDays(5), LocalDate.now().plusDays(25));
            mem.setDiasAccesoTotal(10);

            TipoMembresia tipo = buildTipo(3L, TipoMembresia.ModoControl.accesos,
                    TipoMembresia.DuracionTipo.meses, 1, 10, BigDecimal.valueOf(60));

            when(clienteRepository.findByIdPersonaAndIdCompania(100L, 1L)).thenReturn(Mono.just(cliente));
            when(membresiaRepository.findActivaByIdClienteAndIdCompania(10L, 1L)).thenReturn(Mono.just(mem));
            when(tipoMembresiaRepository.findById(3L)).thenReturn(Mono.just(tipo));
            when(membresiaRepository.countAsistenciasByIdMembresia(1L)).thenReturn(Mono.just(10L)); // agotados

            StepVerifier.create(membresiaService.validarAcceso(100L, 1L))
                    .expectNextMatches(r -> !r.permitido() && "accesos_agotados".equals(r.razon()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("permite acceso con tipo accesos cuando aún quedan accesos")
        void permiteAccesoConAccesosRestantes() {
            Cliente cliente = buildCliente(10L, 100L, 1L, Cliente.Estado.activo);
            Membresia mem = buildMembresia(1L, 10L, 1L, 3L, Membresia.Estado.activa,
                    LocalDate.now().minusDays(5), LocalDate.now().plusDays(25));
            mem.setDiasAccesoTotal(10);

            TipoMembresia tipo = buildTipo(3L, TipoMembresia.ModoControl.accesos,
                    TipoMembresia.DuracionTipo.meses, 1, 10, BigDecimal.valueOf(60));

            when(clienteRepository.findByIdPersonaAndIdCompania(100L, 1L)).thenReturn(Mono.just(cliente));
            when(membresiaRepository.findActivaByIdClienteAndIdCompania(10L, 1L)).thenReturn(Mono.just(mem));
            when(tipoMembresiaRepository.findById(3L)).thenReturn(Mono.just(tipo));
            when(membresiaRepository.countAsistenciasByIdMembresia(1L)).thenReturn(Mono.just(7L)); // 3 restantes

            StepVerifier.create(membresiaService.validarAcceso(100L, 1L))
                    .expectNextMatches(r -> r.permitido()
                            && r.diasAccesoRestantes() != null
                            && r.diasAccesoRestantes() == 3)
                    .verifyComplete();
        }

        @Test
        @DisplayName("deniega acceso cuando el cliente no tiene membresía activa")
        void denegaAccesoCuandoSinMembresiaActiva() {
            Cliente cliente = buildCliente(10L, 100L, 1L, Cliente.Estado.vencido);

            when(clienteRepository.findByIdPersonaAndIdCompania(100L, 1L)).thenReturn(Mono.just(cliente));
            when(membresiaRepository.findPendienteVivaByIdCliente(10L, 1L)).thenReturn(Mono.empty());
            when(membresiaRepository.findUltimaRechazadaByIdCliente(10L, 1L)).thenReturn(Mono.empty());
            when(membresiaRepository.findActivaByIdClienteAndIdCompania(10L, 1L)).thenReturn(Mono.empty());

            StepVerifier.create(membresiaService.validarAcceso(100L, 1L))
                    .expectNextMatches(r -> !r.permitido() && "sin_membresia".equals(r.razon()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("deniega acceso (sin error) cuando la persona no está registrada como cliente en esta compañía")
        void denegaAccesoCuandoPersonaNoRegistradaComoCliente() {
            when(clienteRepository.findByIdPersonaAndIdCompania(999L, 1L)).thenReturn(Mono.empty());

            StepVerifier.create(membresiaService.validarAcceso(999L, 1L))
                    .expectNextMatches(r -> !r.permitido()
                            && r.idCliente() == null
                            && "sin_membresia".equals(r.razon()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("devuelve pago_pendiente cuando existe una PENDIENTE viva")
        void denegaConPagoPendiente() {
            Cliente cliente = buildCliente(10L, 100L, 1L, Cliente.Estado.activo);
            Membresia pendiente = buildMembresia(7L, 10L, 1L, 2L, Membresia.Estado.activa, null, null);
            pendiente.setEstadoPago(Membresia.EstadoPago.PENDIENTE);
            pendiente.setEliminado(false);

            when(clienteRepository.findByIdPersonaAndIdCompania(100L, 1L)).thenReturn(Mono.just(cliente));
            when(membresiaRepository.findPendienteVivaByIdCliente(10L, 1L)).thenReturn(Mono.just(pendiente));

            StepVerifier.create(membresiaService.validarAcceso(100L, 1L))
                    .expectNextMatches(r -> !r.permitido()
                            && "pago_pendiente".equals(r.razon())
                            && r.idMembresia().equals(7L))
                    .verifyComplete();

            verify(membresiaRepository, never()).findActivaByIdClienteAndIdCompania(anyLong(), anyLong());
        }

        @Test
        @DisplayName("devuelve membresia_rechazada cuando la única fila del cliente está eliminada")
        void denegaConMembresiaRechazada() {
            Cliente cliente = buildCliente(10L, 100L, 1L, Cliente.Estado.vencido);
            Membresia rechazada = buildMembresia(8L, 10L, 1L, 2L, Membresia.Estado.activa, null, null);
            rechazada.setEstadoPago(Membresia.EstadoPago.PENDIENTE);
            rechazada.setEliminado(true);

            when(clienteRepository.findByIdPersonaAndIdCompania(100L, 1L)).thenReturn(Mono.just(cliente));
            when(membresiaRepository.findPendienteVivaByIdCliente(10L, 1L)).thenReturn(Mono.empty());
            when(membresiaRepository.findUltimaRechazadaByIdCliente(10L, 1L)).thenReturn(Mono.just(rechazada));

            StepVerifier.create(membresiaService.validarAcceso(100L, 1L))
                    .expectNextMatches(r -> !r.permitido()
                            && "membresia_rechazada".equals(r.razon())
                            && r.idMembresia().equals(8L))
                    .verifyComplete();

            verify(membresiaRepository, never()).findActivaByIdClienteAndIdCompania(anyLong(), anyLong());
        }
    }

    // -------------------------------------------------------------------------
    // confirmarPago
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("confirmarPago")
    class ConfirmarPago {

        @Test
        @DisplayName("transiciona PENDIENTE → PAGADO, calcula fechas y publica evento")
        void confirmaPendienteExitosamente() {
            Membresia pendiente = buildMembresia(1L, 10L, 1L, 2L, Membresia.Estado.activa, null, null);
            pendiente.setEstadoPago(Membresia.EstadoPago.PENDIENTE);
            pendiente.setEliminado(false);
            TipoMembresia tipo = buildTipo(2L, TipoMembresia.ModoControl.calendario,
                    TipoMembresia.DuracionTipo.meses, 1, null, BigDecimal.valueOf(50));
            Cliente cliente = buildCliente(10L, 20L, 1L, Cliente.Estado.vencido);

            when(membresiaRepository.findById(1L)).thenReturn(Mono.just(pendiente));
            when(tipoMembresiaRepository.findById(2L)).thenReturn(Mono.just(tipo));
            when(membresiaRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(clienteRepository.findById(10L)).thenReturn(Mono.just(cliente));
            when(clienteRepository.save(any())).thenReturn(Mono.just(cliente));

            StepVerifier.create(membresiaService.confirmarPago(1L, 1L, 99L))
                    .expectNextMatches(m -> m.getEstadoPago() == Membresia.EstadoPago.PAGADO
                            && m.getFechaInicio().equals(LocalDate.now())
                            && m.getFechaFin().equals(LocalDate.now().plusMonths(1)))
                    .verifyComplete();

            ArgumentCaptor<MembresiaPagadaEvent> captor = ArgumentCaptor.forClass(MembresiaPagadaEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().idMembresia()).isEqualTo(1L);
            assertThat(captor.getValue().montoPagado()).isEqualByComparingTo(BigDecimal.valueOf(50));
        }

        @Test
        @DisplayName("es idempotente cuando ya está PAGADO: no recalcula fechas ni publica evento")
        void idempotenteSiYaPagado() {
            LocalDate inicioViejo = LocalDate.of(2025, 1, 1);
            LocalDate finViejo = LocalDate.of(2025, 2, 1);
            Membresia pagada = buildMembresia(1L, 10L, 1L, 2L, Membresia.Estado.activa, inicioViejo, finViejo);
            pagada.setEstadoPago(Membresia.EstadoPago.PAGADO);
            pagada.setEliminado(false);

            when(membresiaRepository.findById(1L)).thenReturn(Mono.just(pagada));

            StepVerifier.create(membresiaService.confirmarPago(1L, 1L, 99L))
                    .expectNextMatches(m -> m.getFechaInicio().equals(inicioViejo)
                            && m.getFechaFin().equals(finViejo))
                    .verifyComplete();

            verify(membresiaRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("lanza 409 cuando la membresía está eliminada")
        void conflictSiEliminada() {
            Membresia eliminada = buildMembresia(1L, 10L, 1L, 2L, Membresia.Estado.activa, null, null);
            eliminada.setEstadoPago(Membresia.EstadoPago.PENDIENTE);
            eliminada.setEliminado(true);

            when(membresiaRepository.findById(1L)).thenReturn(Mono.just(eliminada));

            StepVerifier.create(membresiaService.confirmarPago(1L, 1L, 99L))
                    .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(ConflictException.class))
                    .verify();

            verify(membresiaRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("lanza NotFoundException cuando la membresía no existe")
        void notFoundSiNoExiste() {
            when(membresiaRepository.findById(99L)).thenReturn(Mono.empty());

            StepVerifier.create(membresiaService.confirmarPago(99L, 1L, 5L))
                    .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(NotFoundException.class))
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    // rechazar
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("rechazar")
    class Rechazar {

        @Test
        @DisplayName("rechaza una PENDIENTE seteando eliminado + auditoría")
        void rechazaPendienteExitosamente() {
            Membresia pendiente = buildMembresia(1L, 10L, 1L, 2L, Membresia.Estado.activa, null, null);
            pendiente.setEstadoPago(Membresia.EstadoPago.PENDIENTE);
            pendiente.setEliminado(false);

            when(membresiaRepository.findById(1L)).thenReturn(Mono.just(pendiente));
            when(membresiaRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            OffsetDateTime antes = OffsetDateTime.now().minusSeconds(1);

            StepVerifier.create(membresiaService.rechazar(1L, 1L, 42L,
                    Membresia.MotivoEliminacion.SOCIO_CAMBIO_OPINION))
                    .expectNextMatches(m -> Boolean.TRUE.equals(m.getEliminado())
                            && m.getMotivoEliminacion() == Membresia.MotivoEliminacion.SOCIO_CAMBIO_OPINION
                            && m.getEliminadoPor() != null && m.getEliminadoPor() == 42
                            && m.getFechaEliminacion() != null
                            && !m.getFechaEliminacion().isBefore(antes))
                    .verifyComplete();
        }

        @Test
        @DisplayName("409 cuando la membresía ya está PAGADA")
        void conflictSiPagada() {
            Membresia pagada = buildMembresia(1L, 10L, 1L, 2L, Membresia.Estado.activa,
                    LocalDate.now(), LocalDate.now().plusMonths(1));
            pagada.setEstadoPago(Membresia.EstadoPago.PAGADO);
            pagada.setEliminado(false);

            when(membresiaRepository.findById(1L)).thenReturn(Mono.just(pagada));

            StepVerifier.create(membresiaService.rechazar(1L, 1L, 42L,
                    Membresia.MotivoEliminacion.OTRO))
                    .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(ConflictException.class))
                    .verify();

            verify(membresiaRepository, never()).save(any());
        }

        @Test
        @DisplayName("409 cuando ya estaba eliminada")
        void conflictSiYaEliminada() {
            Membresia rechazadaYa = buildMembresia(1L, 10L, 1L, 2L, Membresia.Estado.activa, null, null);
            rechazadaYa.setEstadoPago(Membresia.EstadoPago.PENDIENTE);
            rechazadaYa.setEliminado(true);

            when(membresiaRepository.findById(1L)).thenReturn(Mono.just(rechazadaYa));

            StepVerifier.create(membresiaService.rechazar(1L, 1L, 42L,
                    Membresia.MotivoEliminacion.OTRO))
                    .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(ConflictException.class))
                    .verify();
        }

        @Test
        @DisplayName("404 cuando la membresía no existe")
        void notFoundSiNoExiste() {
            when(membresiaRepository.findById(99L)).thenReturn(Mono.empty());

            StepVerifier.create(membresiaService.rechazar(99L, 1L, 42L,
                    Membresia.MotivoEliminacion.OTRO))
                    .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(NotFoundException.class))
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    // listarPendientesPorCompania
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("listarPendientesPorCompania")
    class ListarPendientes {

        @Test
        @DisplayName("enriquece cada pendiente con nombre y modo del tipo y nombre del cliente")
        void enriqueceConTipo() {
            Membresia p1 = buildMembresia(1L, 10L, 1L, 2L, Membresia.Estado.activa, null, null);
            p1.setEstadoPago(Membresia.EstadoPago.PENDIENTE);
            Membresia p2 = buildMembresia(2L, 11L, 1L, 3L, Membresia.Estado.activa, null, null);
            p2.setEstadoPago(Membresia.EstadoPago.PENDIENTE);

            TipoMembresia tipoCal = buildTipo(2L, TipoMembresia.ModoControl.calendario,
                    TipoMembresia.DuracionTipo.meses, 1, null, BigDecimal.valueOf(50));
            TipoMembresia tipoAcc = buildTipo(3L, TipoMembresia.ModoControl.accesos,
                    TipoMembresia.DuracionTipo.meses, 1, 10, BigDecimal.valueOf(60));

            Cliente c1 = new Cliente(); c1.setId(10L); c1.setIdPersona(100L);
            Cliente c2 = new Cliente(); c2.setId(11L); c2.setIdPersona(101L);

            when(membresiaRepository.findPendientesPorCompania(1L)).thenReturn(Flux.just(p1, p2));
            when(tipoMembresiaRepository.findById(2L)).thenReturn(Mono.just(tipoCal));
            when(tipoMembresiaRepository.findById(3L)).thenReturn(Mono.just(tipoAcc));
            when(clienteRepository.findById(10L)).thenReturn(Mono.just(c1));
            when(clienteRepository.findById(11L)).thenReturn(Mono.just(c2));
            when(personaRepository.findNombreById(100L)).thenReturn(Mono.just("Ana Pérez"));
            when(personaRepository.findNombreById(101L)).thenReturn(Mono.just("Bruno Díaz"));

            StepVerifier.create(membresiaService.listarPendientesPorCompania(1L))
                    .expectNextMatches(r -> r.membresia().getId().equals(1L)
                            && "Tipo 2".equals(r.tipoNombre())
                            && "calendario".equals(r.modoControl())
                            && "Ana Pérez".equals(r.nombreCliente()))
                    .expectNextMatches(r -> r.membresia().getId().equals(2L)
                            && "Tipo 3".equals(r.tipoNombre())
                            && "accesos".equals(r.modoControl())
                            && "Bruno Díaz".equals(r.nombreCliente()))
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    // actualizarAsistenciasPrevias
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("actualizarAsistenciasPrevias")
    class ActualizarAsistenciasPrevias {

        @Test
        @DisplayName("actualiza las asistencias previas exitosamente")
        void actualizaExitosamente() {
            Membresia mem = buildMembresia(1L, 10L, 1L, 2L, Membresia.Estado.activa,
                    LocalDate.now(), LocalDate.now().plusMonths(1));
            when(membresiaRepository.findById(1L)).thenReturn(Mono.just(mem));
            when(membresiaRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(membresiaService.actualizarAsistenciasPrevias(1L, 1L, 5))
                    .expectNextMatches(m -> m.getAsistenciasPrevias() != null
                            && m.getAsistenciasPrevias() == 5)
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando la membresía no existe")
        void lanzaNotFoundSiMembresiaNoExiste() {
            when(membresiaRepository.findById(99L)).thenReturn(Mono.empty());

            StepVerifier.create(membresiaService.actualizarAsistenciasPrevias(99L, 1L, 3))
                    .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(NotFoundException.class))
                    .verify();
        }
    }
}
