package com.gymadmin.auth.domain.port.in;

import com.gymadmin.auth.dto.request.CreateRolRequest;
import com.gymadmin.auth.dto.request.UpdateRolPermisosRequest;
import com.gymadmin.auth.dto.response.RolConPermisosResponse;
import com.gymadmin.auth.dto.response.RolResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RolUseCase {
    Flux<RolResponse> listarPorCompania(Integer idCompania);
    Mono<RolResponse> buscarPorId(Integer id, Integer idCompania);
    Mono<RolResponse> crear(Integer idCompania, Integer idSucursal, CreateRolRequest req, String createdBy);
    Mono<RolConPermisosResponse> verPermisos(Integer id, Integer idCompania);
    Mono<Void> actualizarPermisos(Integer id, Integer idCompania, UpdateRolPermisosRequest req, String updatedBy);
    Mono<Void> eliminar(Integer id, Integer idCompania);
}
