package com.gymadmin.billing.domain.port.out;

import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.model.ComprobanteDetalle;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

public interface ComprobanteRepository {

    Mono<Comprobante> save(Comprobante comprobante);

    Mono<Comprobante> findById(Long id);

    Mono<Comprobante> findByClaveAcceso(String claveAcceso);

    Flux<Comprobante> findByEmpresa(Integer idCompania, Integer idSucursal, String estado, int offset, int limit);

    Mono<Long> countByEmpresa(Integer idCompania, Integer idSucursal, String estado);

    /**
     * Lista comprobantes filtrando por tipo (ej. {@code "04"} para NC) y,
     * opcionalmente, por comprobante referenciado (id de la factura original).
     * Preserva la semántica de paginación de {@link #findByEmpresa}.
     */
    Flux<Comprobante> findByEmpresaAndTipo(Integer idCompania, Integer idSucursal, String tipoComprobante,
                                            String estado, Long idComprobanteRef, int offset, int limit);

    /**
     * Total (para paginación) con los mismos filtros que
     * {@link #findByEmpresaAndTipo}.
     */
    Mono<Long> countByEmpresaAndTipo(Integer idCompania, Integer idSucursal, String tipoComprobante,
                                      String estado, Long idComprobanteRef);

    Mono<Comprobante> updateEstado(Long id, String estado, String xmlFirmadoPath, String xmlAutorizadoPath,
                                   String ridePdfPath, OffsetDateTime fechaAutorizacion, String numeroAutorizacion);

    Flux<ComprobanteDetalle> findDetallesByIdComprobante(Long idComprobante);
}
