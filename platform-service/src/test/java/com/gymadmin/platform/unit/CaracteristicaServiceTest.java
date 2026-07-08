package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.CaracteristicaService;
import com.gymadmin.platform.domain.model.Caracteristica;
import com.gymadmin.platform.domain.port.in.CaracteristicaUseCase.CrearCaracteristicaCommand;
import com.gymadmin.platform.domain.port.out.CaracteristicaRepository;
import com.gymadmin.platform.infrastructure.exception.ConflictException;
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
@DisplayName("CaracteristicaService — gestión de características de planes")
class CaracteristicaServiceTest {

    @Mock
    private CaracteristicaRepository repository;

    @InjectMocks
    private CaracteristicaService service;

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("listarCaracteristicas")
    class ListarCaracteristicas {

        @Test
        @DisplayName("retorna todas las características cuando existen registros")
        void retornaTodasLasCaracteristicas() {
            Caracteristica c1 = new Caracteristica(1L, "ACCESO_APP", "Acceso a la app", "mobile", true);
            Caracteristica c2 = new Caracteristica(2L, "REPORTES", "Reportes avanzados", "analytics", true);
            when(repository.findAll()).thenReturn(Flux.just(c1, c2));

            StepVerifier.create(service.listarCaracteristicas())
                    .expectNextMatches(c -> "ACCESO_APP".equals(c.getCodigo()))
                    .expectNextMatches(c -> "REPORTES".equals(c.getCodigo()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna flux vacío cuando no existen características")
        void retornaFluxVacioCuandoNoHayCaracteristicas() {
            when(repository.findAll()).thenReturn(Flux.empty());

            StepVerifier.create(service.listarCaracteristicas())
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("crearCaracteristica")
    class CrearCaracteristica {

        @Test
        @DisplayName("crea y retorna la característica cuando el codigo no existe")
        void creaCaracteristicaCuandoCodigoNoExiste() {
            CrearCaracteristicaCommand command = new CrearCaracteristicaCommand(
                    "NUEVA_FUNC", "Nueva funcionalidad", "core");

            Caracteristica guardada = new Caracteristica(10L, "NUEVA_FUNC", "Nueva funcionalidad", "core", true);

            when(repository.findByCodigo("NUEVA_FUNC")).thenReturn(Mono.empty());
            when(repository.save(any(Caracteristica.class))).thenReturn(Mono.just(guardada));

            StepVerifier.create(service.crearCaracteristica(command))
                    .assertNext(c -> {
                        assertThat(c.getCodigo()).isEqualTo("NUEVA_FUNC");
                        assertThat(c.getNombre()).isEqualTo("Nueva funcionalidad");
                        assertThat(c.getModulo()).isEqualTo("core");
                        assertThat(c.getActivo()).isTrue();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ConflictException cuando el codigo ya existe")
        void lanzaConflictExceptionCuandoCodigoYaExiste() {
            CrearCaracteristicaCommand command = new CrearCaracteristicaCommand(
                    "ACCESO_APP", "Acceso duplicado", "mobile");

            Caracteristica existente = new Caracteristica(1L, "ACCESO_APP", "Acceso a la app", "mobile", true);
            when(repository.findByCodigo("ACCESO_APP")).thenReturn(Mono.just(existente));

            StepVerifier.create(service.crearCaracteristica(command))
                    .expectError(ConflictException.class)
                    .verify();
        }
    }
}
