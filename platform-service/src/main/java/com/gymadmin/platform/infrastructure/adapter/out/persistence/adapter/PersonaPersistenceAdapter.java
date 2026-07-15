package com.gymadmin.platform.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.platform.domain.port.out.PersonaRepository;
import com.gymadmin.platform.domain.validation.CedulaEcuatoriana;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.PersonaEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.PersonaR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class PersonaPersistenceAdapter implements PersonaRepository {

    private final PersonaR2dbcRepository repo;

    public PersonaPersistenceAdapter(PersonaR2dbcRepository repo) {
        this.repo = repo;
    }

    @Override
    public Mono<Long> resolverIdPersona(Long idPersona, String ci, String nombre, String correo, String telefono) {
        if (idPersona != null) {
            return Mono.just(idPersona);
        }
        return repo.findByCi(ci)
                .map(PersonaEntity::getId)
                .switchIfEmpty(Mono.defer(() -> {
                    PersonaEntity entity = new PersonaEntity();
                    entity.setCi(ci);
                    // Marca la identidad como validada solo si la cédula pasa el algoritmo
                    // del dígito verificador ecuatoriano. Cualquier otro documento (o typo)
                    // queda en false; no se rechaza el registro por esto.
                    entity.setCiValidada(CedulaEcuatoriana.esValida(ci));
                    entity.setNombre(nombre);
                    entity.setCorreo(correo);
                    entity.setTelefono(telefono);
                    return repo.save(entity).map(PersonaEntity::getId);
                }));
    }
}
