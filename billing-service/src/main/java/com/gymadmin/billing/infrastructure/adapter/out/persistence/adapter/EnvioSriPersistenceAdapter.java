package com.gymadmin.billing.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.billing.domain.model.EnvioSri;
import com.gymadmin.billing.domain.port.out.EnvioSriRepository;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.EnvioSriEntity;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.repository.EnvioSriR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class EnvioSriPersistenceAdapter implements EnvioSriRepository {

    private final EnvioSriR2dbcRepository repository;

    @Override
    public Mono<EnvioSri> save(EnvioSri envio) {
        return repository.save(toEntity(envio)).map(this::toDomain);
    }

    @Override
    public Flux<EnvioSri> findByIdComprobante(Long idComprobante) {
        return repository.findByIdComprobante(idComprobante).map(this::toDomain);
    }

    private EnvioSri toDomain(EnvioSriEntity e) {
        return EnvioSri.builder()
                .id(e.getId())
                .idCompania(e.getIdCompania())
                .idSucursal(e.getIdSucursal())
                .idComprobante(e.getIdComprobante())
                .tipoOperacion(e.getTipoOperacion())
                .endpointUrl(e.getEndpointUrl())
                .requestSoap(e.getRequestSoap())
                .responseSoap(e.getResponseSoap())
                .httpStatus(e.getHttpStatus())
                .duracionMs(e.getDuracionMs())
                .exitoso(e.getExitoso())
                .estadoSri(e.getEstadoSri())
                .codigoError(e.getCodigoError())
                .mensajeError(e.getMensajeError())
                .intentoNumero(e.getIntentoNumero())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private EnvioSriEntity toEntity(EnvioSri e) {
        return EnvioSriEntity.builder()
                .id(e.getId())
                .idCompania(e.getIdCompania())
                .idSucursal(e.getIdSucursal())
                .idComprobante(e.getIdComprobante())
                .tipoOperacion(e.getTipoOperacion())
                .endpointUrl(e.getEndpointUrl())
                .requestSoap(e.getRequestSoap())
                .responseSoap(e.getResponseSoap())
                .httpStatus(e.getHttpStatus())
                .duracionMs(e.getDuracionMs())
                .exitoso(e.getExitoso())
                .estadoSri(e.getEstadoSri())
                .codigoError(e.getCodigoError())
                .mensajeError(e.getMensajeError())
                .intentoNumero(e.getIntentoNumero())
                .build();
    }
}
