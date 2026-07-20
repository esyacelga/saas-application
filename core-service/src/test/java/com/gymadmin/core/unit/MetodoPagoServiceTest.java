package com.gymadmin.core.unit;

import com.gymadmin.core.application.service.MetodoPagoService;
import com.gymadmin.core.domain.model.MetodoPago;
import com.gymadmin.core.domain.port.out.MetodoPagoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetodoPagoService — listado de métodos de pago activos")
class MetodoPagoServiceTest {

    @Mock
    private MetodoPagoRepository metodoPagoRepository;

    @InjectMocks
    private MetodoPagoService metodoPagoService;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private MetodoPago build(Long id, Long idCompania, String nombre, Boolean activo) {
        MetodoPago m = new MetodoPago();
        m.setId(id);
        m.setIdCompania(idCompania);
        m.setIdSucursal(1L);
        m.setNombre(nombre);
        m.setActivo(activo);
        m.setEliminado(false);
        return m;
    }

    // -------------------------------------------------------------------------
    // listarActivos
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("listarActivos")
    class ListarActivos {

        @Test
        @DisplayName("retorna los métodos de pago activos de la compañía")
        void retornaLosMetodosActivosDeLaCompania() {
            MetodoPago efectivo = build(1L, 10L, "Efectivo", true);
            MetodoPago transferencia = build(2L, 10L, "Transferencia", true);
            when(metodoPagoRepository.findByIdCompaniaAndActivoTrueAndEliminadoFalse(10L))
                    .thenReturn(Flux.just(efectivo, transferencia));

            StepVerifier.create(metodoPagoService.listarActivos(10L))
                    .expectNext(efectivo)
                    .expectNext(transferencia)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna Flux vacío cuando la compañía no tiene métodos activos")
        void retornaFluxVacioCuandoNoHayMetodos() {
            when(metodoPagoRepository.findByIdCompaniaAndActivoTrueAndEliminadoFalse(10L))
                    .thenReturn(Flux.empty());

            StepVerifier.create(metodoPagoService.listarActivos(10L))
                    .verifyComplete();
        }

        @Test
        @DisplayName("propaga el error del repositorio sin capturarlo")
        void propagaErrorDelRepositorio() {
            RuntimeException boom = new RuntimeException("db down");
            when(metodoPagoRepository.findByIdCompaniaAndActivoTrueAndEliminadoFalse(10L))
                    .thenReturn(Flux.error(boom));

            StepVerifier.create(metodoPagoService.listarActivos(10L))
                    .expectErrorMatches(err -> err == boom)
                    .verify();
        }
    }
}
