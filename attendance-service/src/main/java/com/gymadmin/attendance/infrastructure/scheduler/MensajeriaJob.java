package com.gymadmin.attendance.infrastructure.scheduler;

import com.gymadmin.attendance.application.service.MensajeLogService;
import com.gymadmin.attendance.domain.model.PlantillaMensaje;
import com.gymadmin.attendance.domain.port.out.AsistenciaRepository;
import com.gymadmin.attendance.domain.port.out.PlantillaMensajeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class MensajeriaJob {

    private final AsistenciaRepository asistenciaRepository;
    private final PlantillaMensajeRepository plantillaRepository;
    private final MensajeLogService mensajeLogService;

    private static final String TEXTO_DEFAULT_AUSENCIA_2D =
            "Hola {nombre}, te extrañamos en el gym. ¡Hoy es un buen día para volver!";
    private static final String TEXTO_DEFAULT_RECUPERACION_5D =
            "Llevas {dias} días sin entrenar, {nombre}. ¡Te esperamos!";
    private static final String TEXTO_DEFAULT_RECUPERACION_10D =
            "{nombre}, han pasado {dias} días. Tu membresía vence el {fecha_vencimiento}.";
    private static final String TEXTO_DEFAULT_RECUPERACION_15D =
            "{nombre}, llevamos {dias} días sin verte. Tenemos una oferta especial para ti.";
    private static final String TEXTO_DEFAULT_VENCIMIENTO_3D =
            "Hola {nombre}, tu membresía vence en {dias} días. ¡Renueva y sigue sin parar!";
    private static final String TEXTO_DEFAULT_VENCIMIENTO_HOY =
            "{nombre}, tu membresía venció hoy. Acércate para renovar.";

    /**
     * Se ejecuta diariamente a las 00:15 UTC.
     * Cron: segundo minuto hora día mes díaSemana
     */
    @Scheduled(cron = "${scheduling.messaging-job-cron:0 15 0 * * *}")
    public void ejecutar() {
        log.info("[MensajeriaJob] Inicio ejecución {}", LocalDate.now());

        // Por compañía y sucursal activas se procesa cada cliente con membresía vigente.
        // La implementación real requeriría una consulta que traiga clientes con membresía activa.
        // Aquí se muestra el flujo completo con la lógica de negocio.
        procesarAusencias()
                .doOnComplete(() -> log.info("[MensajeriaJob] Fin ejecución"))
                .doOnError(e -> log.error("[MensajeriaJob] Error: {}", e.getMessage()))
                .subscribe();
    }

    private reactor.core.publisher.Flux<Void> procesarAusencias() {
        // Este método debe iterar sobre todos los clientes con membresía activa por compañía.
        // La consulta real vendría de una query agregada al CoreServiceClient o a la BD.
        // Se deja preparado el esqueleto con la lógica completa de negocio.
        return reactor.core.publisher.Flux.empty();
    }

    /**
     * Procesa un cliente individual: calcula días de ausencia y envía el mensaje correspondiente.
     * Llamado desde procesarAusencias() por cada cliente activo.
     */
    public Mono<Void> procesarCliente(Integer idCompania, Integer idSucursal, Integer idCliente,
                                       LocalDate fechaInicioMembresia, LocalDate fechaFinMembresia,
                                       String modoControl, Integer accesosRestantes,
                                       String nombreCliente, String gymNombre) {

        return asistenciaRepository.findUltimaAsistencia(idCliente, idCompania)
                .defaultIfEmpty(fechaInicioMembresia)
                .flatMap(ultimaAsistencia -> {
                    long diasAusente = ChronoUnit.DAYS.between(ultimaAsistencia, LocalDate.now());

                    // Mensajes de vencimiento tienen prioridad
                    if (fechaFinMembresia != null) {
                        long diasParaVencer = ChronoUnit.DAYS.between(LocalDate.now(), fechaFinMembresia);
                        if (diasParaVencer == 0) {
                            return enviarSiNoExiste(idCompania, idSucursal, idCliente,
                                    "vencimiento_hoy", diasAusente, fechaFinMembresia, accesosRestantes,
                                    nombreCliente, gymNombre, ultimaAsistencia);
                        }
                        if (diasParaVencer == 3) {
                            return enviarSiNoExiste(idCompania, idSucursal, idCliente,
                                    "vencimiento_3d", diasAusente, fechaFinMembresia, accesosRestantes,
                                    nombreCliente, gymNombre, ultimaAsistencia);
                        }
                    }

                    // Mensajes de vencimiento por accesos
                    if ("accesos".equals(modoControl) && accesosRestantes != null) {
                        if (accesosRestantes == 0) {
                            return enviarSiNoExiste(idCompania, idSucursal, idCliente,
                                    "vencimiento_hoy", diasAusente, fechaFinMembresia, accesosRestantes,
                                    nombreCliente, gymNombre, ultimaAsistencia);
                        }
                        if (accesosRestantes == 3) {
                            return enviarSiNoExiste(idCompania, idSucursal, idCliente,
                                    "vencimiento_3d", diasAusente, fechaFinMembresia, accesosRestantes,
                                    nombreCliente, gymNombre, ultimaAsistencia);
                        }
                    }

                    // Mensajes de ausencia
                    String tipo = switch ((int) diasAusente) {
                        case 2  -> "ausencia_2d";
                        case 5  -> "recuperacion_5d";
                        case 10 -> "recuperacion_10d";
                        case 15 -> "recuperacion_15d";
                        default -> null;
                    };

                    if (tipo == null) return Mono.empty();

                    return enviarSiNoExiste(idCompania, idSucursal, idCliente,
                            tipo, diasAusente, fechaFinMembresia, accesosRestantes,
                            nombreCliente, gymNombre, ultimaAsistencia);
                });
    }

    private Mono<Void> enviarSiNoExiste(Integer idCompania, Integer idSucursal, Integer idCliente,
                                         String tipo, long diasAusente, LocalDate fechaFin,
                                         Integer accesosRestantes, String nombreCliente, String gymNombre,
                                         LocalDate ultimaAsistencia) {

        // Anti-spam: verificar si ya se envió este tipo desde la última asistencia
        OffsetDateTime desdeUltimaAsistencia = ultimaAsistencia
                .atStartOfDay().atOffset(ZoneOffset.UTC);

        return mensajeLogService.contarEnviadosDesde(idCliente, tipo, desdeUltimaAsistencia)
                .flatMap(count -> {
                    if (count > 0) {
                        log.debug("[MensajeriaJob] Skip cliente={} tipo={} (anti-spam)", idCliente, tipo);
                        return Mono.empty();
                    }

                    // Seleccionar plantilla aleatoria o usar texto default
                    return plantillaRepository.findRandomActivaByTipo(idCompania, tipo)
                            .map(p -> {
                                String contenido = sustituirVariables(p.getContenido(), nombreCliente,
                                        diasAusente, fechaFin, accesosRestantes, gymNombre);
                                return new PlantillaConContenido(p, contenido);
                            })
                            .switchIfEmpty(Mono.fromCallable(() -> {
                                log.warn("[MensajeriaJob] Sin plantilla para tipo={}, usando texto default", tipo);
                                String contenidoDefault = sustituirVariables(
                                        textoDefault(tipo), nombreCliente, diasAusente,
                                        fechaFin, accesosRestantes, gymNombre);
                                return new PlantillaConContenido(null, contenidoDefault);
                            }))
                            .flatMap(pc -> mensajeLogService.guardarMensajeJob(
                                    idCompania, idSucursal, idCliente,
                                    pc.plantilla(), tipo, pc.contenido()))
                            .then();
                });
    }

    private String sustituirVariables(String plantilla, String nombre, long dias,
                                       LocalDate fechaVencimiento, Integer accesosRestantes, String gymNombre) {
        if (plantilla == null) return "";
        return plantilla
                .replace("{nombre}", nombre != null ? nombre : "Cliente")
                .replace("{dias}", String.valueOf(dias))
                .replace("{fecha_vencimiento}", fechaVencimiento != null ? fechaVencimiento.toString() : "N/A")
                .replace("{accesos_restantes}", accesosRestantes != null ? String.valueOf(accesosRestantes) : "0")
                .replace("{gym_nombre}", gymNombre != null ? gymNombre : "el gimnasio");
    }

    private String textoDefault(String tipo) {
        return switch (tipo) {
            case "ausencia_2d"      -> TEXTO_DEFAULT_AUSENCIA_2D;
            case "recuperacion_5d"  -> TEXTO_DEFAULT_RECUPERACION_5D;
            case "recuperacion_10d" -> TEXTO_DEFAULT_RECUPERACION_10D;
            case "recuperacion_15d" -> TEXTO_DEFAULT_RECUPERACION_15D;
            case "vencimiento_3d"   -> TEXTO_DEFAULT_VENCIMIENTO_3D;
            case "vencimiento_hoy"  -> TEXTO_DEFAULT_VENCIMIENTO_HOY;
            default -> "Hola {nombre}, te contactamos desde el gimnasio.";
        };
    }

    private record PlantillaConContenido(PlantillaMensaje plantilla, String contenido) {}
}
