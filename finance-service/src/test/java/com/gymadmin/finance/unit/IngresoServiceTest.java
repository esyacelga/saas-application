package com.gymadmin.finance.unit;

import com.gymadmin.finance.application.service.IngresoService;
import com.gymadmin.finance.domain.model.CategoriaIngreso;
import com.gymadmin.finance.domain.model.Ingreso;
import com.gymadmin.finance.domain.port.in.IngresoUseCase.IngresoListResult;
import com.gymadmin.finance.domain.port.in.IngresoUseCase.ListarCommand;
import com.gymadmin.finance.domain.port.in.IngresoUseCase.RegistrarCommand;
import com.gymadmin.finance.domain.port.out.CategoriaIngresoRepository;
import com.gymadmin.finance.domain.port.out.IngresoRepository;
import com.gymadmin.finance.infrastructure.exception.NotFoundException;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IngresoService — registro y listado de ingresos")
class IngresoServiceTest {

    @Mock
    private IngresoRepository ingresoRepository;

    @Mock
    private CategoriaIngresoRepository categoriaRepository;

    @InjectMocks
    private IngresoService service;

    private CategoriaIngreso buildCategoria(Integer id, Integer idCompania, String nombre) {
        return CategoriaIngreso.builder()
                .id(id).idCompania(idCompania).nombre(nombre).activo(true).eliminado(false).build();
    }

    private Ingreso buildIngreso(Integer id, Integer idCompania, Integer idCategoria,
                                  BigDecimal monto, Integer idMembresia, Integer idVenta) {
        return Ingreso.builder()
                .id(id).idCompania(idCompania).idCategoria(idCategoria)
                .monto(monto).descripcion("Test").fecha(LocalDate.now())
                .idMembresia(idMembresia).idVenta(idVenta).eliminado(false).build();
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("registrar")
    class Registrar {

        @Test
        @DisplayName("registra un ingreso manual cuando la categoría existe")
        void registraIngresoManual() {
            CategoriaIngreso cat = buildCategoria(1, 1, "Mensualidad");
            RegistrarCommand cmd = new RegistrarCommand(
                    1, 1, 1, BigDecimal.valueOf(50.00), "Descripción", LocalDate.now(),
                    null, null, 10
            );
            Ingreso saved = buildIngreso(100, 1, 1, BigDecimal.valueOf(50.00), null, null);

            when(categoriaRepository.findByIdAndIdCompania(1, 1)).thenReturn(Mono.just(cat));
            when(ingresoRepository.save(any())).thenReturn(Mono.just(saved));

            StepVerifier.create(service.registrar(cmd))
                    .assertNext(i -> {
                        assertThat(i.getId()).isEqualTo(100);
                        assertThat(i.getMonto()).isEqualByComparingTo(BigDecimal.valueOf(50.00));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("usa la fecha de hoy cuando la fecha del comando es nula")
        void usaFechaHoyCuandoFechaEsNula() {
            CategoriaIngreso cat = buildCategoria(1, 1, "Mensualidad");
            RegistrarCommand cmd = new RegistrarCommand(
                    1, 1, 1, BigDecimal.valueOf(50.00), "Desc", null,
                    null, null, 10
            );
            Ingreso saved = buildIngreso(101, 1, 1, BigDecimal.valueOf(50.00), null, null);
            saved.setFecha(LocalDate.now());

            when(categoriaRepository.findByIdAndIdCompania(1, 1)).thenReturn(Mono.just(cat));
            when(ingresoRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(service.registrar(cmd))
                    .assertNext(i -> assertThat(i.getFecha()).isEqualTo(LocalDate.now()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("registra un ingreso vinculado a membresía con origen=membresia")
        void registraIngresoDeMembresia() {
            CategoriaIngreso cat = buildCategoria(2, 1, "Membresía");
            RegistrarCommand cmd = new RegistrarCommand(
                    1, 1, 2, BigDecimal.valueOf(100.00), "Membresía mensual", LocalDate.now(),
                    42, null, 5
            );
            Ingreso saved = buildIngreso(200, 1, 2, BigDecimal.valueOf(100.00), 42, null);

            when(categoriaRepository.findByIdAndIdCompania(2, 1)).thenReturn(Mono.just(cat));
            when(ingresoRepository.save(any())).thenReturn(Mono.just(saved));

            StepVerifier.create(service.registrar(cmd))
                    .assertNext(i -> {
                        assertThat(i.getIdMembresia()).isEqualTo(42);
                        assertThat(i.getIdVenta()).isNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando la categoría no existe en la compañía")
        void lanzaNotFoundCuandoCategoriaNoExiste() {
            RegistrarCommand cmd = new RegistrarCommand(
                    1, 1, 99, BigDecimal.valueOf(50.00), "Desc", LocalDate.now(),
                    null, null, 5
            );
            when(categoriaRepository.findByIdAndIdCompania(99, 1)).thenReturn(Mono.empty());

            StepVerifier.create(service.registrar(cmd))
                    .expectError(NotFoundException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("listar")
    class Listar {

        @Test
        @DisplayName("retorna lista paginada con total de período sumado")
        void retornaListaPaginada() {
            LocalDate desde = LocalDate.now().minusMonths(1);
            LocalDate hasta = LocalDate.now();
            ListarCommand cmd = new ListarCommand(1, desde, hasta, null, 1, 10);

            Ingreso ing = buildIngreso(1, 1, 1, BigDecimal.valueOf(75.00), null, null);
            CategoriaIngreso cat = buildCategoria(1, 1, "Mensualidad");

            when(ingresoRepository.countByFilters(1, desde, hasta, null)).thenReturn(Mono.just(1L));
            when(ingresoRepository.sumByFilters(1, desde, hasta, null))
                    .thenReturn(Mono.just(BigDecimal.valueOf(75.00)));
            when(ingresoRepository.findByFilters(eq(1), eq(desde), eq(hasta), eq(null), anyInt(), anyLong()))
                    .thenReturn(Flux.just(ing));
            when(categoriaRepository.findByIdAndIdCompania(1, 1)).thenReturn(Mono.just(cat));

            StepVerifier.create(service.listar(cmd))
                    .assertNext(r -> {
                        assertThat(r.totalRegistros()).isEqualTo(1L);
                        assertThat(r.totalPeriodo()).isEqualByComparingTo(BigDecimal.valueOf(75.00));
                        assertThat(r.datos()).hasSize(1);
                        assertThat(r.datos().get(0).categoria()).isEqualTo("Mensualidad");
                        assertThat(r.datos().get(0).origen()).isEqualTo("manual");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("usa totalPeriodo=ZERO cuando no hay ingresos (suma cero)")
        void usaCeroWhenSumNull() {
            LocalDate desde = LocalDate.now().minusMonths(1);
            LocalDate hasta = LocalDate.now();
            ListarCommand cmd = new ListarCommand(1, desde, hasta, null, 1, 10);

            // El query real usa COALESCE(SUM(monto), 0): nunca emite null, sino ZERO.
            // Reactor prohíbe Mono.just(null), por eso se modela con Mono.just(ZERO).
            when(ingresoRepository.countByFilters(1, desde, hasta, null)).thenReturn(Mono.just(0L));
            when(ingresoRepository.sumByFilters(1, desde, hasta, null)).thenReturn(Mono.just(BigDecimal.ZERO));
            when(ingresoRepository.findByFilters(eq(1), eq(desde), eq(hasta), eq(null), anyInt(), anyLong()))
                    .thenReturn(Flux.empty());

            StepVerifier.create(service.listar(cmd))
                    .assertNext(r -> {
                        assertThat(r.totalPeriodo()).isEqualByComparingTo(BigDecimal.ZERO);
                        assertThat(r.datos()).isEmpty();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("resuelve origen=membresia cuando el ingreso tiene idMembresia")
        void resuelvOrigenMembresia() {
            LocalDate desde = LocalDate.now().minusMonths(1);
            LocalDate hasta = LocalDate.now();
            ListarCommand cmd = new ListarCommand(1, desde, hasta, null, 1, 10);

            Ingreso ingMembresia = buildIngreso(2, 1, 1, BigDecimal.valueOf(50.00), 10, null);
            CategoriaIngreso cat = buildCategoria(1, 1, "Mensualidad");

            when(ingresoRepository.countByFilters(1, desde, hasta, null)).thenReturn(Mono.just(1L));
            when(ingresoRepository.sumByFilters(1, desde, hasta, null))
                    .thenReturn(Mono.just(BigDecimal.valueOf(50.00)));
            when(ingresoRepository.findByFilters(eq(1), eq(desde), eq(hasta), eq(null), anyInt(), anyLong()))
                    .thenReturn(Flux.just(ingMembresia));
            when(categoriaRepository.findByIdAndIdCompania(1, 1)).thenReturn(Mono.just(cat));

            StepVerifier.create(service.listar(cmd))
                    .assertNext(r -> {
                        assertThat(r.datos().get(0).origen()).isEqualTo("membresia");
                        assertThat(r.datos().get(0).idReferencia()).isEqualTo(10);
                    })
                    .verifyComplete();
        }
    }
}
