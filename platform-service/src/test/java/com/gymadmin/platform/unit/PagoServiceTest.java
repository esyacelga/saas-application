package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.PagoService;
import com.gymadmin.platform.domain.model.PagoSuscripcion;
import com.gymadmin.platform.domain.port.in.PagoUseCase.RegistrarPagoCommand;
import com.gymadmin.platform.domain.port.out.PagoRepository;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PagoService — gestión de pagos de suscripción")
class PagoServiceTest {

    @Mock
    private PagoRepository pagoRepository;

    @InjectMocks
    private PagoService service;

    private PagoSuscripcion buildPago(Long id, Long idCompaniaPlan, PagoSuscripcion.EstadoPago estado) {
        PagoSuscripcion pago = new PagoSuscripcion();
        pago.setId(id);
        pago.setIdCompaniaPlan(idCompaniaPlan);
        pago.setMonto(BigDecimal.valueOf(99.99));
        pago.setMetodoPago(PagoSuscripcion.MetodoPago.TRANSFERENCIA);
        pago.setTipoPago(PagoSuscripcion.TipoPago.PAGO_COMPLETO);
        pago.setEstado(estado);
        pago.setFechaPago(LocalDate.now());
        return pago;
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("getHistorialPagos")
    class GetHistorialPagos {

        @Test
        @DisplayName("retorna el historial de pagos de la compañía")
        void retornaHistorialDePagos() {
            PagoSuscripcion p1 = buildPago(1L, 100L, PagoSuscripcion.EstadoPago.PAGADO);
            PagoSuscripcion p2 = buildPago(2L, 100L, PagoSuscripcion.EstadoPago.PENDIENTE);
            when(pagoRepository.findByIdCompania(100L)).thenReturn(Flux.just(p1, p2));

            StepVerifier.create(service.getHistorialPagos(100L))
                    .assertNext(p -> assertThat(p.getEstado()).isEqualTo(PagoSuscripcion.EstadoPago.PAGADO))
                    .assertNext(p -> assertThat(p.getEstado()).isEqualTo(PagoSuscripcion.EstadoPago.PENDIENTE))
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna flux vacío cuando la compañía no tiene pagos")
        void retornaFluxVacioCuandoNoHayPagos() {
            when(pagoRepository.findByIdCompania(999L)).thenReturn(Flux.empty());

            StepVerifier.create(service.getHistorialPagos(999L))
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("registrarPago")
    class RegistrarPago {

        @Test
        @DisplayName("registra el pago con estado PENDIENTE y retorna el pago guardado")
        void registraPagoExitosamente() {
            RegistrarPagoCommand command = new RegistrarPagoCommand(
                    200L,
                    BigDecimal.valueOf(199.99),
                    "TRANSFERENCIA",
                    "PAGO_COMPLETO",
                    "REF-001",
                    LocalDate.of(2026, 7, 1),
                    LocalDate.of(2026, 7, 31)
            );

            PagoSuscripcion guardado = buildPago(10L, 200L, PagoSuscripcion.EstadoPago.PENDIENTE);
            when(pagoRepository.save(any(PagoSuscripcion.class))).thenReturn(Mono.just(guardado));

            StepVerifier.create(service.registrarPago(command))
                    .assertNext(p -> {
                        assertThat(p.getId()).isEqualTo(10L);
                        assertThat(p.getEstado()).isEqualTo(PagoSuscripcion.EstadoPago.PENDIENTE);
                        assertThat(p.getIdCompaniaPlan()).isEqualTo(200L);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("normaliza el metodo de pago con guiones (ej: PAGO-COMPLETO → PAGO_COMPLETO)")
        void normalizaMetodoDePagoConGuiones() {
            RegistrarPagoCommand command = new RegistrarPagoCommand(
                    200L,
                    BigDecimal.valueOf(50.00),
                    "EFECTIVO",
                    "DIFERENCIA-UPGRADE",
                    null,
                    LocalDate.now(),
                    LocalDate.now().plusMonths(1)
            );

            PagoSuscripcion guardado = buildPago(11L, 200L, PagoSuscripcion.EstadoPago.PENDIENTE);
            guardado.setTipoPago(PagoSuscripcion.TipoPago.DIFERENCIA_UPGRADE);
            when(pagoRepository.save(any(PagoSuscripcion.class))).thenReturn(Mono.just(guardado));

            StepVerifier.create(service.registrarPago(command))
                    .assertNext(p -> assertThat(p.getTipoPago()).isEqualTo(PagoSuscripcion.TipoPago.DIFERENCIA_UPGRADE))
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("confirmarPago")
    class ConfirmarPago {

        @Test
        @DisplayName("actualiza el estado a PAGADO cuando el pago existe")
        void confirmaPageExitosamente() {
            PagoSuscripcion pendiente = buildPago(5L, 100L, PagoSuscripcion.EstadoPago.PENDIENTE);
            PagoSuscripcion confirmado = buildPago(5L, 100L, PagoSuscripcion.EstadoPago.PAGADO);

            when(pagoRepository.findById(5L)).thenReturn(Mono.just(pendiente));
            when(pagoRepository.update(any(PagoSuscripcion.class))).thenReturn(Mono.just(confirmado));

            StepVerifier.create(service.confirmarPago(5L))
                    .assertNext(p -> {
                        assertThat(p.getId()).isEqualTo(5L);
                        assertThat(p.getEstado()).isEqualTo(PagoSuscripcion.EstadoPago.PAGADO);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando el pago no existe")
        void lanzaNotFoundCuandoPagoNoExiste() {
            when(pagoRepository.findById(999L)).thenReturn(Mono.empty());

            StepVerifier.create(service.confirmarPago(999L))
                    .expectError(NotFoundException.class)
                    .verify();
        }
    }
}
