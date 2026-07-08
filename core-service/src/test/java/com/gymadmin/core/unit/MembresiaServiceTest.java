package com.gymadmin.core.unit;

import com.gymadmin.core.application.service.MembresiaService;
import com.gymadmin.core.domain.model.Cliente;
import com.gymadmin.core.domain.model.Membresia;
import com.gymadmin.core.domain.model.TipoMembresia;
import com.gymadmin.core.domain.port.in.MembresiaUseCase;
import com.gymadmin.core.domain.port.out.ClienteRepository;
import com.gymadmin.core.domain.port.out.MembresiaRepository;
import com.gymadmin.core.domain.port.out.TipoMembresiaRepository;
import com.gymadmin.core.infrastructure.exception.BusinessException;
import com.gymadmin.core.infrastructure.exception.ConflictException;
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

import java.math.BigDecimal;
import java.time.LocalDate;

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
            return new MembresiaUseCase.VenderCommand(idTipo, inicio, 1L, descuento);
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
