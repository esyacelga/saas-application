package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.ConsentimientoWaService;
import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.port.out.CompaniaRepository;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fase 6 (bloque E): opt-in del dueño — sella la fecha con el Clock al aceptar, la limpia al rechazar,
 * y 404 si la compañía no existe.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConsentimientoWaService — opt-in/opt-out del dueño (Fase 6)")
class ConsentimientoWaServiceTest {

    @Mock CompaniaRepository companiaRepository;

    private final Instant now = Instant.parse("2026-07-15T10:00:00Z");
    private final Clock clockFijo = Clock.fixed(now, ZoneOffset.UTC);

    private ConsentimientoWaService service;

    @BeforeEach
    void setUp() {
        service = new ConsentimientoWaService(companiaRepository, clockFijo);
    }

    private Compania compania() {
        Compania c = new Compania();
        c.setId(7L);
        return c;
    }

    @Test
    @DisplayName("acepta=true → sella fecha con el Clock")
    void aceptaTrue_sellaFecha() {
        when(companiaRepository.findById(7L)).thenReturn(Mono.just(compania()));
        Compania actualizada = compania();
        actualizada.setAceptaWhatsapp(true);
        actualizada.setFechaConsentimientoWa(now);
        when(companiaRepository.updateConsentimientoWa(eq(7L), eq(true), any()))
                .thenReturn(Mono.just(actualizada));

        StepVerifier.create(service.actualizarConsentimiento(7L, true))
                .expectNextMatches(c -> c.isAceptaWhatsapp() && now.equals(c.getFechaConsentimientoWa()))
                .verifyComplete();

        ArgumentCaptor<Instant> fecha = ArgumentCaptor.forClass(Instant.class);
        verify(companiaRepository).updateConsentimientoWa(eq(7L), eq(true), fecha.capture());
        assertThat(fecha.getValue()).isEqualTo(now);
    }

    @Test
    @DisplayName("acepta=false → limpia fecha a null (opt-out)")
    void aceptaFalse_limpiaFecha() {
        when(companiaRepository.findById(7L)).thenReturn(Mono.just(compania()));
        when(companiaRepository.updateConsentimientoWa(eq(7L), eq(false), any()))
                .thenReturn(Mono.just(compania()));

        StepVerifier.create(service.actualizarConsentimiento(7L, false)).expectNextCount(1).verifyComplete();

        ArgumentCaptor<Instant> fecha = ArgumentCaptor.forClass(Instant.class);
        verify(companiaRepository).updateConsentimientoWa(eq(7L), eq(false), fecha.capture());
        assertThat(fecha.getValue()).isNull();
    }

    @Test
    @DisplayName("compañía inexistente → 404, no escribe")
    void companiaInexistente_404() {
        when(companiaRepository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(service.actualizarConsentimiento(99L, true))
                .expectError(NotFoundException.class)
                .verify();

        verify(companiaRepository, never()).updateConsentimientoWa(any(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }
}
