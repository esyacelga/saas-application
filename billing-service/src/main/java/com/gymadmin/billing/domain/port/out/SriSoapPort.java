package com.gymadmin.billing.domain.port.out;

import com.gymadmin.billing.domain.model.sri.RespuestaAutorizacion;
import com.gymadmin.billing.domain.model.sri.RespuestaRecepcion;
import reactor.core.publisher.Mono;

public interface SriSoapPort {
    Mono<RespuestaRecepcion> enviarComprobante(String xmlFirmado, String ambiente);
    Mono<RespuestaAutorizacion> autorizarComprobante(String claveAcceso, String ambiente);
}
