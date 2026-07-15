package com.gymadmin.attendance.infrastructure.scheduler;

import com.gymadmin.attendance.application.service.MensajeLogService;
import com.gymadmin.attendance.domain.port.out.AsistenciaRepository;
import com.gymadmin.attendance.domain.validation.PhoneNumberE164Normalizer;
import com.gymadmin.attendance.infrastructure.adapter.out.core.CoreServiceClient;
import com.gymadmin.attendance.infrastructure.adapter.out.core.CoreServiceClient.ClientePorVencer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * REQ-SAAS-001 (Fase 5): job diario que avisa por WhatsApp a los socios cuya <b>membresía</b> está
 * por vencer (calendario o accesos). Consume el endpoint interno de core
 * ({@code /internal/v1/companias/{id}/clientes-por-vencer}, Fase 4) — <b>no</b> duplica la detección
 * de vencimiento — y envía plantillas HSM pre-aprobadas vía {@link CoreServiceClient} + el
 * {@code WhatsAppSender}.
 *
 * <p><b>Buckets del socio {3, 0}</b> (aviso previo a 3 días + día del vencimiento), heredados de la
 * lógica existente. Mapeo {@code tipo} → plantilla HSM por {@code modoControl} (calendario/accesos).
 *
 * <p><b>Reglas de envío:</b>
 * <ul>
 *   <li><b>R4/opt-in:</b> solo se envía si {@code aceptaWhatsapp = TRUE} y el teléfono es
 *       normalizable a E.164; si no, se omite (attendance no manda emails → sin fallback).</li>
 *   <li><b>RN-05:</b> el endpoint de core ya excluye {@code congelado}; aquí se reconfirma.</li>
 *   <li><b>C2/idempotencia:</b> {@code existsEnviadoHoy(idCliente, tipo, 'whatsapp')} evita duplicar
 *       el mismo aviso el mismo día (un reinicio del job no reenvía → Meta no bloquea el número).</li>
 * </ul>
 *
 * <p>La JVM corre en {@code America/Guayaquil} (fijada en el arranque), así que {@code LocalDate.now()}
 * ya refleja el día de negocio.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MensajeriaJob {

    private final AsistenciaRepository asistenciaRepository;
    private final CoreServiceClient coreServiceClient;
    private final MensajeLogService mensajeLogService;

    /** Buckets del socio: aviso previo a 3 días + día del vencimiento. */
    private static final int BUCKET_PREVIO = 3;
    private static final int BUCKET_DIA_0 = 0;

    private static final String CANAL_WHATSAPP = "whatsapp";
    private static final String IDIOMA = "es";
    private static final DateTimeFormatter FECHA_ES = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Plantillas HSM del socio (categoría UTILITY, idioma es).
    private static final String TPL_MEMBRESIA_PREVIO = "venc_membresia_previo"; // [nombre, gym, fecha, dias]
    private static final String TPL_MEMBRESIA_HOY = "venc_membresia_hoy";       // [nombre, gym]
    private static final String TPL_ACCESOS_PREVIO = "venc_accesos_previo";     // [nombre, accesos, gym]
    private static final String TPL_ACCESOS_FINAL = "venc_accesos_final";       // [nombre, gym]

    // Tipos lógicos (los mismos que ya usa mensajes_log): distinguen previo vs día del vencimiento.
    private static final String TIPO_PREVIO = "vencimiento_3d";
    private static final String TIPO_HOY = "vencimiento_hoy";

    /** Diariamente a las 00:15 (hora Guayaquil, JVM). Cron sobreescribible por env var. */
    @Scheduled(cron = "${scheduling.messaging-job-cron:0 15 0 * * *}")
    public void ejecutar() {
        log.info("[MensajeriaJob] Inicio ejecución {}", LocalDate.now());
        procesarAusencias()
                .doOnComplete(() -> log.info("[MensajeriaJob] Fin ejecución"))
                .doOnError(e -> log.error("[MensajeriaJob] Error: {}", e.getMessage()))
                .subscribe();
    }

    /**
     * Itera las compañías activas, pide a core sus socios por vencer (buckets {3,0}) y despacha un
     * aviso por WhatsApp a cada uno. Público para poder verificarlo en unit tests sin reflexión.
     */
    public Flux<Void> procesarAusencias() {
        return asistenciaRepository.findCompaniasActivas()
                .flatMap(this::procesarCompania);
    }

    private Flux<Void> procesarCompania(Integer idCompania) {
        Mono<String> gymNombre = asistenciaRepository.findNombreCompania(idCompania)
                .defaultIfEmpty("tu gimnasio")
                .cache();
        return coreServiceClient.listarClientesPorVencer(idCompania, BUCKET_PREVIO, "todos")
                .flatMap(cliente -> gymNombre.flatMap(gym -> procesarCliente(idCompania, cliente, gym)));
    }

    /**
     * Despacha un socio individual: valida opt-in + teléfono, resuelve {@code tipo}+plantilla por
     * {@code modoControl} y bucket, aplica idempotencia y envía. Package-private para tests.
     */
    Mono<Void> procesarCliente(Integer idCompania, ClientePorVencer cliente, String gymNombre) {
        // RN-05: el endpoint ya excluye congelado; reconfirmar por si el contrato cambia.
        if ("congelado".equals(cliente.getEstadoCliente())) {
            return Mono.empty();
        }
        // R4: sin opt-in explícito no se envía WhatsApp (attendance no cae a email).
        if (!cliente.isAceptaWhatsapp()) {
            log.debug("[MensajeriaJob] cliente={} sin opt-in whatsapp, skip", cliente.getIdCliente());
            return Mono.empty();
        }
        Optional<String> e164 = PhoneNumberE164Normalizer.normalizar(cliente.getTelefono());
        if (e164.isEmpty()) {
            log.debug("[MensajeriaJob] cliente={} telefono no normalizable, skip telefono_invalido",
                    cliente.getIdCliente());
            return Mono.empty();
        }

        Aviso aviso = resolverAviso(cliente, gymNombre);
        if (aviso == null) {
            return Mono.empty(); // no cae en ningún bucket {3,0} para su modo
        }

        return mensajeLogService.existsEnviadoHoy(cliente.getIdCliente(), aviso.tipo(), CANAL_WHATSAPP)
                .flatMap(existe -> {
                    if (Boolean.TRUE.equals(existe)) {
                        log.debug("[MensajeriaJob] cliente={} tipo={} ya enviado hoy (C2), skip",
                                cliente.getIdCliente(), aviso.tipo());
                        return Mono.<Void>empty();
                    }
                    return mensajeLogService.enviarWhatsAppJob(
                                    idCompania, cliente.getIdSucursal(), cliente.getIdCliente(),
                                    aviso.tipo(), CANAL_WHATSAPP, e164.get(),
                                    aviso.template(), IDIOMA, aviso.params(), aviso.contenidoLegible())
                            .then();
                });
    }

    /**
     * Resuelve el aviso (tipo + plantilla HSM + params) según {@code modoControl} y el bucket.
     * Devuelve {@code null} si el socio no cae en ningún bucket {3,0} de su modo.
     */
    private Aviso resolverAviso(ClientePorVencer c, String gym) {
        String nombre = c.getNombre() != null ? c.getNombre() : "Cliente";

        if ("accesos".equals(c.getModoControl())) {
            Integer restantes = c.getAccesosRestantes();
            if (restantes == null) return null;
            if (restantes == BUCKET_DIA_0) {
                return new Aviso(TIPO_HOY, TPL_ACCESOS_FINAL, List.of(nombre, gym),
                        "Usaste tu última entrada en " + gym + ".");
            }
            if (restantes == BUCKET_PREVIO) {
                return new Aviso(TIPO_PREVIO, TPL_ACCESOS_PREVIO,
                        List.of(nombre, String.valueOf(restantes), gym),
                        "Te quedan " + restantes + " entradas en " + gym + ".");
            }
            return null;
        }

        // calendario (default)
        Integer dias = c.getDiasParaVencer();
        if (dias == null) return null;
        LocalDate fechaFin = parseFecha(c.getFechaFin());
        String fechaTxt = fechaFin != null ? fechaFin.format(FECHA_ES) : "N/A";

        if (dias == BUCKET_DIA_0) {
            return new Aviso(TIPO_HOY, TPL_MEMBRESIA_HOY, List.of(nombre, gym),
                    "Tu membresía en " + gym + " vence hoy.");
        }
        if (dias >= 1 && dias <= BUCKET_PREVIO) {
            return new Aviso(TIPO_PREVIO, TPL_MEMBRESIA_PREVIO,
                    List.of(nombre, gym, fechaTxt, String.valueOf(dias)),
                    "Tu membresía en " + gym + " vence el " + fechaTxt + ", en " + dias + " días.");
        }
        return null;
    }

    private static LocalDate parseFecha(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return LocalDate.parse(iso);
        } catch (Exception e) {
            return null;
        }
    }

    /** Aviso resuelto: tipo lógico (mensajes_log), plantilla HSM, params en orden, texto legible. */
    private record Aviso(String tipo, String template, List<String> params, String contenidoLegible) {}
}
