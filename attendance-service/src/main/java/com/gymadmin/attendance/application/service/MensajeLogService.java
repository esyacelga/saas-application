package com.gymadmin.attendance.application.service;

import com.gymadmin.attendance.domain.model.MensajeLog;
import com.gymadmin.attendance.domain.model.PlantillaMensaje;
import com.gymadmin.attendance.domain.port.in.MensajeLogUseCase;
import com.gymadmin.attendance.domain.port.out.MensajeLogRepository;
import com.gymadmin.attendance.domain.port.out.PlantillaMensajeRepository;
import com.gymadmin.attendance.infrastructure.exception.IllegalArgumentException;
import com.gymadmin.attendance.infrastructure.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class MensajeLogService implements MensajeLogUseCase {

    private final MensajeLogRepository mensajeLogRepository;
    private final PlantillaMensajeRepository plantillaRepository;

    @Override
    public Flux<MensajeLog> listar(Integer idCompania, Integer idCliente, String tipo, String estado, LocalDate desde) {
        return mensajeLogRepository.findByFiltros(idCompania, idCliente, tipo, estado, desde);
    }

    @Override
    public Mono<MensajeLog> enviarManual(EnviarMensajeCommand command) {
        return plantillaRepository.findById(command.idPlantilla())
                .switchIfEmpty(Mono.error(new NotFoundException("Plantilla no encontrada")))
                .flatMap(plantilla -> {
                    String contenido = plantilla.getContenido();

                    MensajeLog log = new MensajeLog();
                    log.setIdCompania(command.idCompania());
                    log.setIdSucursal(command.idSucursal());
                    log.setIdCliente(command.idCliente());
                    log.setIdPlantilla(command.idPlantilla());
                    log.setTipo(plantilla.getTipo());
                    log.setCanal(command.canal());
                    log.setContenido(contenido);
                    log.setEstado("pendiente");
                    log.setFechaProgramada(OffsetDateTime.now(ZoneOffset.UTC));
                    if (command.idUsuarioEnvio() != null && command.idUsuarioEnvio().matches("\\d+")) {
                        log.setIdUsuarioEnvio(Integer.parseInt(command.idUsuarioEnvio()));
                    }

                    return mensajeLogRepository.save(log)
                            .flatMap(saved -> enviarAlProveedor(saved)
                                    .flatMap(enviado -> {
                                        enviado.setEstado("enviado");
                                        enviado.setFechaEnvio(OffsetDateTime.now(ZoneOffset.UTC));
                                        return mensajeLogRepository.update(enviado);
                                    })
                                    .onErrorResume(e -> {
                                        saved.setEstado("fallido");
                                        return mensajeLogRepository.update(saved);
                                    }));
                });
    }

    @Override
    public Mono<MensajeLog> reenviar(Long id, Integer idCompania) {
        return mensajeLogRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Mensaje no encontrado")))
                .flatMap(existing -> {
                    if (!"fallido".equals(existing.getEstado())) {
                        return Mono.error(new IllegalArgumentException(
                                "Solo se pueden reenviar mensajes con estado 'fallido'"));
                    }

                    return enviarAlProveedor(existing)
                            .flatMap(enviado -> {
                                enviado.setEstado("enviado");
                                enviado.setFechaEnvio(OffsetDateTime.now(ZoneOffset.UTC));
                                return mensajeLogRepository.update(enviado);
                            })
                            .onErrorResume(e -> {
                                existing.setEstado("fallido");
                                return mensajeLogRepository.update(existing);
                            });
                });
    }

    private Mono<MensajeLog> enviarAlProveedor(MensajeLog mensaje) {
        // Integración real con Twilio/Meta según canal
        // Por ahora simula el envío exitoso
        return Mono.just(mensaje);
    }

    public Mono<MensajeLog> guardarMensajeJob(Integer idCompania, Integer idSucursal, Integer idCliente,
                                               PlantillaMensaje plantilla, String tipo, String contenido) {
        MensajeLog log = new MensajeLog();
        log.setIdCompania(idCompania);
        log.setIdSucursal(idSucursal);
        log.setIdCliente(idCliente);
        log.setIdPlantilla(plantilla != null ? plantilla.getId() : null);
        log.setTipo(tipo);
        log.setCanal("whatsapp");
        log.setContenido(contenido);
        log.setEstado("pendiente");
        log.setFechaProgramada(OffsetDateTime.now(ZoneOffset.UTC));

        return mensajeLogRepository.save(log)
                .flatMap(saved -> enviarAlProveedor(saved)
                        .flatMap(enviado -> {
                            enviado.setEstado("enviado");
                            enviado.setFechaEnvio(OffsetDateTime.now(ZoneOffset.UTC));
                            return mensajeLogRepository.update(enviado);
                        })
                        .onErrorResume(e -> {
                            saved.setEstado("fallido");
                            return mensajeLogRepository.update(saved);
                        }));
    }

    public Mono<Long> contarEnviadosDesde(Integer idCliente, String tipo, OffsetDateTime desde) {
        return mensajeLogRepository.countByClienteAndTipoDesde(idCliente, tipo, desde);
    }
}
