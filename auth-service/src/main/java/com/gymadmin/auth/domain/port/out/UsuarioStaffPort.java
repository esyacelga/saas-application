package com.gymadmin.auth.domain.port.out;

import com.gymadmin.auth.domain.model.UsuarioStaff;
import com.gymadmin.auth.dto.response.CompaniaBasicaResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UsuarioStaffPort {
    Mono<UsuarioStaff> findByCorreoAndIdCompania(String correo, Integer idCompania);
    Mono<Boolean> existsByCorreoAndIdCompania(String correo, Integer idCompania);
    Flux<UsuarioStaff> findByIdCompania(Integer idCompania);
    Flux<UsuarioStaff> findByIdPersona(Integer idPersona);
    Mono<UsuarioStaff> findByIdAndIdCompania(Integer id, Integer idCompania);
    Mono<UsuarioStaff> findById(Integer id);
    Mono<UsuarioStaff> save(UsuarioStaff usuario);
    Mono<Long> countActiveDuenos(Integer idCompania);
    Mono<Boolean> existsByIdRolInCompania(Integer idRol, Integer idCompania);
    Flux<CompaniaBasicaResponse> findCompaniesByCorreo(String correo);
}
