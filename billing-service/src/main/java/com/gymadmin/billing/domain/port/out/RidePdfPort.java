package com.gymadmin.billing.domain.port.out;

import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.model.ComprobanteDetalle;
import com.gymadmin.billing.domain.model.ConfigSri;
import reactor.core.publisher.Mono;

import java.util.List;

public interface RidePdfPort {
    Mono<byte[]> generarRide(Comprobante comprobante, List<ComprobanteDetalle> detalles, ConfigSri configSri);
}
