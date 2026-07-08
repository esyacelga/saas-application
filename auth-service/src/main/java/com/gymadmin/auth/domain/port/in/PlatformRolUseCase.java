package com.gymadmin.auth.domain.port.in;

import com.gymadmin.auth.dto.request.AsignarPermisoRolRequest;
import com.gymadmin.auth.dto.request.CreatePlatformRolRequest;
import com.gymadmin.auth.dto.request.UpdatePlatformRolRequest;
import com.gymadmin.auth.dto.request.UpdateRolPermisosRequest;
import com.gymadmin.auth.dto.response.CompaniaBasicaResponse;
import com.gymadmin.auth.dto.response.PermisoRolResponse;
import com.gymadmin.auth.dto.response.RolConPermisosPlataformaResponse;
import com.gymadmin.auth.dto.response.RolPlataformaResponse;
import com.gymadmin.auth.dto.response.SucursalBasicaResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlatformRolUseCase {
    Flux<RolPlataformaResponse> listarRoles();
    Mono<RolConPermisosPlataformaResponse> verPermisosPorRol(Integer id);
    Flux<CompaniaBasicaResponse> listarCompanias();
    Flux<SucursalBasicaResponse> listarSucursales(Integer idCompania);
    Mono<RolPlataformaResponse> crearRol(CreatePlatformRolRequest req, String createdBy);
    Mono<RolPlataformaResponse> actualizarRol(Integer id, UpdatePlatformRolRequest req, String updatedBy);
    Mono<Void> eliminarRol(Integer id);
    Mono<Void> reemplazarPermisos(Integer id, UpdateRolPermisosRequest req, String updatedBy);

    // Granular rol_permisos management
    Flux<PermisoRolResponse> verPermisosDetalle(Integer idRol);
    Mono<Void> asignarPermiso(Integer idRol, AsignarPermisoRolRequest req, String createdBy);
    Mono<Void> eliminarPermisoDeRol(Integer idRol, Integer idPermiso, String updatedBy);
}
