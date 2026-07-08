package com.gymadmin.platform.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.platform.domain.port.out.UsuarioGymRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.UsuarioGymEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.UsuarioGymR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class UsuarioGymPersistenceAdapter implements UsuarioGymRepository {

    private final UsuarioGymR2dbcRepository repo;

    public UsuarioGymPersistenceAdapter(UsuarioGymR2dbcRepository repo) {
        this.repo = repo;
    }

    @Override
    public Mono<UsuarioCreado> crearUsuario(Long idCompania, Long idSucursal, Long idRol,
                                             Long idPersona, String correo, String passwordHash) {
        UsuarioGymEntity entity = new UsuarioGymEntity();
        entity.setIdCompania(idCompania);
        entity.setIdSucursal(idSucursal);
        entity.setIdRol(idRol);
        entity.setIdPersona(idPersona);
        entity.setCorreo(correo);
        entity.setPasswordHash(passwordHash);
        entity.setRequiereCambioPwd(false);
        entity.setActivo(true);
        return repo.save(entity)
                .map(saved -> new UsuarioCreado(saved.getId(), saved.getIdPersona(), saved.getCorreo()));
    }

    @Override
    public Mono<Boolean> existeCorreo(Long idCompania, String correo) {
        return repo.findByIdCompaniaAndCorreoAndEliminadoFalse(idCompania, correo)
                .map(u -> true)
                .defaultIfEmpty(false);
    }
}
