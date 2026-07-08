package com.gymadmin.attendance.domain.port.in;

import com.gymadmin.attendance.domain.model.MensajeLog;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface MensajeLogUseCase {

    Flux<MensajeLog> listar(Integer idCompania, Integer idCliente, String tipo, String estado, LocalDate desde);

    Mono<MensajeLog> enviarManual(EnviarMensajeCommand command);

    Mono<MensajeLog> reenviar(Long id, Integer idCompania);

    record EnviarMensajeCommand(Integer idCliente, String canal, Integer idPlantilla,
                                 Integer idCompania, Integer idSucursal, String idUsuarioEnvio) {}
}
