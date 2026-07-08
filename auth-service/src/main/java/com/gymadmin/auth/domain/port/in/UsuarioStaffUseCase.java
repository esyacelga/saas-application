package com.gymadmin.auth.domain.port.in;

import com.gymadmin.auth.dto.request.CreateUsuarioStaffRequest;
import com.gymadmin.auth.dto.request.UpdateUsuarioStaffRequest;
import com.gymadmin.auth.dto.response.UsuarioPermisosResponse;
import com.gymadmin.auth.dto.response.UsuarioStaffResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UsuarioStaffUseCase {
    Flux<UsuarioStaffResponse> listar(Integer idCompania);
    Flux<UsuarioStaffResponse> listarPorPersona(Integer idPersona);
    Mono<UsuarioStaffResponse> crear(Integer idCompania, Integer idSucursal, CreateUsuarioStaffRequest req, String createdBy);
    Mono<UsuarioStaffResponse> editar(Integer id, Integer idCompania, UpdateUsuarioStaffRequest req, String updatedBy);
    Mono<UsuarioPermisosResponse> verPermisos(Integer id, Integer idCompania);
    Mono<Void> desactivar(Integer id, Integer idCompania, String updatedBy);
    Mono<Void> activar(Integer id, Integer idCompania, String updatedBy);
    Mono<Void> resetPassword(Integer id, Integer idCompania, String newPassword, String updatedBy);
}
