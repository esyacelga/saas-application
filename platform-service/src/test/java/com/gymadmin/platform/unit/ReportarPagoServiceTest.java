package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.CloudinaryService;
import com.gymadmin.platform.application.service.CloudinaryService.ComprobanteSubidoResponse;
import com.gymadmin.platform.application.service.ReportarPagoService;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.model.PagoPendienteValidacion;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.in.ReportarPagoUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.PagoPendienteValidacionRepository;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-SAAS-001 (Sub-fase 1.6, item #4): tests unitarios del
 * {@link ReportarPagoService} verificando que cuando el owner reporta un pago
 * sin comprobante, NO se invoca a Cloudinary y el registro persiste con
 * {@code comprobanteUrl = null}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportarPagoService — comprobante opcional (Sub-fase 1.6)")
class ReportarPagoServiceTest {

    @Mock PagoPendienteValidacionRepository pagoRepository;
    @Mock CloudinaryService cloudinaryService;
    @Mock CompaniaPlanRepository companiaPlanRepository;
    @Mock PlanRepository planRepository;
    @Mock ActividadPlataformaUseCase actividadPlataformaUseCase;

    private ReportarPagoService service;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.parse("2026-07-10T10:00:00Z"), ZoneOffset.UTC);
        service = new ReportarPagoService(pagoRepository, cloudinaryService,
                companiaPlanRepository, planRepository, actividadPlataformaUseCase, fixed);
    }

    private Plan planPremium() {
        Plan p = new Plan();
        p.setId(300L);
        p.setCodigo("PREMIUM");
        p.setMoneda("USD");
        return p;
    }

    @Test
    @DisplayName("sin comprobante (bytes null) → no invoca Cloudinary y persiste comprobanteUrl null")
    void sinComprobanteNoSubeACloudinary() {
        when(pagoRepository.findByHashIdempotencia(anyString())).thenReturn(Mono.empty());
        when(companiaPlanRepository.findActivoByIdCompania(anyLong())).thenReturn(Mono.empty());
        when(planRepository.findById(300L)).thenReturn(Mono.just(planPremium()));
        when(pagoRepository.save(any())).thenAnswer(inv -> {
            PagoPendienteValidacion p = inv.getArgument(0);
            p.setId(77L);
            return Mono.just(p);
        });
        when(actividadPlataformaUseCase.registrar(
                any(ActividadPlataformaUseCase.RegistrarActividadCommand.class))).thenReturn(Mono.empty());

        ReportarPagoUseCase.ReportarPagoCommand cmd = new ReportarPagoUseCase.ReportarPagoCommand(
                42L, 300L, new BigDecimal("29.99"), LocalDate.of(2026, 7, 9),
                "Pichincha", "REF-999", null, null, 1L, "10.0.0.1"
        );

        StepVerifier.create(service.reportar(cmd))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isEqualTo(77L);
                    assertThat(saved.getComprobanteUrl()).isNull();
                    assertThat(saved.getComprobanteHash()).isNull();
                    assertThat(saved.getEstado()).isEqualTo(PagoPendienteValidacion.Estado.PENDIENTE);
                })
                .verifyComplete();

        verify(cloudinaryService, never()).subirComprobante(any(), any(), anyLong());
        ArgumentCaptor<PagoPendienteValidacion> pagoCap =
                ArgumentCaptor.forClass(PagoPendienteValidacion.class);
        verify(pagoRepository).save(pagoCap.capture());
        assertThat(pagoCap.getValue().getComprobanteUrl()).isNull();
    }

    @Test
    @DisplayName("sin comprobante (bytes de longitud 0) → equivalente a null: no invoca Cloudinary")
    void bytesVaciosTambienSonSinComprobante() {
        when(pagoRepository.findByHashIdempotencia(anyString())).thenReturn(Mono.empty());
        when(companiaPlanRepository.findActivoByIdCompania(anyLong())).thenReturn(Mono.empty());
        when(planRepository.findById(300L)).thenReturn(Mono.just(planPremium()));
        when(pagoRepository.save(any())).thenAnswer(inv -> {
            PagoPendienteValidacion p = inv.getArgument(0);
            p.setId(78L);
            return Mono.just(p);
        });
        when(actividadPlataformaUseCase.registrar(
                any(ActividadPlataformaUseCase.RegistrarActividadCommand.class))).thenReturn(Mono.empty());

        ReportarPagoUseCase.ReportarPagoCommand cmd = new ReportarPagoUseCase.ReportarPagoCommand(
                42L, 300L, new BigDecimal("29.99"), LocalDate.of(2026, 7, 9),
                null, "REF-000", new byte[0], null, 1L, null
        );

        StepVerifier.create(service.reportar(cmd))
                .assertNext(saved -> assertThat(saved.getComprobanteUrl()).isNull())
                .verifyComplete();

        verify(cloudinaryService, never()).subirComprobante(any(), any(), anyLong());
    }

    @Test
    @DisplayName("con comprobante → Cloudinary sí se invoca y comprobanteUrl viene de la respuesta")
    void conComprobanteInvocaCloudinary() {
        when(pagoRepository.findByHashIdempotencia(anyString())).thenReturn(Mono.empty());
        when(cloudinaryService.subirComprobante(any(byte[].class), anyString(), anyLong()))
                .thenReturn(Mono.just(new ComprobanteSubidoResponse(
                        "https://cdn/test/comprobante.pdf", "sha256:abc")));
        when(companiaPlanRepository.findActivoByIdCompania(anyLong())).thenReturn(Mono.empty());
        when(planRepository.findById(300L)).thenReturn(Mono.just(planPremium()));
        when(pagoRepository.save(any())).thenAnswer(inv -> {
            PagoPendienteValidacion p = inv.getArgument(0);
            p.setId(79L);
            return Mono.just(p);
        });
        when(actividadPlataformaUseCase.registrar(
                any(ActividadPlataformaUseCase.RegistrarActividadCommand.class))).thenReturn(Mono.empty());

        ReportarPagoUseCase.ReportarPagoCommand cmd = new ReportarPagoUseCase.ReportarPagoCommand(
                42L, 300L, new BigDecimal("29.99"), LocalDate.of(2026, 7, 9),
                "Pichincha", "REF-001", "PDF".getBytes(), "comprobante.pdf", 1L, null
        );

        StepVerifier.create(service.reportar(cmd))
                .assertNext(saved -> {
                    assertThat(saved.getComprobanteUrl()).isEqualTo("https://cdn/test/comprobante.pdf");
                    assertThat(saved.getComprobanteHash()).isEqualTo("sha256:abc");
                })
                .verifyComplete();
    }
}
