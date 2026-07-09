package com.gymadmin.billing.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.ComprobanteEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

public interface ComprobanteR2dbcRepository extends ReactiveCrudRepository<ComprobanteEntity, Long> {

    @Query("SELECT * FROM facturacion.comprobantes WHERE clave_acceso = :claveAcceso LIMIT 1")
    Mono<ComprobanteEntity> findByClaveAcceso(String claveAcceso);

    @Query("""
            SELECT * FROM facturacion.comprobantes
            WHERE id_compania = :idCompania
              AND (:idSucursal IS NULL OR id_sucursal = :idSucursal)
              AND (:estado IS NULL OR estado = :estado)
            ORDER BY created_at DESC
            LIMIT :limit OFFSET :offset
            """)
    Flux<ComprobanteEntity> findByEmpresa(Integer idCompania, Integer idSucursal, String estado, int limit, int offset);

    @Query("""
            SELECT COUNT(*) FROM facturacion.comprobantes
            WHERE id_compania = :idCompania
              AND (:idSucursal IS NULL OR id_sucursal = :idSucursal)
              AND (:estado IS NULL OR estado = :estado)
            """)
    Mono<Long> countByEmpresa(Integer idCompania, Integer idSucursal, String estado);

    @Modifying
    @Query("""
            UPDATE facturacion.comprobantes
            SET estado = :estado,
                xml_firmado_path = :xmlFirmadoPath,
                xml_autorizado_path = :xmlAutorizadoPath,
                ride_pdf_path = :ridePdfPath,
                fecha_autorizacion = :fechaAutorizacion,
                numero_autorizacion = :numeroAutorizacion,
                updated_at = NOW()
            WHERE id = :id
            """)
    Mono<Integer> updateEstadoById(Long id, String estado, String xmlFirmadoPath,
                                    String xmlAutorizadoPath, String ridePdfPath,
                                    OffsetDateTime fechaAutorizacion, String numeroAutorizacion);
}
