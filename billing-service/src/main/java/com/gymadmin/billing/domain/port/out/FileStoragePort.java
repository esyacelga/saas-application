package com.gymadmin.billing.domain.port.out;

import reactor.core.publisher.Mono;

public interface FileStoragePort {
    Mono<String> saveXmlFirmado(Long idComprobante, String xmlContent);
    Mono<String> saveXmlAutorizado(Long idComprobante, String xmlContent);
    Mono<String> saveRidePdf(Long idComprobante, byte[] pdfContent);
    Mono<String> readFile(String path);
    Mono<byte[]> readFileBytes(String path);
}
