package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.model.ActividadPlataforma.TipoActor;
import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.model.NotificacionSuscripcion;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.in.EnviarNotificacionUseCase;
import com.gymadmin.platform.domain.port.in.ProcesarColaEmailsUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaRepository;
import com.gymadmin.platform.domain.port.out.EmailSender;
import com.gymadmin.platform.domain.port.out.NotificacionRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REQ-SAAS-001 (Sub-fase 1.5): cola de notificaciones (email + banner) sobre
 * Postgres — {@code FOR UPDATE SKIP LOCKED} para claim atómico, retry con
 * backoff exponencial y "DLQ" implícita en {@code estado='fallido'}.
 * <p>
 * Sin Redis (ver {@code docs/REDIS_REMOVAL.md}).
 */
@Service
public class EmailQueueService implements EnviarNotificacionUseCase, ProcesarColaEmailsUseCase {

    private static final Logger log = LoggerFactory.getLogger(EmailQueueService.class);

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
    private final EmailSender emailSender;
    private final EmailTemplateEngine templateEngine;
    private final ActividadPlataformaUseCase actividadUseCase;
    private final Clock clock;
    private final String urlComprarPremium;
    private final String urlGymAdmin;

    public EmailQueueService(NotificacionRepository notificacionRepository,
                              CompaniaRepository companiaRepository,
                              EmailSender emailSender,
                              EmailTemplateEngine templateEngine,
                              ActividadPlataformaUseCase actividadUseCase,
                              Clock clock,
                              @Value("${notificacion.email.urls.comprar-premium:https://gymadmin.app/planes}") String urlComprarPremium,
                              @Value("${notificacion.email.urls.gym-admin:https://gymadmin.app}") String urlGymAdmin) {
        this.notificacionRepository = notificacionRepository;
        this.companiaRepository = companiaRepository;
        this.emailSender = emailSender;
        this.templateEngine = templateEngine;
        this.actividadUseCase = actividadUseCase;
        this.clock = clock;
        this.urlComprarPremium = urlComprarPremium;
        this.urlGymAdmin = urlGymAdmin;
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
                .map(java.util.Optional::of)
                .defaultIfEmpty(java.util.Optional.empty())
                .flatMap(opt -> {
                    if (opt.isEmpty()) {
                        log.warn("Notif {} referencia compania {} inexistente", notif.getId(), notif.getIdCompania());
                        return marcarFallidoConEvento(notif, "compania inexistente");
                    }
                    return enviarConCompania(notif, opt.get());
                });
    }

    private Mono<Void> enviarConCompania(NotificacionSuscripcion notif, Compania compania) {
        String destinatario = compania.getCorreo();
        if (destinatario == null || destinatario.isBlank()) {
            return marcarFallidoConEvento(notif, "compania sin correo");
        }
        RenderedEmail rendered = renderNotif(notif, compania);
        return emailSender.enviar(destinatario, rendered.subject(), rendered.html(), rendered.text())
                .then(Mono.defer(() -> notificacionRepository.marcarEnviado(notif.getId())))
                .then(Mono.defer(() -> registrarEnviada(notif)))
                .onErrorResume(err -> manejarError(notif, err));
    }

    private RenderedEmail renderNotif(NotificacionSuscripcion notif, Compania compania) {
        String templateKey = templateKeyPorDias(notif.getDiasAntes());
        Map<String, Object> vars = new HashMap<>();
        vars.put("owner_nombre", compania.getNombre() != null ? compania.getNombre() : "");
        vars.put("plan_actual", planActualDeTipo(notif.getTipo()));
        vars.put("plan_destino", "Free");
        vars.put("dias_restantes", notif.getDiasAntes() != null ? notif.getDiasAntes() : 0);
        vars.put("fecha_vencimiento", "");
        vars.put("url_comprar_premium", urlComprarPremium);
        vars.put("url_gym_admin", urlGymAdmin);
        return templateEngine.render(templateKey, vars);
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
}
