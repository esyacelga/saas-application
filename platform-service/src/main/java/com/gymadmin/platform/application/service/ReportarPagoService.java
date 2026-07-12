package com.gymadmin.platform.application.service;

import com.gymadmin.platform.application.service.CloudinaryService.ComprobanteSubidoResponse;
import com.gymadmin.platform.domain.exception.PagoDuplicadoException;
import com.gymadmin.platform.domain.model.ActividadPlataforma.TipoActor;
import com.gymadmin.platform.domain.model.PagoPendienteValidacion;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.in.ReportarPagoUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.PagoPendienteValidacionRepository;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * REQ-SAAS-001 (RN-08): reporte de pago por transferencia por parte del owner.
 * Persiste en {@code tenant.pagos_pendientes_validacion} en estado PENDIENTE.
 */
@Service
public class ReportarPagoService implements ReportarPagoUseCase {

    private static final String CODIGO_PLAN_TRIAL = "TRIAL";

    private final PagoPendienteValidacionRepository pagoRepository;
    private final CloudinaryService cloudinaryService;
    private final CompaniaPlanRepository companiaPlanRepository;
    private final PlanRepository planRepository;
    private final ActividadPlataformaUseCase actividadPlataformaUseCase;
    private final Clock clock;

    public ReportarPagoService(PagoPendienteValidacionRepository pagoRepository,
                                CloudinaryService cloudinaryService,
                                CompaniaPlanRepository companiaPlanRepository,
                                PlanRepository planRepository,
                                ActividadPlataformaUseCase actividadPlataformaUseCase,
                                Clock clock) {
        this.pagoRepository = pagoRepository;
        this.cloudinaryService = cloudinaryService;
        this.companiaPlanRepository = companiaPlanRepository;
        this.planRepository = planRepository;
        this.actividadPlataformaUseCase = actividadPlataformaUseCase;
        this.clock = clock;
    }

    @Override
    public Mono<PagoPendienteValidacion> reportar(ReportarPagoCommand command) {
        String hash = calcularHashIdempotencia(command);
        return pagoRepository.findByHashIdempotencia(hash)
                .flatMap(existente -> Mono.<PagoPendienteValidacion>error(new PagoDuplicadoException(
                        "Ya existe un pago " + existente.getEstado() + " con el mismo hash de idempotencia",
                        hash)))
                .switchIfEmpty(Mono.defer(() -> subirYPersistir(command, hash)));
    }

    private Mono<PagoPendienteValidacion> subirYPersistir(ReportarPagoCommand command, String hash) {
        return subirComprobanteSiExiste(command)
                .flatMap(comprobante -> resolverActivacionProgramada(command.idCompania())
                        .flatMap(activacionProgramada -> planRepository.findById(command.idPlanDestino())
                                .switchIfEmpty(Mono.error(new NotFoundException("Plan", command.idPlanDestino())))
                                .flatMap(plan -> persistirPago(command, hash, comprobante, plan, activacionProgramada))
                                .flatMap(saved -> registrarEventoReporte(command, hash, saved).thenReturn(saved))));
    }

    /**
     * REQ-SAAS-001 (Sub-fase 1.6, item #4): el comprobante ahora es opcional.
     * Cuando el owner reporta el pago sin adjuntar archivo, no invocamos a
     * Cloudinary y devolvemos un {@link ComprobanteSubidoResponse} con
     * {@code url = null} y {@code hash = null}.
     */
    private Mono<ComprobanteSubidoResponse> subirComprobanteSiExiste(ReportarPagoCommand command) {
        if (command.comprobanteBytes() == null || command.comprobanteBytes().length == 0) {
            return Mono.just(new ComprobanteSubidoResponse(null, null));
        }
        return cloudinaryService.subirComprobante(command.comprobanteBytes(), command.nombreArchivo(), command.idCompania());
    }

    private Mono<Boolean> resolverActivacionProgramada(Long idCompania) {
        return companiaPlanRepository.findActivoByIdCompania(idCompania)
                .flatMap(actual -> planRepository.findById(actual.getIdPlan())
                        .map(planActual -> CODIGO_PLAN_TRIAL.equalsIgnoreCase(planActual.getCodigo())))
                .defaultIfEmpty(false);
    }

    private Mono<PagoPendienteValidacion> persistirPago(ReportarPagoCommand command,
                                                         String hash,
                                                         ComprobanteSubidoResponse comprobante,
                                                         Plan planDestino,
                                                         boolean activacionProgramada) {
        PagoPendienteValidacion pago = new PagoPendienteValidacion();
        pago.setIdCompania(command.idCompania());
        pago.setIdPlanDestino(command.idPlanDestino());
        pago.setMonto(command.monto());
        pago.setMoneda(planDestino.getMoneda() != null ? planDestino.getMoneda() : "USD");
        pago.setFechaReporte(Instant.now(clock));
        pago.setFechaTransferencia(command.fechaTransferencia());
        pago.setComprobanteUrl(comprobante.url());
        pago.setComprobanteHash(comprobante.hash());
        pago.setBancoOrigen(command.bancoOrigen());
        pago.setReferencia(command.referencia());
        pago.setHashIdempotencia(hash);
        pago.setEstado(PagoPendienteValidacion.Estado.PENDIENTE);
        pago.setActivacionProgramada(activacionProgramada);
        return pagoRepository.save(pago);
    }

    private Mono<Void> registrarEventoReporte(ReportarPagoCommand command, String hash, PagoPendienteValidacion saved) {
        Map<String, Object> detalle = new HashMap<>();
        detalle.put("id_pago_pendiente", saved.getId());
        detalle.put("id_plan_destino", command.idPlanDestino());
        detalle.put("monto", command.monto() != null ? command.monto().toPlainString() : "");
        detalle.put("hash_idempotencia", hash);
        detalle.put("activacion_programada", saved.isActivacionProgramada());
        return actividadPlataformaUseCase.registrar(new ActividadPlataformaUseCase.RegistrarActividadCommand(
                "PAGO_REPORTADO",
                TipoActor.OWNER,
                command.idUsuarioActor(),
                command.ipActor(),
                command.idCompania(),
                detalle
        ));
    }

    private String calcularHashIdempotencia(ReportarPagoCommand command) {
        String raw = command.idCompania() + "|"
                + (command.monto() != null ? command.monto().toPlainString() : "")
                + "|" + (command.fechaTransferencia() != null ? command.fechaTransferencia().toString() : "")
                + "|" + (command.referencia() != null ? command.referencia() : "");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

}
