package com.gymadmin.billing.domain.port.out;

import reactor.core.publisher.Mono;

public interface CertificadoRepository {

    Mono<byte[]> getActiveCertificateContent(Integer idCompania, Integer idSucursal);

    Mono<String> getActiveCertificatePassword(Integer idCompania, Integer idSucursal);
}
