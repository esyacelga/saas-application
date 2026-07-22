package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.NotificacionSuscripcion;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.port.in.EnviarRecordatorioVencimientoUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.CompaniaRepository;
import com.gymadmin.platform.domain.port.out.NotificacionRepository;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import com.gymadmin.platform.domain.validation.PhoneNumberE164Normalizer;
import com.gymadmin.platform.infrastructure.exception.ErrorCode;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
import com.gymadmin.platform.infrastructure.exception.RecordatorioNoEnviableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * GYM-002: implementación del disparo manual del recordatorio de vencimiento por WhatsApp.
 *
 * <p>Reutiliza la maquinaria de la cola ({@link WhatsAppQueueService}): elección de plantilla por
 * días ({@code templatePorDias}), construcción de params en orden ({@code construirParams}) y el
 * único punto de invocación del sender ({@code enviarPlantilla}). Así no duplica sender, idioma ni
 * el formato de fecha. El envío es directo e inmediato; el fallo del sender se propaga al endpoint.
 */
@Service
public class EnviarRecordatorioVencimientoService implements EnviarRecordatorioVencimientoUseCase {

    private static final Logger log = LoggerFactory.getLogger(EnviarRecordatorioVencimientoService.class);

    /** Mismo criterio que {@code NotificacionVencimientoJob}: TRIAL vs. el resto (PREMIUM). */
    private static final String CODIGO_PLAN_TRIAL = "TRIAL";

    private final CompaniaRepository companiaRepository;
    private final CompaniaPlanRepository companiaPlanRepository;
    private final PlanRepository planRepository;
    private final WhatsAppQueueService whatsAppQueueService;
    private final NotificacionRepository notificacionRepository;

    public EnviarRecordatorioVencimientoService(CompaniaRepository companiaRepository,
                                                CompaniaPlanRepository companiaPlanRepository,
                                                PlanRepository planRepository,
                                                WhatsAppQueueService whatsAppQueueService,
                                                NotificacionRepository notificacionRepository) {
        this.companiaRepository = companiaRepository;
        this.companiaPlanRepository = companiaPlanRepository;
        this.planRepository = planRepository;
        this.whatsAppQueueService = whatsAppQueueService;
        this.notificacionRepository = notificacionRepository;
    }

    @Override
    public Mono<Resultado> enviar(Long idCompania, boolean forzar) {
        return companiaRepository.findById(idCompania)
                .switchIfEmpty(Mono.error(new NotFoundException("Compañía " + idCompania + " no encontrada")))
                .flatMap(compania -> enviarParaCompania(compania, forzar));
    }

    private Mono<Resultado> enviarParaCompania(Compania compania, boolean forzar) {
        if (!compania.isAceptaWhatsapp()) {
            return Mono.error(new RecordatorioNoEnviableException(
                    ErrorCode.NO_CONSENTIMIENTO,
                    "La compañía no ha dado consentimiento para recibir WhatsApp"));
        }

        String rawTelefono = compania.getWhatsapp() != null ? compania.getWhatsapp() : compania.getTelefono();
        Optional<String> e164 = PhoneNumberE164Normalizer.normalizar(rawTelefono);
        if (e164.isEmpty()) {
            return Mono.error(new RecordatorioNoEnviableException(
                    ErrorCode.TELEFONO_INVALIDO,
                    "El teléfono de la compañía no es un celular válido para WhatsApp"));
        }

        return companiaPlanRepository.findActivoByIdCompania(compania.getId())
                .switchIfEmpty(Mono.error(new RecordatorioNoEnviableException(
                        ErrorCode.SIN_SUSCRIPCION,
                        "La compañía no tiene una suscripción activa para recordar")))
                .flatMap(plan -> enviarConPlan(compania, plan, e164.get(), forzar));
    }

    private Mono<Resultado> enviarConPlan(Compania compania, CompaniaPlan companiaPlan, String e164, boolean forzar) {
        long dias = ChronoUnit.DAYS.between(LocalDate.now(), companiaPlan.getFechaFin());
        Integer diasAntes = (int) dias;
        String template = WhatsAppQueueService.templatePorDias(diasAntes);
        String ownerNombre = compania.getNombre() != null ? compania.getNombre() : "";
        String fechaVenc = WhatsAppQueueService.formatearFecha(companiaPlan.getFechaFin());

        // El bucket es el mismo concepto que usa el job (0 para el día del vencimiento). Un aviso
        // ya enviado con dias_antes MAYOR cubre a este, así que la guarda usa >= (ver fechaEnvioPrevio).
        int bucket = Math.max(diasAntes, 0);

        return planRepository.findById(companiaPlan.getIdPlan())
                .defaultIfEmpty(new Plan())
                .flatMap(plan -> {
                    String planNombre = plan.getNombre() != null ? plan.getNombre() : "";
                    String tipo = tipoNotificacion(plan.getCodigo());

                    return guardaIdempotencia(companiaPlan.getId(), tipo, bucket, forzar)
                            .then(Mono.defer(() -> {
                                List<String> params = WhatsAppQueueService.construirParams(
                                        template, ownerNombre, planNombre, fechaVenc, diasAntes);
                                log.info("Recordatorio vencimiento manual → compania {} template {} dias {} forzar {}",
                                        compania.getId(), template, diasAntes, forzar);
                                return whatsAppQueueService.enviarPlantilla(e164, template, params)
                                        // Solo se registra tras un envío exitoso: si el sender falla, el
                                        // mensaje no se cobró y el operador debe poder reintentar sin forzar.
                                        // Mono.defer es obligatorio: sin él, registrarEnvio() se evalúa al
                                        // construir la cadena y el save() ocurre aunque el envío falle.
                                        .then(Mono.defer(() -> registrarEnvio(compania, companiaPlan, tipo, bucket)))
                                        .thenReturn(new Resultado(true, e164, template));
                            }));
                });
    }

    /**
     * Corta el flujo con 409 {@code notificacion_ya_enviada} si ya hay un aviso registrado para este
     * bucket. Cada mensaje de WhatsApp tiene costo: sin esta guarda, dos clicks cobran dos veces.
     */
    private Mono<Void> guardaIdempotencia(Long idCompaniaPlan, String tipo, int bucket, boolean forzar) {
        if (forzar) {
            return Mono.empty();
        }
        return notificacionRepository
                .fechaEnvioPrevio(idCompaniaPlan, tipo, NotificacionSuscripcion.CANAL_WHATSAPP, bucket)
                .flatMap(fecha -> Mono.<Void>error(new RecordatorioNoEnviableException(
                        ErrorCode.NOTIFICACION_YA_ENVIADA,
                        "El recordatorio ya fue enviado para esta suscripción",
                        fecha)));
    }

    /**
     * Deja constancia del envío en la MISMA tabla que usa el job automático, para que este tampoco
     * repita el aviso después (el hueco job↔botón que existía cuando el envío manual no dejaba rastro).
     */
    private Mono<Void> registrarEnvio(Compania compania, CompaniaPlan companiaPlan, String tipo, int bucket) {
        NotificacionSuscripcion n = new NotificacionSuscripcion();
        n.setIdCompania(compania.getId());
        n.setIdCompaniaPlan(companiaPlan.getId());
        n.setTipo(tipo);
        n.setDiasAntes(bucket);
        n.setCanal(NotificacionSuscripcion.CANAL_WHATSAPP);
        n.setEstado(NotificacionSuscripcion.ESTADO_ENVIADO);
        n.setIntentos(1);
        n.setFechaEnvio(LocalDateTime.now());
        return notificacionRepository.save(n)
                .doOnError(e -> log.error("Envío manual OK pero no se pudo registrar (compania {}): {}",
                        compania.getId(), e.getMessage()))
                .then();
    }

    private static String tipoNotificacion(String codigoPlan) {
        return CODIGO_PLAN_TRIAL.equalsIgnoreCase(codigoPlan) ? "VENCIMIENTO_TRIAL" : "VENCIMIENTO_PREMIUM";
    }
}
