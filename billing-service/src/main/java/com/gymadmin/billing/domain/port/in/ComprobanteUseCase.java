package com.gymadmin.billing.domain.port.in;

import com.gymadmin.billing.application.command.EmitirFacturaCommand;
import com.gymadmin.billing.domain.model.Comprobante;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ComprobanteUseCase {

    Mono<Comprobante> emitirFactura(EmitirFacturaCommand command);

    Mono<Comprobante> buscarPorId(Long id, Integer idCompania);

    Flux<Comprobante> listarPorEmpresa(Integer idCompania, Integer idSucursal, String estado, int page, int limit);

    Mono<Long> contarPorEmpresa(Integer idCompania, Integer idSucursal, String estado);

    Mono<Comprobante> procesarEnvioSri(Long id, Integer idCompania, Integer idSucursal);

    Mono<String> leerXmlFirmado(Long id, Integer idCompania);

    Mono<byte[]> leerRidePdf(Long id, Integer idCompania);

    Mono<Comprobante> anularComprobante(Long id, Integer idCompania);

    Mono<Void> reenviarEmail(Long id, Integer idCompania);
}
