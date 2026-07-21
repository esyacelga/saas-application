package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.port.in.EnviarRecordatorioVencimientoUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.CompaniaRepository;
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

    private final CompaniaRepository companiaRepository;
    private final CompaniaPlanRepository companiaPlanRepository;
    private final PlanRepository planRepository;
    private final WhatsAppQueueService whatsAppQueueService;

    public EnviarRecordatorioVencimientoService(CompaniaRepository companiaRepository,
                                                CompaniaPlanRepository companiaPlanRepository,
                                                PlanRepository planRepository,
                                                WhatsAppQueueService whatsAppQueueService) {
        this.companiaRepository = companiaRepository;
        this.companiaPlanRepository = companiaPlanRepository;
        this.planRepository = planRepository;
        this.whatsAppQueueService = whatsAppQueueService;
    }

    @Override
    public Mono<Resultado> enviar(Long idCompania) {
        return companiaRepository.findById(idCompania)
                .switchIfEmpty(Mono.error(new NotFoundException("Compañía " + idCompania + " no encontrada")))
                .flatMap(this::enviarParaCompania);
    }

    private Mono<Resultado> enviarParaCompania(Compania compania) {
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
                .flatMap(plan -> enviarConPlan(compania, plan, e164.get()));
    }

    private Mono<Resultado> enviarConPlan(Compania compania, CompaniaPlan companiaPlan, String e164) {
        long dias = ChronoUnit.DAYS.between(LocalDate.now(), companiaPlan.getFechaFin());
        Integer diasAntes = (int) dias;
        String template = WhatsAppQueueService.templatePorDias(diasAntes);
        String ownerNombre = compania.getNombre() != null ? compania.getNombre() : "";
        String fechaVenc = WhatsAppQueueService.formatearFecha(companiaPlan.getFechaFin());

        return planRepository.findById(companiaPlan.getIdPlan())
                .map(p -> p.getNombre() != null ? p.getNombre() : "")
                .defaultIfEmpty("")
                .flatMap(planNombre -> {
                    List<String> params = WhatsAppQueueService.construirParams(
                            template, ownerNombre, planNombre, fechaVenc, diasAntes);
                    log.info("Recordatorio vencimiento manual → compania {} template {} dias {}",
                            compania.getId(), template, diasAntes);
                    return whatsAppQueueService.enviarPlantilla(e164, template, params)
                            .thenReturn(new Resultado(true, e164, template));
                });
    }
}
