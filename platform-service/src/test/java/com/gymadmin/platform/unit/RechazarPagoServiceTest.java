package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.RechazarPagoService;
import com.gymadmin.platform.domain.exception.PagoYaProcesadoException;
import com.gymadmin.platform.domain.model.PagoPendienteValidacion;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.in.EnviarNotificacionUseCase;
import com.gymadmin.platform.domain.port.out.PagoPendienteValidacionRepository;
import com.gymadmin.platform.infrastructure.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RechazarPagoService — RN-08 motivo obligatorio y UPDATE atómico")
class RechazarPagoServiceTest {

    @Mock PagoPendienteValidacionRepository pagoRepository;
    @Mock ActividadPlataformaUseCase actividadPlataformaUseCase;
    @Mock EnviarNotificacionUseCase enviarNotificacionUseCase;

    private RechazarPagoService service;
    private final Clock clockFijo = Clock.fixed(
            LocalDate.of(2026, 7, 9).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new RechazarPagoService(pagoRepository, actividadPlataformaUseCase,
                enviarNotificacionUseCase, clockFijo);
    }

    @Test
    @DisplayName("motivo null → BusinessException")
    void motivoNullLanza() {
        StepVerifier.create(service.rechazar(1L, 42L, null))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("motivo con menos de 10 chars → BusinessException")
    void motivoCortoLanza() {
        StepVerifier.create(service.rechazar(1L, 42L, "corto"))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("UPDATE atómico afecta 0 filas → PagoYaProcesadoException")
    void doubleProcesamientoLanza() {
        when(pagoRepository.marcarRechazado(eq(1L), eq(42L), any(String.class), any(Instant.class)))
                .thenReturn(Mono.just(0L));

        StepVerifier.create(service.rechazar(1L, 42L, "motivo suficientemente largo"))
                .expectError(PagoYaProcesadoException.class)
                .verify();
    }

    @Test
    @DisplayName("rechazo exitoso → registra evento PAGO_RECHAZADO")
    void rechazoExitosoOk() {
        PagoPendienteValidacion pago = new PagoPendienteValidacion();
        pago.setId(1L);
        pago.setIdCompania(5L);
        pago.setIdPlanDestino(300L);

        when(pagoRepository.marcarRechazado(eq(1L), eq(42L), any(String.class), any(Instant.class)))
                .thenReturn(Mono.just(1L));
        when(pagoRepository.findById(eq(1L))).thenReturn(Mono.just(pago));
        when(actividadPlataformaUseCase.registrar(any(ActividadPlataformaUseCase.RegistrarActividadCommand.class)))
                .thenReturn(Mono.empty());
        when(enviarNotificacionUseCase.encolar(any(EnviarNotificacionUseCase.EncolarNotificacionCommand.class)))
                .thenReturn(Mono.just(888L));

        StepVerifier.create(service.rechazar(1L, 42L, "documento ilegible y borroso"))
                .verifyComplete();
    }
}
