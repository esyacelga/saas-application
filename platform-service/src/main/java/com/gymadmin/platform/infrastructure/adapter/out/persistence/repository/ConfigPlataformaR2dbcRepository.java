package com.gymadmin.platform.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.ConfigPlataformaEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

/**
 * REQ-SAAS-001 (sección 11.4) — configuración runtime editable por root.
 * PK es {@code clave VARCHAR(100)}, por eso el tipo del ID es String.
 * Los lookups por clave son directamente {@code findById(String)}.
 */
public interface ConfigPlataformaR2dbcRepository
        extends ReactiveCrudRepository<ConfigPlataformaEntity, String> {
}
