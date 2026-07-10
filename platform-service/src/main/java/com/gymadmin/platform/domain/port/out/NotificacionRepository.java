package com.gymadmin.platform.domain.port.out;

import com.gymadmin.platform.domain.model.NotificacionSuscripcion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface NotificacionRepository {

    Mono<NotificacionSuscripcion> save(NotificacionSuscripcion notificacion);

    Flux<NotificacionSuscripcion> findByIdCompaniaPlan(Long idCompaniaPlan);

    Mono<Boolean> existsByIdCompaniaPlanAndDiasAntes(Long idCompaniaPlan, Integer diasAntes);

    /**
     * REQ-SAAS-001 (Sub-fase 1.5, RN-07): predicado idempotente del scheduler de
     * vencimiento. Retorna true si ya existe una notificación para ese
     * {@code idCompaniaPlan}, {@code tipo} y {@code canal} con
     * {@code dias_antes >= diasAntes} — evita duplicar recordatorios ya cubiertos
     * por un bucket anterior (ej: si ya se mandó T-15 no se manda T-7).
     */
    Mono<Boolean> existsIdempotente(Long idCompaniaPlan, String tipo, String canal, Integer diasAntes);

    /**
     * REQ-SAAS-001 (Sub-fase 1.5): banners activos del tenant en el día actual.
     * Excluye los descartados hoy mismo (los descartados en días previos vuelven a
     * aparecer para que el owner los vea de nuevo).
     */
    Flux<NotificacionSuscripcion> findBannersActivosHoy(Long idCompania);

    Mono<NotificacionSuscripcion> findById(Long id);

    /**
     * REQ-SAAS-001 (Sub-fase 1.5): marca el banner como descartado por el owner.
     * Verifica que el {@code idCompania} coincide con el del banner — retorna
     * cantidad de filas afectadas (0 si el banner no existe o no pertenece al tenant).
     */
    Mono<Long> descartarBanner(Long idBanner, Long idCompania);

    /**
     * REQ-SAAS-001 (Sub-fase 1.5): claim atómico de un lote de emails pendientes
     * usando {@code UPDATE ... WHERE id IN (SELECT ... FOR UPDATE SKIP LOCKED)}.
     * Marca las filas seleccionadas y las devuelve al worker para procesar.
     */
    Flux<NotificacionSuscripcion> claimLoteEmails(int max);

    Mono<Void> marcarEnviado(Long id);

    Mono<Void> marcarReintentar(Long id, int intentos, String ultimoError, java.time.OffsetDateTime proximoIntento);

    Mono<Void> marcarFallido(Long id, String ultimoError);
}
