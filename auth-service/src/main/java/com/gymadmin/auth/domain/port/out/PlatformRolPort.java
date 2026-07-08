package com.gymadmin.auth.domain.port.out;

import com.gymadmin.auth.dto.response.CompaniaBasicaResponse;
import com.gymadmin.auth.dto.response.RolPlataformaResponse;
import com.gymadmin.auth.dto.response.SucursalBasicaResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlatformRolPort {
    Flux<RolPlataformaResponse> findAllRoles();
    Mono<RolPlataformaResponse> findRolById(Integer id);
    Flux<CompaniaBasicaResponse> findAllCompanias();
    Mono<Boolean> existsCompania(Integer idCompania);
    Flux<SucursalBasicaResponse> findSucursalesByCompania(Integer idCompania);
    Mono<RolPlataformaResponse> save(Integer idCompania, String nombre, String descripcion, String createdBy, Integer idSucursal);
    Mono<RolPlataformaResponse> update(Integer id, String nombre, String descripcion, String updatedBy);
    Mono<Long> countUsuariosByRolId(Integer id);
    Mono<Void> deleteById(Integer id);
}
