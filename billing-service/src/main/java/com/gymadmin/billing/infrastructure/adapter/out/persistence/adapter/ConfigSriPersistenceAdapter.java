package com.gymadmin.billing.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.billing.domain.model.ConfigSri;
import com.gymadmin.billing.domain.port.out.ConfigSriRepository;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.ConfigSriEntity;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.repository.ConfigSriR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ConfigSriPersistenceAdapter implements ConfigSriRepository {

    private final ConfigSriR2dbcRepository repository;

    @Override
    public Mono<ConfigSri> findByEmpresa(Integer idCompania, Integer idSucursal) {
        return repository.findActiveByEmpresa(idCompania, idSucursal).map(this::toDomain);
    }

    private ConfigSri toDomain(ConfigSriEntity e) {
        return ConfigSri.builder()
                .id(e.getId())
                .idCompania(e.getIdCompania())
                .idSucursal(e.getIdSucursal())
                .ruc(e.getRuc())
                .razonSocial(e.getRazonSocial())
                .nombreComercial(e.getNombreComercial())
                .dirMatriz(e.getDirMatriz())
                .ambiente(e.getAmbiente())
                .contribuyenteEspecial(e.getContribuyenteEspecial())
                .obligadoContabilidad(e.getObligadoContabilidad())
                .codEstablecimiento(e.getCodEstablecimiento())
                .codPuntoEmision(e.getCodPuntoEmision())
                .activo(e.getActivo())
                .build();
    }
}
