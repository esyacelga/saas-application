package com.gymadmin.platform.domain.port.in;

import com.gymadmin.platform.domain.model.ModuloCheckResult;
import reactor.core.publisher.Mono;

public interface ModuloCheckUseCase {

    Mono<ModuloCheckResult> checkAcceso(Long idCompania, String codigo);
}
