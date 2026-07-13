package com.gymadmin.billing.domain.port.out;

import com.gymadmin.billing.domain.model.sri.FormaPagoSri;
import com.gymadmin.billing.domain.model.sri.MotivoAnulacionNcSri;
import com.gymadmin.billing.domain.model.sri.TarifaIvaSri;
import com.gymadmin.billing.domain.model.sri.TipoComprobanteSri;
import com.gymadmin.billing.domain.model.sri.TipoIdentificacionSri;
import com.gymadmin.billing.domain.model.sri.TipoImpuestoSri;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Puerto de solo lectura para los 6 catálogos SRI publicados por el schema
 * {@code sri}. Cada método debe devolver {@link Mono#empty()} cuando el código
 * no existe; nunca lanzar excepciones. La política de fallo (traducir a
 * {@link com.gymadmin.billing.infrastructure.exception.BusinessException} u otro)
 * corresponde a la capa de aplicación.
 */
public interface CatalogoSriRepository {

    Mono<TipoComprobanteSri> findTipoComprobante(String codigo);

    Mono<TipoIdentificacionSri> findTipoIdentificacion(String codigo);

    Mono<FormaPagoSri> findFormaPago(String codigo);

    Mono<TipoImpuestoSri> findTipoImpuesto(String codigo);

    Mono<TarifaIvaSri> findTarifaIva(String codigo);

    Mono<MotivoAnulacionNcSri> findMotivoAnulacionNc(String codigo);

    /**
     * Lista todos los motivos de anulación de NC. El catálogo es pequeño y
     * estático (5 códigos oficiales SRI); no requiere paginación.
     */
    Flux<MotivoAnulacionNcSri> listMotivosAnulacionNc();
}
