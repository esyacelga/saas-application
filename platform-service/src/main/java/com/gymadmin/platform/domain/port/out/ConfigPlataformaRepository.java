package com.gymadmin.platform.domain.port.out;

import com.gymadmin.platform.domain.model.ConfigPlataforma;
import reactor.core.publisher.Mono;

/**
 * REQ-SAAS-001 (sección 11.4): puerto out para configuración runtime
 * clave-valor de la plataforma (datos bancarios, textos, umbrales).
 */
public interface ConfigPlataformaRepository {

    Mono<ConfigPlataforma> findByClave(String clave);

    Mono<ConfigPlataforma> save(ConfigPlataforma config);
}
