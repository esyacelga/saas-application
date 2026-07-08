package com.gymadmin.attendance.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.attendance.infrastructure.adapter.out.persistence.entity.AsistenciaEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface AsistenciaR2dbcRepository extends ReactiveCrudRepository<AsistenciaEntity, Long> {

    @Query("""
            SELECT * FROM asistencia.asistencias
            WHERE id_cliente = :idCliente
              AND id_compania = :idCompania
              AND eliminado = false
              AND (:idMembresia IS NULL OR id_membresia = :idMembresia)
              AND fecha BETWEEN :desde AND :hasta
            ORDER BY fecha DESC
            """)
    Flux<AsistenciaEntity> findByClienteAndPeriodo(Integer idCliente, Integer idCompania,
                                                    LocalDate desde, LocalDate hasta, Integer idMembresia);

    @Query("""
            SELECT * FROM asistencia.asistencias
            WHERE id_cliente = :idCliente
              AND id_compania = :idCompania
              AND eliminado = false
              AND fecha >= :desde
            ORDER BY fecha DESC
            """)
    Flux<AsistenciaEntity> findByClienteUltimos30Dias(Integer idCliente, Integer idCompania, LocalDate desde);

    @Query("""
            SELECT * FROM asistencia.asistencias
            WHERE id_compania = :idCompania
              AND (:idSucursal IS NULL OR id_sucursal = :idSucursal)
              AND fecha = :fecha
              AND eliminado = false
            ORDER BY hora_entrada DESC
            """)
    Flux<AsistenciaEntity> findByCompaniaAndFecha(Integer idCompania, Integer idSucursal, LocalDate fecha);

    @Query("""
            SELECT MAX(fecha) FROM asistencia.asistencias
            WHERE id_cliente = :idCliente
              AND id_compania = :idCompania
              AND eliminado = false
            """)
    Mono<LocalDate> findUltimaAsistencia(Integer idCliente, Integer idCompania);

    @Query("""
            SELECT COUNT(*) FROM asistencia.asistencias
            WHERE id_compania = :idCompania
              AND eliminado = false
              AND fecha BETWEEN :desde AND :hasta
            """)
    Mono<Long> countByCompaniaAndPeriodo(Integer idCompania, LocalDate desde, LocalDate hasta);

    @Query("""
            SELECT a.* FROM asistencia.asistencias a
            INNER JOIN core.clientes c ON c.id = a.id_cliente
            WHERE c.id_persona = :idPersona
              AND a.id_compania = :idCompania
              AND a.eliminado = false
              AND a.fecha >= :desde
            ORDER BY a.fecha DESC
            """)
    Flux<AsistenciaEntity> findByPersonaUltimos30Dias(Long idPersona, Integer idCompania, LocalDate desde);

    @Query("""
            SELECT a.* FROM asistencia.asistencias a
            INNER JOIN core.clientes c ON c.id = a.id_cliente
            WHERE c.id_persona = :idPersona
              AND a.id_compania = :idCompania
              AND a.eliminado = false
              AND a.fecha BETWEEN :desde AND :hasta
            ORDER BY a.fecha DESC
            """)
    Flux<AsistenciaEntity> findByPersonaAndPeriodo(Long idPersona, Integer idCompania, LocalDate desde, LocalDate hasta);
}
