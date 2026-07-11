package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.model.ActividadPlataforma.TipoActor;
import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.NotificacionSuscripcion;
import com.gymadmin.platform.domain.model.PagoPendienteValidacion;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.in.EnviarNotificacionUseCase;
import com.gymadmin.platform.domain.port.in.ProcesarColaEmailsUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.CompaniaRepository;
import com.gymadmin.platform.domain.port.out.EmailSender;
import com.gymadmin.platform.domain.port.out.NotificacionRepository;
import com.gymadmin.platform.domain.port.out.PagoPendienteValidacionRepository;
import com.gymadmin.platform.infrastructure.email.EmailTemplateEngine;
import com.gymadmin.platform.infrastructure.email.EmailTemplateEngine.RenderedEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REQ-SAAS-001 (Sub-fase 1.5 y 1.6): cola de notificaciones (email + banner) sobre
 * Postgres — {@code FOR UPDATE SKIP LOCKED} para claim atómico, retry con
 * backoff exponencial y "DLQ" implícita en {@code estado='fallido'}.
 * <p>
 * Sub-fase 1.6 añade ruteo de template por {@code tipo} (no solo por
 * {@code diasAntes}) para soportar {@code TRIAL_ACTIVADO} y {@code PAGO_RECHAZADO}.
 * Los datos contextuales (fecha fin del trial, motivo del rechazo) se cargan
 * on-demand desde sus repositorios en lugar de persistirse en la notificación.
 * <p>
 * Sin Redis (ver {@code docs/REDIS_REMOVAL.md}).
 */
@Service
public class EmailQueueService implements EnviarNotificacionUseCase, ProcesarColaEmailsUseCase {

    private static final Logger log = LoggerFactory.getLogger(EmailQueueService.class);

    private static final DateTimeFormatter FECHA_ES = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** REQ-SAAS-001 Sub-fase 1.6: tipos de notificación por email cableados a template propio. */
    static final String TIPO_TRIAL_ACTIVADO = "TRIAL_ACTIVADO";
    static final String TIPO_PAGO_RECHAZADO = "PAGO_RECHAZADO";

    /**
     * Backoff exponencial por intento (índice = intentos previos).
     * Intento 1 falla → programa retry en 30s; intento 2 falla → 2m; etc.
     * A partir del 5º intento (índice 4) → {@code fallido}.
     */
    private static final List<Duration> BACKOFF = List.of(
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            Duration.ofMinutes(10),
            Duration.ofHours(1)
    );
    private static final int MAX_INTENTOS = BACKOFF.size();

    private final NotificacionRepository notificacionRepository;
    private final CompaniaRepository companiaRepository;
    private final CompaniaPlanRepository companiaPlanRepository;
    private final PagoPendienteValidacionRepository pagoPendienteRepository;
    private final EmailSender emailSender;
    private final EmailTemplateEngine templateEngine;
    private final ActividadPlataformaUseCase actividadUseCase;
    private final Clock clock;
    private final String urlComprarPremium;
    private final String urlGymAdmin;
    private final String urlReportarPago;

    public EmailQueueService(NotificacionRepository notificacionRepository,
                              CompaniaRepository companiaRepository,
                              CompaniaPlanRepository companiaPlanRepository,
                              PagoPendienteValidacionRepository pagoPendienteRepository,
                              EmailSender emailSender,
                              EmailTemplateEngine templateEngine,
                              ActividadPlataformaUseCase actividadUseCase,
                              Clock clock,
                              @Value("${notificacion.email.urls.comprar-premium:https://gymadmin.app/planes}") String urlComprarPremium,
                              @Value("${notificacion.email.urls.gym-admin:https://gymadmin.app}") String urlGymAdmin,
                              @Value("${notificacion.email.urls.reportar-pago:https://gymadmin.app/mi-suscripcion/reportar-pago}") String urlReportarPago) {
        this.notificacionRepository = notificacionRepository;
        this.companiaRepository = companiaRepository;
        this.companiaPlanRepository = companiaPlanRepository;
        this.pagoPendienteRepository = pagoPendienteRepository;
        this.emailSender = emailSender;
        this.templateEngine = templateEngine;
        this.actividadUseCase = actividadUseCase;
        this.clock = clock;
        this.urlComprarPremium = urlComprarPremium;
        this.urlGymAdmin = urlGymAdmin;
        this.urlReportarPago = urlReportarPago;
    }

    @Override
    public Mono<Long> encolar(EncolarNotificacionCommand cmd) {
        NotificacionSuscripcion notif = new NotificacionSuscripcion();
        notif.setIdCompania(cmd.idCompania());
        notif.setIdCompaniaPlan(cmd.idCompaniaPlan());
        notif.setTipo(cmd.tipo());
        notif.setDiasAntes(cmd.diasAntes());
        notif.setCanal(cmd.canal());
        notif.setEstado(NotificacionSuscripcion.ESTADO_PENDIENTE);
        notif.setIntentos(0);
        notif.setProximoIntento(OffsetDateTime.now(clock));
        return notificacionRepository.save(notif).map(NotificacionSuscripcion::getId);
    }

    @Override
    public Mono<Integer> procesarLote(int max) {
        return notificacionRepository.claimLoteEmails(max)
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
                        log.warn("Notif {} referencia compania {} inexistente", notif.getId(), notif.getIdCompania());
                        return marcarFallidoConEvento(notif, "compania inexistente");
                    }
                    return cargarContexto(notif)
                            .flatMap(ctx -> enviarConCompania(notif, opt.get(), ctx));
                });
    }

    /**
     * REQ-SAAS-001 Sub-fase 1.6: carga los datos externos necesarios para renderizar
     * cada tipo de template. {@code TRIAL_ACTIVADO} necesita el {@code CompaniaPlan}
     * para la {@code fecha_vencimiento}; {@code PAGO_RECHAZADO} necesita el último
     * pago rechazado para {@code motivo_rechazo} y {@code fecha_reporte}.
     */
    private Mono<RenderContext> cargarContexto(NotificacionSuscripcion notif) {
        String tipo = notif.getTipo();
        if (TIPO_PAGO_RECHAZADO.equals(tipo)) {
            return pagoPendienteRepository.findUltimoRechazadoByCompania(notif.getIdCompania())
                    .map(pago -> new RenderContext(null, pago))
                    .defaultIfEmpty(new RenderContext(null, null));
        }
        if (TIPO_TRIAL_ACTIVADO.equals(tipo) && notif.getIdCompaniaPlan() != null) {
            return companiaPlanRepository.findById(notif.getIdCompaniaPlan())
                    .map(cp -> new RenderContext(cp, null))
                    .defaultIfEmpty(new RenderContext(null, null));
        }
        return Mono.just(new RenderContext(null, null));
    }

    private Mono<Void> enviarConCompania(NotificacionSuscripcion notif, Compania compania, RenderContext ctx) {
        String destinatario = compania.getCorreo();
        if (destinatario == null || destinatario.isBlank()) {
            return marcarFallidoConEvento(notif, "compania sin correo");
        }
        RenderedEmail rendered = renderNotif(notif, compania, ctx);
        return emailSender.enviar(destinatario, rendered.subject(), rendered.html(), rendered.text())
                .then(Mono.defer(() -> notificacionRepository.marcarEnviado(notif.getId())))
                .then(Mono.defer(() -> registrarEnviada(notif)))
                .onErrorResume(err -> manejarError(notif, err));
    }

    private RenderedEmail renderNotif(NotificacionSuscripcion notif, Compania compania, RenderContext ctx) {
        String templateKey = templateKey(notif);
        Map<String, Object> vars = new HashMap<>();
        vars.put("owner_nombre", compania.getNombre() != null ? compania.getNombre() : "");
        vars.put("url_comprar_premium", urlComprarPremium);
        vars.put("url_gym_admin", urlGymAdmin);
        vars.put("url_reportar_pago", urlReportarPago);

        if (TIPO_TRIAL_ACTIVADO.equals(notif.getTipo())) {
            vars.put("plan_actual", "Trial");
            vars.put("dias_trial", 60);
            String fechaVenc = ctx.companiaPlan() != null && ctx.companiaPlan().getFechaFin() != null
                    ? ctx.companiaPlan().getFechaFin().format(FECHA_ES)
                    : "";
            vars.put("fecha_vencimiento", fechaVenc);
        } else if (TIPO_PAGO_RECHAZADO.equals(notif.getTipo())) {
            vars.put("plan_actual", "");
            String motivo = ctx.pagoRechazado() != null && ctx.pagoRechazado().getMotivoRechazo() != null
                    ? ctx.pagoRechazado().getMotivoRechazo()
                    : "";
            vars.put("motivo_rechazo", motivo);
            String fechaReporte = ctx.pagoRechazado() != null && ctx.pagoRechazado().getFechaReporte() != null
                    ? ctx.pagoRechazado().getFechaReporte().atOffset(java.time.ZoneOffset.UTC).format(FECHA_ES)
                    : "";
            vars.put("fecha_reporte", fechaReporte);
        } else {
            // Ruta clásica de vencimiento (Sub-fase 1.5).
            vars.put("plan_actual", planActualDeTipo(notif.getTipo()));
            vars.put("plan_destino", "Free");
            vars.put("dias_restantes", notif.getDiasAntes() != null ? notif.getDiasAntes() : 0);
            vars.put("fecha_vencimiento", "");
        }
        return templateEngine.render(templateKey, vars);
    }

    /**
     * REQ-SAAS-001 Sub-fase 1.6: rutea al templateKey correcto por {@code tipo}.
     * Los tipos {@code TRIAL_ACTIVADO} y {@code PAGO_RECHAZADO} tienen template propio;
     * cualquier otro tipo cae a la lógica clásica por {@code dias_antes}.
     */
    static String templateKey(NotificacionSuscripcion notif) {
        if (notif == null) return "vencimiento_0d";
        if (TIPO_TRIAL_ACTIVADO.equals(notif.getTipo())) return "trial_activado";
        if (TIPO_PAGO_RECHAZADO.equals(notif.getTipo())) return "pago_rechazado";
        return templateKeyPorDias(notif.getDiasAntes());
    }

    /** REQ-SAAS-001 (Sub-fase 1.5): template key desde {@code dias_antes}. */
    static String templateKeyPorDias(Integer diasAntes) {
        if (diasAntes == null) return "vencimiento_0d";
        int d = diasAntes;
        if (d >= 15) return "vencimiento_15d";
        if (d >= 7)  return "vencimiento_7d";
        if (d >= 3)  return "vencimiento_3d";
        if (d >= 1)  return "vencimiento_1d";
        return "vencimiento_0d";
    }

    private static String planActualDeTipo(String tipo) {
        if (tipo == null) return "";
        return switch (tipo) {
            case "VENCIMIENTO_TRIAL" -> "Trial";
            case "VENCIMIENTO_PREMIUM" -> "Premium";
            default -> tipo;
        };
    }

    private Mono<Void> manejarError(NotificacionSuscripcion notif, Throwable err) {
        int intentosPrevios = notif.getIntentos() != null ? notif.getIntentos() : 0;
        int nuevoIntento = intentosPrevios + 1;
        String mensajeError = err.getMessage() != null ? err.getMessage() : err.getClass().getSimpleName();
        log.warn("Fallo envío notif {} (intento {}): {}", notif.getId(), nuevoIntento, mensajeError);

        if (nuevoIntento >= MAX_INTENTOS) {
            return marcarFallidoConEvento(notif, mensajeError);
        }
        OffsetDateTime proximo = OffsetDateTime.now(clock).plus(BACKOFF.get(intentosPrevios));
        return notificacionRepository.marcarReintentar(notif.getId(), nuevoIntento, mensajeError, proximo);
    }

    private Mono<Void> marcarFallidoConEvento(NotificacionSuscripcion notif, String mensajeError) {
        return notificacionRepository.marcarFallido(notif.getId(), mensajeError)
                .then(actividadUseCase.registrar(new ActividadPlataformaUseCase.RegistrarActividadCommand(
                        "NOTIF_EMAIL_FALLIDA",
                        TipoActor.SISTEMA,
                        null,
                        null,
                        notif.getIdCompania(),
                        Map.of(
                                "id_notif", notif.getId(),
                                "intentos", MAX_INTENTOS,
                                "ultimo_error", mensajeError != null ? mensajeError : ""
                        )
                )));
    }

    private Mono<Void> registrarEnviada(NotificacionSuscripcion notif) {
        Map<String, Object> detalle = new HashMap<>();
        detalle.put("dias_antes", notif.getDiasAntes());
        detalle.put("canal", notif.getCanal());
        detalle.put("id_compania_plan", notif.getIdCompaniaPlan());
        detalle.put("tipo", notif.getTipo());
        return actividadUseCase.registrar(new ActividadPlataformaUseCase.RegistrarActividadCommand(
                "NOTIF_VENCIMIENTO_ENVIADA",
                TipoActor.SISTEMA,
                null,
                null,
                notif.getIdCompania(),
                detalle
        ));
    }

    /** Contexto opcional cargado según el tipo de notificación. */
    private record RenderContext(CompaniaPlan companiaPlan, PagoPendienteValidacion pagoRechazado) {}
}
