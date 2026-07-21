package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.exception.WhatsAppSendException;
import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.NotificacionSuscripcion;
import com.gymadmin.platform.domain.port.in.ProcesarColaWhatsAppUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.CompaniaRepository;
import com.gymadmin.platform.domain.port.out.NotificacionRepository;
import com.gymadmin.platform.domain.port.out.WhatsAppSender;
import com.gymadmin.platform.domain.validation.PhoneNumberE164Normalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * REQ-SAAS-001 (Fase 3): worker de la cola de notificaciones por WhatsApp del dueño.
 *
 * <p><b>C1 — hermano, no extensión de {@link EmailQueueService}:</b> lee la MISMA tabla
 * {@code tenant.notificaciones_suscripcion} filtrando {@code canal='whatsapp'} vía
 * {@link NotificacionRepository#claimLoteWhatsapp(int)}, pero con su propio
 * {@link WhatsAppSender} y su propia clasificación de errores/backoff. Reutiliza el mismo
 * escalado de backoff que el email ({@code [30s, 2m, 10m, 1h]}, 4 intentos → {@code fallido}).
 *
 * <p><b>Regla cross-day del día 0:</b> el aviso "vence hoy" ({@code dias_antes == 0}) no debe
 * reintentarse si el backoff cruzaría a un día distinto — el texto ya sería falso. En ese caso
 * se marca {@code fallido} sin reintentar.
 *
 * <p><b>R3 — {@code fecha_vencimiento}:</b> se carga el {@link CompaniaPlan} para poblar la fecha
 * de vencimiento formateada ({@code dd/MM/yyyy}) en el aviso previo, evitando el {@code {{3}}}
 * vacío que Meta rechazaría en runtime.
 */
@Service
public class WhatsAppQueueService implements ProcesarColaWhatsAppUseCase {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppQueueService.class);

    private static final DateTimeFormatter FECHA_ES = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String IDIOMA = "es";

    /** Plantillas HSM del dueño (Meta). Ver docs/gym-administrator/pendientes/whatsapp-avisos-vencimiento.md. */
    static final String TEMPLATE_PREVIO = "recordatorio_vencimiento_suscripcion";
    static final String TEMPLATE_HOY = "venc_suscripcion_hoy";

    /**
     * Backoff exponencial por intento (índice = intentos previos), calcado de {@link EmailQueueService}.
     * Intento 1 falla → retry en 30s; 2 → 2m; 3 → 10m; 4 → 1h. 5º (índice 4) → {@code fallido}.
     */
    static final List<Duration> BACKOFF = List.of(
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            Duration.ofMinutes(10),
            Duration.ofHours(1)
    );
    static final int MAX_INTENTOS = BACKOFF.size();

    private final NotificacionRepository notificacionRepository;
    private final CompaniaRepository companiaRepository;
    private final CompaniaPlanRepository companiaPlanRepository;
    private final WhatsAppSender whatsAppSender;
    private final Clock clock;

    public WhatsAppQueueService(NotificacionRepository notificacionRepository,
                                 CompaniaRepository companiaRepository,
                                 CompaniaPlanRepository companiaPlanRepository,
                                 WhatsAppSender whatsAppSender,
                                 Clock clock) {
        this.notificacionRepository = notificacionRepository;
        this.companiaRepository = companiaRepository;
        this.companiaPlanRepository = companiaPlanRepository;
        this.whatsAppSender = whatsAppSender;
        this.clock = clock;
    }

    @Override
    public Mono<Integer> procesarLote(int max) {
        return notificacionRepository.claimLoteWhatsapp(max)
                .concatMap(notif -> procesarUna(notif).thenReturn(notif.getId()))
                .count()
                .map(Long::intValue);
    }

    private Mono<Void> procesarUna(NotificacionSuscripcion notif) {
        return companiaRepository.findById(notif.getIdCompania())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(opt -> {
                    if (opt.isEmpty()) {
                        log.warn("Notif WA {} referencia compania {} inexistente",
                                notif.getId(), notif.getIdCompania());
                        return notificacionRepository.marcarFallido(notif.getId(), "compania inexistente");
                    }
                    return enviarConCompania(notif, opt.get());
                });
    }

    private Mono<Void> enviarConCompania(NotificacionSuscripcion notif, Compania compania) {
        // Teléfono normalizado a E.164 (whatsapp dedicado, con fallback a telefono).
        String rawTelefono = compania.getWhatsapp() != null ? compania.getWhatsapp() : compania.getTelefono();
        Optional<String> e164 = PhoneNumberE164Normalizer.normalizar(rawTelefono);
        if (e164.isEmpty()) {
            log.debug("Notif WA {} — telefono no normalizable (compania {}), skip", notif.getId(), compania.getId());
            return notificacionRepository.marcarFallido(notif.getId(), "telefono_invalido");
        }

        // Defensivo: el encolado (R4) ya exige opt-in, pero no enviamos sin consentimiento.
        if (!compania.isAceptaWhatsapp()) {
            log.debug("Notif WA {} — sin consentimiento (compania {}), skip", notif.getId(), compania.getId());
            return notificacionRepository.marcarFallido(notif.getId(), "no_consentimiento");
        }

        String template = templatePorDias(notif.getDiasAntes());
        String plan = planActualDeTipo(notif.getTipo());
        String ownerNombre = compania.getNombre() != null ? compania.getNombre() : "";

        Mono<String> fechaVencMono = TEMPLATE_PREVIO.equals(template)
                ? cargarFechaVencimiento(notif)
                : Mono.just("");

        return fechaVencMono.flatMap(fechaVenc -> {
            List<String> params = construirParams(template, ownerNombre, plan, fechaVenc, notif.getDiasAntes());
            return whatsAppSender.enviarPlantilla(e164.get(), template, IDIOMA, params)
                    .then(Mono.defer(() -> notificacionRepository.marcarEnviado(notif.getId())))
                    .onErrorResume(err -> manejarError(notif, err));
        });
    }

    /**
     * GYM-002: envío directo de una plantilla HSM a una compañía dado su {@code e164} ya normalizado.
     * Punto único de invocación del {@link WhatsAppSender} — el flujo directo (botón del panel) lo
     * reutiliza para no duplicar sender ni idioma. NO toca la cola: el error del sender se propaga tal
     * cual para que el llamador decida (aquí, devolver éxito/fallo real al endpoint).
     */
    Mono<Void> enviarPlantilla(String e164, String template, List<String> params) {
        return whatsAppSender.enviarPlantilla(e164, template, IDIOMA, params);
    }

    /** R3: carga la fecha de vencimiento del CompaniaPlan formateada; "" si no está disponible. */
    private Mono<String> cargarFechaVencimiento(NotificacionSuscripcion notif) {
        if (notif.getIdCompaniaPlan() == null) {
            return Mono.just("");
        }
        return companiaPlanRepository.findById(notif.getIdCompaniaPlan())
                .map(this::formatearFechaFin)
                .defaultIfEmpty("");
    }

    private String formatearFechaFin(CompaniaPlan cp) {
        return cp.getFechaFin() != null ? cp.getFechaFin().format(FECHA_ES) : "";
    }

    /**
     * Params en orden según la plantilla:
     * <ul>
     *   <li>{@code recordatorio_vencimiento_suscripcion}: [nombre, plan, fecha_vencimiento, dias] → {{1}}..{{4}}.</li>
     *   <li>{@code venc_suscripcion_hoy}: [nombre, plan] → {{1}}, {{2}}.</li>
     * </ul>
     */
    static List<String> construirParams(String template, String ownerNombre, String plan,
                                          String fechaVenc, Integer diasAntes) {
        List<String> params = new ArrayList<>();
        params.add(ownerNombre);
        params.add(plan);
        if (TEMPLATE_PREVIO.equals(template)) {
            params.add(fechaVenc);
            params.add(String.valueOf(diasAntes != null ? diasAntes : 0));
        }
        return params;
    }

    static String templatePorDias(Integer diasAntes) {
        return (diasAntes != null && diasAntes == 0) ? TEMPLATE_HOY : TEMPLATE_PREVIO;
    }

    /** GYM-002: formatea una fecha de vencimiento con el mismo patrón {@code dd/MM/yyyy} de la cola. */
    static String formatearFecha(LocalDate fecha) {
        return fecha != null ? fecha.format(FECHA_ES) : "";
    }

    private static String planActualDeTipo(String tipo) {
        if (tipo == null) return "";
        return switch (tipo) {
            case "VENCIMIENTO_TRIAL" -> "Trial";
            case "VENCIMIENTO_PREMIUM" -> "Premium";
            default -> tipo;
        };
    }

    /**
     * Clasifica el fallo y decide {@code reintentar} vs {@code fallido}, respetando la regla
     * cross-day del día 0 (no reintentar el aviso "vence hoy" si el backoff cruzaría de fecha).
     */
    private Mono<Void> manejarError(NotificacionSuscripcion notif, Throwable err) {
        boolean retryable;
        Integer metaCode = null;
        if (err instanceof WhatsAppSendException wse) {
            retryable = wse.isRetryable();
            metaCode = wse.getMetaErrorCode();
        } else {
            // Error inesperado (no del sender): trátalo como retryable genérico.
            retryable = true;
        }

        int intentosPrevios = notif.getIntentos() != null ? notif.getIntentos() : 0;
        int nuevoIntento = intentosPrevios + 1;
        String base = err.getMessage() != null ? err.getMessage() : err.getClass().getSimpleName();
        String mensajeError = metaCode != null ? base + " [meta_code=" + metaCode + "]" : base;

        log.warn("Fallo envío WA notif {} (intento {}, retryable={}): {}",
                notif.getId(), nuevoIntento, retryable, mensajeError);

        if (!retryable) {
            return notificacionRepository.marcarFallido(notif.getId(), mensajeError);
        }

        OffsetDateTime ahora = OffsetDateTime.now(clock);
        OffsetDateTime proximo = ahora.plus(BACKOFF.get(intentosPrevios));

        // Regla cross-day: el aviso día 0 no se reintenta si el retry cae en otra fecha.
        boolean esDia0 = notif.getDiasAntes() != null && notif.getDiasAntes() == 0;
        if (esDia0 && cruzaDeDia(ahora, proximo)) {
            log.warn("Notif WA {} — aviso día 0, retry cruzaría de fecha → fallido sin reintentar", notif.getId());
            return notificacionRepository.marcarFallido(notif.getId(), mensajeError + " (cross-day dia 0)");
        }

        if (nuevoIntento >= MAX_INTENTOS) {
            return notificacionRepository.marcarFallido(notif.getId(), mensajeError);
        }
        return notificacionRepository.marcarReintentar(notif.getId(), nuevoIntento, mensajeError, proximo);
    }

    private static boolean cruzaDeDia(OffsetDateTime ahora, OffsetDateTime proximo) {
        LocalDate hoy = ahora.toLocalDate();
        return proximo.toLocalDate().isAfter(hoy);
    }
}
