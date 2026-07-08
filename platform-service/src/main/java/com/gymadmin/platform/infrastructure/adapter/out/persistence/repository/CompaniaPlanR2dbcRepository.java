package com.gymadmin.platform.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.CompaniaPlanEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface CompaniaPlanR2dbcRepository extends ReactiveCrudRepository<CompaniaPlanEntity, Long> {

    @Query("SELECT * FROM tenant.compania_planes WHERE id_compania = :idCompania AND estado IN ('activo', 'en_gracia') ORDER BY id DESC LIMIT 1")
    Mono<CompaniaPlanEntity> findActivoByIdCompania(Long idCompania);

    @Query("SELECT * FROM tenant.compania_planes WHERE id_compania = :idCompania ORDER BY id DESC")
    Flux<CompaniaPlanEntity> findByIdCompania(Long idCompania);

    @Query("SELECT * FROM tenant.compania_planes WHERE estado = :estado")
    Flux<CompaniaPlanEntity> findByEstado(String estado);

    @Query("SELECT * FROM tenant.compania_planes WHERE estado = 'activo' AND fecha_fin < :today")
    Flux<CompaniaPlanEntity> findActivosVencidos(LocalDate today);

    @Query("SELECT * FROM tenant.compania_planes WHERE estado = 'en_gracia' AND (fecha_fin + CAST(dias_gracia || ' days' AS INTERVAL)) < :today")
    Flux<CompaniaPlanEntity> findEnGraciaVencidos(LocalDate today);

    @Query("SELECT * FROM tenant.compania_planes WHERE estado = 'programado' AND fecha_inicio <= :today")
    Flux<CompaniaPlanEntity> findProgramadosParaActivar(LocalDate today);

    @Query("SELECT * FROM tenant.compania_planes WHERE estado IN ('activo', 'en_gracia')")
    Flux<CompaniaPlanEntity> findActivosAndEnGracia();

    @Modifying
    @Query("UPDATE tenant.compania_planes SET estado = :estado, motivo_suspension = :motivo WHERE id = :id")
    Mono<Void> updateEstadoById(Long id, String estado, String motivo);
}
