package com.gymadmin.billing.domain.port.out;

import reactor.core.publisher.Mono;

public interface XmlSignaturePort {

    Mono<String> sign(String xmlContent, byte[] p12Content, String p12Password);
}
