package com.gymadmin.billing.domain.port.in;

import com.gymadmin.billing.domain.model.AuditoriaEmision;
import reactor.core.publisher.Flux;

import java.time.LocalDate;

public interface AuditoriaUseCase {

    Flux<AuditoriaEmision> listarAuditoria(Integer idCompania, LocalDate desde, LocalDate hasta, String estado);
}
