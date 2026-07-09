package com.gymadmin.billing.domain.port.out;

import com.gymadmin.billing.domain.model.CertificadoInfo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CertificadoRepository {

    Mono<byte[]> getActiveCertificateContent(Integer idCompania, Integer idSucursal);

    Mono<String> getActiveCertificatePassword(Integer idCompania, Integer idSucursal);

    Flux<CertificadoInfo> findProximosAVencer(int diasLimite);
}
