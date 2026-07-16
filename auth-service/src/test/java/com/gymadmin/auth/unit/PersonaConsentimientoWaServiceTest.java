package com.gymadmin.auth.unit;

import com.gymadmin.auth.application.service.PersonaApplicationService;
import com.gymadmin.auth.domain.exception.ResourceNotFoundException;
import com.gymadmin.auth.domain.model.Persona;
import com.gymadmin.auth.domain.port.out.PersonaPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fase 6 (bloque E): opt-in del socio — sella la fecha al aceptar, la limpia al rechazar, 404 si la
 * persona no existe (0 filas afectadas).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PersonaApplicationService.actualizarConsentimientoWa — opt-in del socio (Fase 6)")
class PersonaConsentimientoWaServiceTest {

    @Mock PersonaPort personaPort;

    private PersonaApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PersonaApplicationService(personaPort);
    }

    private Persona persona(boolean acepta, OffsetDateTime fecha) {
        return Persona.builder().id(10).aceptaWhatsapp(acepta).fechaConsentimientoWa(fecha).build();
    }

    @Test
    @DisplayName("acepta=true → sella fecha (no null) y devuelve estado")
    void aceptaTrue_sellaFecha() {
        when(personaPort.updateConsentimientoWa(eq(10), eq(true), any())).thenReturn(Mono.just(1L));
        when(personaPort.findById(10)).thenReturn(Mono.just(persona(true, OffsetDateTime.now())));

        StepVerifier.create(service.actualizarConsentimientoWa(10, true))
                .expectNextMatches(r -> r.aceptaWhatsapp() && r.fechaConsentimientoWa() != null)
                .verifyComplete();

        ArgumentCaptor<OffsetDateTime> fecha = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(personaPort).updateConsentimientoWa(eq(10), eq(true), fecha.capture());
        assertThat(fecha.getValue()).isNotNull();
    }

    @Test
    @DisplayName("acepta=false → fecha null (opt-out)")
    void aceptaFalse_fechaNull() {
        when(personaPort.updateConsentimientoWa(eq(10), eq(false), any())).thenReturn(Mono.just(1L));
        when(personaPort.findById(10)).thenReturn(Mono.just(persona(false, null)));

        StepVerifier.create(service.actualizarConsentimientoWa(10, false))
                .expectNextMatches(r -> !r.aceptaWhatsapp() && r.fechaConsentimientoWa() == null)
                .verifyComplete();

        ArgumentCaptor<OffsetDateTime> fecha = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(personaPort).updateConsentimientoWa(eq(10), eq(false), fecha.capture());
        assertThat(fecha.getValue()).isNull();
    }

    @Test
    @DisplayName("persona inexistente (0 filas) → 404, no re-lee")
    void personaInexistente_404() {
        when(personaPort.updateConsentimientoWa(eq(99), anyBoolean(), any())).thenReturn(Mono.just(0L));

        StepVerifier.create(service.actualizarConsentimientoWa(99, true))
                .expectError(ResourceNotFoundException.class)
                .verify();

        verify(personaPort, never()).findById(any());
    }
}
