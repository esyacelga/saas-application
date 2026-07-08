package com.gymadmin.auth.unit;

import com.gymadmin.auth.application.service.BitacoraApplicationService;
import com.gymadmin.auth.domain.port.out.BitacoraPort;
import com.gymadmin.auth.dto.response.BitacoraPagedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BitacoraApplicationService")
class BitacoraApplicationServiceTest {

    @Mock
    private BitacoraPort bitacoraPort;

    @InjectMocks
    private BitacoraApplicationService service;

    private OffsetDateTime desde;
    private OffsetDateTime hasta;

    @BeforeEach
    void setUp() {
        desde = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        hasta = OffsetDateTime.parse("2026-12-31T23:59:59Z");
    }

    @Nested
    @DisplayName("listar entradas de bitacora")
    class Listar {

        @Test
        @DisplayName("retorna respuesta paginada con entradas cuando existen registros")
        void retornaPaginaConEntradas() {
            BitacoraPagedResponse.EntryDto entrada = new BitacoraPagedResponse.EntryDto(
                    1L, 100, "Ana Lopez", "usuarios", "CREAR",
                    50, "192.168.1.1", OffsetDateTime.parse("2026-06-15T10:00:00Z")
            );
            BitacoraPagedResponse respuesta = new BitacoraPagedResponse(1L, 0, List.of(entrada));

            when(bitacoraPort.findWithFilters(1, "usuarios", desde, hasta, 100, 0, 10))
                    .thenReturn(Mono.just(respuesta));

            StepVerifier.create(service.listar(1, "usuarios", desde, hasta, 100, 0, 10))
                    .expectNextMatches(r ->
                            r.total() == 1L
                                    && r.pagina() == 0
                                    && r.datos().size() == 1
                                    && r.datos().get(0).modulo().equals("usuarios")
                    )
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna pagina vacia cuando no hay registros en el rango")
        void retornaPaginaVaciaCuandoNoHayRegistros() {
            BitacoraPagedResponse respuesta = new BitacoraPagedResponse(0L, 0, List.of());

            when(bitacoraPort.findWithFilters(1, null, null, null, null, 0, 10))
                    .thenReturn(Mono.just(respuesta));

            StepVerifier.create(service.listar(1, null, null, null, null, 0, 10))
                    .expectNextMatches(r ->
                            r.total() == 0L
                                    && r.datos().isEmpty()
                    )
                    .verifyComplete();
        }

        @Test
        @DisplayName("delega directamente al puerto sin transformaciones")
        void delegaAlPuertoSinTransformaciones() {
            BitacoraPagedResponse respuesta = new BitacoraPagedResponse(5L, 1, List.of());

            when(bitacoraPort.findWithFilters(2, "roles", desde, hasta, null, 1, 20))
                    .thenReturn(Mono.just(respuesta));

            StepVerifier.create(service.listar(2, "roles", desde, hasta, null, 1, 20))
                    .expectNextMatches(r -> r.total() == 5L && r.pagina() == 1)
                    .verifyComplete();
        }
    }
}
