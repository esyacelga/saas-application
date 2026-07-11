package com.gymadmin.billing.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.billing.domain.model.CertificadoInfo;
import com.gymadmin.billing.domain.port.out.CertificadoRepository;
import com.gymadmin.billing.infrastructure.adapter.out.crypto.CertificadoDecryptionService;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.CertificadoEntity;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.repository.CertificadoR2dbcRepository;
import com.gymadmin.billing.infrastructure.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class CertificadoPersistenceAdapter implements CertificadoRepository {

    private final CertificadoR2dbcRepository repo;
    private final CertificadoDecryptionService decryptionService;

    @Override
    public Mono<byte[]> getActiveCertificateContent(Integer idCompania, Integer idSucursal) {
        return repo.findActiveByEmpresa(idCompania, idSucursal)
                .switchIfEmpty(Mono.error(new NotFoundException(
                        "Certificado activo no encontrado para la empresa " + idCompania)))
                .map(e -> decryptionService.decrypt(e.getP12Cifrado()));
    }

    @Override
    public Mono<String> getActiveCertificatePassword(Integer idCompania, Integer idSucursal) {
        return repo.findActiveByEmpresa(idCompania, idSucursal)
                .switchIfEmpty(Mono.error(new NotFoundException(
                        "Certificado activo no encontrado para la empresa " + idCompania)))
                .map(e -> decryptionService.decryptString(e.getPasswordCifrado()));
    }

    @Override
    public Flux<CertificadoInfo> findProximosAVencer(int diasLimite) {
        return repo.findProximosAVencer(diasLimite)
                .map(this::toInfo);
    }

    private CertificadoInfo toInfo(CertificadoEntity e) {
        return new CertificadoInfo(e.getId(), e.getIdCompania(), e.getIdSucursal(), e.getFechaVencimiento());
    }
}
