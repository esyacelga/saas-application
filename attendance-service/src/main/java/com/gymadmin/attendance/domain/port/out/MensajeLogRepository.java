package com.gymadmin.attendance.domain.port.out;

import com.gymadmin.attendance.domain.model.MensajeLog;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public interface MensajeLogRepository {

    Mono<MensajeLog> save(MensajeLog mensajeLog);

    Mono<MensajeLog> update(MensajeLog mensajeLog);

    Mono<MensajeLog> findById(Long id);

    Flux<MensajeLog> findByFiltros(Integer idCompania, Integer idCliente, String tipo, String estado, LocalDate desde);

    Mono<Long> countByClienteAndTipoDesde(Integer idCliente, String tipo, OffsetDateTime desde);
}
