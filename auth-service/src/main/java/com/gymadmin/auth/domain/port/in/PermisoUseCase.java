package com.gymadmin.auth.domain.port.in;

import com.gymadmin.auth.dto.response.PermisoResponse;
import reactor.core.publisher.Flux;

public interface PermisoUseCase {
    Flux<PermisoResponse> listarPorCompania(Integer idCompania);
    Flux<PermisoResponse> listarPorRol(Integer idRol, Integer idCompania);
}
