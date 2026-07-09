package com.gymadmin.platform.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.PagoPendienteValidacionEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

/**
 * REQ-SAAS-001 (RN-08) — buzón de pagos reportados pendientes de validación manual.
 * Sub-fase 1.2: mínimo indispensable (save, findById, lookup por hash de idempotencia).
 * Sub-fase 1.3 añadirá listados paginados por estado/tenant.
 */
public interface PagoPendienteValidacionR2dbcRepository
        extends ReactiveCrudRepository<PagoPendienteValidacionEntity, Long> {

    /**
     * Idempotencia RN-08: una compañía no puede reportar dos pagos con el mismo
     * hash mientras uno esté "pendiente" o "aprobado". El índice único parcial de
     * DB {@code ux_pagos_pendientes_hash} garantiza esta invariante, este método
     * expone la lectura para validar antes de intentar el INSERT.
     */
    @Query("SELECT * FROM tenant.pagos_pendientes_validacion " +
           "WHERE hash_idempotencia = :hash AND estado IN ('pendiente','aprobado') " +
           "LIMIT 1")
    Mono<PagoPendienteValidacionEntity> findByHashIdempotencia(String hash);
}
