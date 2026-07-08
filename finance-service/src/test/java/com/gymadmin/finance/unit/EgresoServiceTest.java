package com.gymadmin.finance.unit;

import com.gymadmin.finance.application.service.EgresoService;
import com.gymadmin.finance.domain.model.CategoriaEgreso;
import com.gymadmin.finance.domain.model.Egreso;
import com.gymadmin.finance.domain.port.in.EgresoUseCase.ListarCommand;
import com.gymadmin.finance.domain.port.in.EgresoUseCase.RegistrarCommand;
import com.gymadmin.finance.domain.port.out.CategoriaEgresoRepository;
import com.gymadmin.finance.domain.port.out.EgresoRepository;
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
@DisplayName("EgresoService — registro y listado de egresos")
class EgresoServiceTest {

    @Mock
    private EgresoRepository egresoRepository;

    @Mock
    private CategoriaEgresoRepository categoriaRepository;

    @InjectMocks
    private EgresoService service;

    private CategoriaEgreso buildCategoria(Integer id, Integer idCompania, String nombre) {
        return CategoriaEgreso.builder()
                .id(id).idCompania(idCompania).nombre(nombre).activo(true).eliminado(false).build();
    }

    private Egreso buildEgreso(Integer id, Integer idCompania, Integer idCategoria, BigDecimal monto) {
        return Egreso.builder()
                .id(id).idCompania(idCompania).idCategoria(idCategoria)
                .monto(monto).descripcion("Test").fecha(LocalDate.now()).eliminado(false).build();
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("registrar")
    class Registrar {

        @Test
        @DisplayName("registra un egreso exitosamente cuando la categoría existe")
        void registraExitosamente() {
            CategoriaEgreso cat = buildCategoria(1, 1, "Sueldos");
            RegistrarCommand cmd = new RegistrarCommand(
                    1, 1, 1, BigDecimal.valueOf(200.00), "Sueldo empleado", LocalDate.now(), 5
            );
            Egreso saved = buildEgreso(50, 1, 1, BigDecimal.valueOf(200.00));

            when(categoriaRepository.findByIdAndIdCompania(1, 1)).thenReturn(Mono.just(cat));
            when(egresoRepository.save(any())).thenReturn(Mono.just(saved));

            StepVerifier.create(service.registrar(cmd))
                    .assertNext(e -> {
                        assertThat(e.getId()).isEqualTo(50);
                        assertThat(e.getMonto()).isEqualByComparingTo(BigDecimal.valueOf(200.00));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("usa la fecha de hoy cuando la fecha del comando es nula")
        void usaFechaHoyCuandoFechaEsNula() {
            CategoriaEgreso cat = buildCategoria(1, 1, "Servicios");
            RegistrarCommand cmd = new RegistrarCommand(
                    1, 1, 1, BigDecimal.valueOf(50.00), "Luz", null, 5
            );

            when(categoriaRepository.findByIdAndIdCompania(1, 1)).thenReturn(Mono.just(cat));
            when(egresoRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(service.registrar(cmd))
                    .assertNext(e -> assertThat(e.getFecha()).isEqualTo(LocalDate.now()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando la categoría de egreso no existe")
        void lanzaNotFoundCuandoCategoriaNoExiste() {
            RegistrarCommand cmd = new RegistrarCommand(
                    1, 1, 99, BigDecimal.valueOf(100.00), "Desc", LocalDate.now(), 5
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
        @DisplayName("retorna lista paginada con total del período")
        void retornaListaPaginada() {
            LocalDate desde = LocalDate.now().minusMonths(1);
            LocalDate hasta = LocalDate.now();
            ListarCommand cmd = new ListarCommand(1, desde, hasta, null, 1, 10);

            Egreso egreso = buildEgreso(1, 1, 1, BigDecimal.valueOf(150.00));
            CategoriaEgreso cat = buildCategoria(1, 1, "Arriendo");

            when(egresoRepository.countByFilters(1, desde, hasta, null)).thenReturn(Mono.just(1L));
            when(egresoRepository.sumByFilters(1, desde, hasta, null))
                    .thenReturn(Mono.just(BigDecimal.valueOf(150.00)));
            when(egresoRepository.findByFilters(eq(1), eq(desde), eq(hasta), eq(null), anyInt(), anyLong()))
                    .thenReturn(Flux.just(egreso));
            when(categoriaRepository.findByIdAndIdCompania(1, 1)).thenReturn(Mono.just(cat));

            StepVerifier.create(service.listar(cmd))
                    .assertNext(r -> {
                        assertThat(r.totalRegistros()).isEqualTo(1L);
                        assertThat(r.totalPeriodo()).isEqualByComparingTo(BigDecimal.valueOf(150.00));
                        assertThat(r.datos()).hasSize(1);
                        assertThat(r.datos().get(0).categoria()).isEqualTo("Arriendo");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna totalPeriodo=ZERO cuando sumByFilters retorna null")
        void usaCeroWhenSumNull() {
            LocalDate desde = LocalDate.now().minusMonths(1);
            LocalDate hasta = LocalDate.now();
            ListarCommand cmd = new ListarCommand(1, desde, hasta, null, 1, 10);

            when(egresoRepository.countByFilters(1, desde, hasta, null)).thenReturn(Mono.just(0L));
            when(egresoRepository.sumByFilters(1, desde, hasta, null)).thenReturn(Mono.just(null));
            when(egresoRepository.findByFilters(eq(1), eq(desde), eq(hasta), eq(null), anyInt(), anyLong()))
                    .thenReturn(Flux.empty());

            StepVerifier.create(service.listar(cmd))
                    .assertNext(r -> {
                        assertThat(r.totalPeriodo()).isEqualByComparingTo(BigDecimal.ZERO);
                        assertThat(r.datos()).isEmpty();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("usa 'Sin categoría' como nombre cuando la categoría no existe en la compañía")
        void usaFallbackCuandoCategoriaNoEncontrada() {
            LocalDate desde = LocalDate.now().minusMonths(1);
            LocalDate hasta = LocalDate.now();
            ListarCommand cmd = new ListarCommand(1, desde, hasta, null, 1, 10);

            Egreso egreso = buildEgreso(1, 1, 99, BigDecimal.valueOf(50.00));

            when(egresoRepository.countByFilters(1, desde, hasta, null)).thenReturn(Mono.just(1L));
            when(egresoRepository.sumByFilters(1, desde, hasta, null))
                    .thenReturn(Mono.just(BigDecimal.valueOf(50.00)));
            when(egresoRepository.findByFilters(eq(1), eq(desde), eq(hasta), eq(null), anyInt(), anyLong()))
                    .thenReturn(Flux.just(egreso));
            when(categoriaRepository.findByIdAndIdCompania(99, 1)).thenReturn(Mono.empty());

            StepVerifier.create(service.listar(cmd))
                    .assertNext(r -> assertThat(r.datos().get(0).categoria()).isEqualTo("Sin categoría"))
                    .verifyComplete();
        }
    }
}
