package com.gymadmin.billing.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.ColaEnvioEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ColaEnvioR2dbcRepository extends ReactiveCrudRepository<ColaEnvioEntity, Long> {

    @Query("SELECT * FROM facturacion.cola_envio WHERE estado = 'PENDIENTE' AND intentos < max_intentos AND (proxima_ejecucion IS NULL OR proxima_ejecucion <= NOW()) ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED")
    Flux<ColaEnvioEntity> findPendientesParaEnviar(int limit);

    @Query("SELECT * FROM facturacion.cola_envio WHERE id_comprobante = :idComprobante ORDER BY created_at DESC LIMIT 1")
    Mono<ColaEnvioEntity> findLatestByIdComprobante(Long idComprobante);
}
