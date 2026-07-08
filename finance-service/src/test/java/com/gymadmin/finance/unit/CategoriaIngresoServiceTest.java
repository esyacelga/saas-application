package com.gymadmin.finance.unit;

import com.gymadmin.finance.application.service.CategoriaIngresoService;
import com.gymadmin.finance.domain.model.CategoriaIngreso;
import com.gymadmin.finance.domain.port.in.CategoriaIngresoUseCase;
import com.gymadmin.finance.domain.port.out.CategoriaIngresoRepository;
import com.gymadmin.finance.infrastructure.exception.ConflictException;
import com.gymadmin.finance.infrastructure.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoriaIngresoService — gestión de categorías de ingreso")
class CategoriaIngresoServiceTest {

    @Mock
    private CategoriaIngresoRepository repository;

    @InjectMocks
    private CategoriaIngresoService service;

    // =========================================================================

    @Nested
    @DisplayName("listar")
    class Listar {

        @Test
        @DisplayName("retorna todas las categorías de la compañía cuando idSucursal es null")
        void retornaTodasCuandoSucursalEsNull() {
            CategoriaIngreso cat1 = CategoriaIngreso.builder()
                    .id(1).idCompania(10).nombre("Membresías").activo(true).eliminado(false).build();
            CategoriaIngreso cat2 = CategoriaIngreso.builder()
                    .id(2).idCompania(10).nombre("Clases").activo(true).eliminado(false).build();

            when(repository.findByIdCompania(10)).thenReturn(Flux.just(cat1, cat2));

            StepVerifier.create(service.listar(10, null))
                    .expectNext(cat1)
                    .expectNext(cat2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna categorías filtradas por sucursal cuando idSucursal está presente")
        void retornaFiltradoPorSucursal() {
            CategoriaIngreso cat = CategoriaIngreso.builder()
                    .id(1).idCompania(10).idSucursal(5).nombre("Membresías").activo(true).eliminado(false).build();

            when(repository.findByIdCompaniaAndIdSucursal(10, 5)).thenReturn(Flux.just(cat));

            StepVerifier.create(service.listar(10, 5))
                    .expectNext(cat)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna Flux vacío cuando no hay categorías para la compañía")
        void retornaVacioCuandoNoHayCategorias() {
            when(repository.findByIdCompania(99)).thenReturn(Flux.empty());

            StepVerifier.create(service.listar(99, null))
                    .verifyComplete();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("crear")
    class Crear {

        @Test
        @DisplayName("crea y guarda una categoría con activo=true y eliminado=false")
        void creaYGuardaCorrectamente() {
            CategoriaIngresoUseCase.CrearCommand command =
                    new CategoriaIngresoUseCase.CrearCommand(10, 5, "Membresías");

            CategoriaIngreso guardada = CategoriaIngreso.builder()
                    .id(1).idCompania(10).idSucursal(5).nombre("Membresías").activo(true).eliminado(false).build();

            ArgumentCaptor<CategoriaIngreso> captor = ArgumentCaptor.forClass(CategoriaIngreso.class);
            when(repository.save(captor.capture())).thenReturn(Mono.just(guardada));

            StepVerifier.create(service.crear(command))
                    .expectNext(guardada)
                    .verifyComplete();

            CategoriaIngreso capturada = captor.getValue();
            assertThat(capturada.getIdCompania()).isEqualTo(10);
            assertThat(capturada.getIdSucursal()).isEqualTo(5);
            assertThat(capturada.getNombre()).isEqualTo("Membresías");
            assertThat(capturada.getActivo()).isTrue();
            assertThat(capturada.getEliminado()).isFalse();
        }

        @Test
        @DisplayName("la categoría guardada hereda idCompania y nombre del comando")
        void categoriaHeredaDatosDelComando() {
            CategoriaIngresoUseCase.CrearCommand command =
                    new CategoriaIngresoUseCase.CrearCommand(7, null, "Venta de suplementos");

            ArgumentCaptor<CategoriaIngreso> captor = ArgumentCaptor.forClass(CategoriaIngreso.class);
            CategoriaIngreso guardada = CategoriaIngreso.builder()
                    .id(3).idCompania(7).nombre("Venta de suplementos").activo(true).eliminado(false).build();

            when(repository.save(captor.capture())).thenReturn(Mono.just(guardada));

            StepVerifier.create(service.crear(command))
                    .expectNextMatches(c -> c.getIdCompania().equals(7) && c.getNombre().equals("Venta de suplementos"))
                    .verifyComplete();

            assertThat(captor.getValue().getIdCompania()).isEqualTo(7);
            assertThat(captor.getValue().getNombre()).isEqualTo("Venta de suplementos");
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("desactivar")
    class Desactivar {

        @Test
        @DisplayName("desactiva la categoría exitosamente cuando existe y no tiene ingresos")
        void desactivaCuandoExisteYNoEstaEnUso() {
            CategoriaIngreso cat = CategoriaIngreso.builder()
                    .id(1).idCompania(10).nombre("Membresías").activo(true).eliminado(false).build();

            CategoriaIngreso catDesactivada = CategoriaIngreso.builder()
                    .id(1).idCompania(10).nombre("Membresías").activo(false).eliminado(false).build();

            when(repository.findByIdAndIdCompania(1, 10)).thenReturn(Mono.just(cat));
            when(repository.existsIngresosByIdCategoria(1)).thenReturn(Mono.just(false));
            when(repository.save(any(CategoriaIngreso.class))).thenReturn(Mono.just(catDesactivada));

            StepVerifier.create(service.desactivar(1, 10))
                    .expectNextMatches(c -> !c.getActivo())
                    .verifyComplete();
        }

        @Test
        @DisplayName("emite NotFoundException cuando la categoría no existe para la compañía")
        void emiteNotFoundCuandoNoExiste() {
            when(repository.findByIdAndIdCompania(99, 10)).thenReturn(Mono.empty());

            StepVerifier.create(service.desactivar(99, 10))
                    .expectErrorMatches(e -> e instanceof NotFoundException
                            && e.getMessage().contains("99"))
                    .verify();
        }

        @Test
        @DisplayName("emite ConflictException con código CATEGORIA_EN_USO cuando tiene ingresos")
        void emiteConflictCuandoCategoriaEstaEnUso() {
            CategoriaIngreso cat = CategoriaIngreso.builder()
                    .id(1).idCompania(10).nombre("Membresías").activo(true).eliminado(false).build();

            when(repository.findByIdAndIdCompania(1, 10)).thenReturn(Mono.just(cat));
            when(repository.existsIngresosByIdCategoria(1)).thenReturn(Mono.just(true));

            StepVerifier.create(service.desactivar(1, 10))
                    .expectErrorMatches(e -> e instanceof ConflictException
                            && ((ConflictException) e).getCodigo().equals("CATEGORIA_EN_USO"))
                    .verify();
        }
    }
}
