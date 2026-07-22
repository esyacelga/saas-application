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
     * GYM-002: variante de {@link #existsIdempotente} para el disparo manual. Mismo predicado,
     * pero devuelve <b>cuándo</b> se envió el aviso previo para poder informarlo en el 409
     * ("ya se envió el {fecha}") en vez de un booleano ciego.
     *
     * <p>Cada mensaje de WhatsApp tiene costo, así que el botón consulta esto antes de enviar.
     * Devuelve {@code Mono.empty()} si no hay envío previo que bloquee.
     *
     * <p>La fila más reciente puede tener {@code fecha_envio} nula (encolada, aún no procesada);
     * en ese caso se cae a {@code creacion_fecha}, que siempre está poblada.
     */
    Mono<java.time.LocalDateTime> fechaEnvioPrevio(Long idCompaniaPlan, String tipo, String canal, Integer diasAntes);

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

    /**
     * REQ-SAAS-001 (Fase 3): claim atómico de un lote de notificaciones
     * {@code canal='whatsapp'} pendientes usando
     * {@code UPDATE ... WHERE id IN (SELECT ... FOR UPDATE SKIP LOCKED)}.
     * Análogo a {@link #claimLoteEmails(int)} pero para el canal WhatsApp del dueño.
     */
    Flux<NotificacionSuscripcion> claimLoteWhatsapp(int max);

    Mono<Void> marcarEnviado(Long id);

    Mono<Void> marcarReintentar(Long id, int intentos, String ultimoError, java.time.OffsetDateTime proximoIntento);

    Mono<Void> marcarFallido(Long id, String ultimoError);
}
