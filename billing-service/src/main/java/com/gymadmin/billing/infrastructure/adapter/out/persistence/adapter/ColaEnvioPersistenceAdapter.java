package com.gymadmin.billing.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.billing.domain.model.ColaEnvio;
import com.gymadmin.billing.domain.port.out.ColaEnvioRepository;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.ColaEnvioEntity;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.repository.ColaEnvioR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ColaEnvioPersistenceAdapter implements ColaEnvioRepository {

    private final ColaEnvioR2dbcRepository repository;

    @Override
    public Mono<ColaEnvio> save(ColaEnvio cola) {
        return repository.save(toEntity(cola)).map(this::toDomain);
    }

    @Override
    public Mono<ColaEnvio> update(ColaEnvio cola) {
        return repository.findById(cola.getId())
                .flatMap(existing -> {
                    if (cola.getEstado() != null)           existing.setEstado(cola.getEstado());
                    if (cola.getProximaEjecucion() != null) existing.setProximaEjecucion(cola.getProximaEjecucion());
                    if (cola.getIntentos() != null)         existing.setIntentos(cola.getIntentos());
                    if (cola.getUltimoError() != null)      existing.setUltimoError(cola.getUltimoError());
                    return repository.save(existing);
                })
                .map(this::toDomain);
    }

    @Override
    public Flux<ColaEnvio> findPendientes(int limit) {
        return repository.findPendientesParaEnviar(limit).map(this::toDomain);
    }

    @Override
    public Mono<ColaEnvio> findLatestByIdComprobante(Long idComprobante) {
        return repository.findLatestByIdComprobante(idComprobante).map(this::toDomain);
    }

    private ColaEnvio toDomain(ColaEnvioEntity e) {
        return ColaEnvio.builder()
                .id(e.getId())
                .idCompania(e.getIdCompania())
                .idSucursal(e.getIdSucursal())
                .idComprobante(e.getIdComprobante())
                .estado(e.getEstado())
                .proximaEjecucion(e.getProximaEjecucion())
                .intentos(e.getIntentos())
                .maxIntentos(e.getMaxIntentos())
                .ultimoError(e.getUltimoError())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private ColaEnvioEntity toEntity(ColaEnvio c) {
        return ColaEnvioEntity.builder()
                .id(c.getId())
                .idCompania(c.getIdCompania())
                .idSucursal(c.getIdSucursal())
                .idComprobante(c.getIdComprobante())
                .estado(c.getEstado())
                .proximaEjecucion(c.getProximaEjecucion())
                .intentos(c.getIntentos())
                .maxIntentos(c.getMaxIntentos())
                .ultimoError(c.getUltimoError())
                .build();
    }
}
