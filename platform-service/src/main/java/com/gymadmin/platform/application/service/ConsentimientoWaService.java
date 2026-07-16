package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.port.in.ConsentimientoWaUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaRepository;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;

/**
 * Fase 6 (bloque E): opt-in de WhatsApp del dueño. La fecha se sella con el {@link Clock} inyectable
 * (mismo patrón que el resto de servicios de platform) solo cuando se acepta; en opt-out se limpia.
 */
@Service
public class ConsentimientoWaService implements ConsentimientoWaUseCase {

    private final CompaniaRepository companiaRepository;
    private final Clock clock;

    public ConsentimientoWaService(CompaniaRepository companiaRepository, Clock clock) {
        this.companiaRepository = companiaRepository;
        this.clock = clock;
    }

    @Override
    public Mono<Compania> actualizarConsentimiento(Long idCompania, boolean acepta) {
        Instant fecha = acepta ? clock.instant() : null;
        return companiaRepository.findById(idCompania)
                .switchIfEmpty(Mono.error(new NotFoundException("Compañía no encontrada: " + idCompania)))
                .flatMap(c -> companiaRepository.updateConsentimientoWa(idCompania, acepta, fecha));
    }
}
