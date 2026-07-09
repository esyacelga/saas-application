package com.gymadmin.billing.domain.port.out;

import com.gymadmin.billing.domain.model.Comprobante;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

public interface ComprobanteRepository {

    Mono<Comprobante> save(Comprobante comprobante);

    Mono<Comprobante> findById(Long id);

    Mono<Comprobante> findByClaveAcceso(String claveAcceso);

    Flux<Comprobante> findByEmpresa(Integer idCompania, Integer idSucursal, String estado, int offset, int limit);

    Mono<Long> countByEmpresa(Integer idCompania, Integer idSucursal, String estado);

    Mono<Comprobante> updateEstado(Long id, String estado, String xmlFirmadoPath, String xmlAutorizadoPath,
                                   String ridePdfPath, OffsetDateTime fechaAutorizacion, String numeroAutorizacion);
}
