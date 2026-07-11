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

    @Override
    public Mono<ConfigSri> findFirstByCompania(Integer idCompania) {
        return repository.findFirstActiveByCompania(idCompania).map(this::toDomain);
    }

    private ConfigSri toDomain(ConfigSriEntity e) {
        return ConfigSri.builder()
                .idCompania(e.getIdCompania())
                .idSucursal(e.getIdSucursal())
                .ruc(e.getRuc())
                .razonSocial(e.getRazonSocial())
                .nombreComercial(e.getNombreComercial())
                .dirEstablecimiento(e.getDirEstablecimiento())
                .ambiente(e.getAmbiente())
                .tipoEmision(e.getTipoEmision())
                .contribuyenteEspecial(e.getContribuyenteEspecial())
                .obligadoContabilidad(e.getObligadoContabilidad())
                .facturacionActiva(e.getFacturacionActiva())
                .emailNotificacion(e.getEmailNotificacion())
                .build();
    }
}
