package com.gymadmin.core.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.core.infrastructure.adapter.out.persistence.entity.TipoMembresiaEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TipoMembresiaR2dbcRepository extends ReactiveCrudRepository<TipoMembresiaEntity, Long> {

    @Query("SELECT * FROM core.tipos_membresia WHERE id_compania = :idCompania AND activo = true")
    Flux<TipoMembresiaEntity> findActivosByIdCompania(Long idCompania);

    @Query("SELECT * FROM core.tipos_membresia WHERE nombre = :nombre AND id_compania = :idCompania")
    Mono<TipoMembresiaEntity> findByNombreAndIdCompania(String nombre, Long idCompania);

    @Query("SELECT COUNT(*) > 0 FROM core.membresias WHERE id_tipo_membresia = :idTipoMembresia AND estado = 'activa'")
    Mono<Boolean> existeMembresiaActivaDeEsteTipo(Long idTipoMembresia);
}
