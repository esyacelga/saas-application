package com.gymadmin.billing.infrastructure.adapter.out.storage;

import com.gymadmin.billing.domain.port.out.FileStoragePort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class FileStorageStubAdapter implements FileStoragePort {

    @Override
    public Mono<String> saveXmlFirmado(Long idComprobante, String xmlContent) {
        return Mono.just("stub-path/xml-firmado/" + idComprobante + ".xml");
    }

    @Override
    public Mono<String> saveXmlAutorizado(Long idComprobante, String xmlContent) {
        return Mono.just("stub-path/xml-autorizado/" + idComprobante + ".xml");
    }

    @Override
    public Mono<String> saveRidePdf(Long idComprobante, byte[] pdfContent) {
        return Mono.just("stub-path/ride/" + idComprobante + ".pdf");
    }

    @Override
    public Mono<String> readFile(String path) {
        return Mono.just("stub-path");
    }
}
