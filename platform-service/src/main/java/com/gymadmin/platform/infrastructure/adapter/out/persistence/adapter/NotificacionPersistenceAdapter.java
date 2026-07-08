package com.gymadmin.platform.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.platform.domain.model.NotificacionSuscripcion;
import com.gymadmin.platform.domain.port.out.NotificacionRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.NotificacionSuscripcionEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.NotificacionR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class NotificacionPersistenceAdapter implements NotificacionRepository {

    private final NotificacionR2dbcRepository repository;

    public NotificacionPersistenceAdapter(NotificacionR2dbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<NotificacionSuscripcion> save(NotificacionSuscripcion notificacion) {
        return repository.save(toEntity(notificacion)).map(this::toDomain);
    }

    @Override
    public Flux<NotificacionSuscripcion> findByIdCompaniaPlan(Long idCompaniaPlan) {
        return repository.findByIdCompaniaPlan(idCompaniaPlan).map(this::toDomain);
    }

    @Override
    public Mono<Boolean> existsByIdCompaniaPlanAndDiasAntes(Long idCompaniaPlan, Integer diasAntes) {
        return repository.existsByIdCompaniaPlanAndDiasAntes(idCompaniaPlan, diasAntes);
    }

    private NotificacionSuscripcion toDomain(NotificacionSuscripcionEntity entity) {
        return new NotificacionSuscripcion(
                entity.getId(),
                entity.getIdCompaniaPlan(),
                entity.getDiasAntes(),
                entity.getCanal(),
                entity.getEstado(),
                entity.getFechaEnvio()
        );
    }

    private NotificacionSuscripcionEntity toEntity(NotificacionSuscripcion n) {
        NotificacionSuscripcionEntity entity = new NotificacionSuscripcionEntity();
        entity.setId(n.getId());
        entity.setIdCompaniaPlan(n.getIdCompaniaPlan());
        entity.setDiasAntes(n.getDiasAntes());
        entity.setCanal(n.getCanal());
        entity.setEstado(n.getEstado());
        entity.setFechaEnvio(n.getFechaEnvio());
        return entity;
    }
}
