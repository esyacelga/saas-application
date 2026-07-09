package com.gymadmin.platform.domain.port.in;

import com.gymadmin.platform.domain.model.ActividadPlataforma;
import com.gymadmin.platform.domain.model.ActividadPlataforma.TipoActor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface ActividadPlataformaUseCase {

    Flux<ActividadPlataforma> listar(ListarQuery query);

    Mono<Long> contar(ListarQuery query);

    /**
     * Compatibilidad hacia atrás — comando "clásico" (Sub-fases previas).
     */
    Mono<Void> registrar(RegistrarCommand command);

    /**
     * REQ-SAAS-001 sección 6bis — nuevo comando estructurado.
     * <p>
     * El {@code evento} es un String libre (no enum) — los valores permitidos son
     * los listados en la sección 6bis del requerimiento (TRIAL_ACTIVADO,
     * PLAN_SELECCIONADO, PAGO_REPORTADO, PAGO_APROBADO, PAGO_RECHAZADO,
     * PLAN_DEGRADADO_AUTO, PLAN_UPGRADED, SUSCRIPCION_CANCELADA,
     * SUSCRIPCION_SUSPENDIDA, LIMITE_FREE_ALCANZADO, SOBRE_LIMITE_DETECTADO,
     * SOBRE_LIMITE_AUTO_ARCHIVADO, NOTIF_VENCIMIENTO_ENVIADA, NOTIF_EMAIL_FALLIDA).
     * El {@code detalle} se serializa a JSON con Jackson.
     */
    Mono<Void> registrar(RegistrarActividadCommand command);

    record ListarQuery(
            String modulo,
            String tipoEvento,
            String desde,
            String hasta,
            int pagina,
            int porPagina
    ) {}

    record RegistrarCommand(
            String tipoEvento,
            String modulo,
            Long entidadId,
            String entidadNombre,
            String detalle,
            String usuario
    ) {}

    record RegistrarActividadCommand(
            String evento,
            TipoActor tipoActor,
            Long idUsuarioActor,
            String ipActor,
            Long idCompania,
            Map<String, Object> detalle
    ) {}
}
