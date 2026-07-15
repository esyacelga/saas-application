package com.gymadmin.attendance.application.service;

import com.gymadmin.attendance.domain.model.MensajeLog;
import com.gymadmin.attendance.domain.model.PlantillaMensaje;
import com.gymadmin.attendance.domain.port.in.MensajeLogUseCase;
import com.gymadmin.attendance.domain.port.out.MensajeLogRepository;
import com.gymadmin.attendance.domain.port.out.PlantillaMensajeRepository;
import com.gymadmin.attendance.domain.port.out.WhatsAppSender;
import com.gymadmin.attendance.infrastructure.exception.IllegalArgumentException;
import com.gymadmin.attendance.infrastructure.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MensajeLogService implements MensajeLogUseCase {

    private final MensajeLogRepository mensajeLogRepository;
    private final PlantillaMensajeRepository plantillaRepository;
    private final WhatsAppSender whatsAppSender;

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

    /** REQ-SAAS-001 (Fase 5, C2): ¿ya salió este aviso hoy para el cliente/tipo/canal? */
    public Mono<Boolean> existsEnviadoHoy(Integer idCliente, String tipo, String canal) {
        return mensajeLogRepository.existsEnviadoHoy(idCliente, tipo, canal, LocalDate.now());
    }

    /**
     * REQ-SAAS-001 (Fase 5): registra y envía un aviso de vencimiento por WhatsApp (plantilla HSM).
     * Inserta {@code mensajes_log} en {@code pendiente}, llama al {@link WhatsAppSender} con el
     * template + params en orden y transiciona a {@code enviado} (con {@code fecha_envio}) o
     * {@code fallido}. El {@code contenidoLegible} es solo para trazabilidad en {@code mensajes_log}
     * (el texto real lo renderiza Meta desde la plantilla aprobada).
     */
    public Mono<MensajeLog> enviarWhatsAppJob(Integer idCompania, Integer idSucursal, Integer idCliente,
                                              String tipo, String canal, String destinatarioE164,
                                              String template, String idioma, List<String> params,
                                              String contenidoLegible) {
        MensajeLog mensaje = new MensajeLog();
        mensaje.setIdCompania(idCompania);
        mensaje.setIdSucursal(idSucursal);
        mensaje.setIdCliente(idCliente);
        mensaje.setTipo(tipo);
        mensaje.setCanal(canal);
        mensaje.setContenido(contenidoLegible);
        mensaje.setEstado("pendiente");
        mensaje.setFechaProgramada(OffsetDateTime.now(ZoneOffset.UTC));

        return mensajeLogRepository.save(mensaje)
                .flatMap(saved -> whatsAppSender.enviarPlantilla(destinatarioE164, template, idioma, params)
                        .then(Mono.defer(() -> {
                            saved.setEstado("enviado");
                            saved.setFechaEnvio(OffsetDateTime.now(ZoneOffset.UTC));
                            return mensajeLogRepository.update(saved);
                        }))
                        .onErrorResume(e -> {
                            log.warn("[MensajeriaJob] Fallo envío WA cliente={} tipo={}: {}",
                                    idCliente, tipo, e.getMessage());
                            saved.setEstado("fallido");
                            return mensajeLogRepository.update(saved);
                        }));
    }
}
