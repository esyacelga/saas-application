package com.gymadmin.platform.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.ActividadPlataformaEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface ActividadPlataformaR2dbcRepository
        extends ReactiveCrudRepository<ActividadPlataformaEntity, Long> {
}
