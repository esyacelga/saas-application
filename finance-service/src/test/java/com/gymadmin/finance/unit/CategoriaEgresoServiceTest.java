package com.gymadmin.finance.unit;

import com.gymadmin.finance.application.service.CategoriaEgresoService;
import com.gymadmin.finance.domain.model.CategoriaEgreso;
import com.gymadmin.finance.domain.port.in.CategoriaEgresoUseCase.CrearCommand;
import com.gymadmin.finance.domain.port.out.CategoriaEgresoRepository;
import com.gymadmin.finance.infrastructure.exception.ConflictException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoriaEgresoService — gestión de categorías de egreso")
class CategoriaEgresoServiceTest {

    @Mock
    private CategoriaEgresoRepository repository;

    @InjectMocks
    private CategoriaEgresoService service;

    private CategoriaEgreso buildCategoria(Integer id, Integer idCompania, String nombre, boolean activo) {
        return CategoriaEgreso.builder()
                .id(id).idCompania(idCompania).nombre(nombre).activo(activo).eliminado(false).build();
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("listar")
    class Listar {

        @Test
        @DisplayName("retorna todas las categorías de la compañía cuando no se filtra por sucursal")
        void retornaTodasPorCompania() {
            CategoriaEgreso c1 = buildCategoria(1, 1, "Arriendo", true);
            CategoriaEgreso c2 = buildCategoria(2, 1, "Servicios", true);
            when(repository.findByIdCompania(1)).thenReturn(Flux.just(c1, c2));

            StepVerifier.create(service.listar(1, null))
                    .expectNext(c1)
                    .expectNext(c2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna categorías filtradas por sucursal cuando se provee idSucursal")
        void retornaFiltradoPorSucursal() {
            CategoriaEgreso c1 = buildCategoria(1, 1, "Arriendo", true);
            when(repository.findByIdCompaniaAndIdSucursal(1, 2)).thenReturn(Flux.just(c1));

            StepVerifier.create(service.listar(1, 2))
                    .expectNext(c1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna Flux vacío cuando no hay categorías")
        void retornaVacioCuandoSinCategorias() {
            when(repository.findByIdCompania(1)).thenReturn(Flux.empty());

            StepVerifier.create(service.listar(1, null))
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("crear")
    class Crear {

        @Test
        @DisplayName("crea la categoría con activo=true y eliminado=false")
        void creaExitosamente() {
            CrearCommand cmd = new CrearCommand(1, 1, "Sueldos");
            CategoriaEgreso saved = buildCategoria(10, 1, "Sueldos", true);
            when(repository.save(any())).thenReturn(Mono.just(saved));

            StepVerifier.create(service.crear(cmd))
                    .assertNext(c -> {
                        assertThat(c.getNombre()).isEqualTo("Sueldos");
                        assertThat(c.getActivo()).isTrue();
                        assertThat(c.getEliminado()).isFalse();
                    })
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("desactivar")
    class Desactivar {

        @Test
        @DisplayName("desactiva la categoría cuando no está en uso")
        void desactivaExitosamente() {
            CategoriaEgreso cat = buildCategoria(1, 1, "Arriendo", true);
            CategoriaEgreso desactivada = buildCategoria(1, 1, "Arriendo", false);

            when(repository.findByIdAndIdCompania(1, 1)).thenReturn(Mono.just(cat));
            when(repository.existsEgresosByIdCategoria(1)).thenReturn(Mono.just(false));
            when(repository.save(any())).thenReturn(Mono.just(desactivada));

            StepVerifier.create(service.desactivar(1, 1))
                    .assertNext(c -> assertThat(c.getActivo()).isFalse())
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ConflictException cuando la categoría tiene egresos asociados")
        void lanzaConflictCuandoEnUso() {
            CategoriaEgreso cat = buildCategoria(1, 1, "Arriendo", true);

            when(repository.findByIdAndIdCompania(1, 1)).thenReturn(Mono.just(cat));
            when(repository.existsEgresosByIdCategoria(1)).thenReturn(Mono.just(true));

            StepVerifier.create(service.desactivar(1, 1))
                    .expectErrorSatisfies(e -> {
                        assertThat(e).isInstanceOf(ConflictException.class);
                        assertThat(e.getMessage()).contains("en uso");
                    })
                    .verify();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando la categoría no existe en la compañía")
        void lanzaNotFoundCuandoNoExiste() {
            when(repository.findByIdAndIdCompania(99, 1)).thenReturn(Mono.empty());

            StepVerifier.create(service.desactivar(99, 1))
                    .expectError(NotFoundException.class)
                    .verify();
        }
    }
}
