package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.ListarPagosPendientesOwnerService;
import com.gymadmin.platform.domain.model.PagoPendienteValidacion;
import com.gymadmin.platform.domain.port.out.PagoPendienteValidacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListarPagosPendientesOwnerService — REQ-SAAS-001 Sub-fase 1.6 item #3")
class ListarPagosPendientesOwnerServiceTest {

    @Mock PagoPendienteValidacionRepository pagoRepository;

    private ListarPagosPendientesOwnerService service;

    @BeforeEach
    void setUp() {
        service = new ListarPagosPendientesOwnerService(pagoRepository);
    }

    @Test
    @DisplayName("devuelve los pagos del tenant en el orden que da el repositorio")
    void devuelveResultadosDelRepositorio() {
        PagoPendienteValidacion nuevo = pago(2L, PagoPendienteValidacion.Estado.PENDIENTE, null,
                Instant.parse("2026-07-10T10:00:00Z"));
        PagoPendienteValidacion viejo = pago(1L, PagoPendienteValidacion.Estado.RECHAZADO,
                "Comprobante ilegible", Instant.parse("2026-07-05T10:00:00Z"));

        when(pagoRepository.listarPorCompania(eq(42L), anyInt()))
                .thenReturn(Flux.just(nuevo, viejo));

        StepVerifier.create(service.listarPorCompania(42L, 10))
                .assertNext(p -> assertThat(p.getId()).isEqualTo(2L))
                .assertNext(p -> {
                    assertThat(p.getId()).isEqualTo(1L);
                    assertThat(p.getMotivoRechazo()).isEqualTo("Comprobante ilegible");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("empty case: el repositorio no devuelve nada → Flux vacío")
    void emptyCase() {
        when(pagoRepository.listarPorCompania(eq(42L), anyInt())).thenReturn(Flux.empty());

        StepVerifier.create(service.listarPorCompania(42L, 10))
                .verifyComplete();
    }

    @Test
    @DisplayName("limit <= 0 → aplica el default (10)")
    void limitInvalidoAplicaDefault() {
        when(pagoRepository.listarPorCompania(eq(42L), anyInt())).thenReturn(Flux.empty());

        service.listarPorCompania(42L, 0).blockLast();

        ArgumentCaptor<Integer> limitCap = ArgumentCaptor.forClass(Integer.class);
        verify(pagoRepository).listarPorCompania(eq(42L), limitCap.capture());
        assertThat(limitCap.getValue()).isEqualTo(10);
    }

    @Test
    @DisplayName("limit por encima del máximo → se recorta a 50")
    void limitDemasiadoAltoSeRecorta() {
        when(pagoRepository.listarPorCompania(eq(42L), anyInt())).thenReturn(Flux.empty());

        service.listarPorCompania(42L, 9999).blockLast();

        ArgumentCaptor<Integer> limitCap = ArgumentCaptor.forClass(Integer.class);
        verify(pagoRepository).listarPorCompania(eq(42L), limitCap.capture());
        assertThat(limitCap.getValue()).isEqualTo(50);
    }

    private PagoPendienteValidacion pago(Long id,
                                         PagoPendienteValidacion.Estado estado,
                                         String motivoRechazo,
                                         Instant fechaReporte) {
        PagoPendienteValidacion p = new PagoPendienteValidacion();
        p.setId(id);
        p.setIdCompania(42L);
        p.setEstado(estado);
        p.setMotivoRechazo(motivoRechazo);
        p.setFechaReporte(fechaReporte);
        return p;
    }
}
