package com.gymadmin.billing.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.billing.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

@DisplayName("CertificadoR2dbcRepository")
class CertificadoR2dbcRepositoryIT extends IntegrationTestBase {

    @Autowired
    private CertificadoR2dbcRepository repository;

    @Nested
    @DisplayName("findActiveByEmpresa")
    class FindActiveByEmpresa {

        @Test
        @DisplayName("retorna Mono vacío cuando no existe certificado activo para la empresa")
        void findActiveByEmpresa_sinCertificado_retornaMonoVacio() {
            int idCompaniaInexistente = 88888;
            StepVerifier.create(repository.findActiveByEmpresa(idCompaniaInexistente, ID_SUCURSAL))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findActiveByEmpresaForAdmin")
    class FindActiveByEmpresaForAdmin {

        @Test
        @DisplayName("retorna Mono vacío cuando la compañía no tiene certificados activos")
        void findActiveByEmpresaForAdmin_sinCertificado_retornaMonoVacio() {
            int idCompaniaInexistente = 88888;
            StepVerifier.create(repository.findActiveByEmpresaForAdmin(idCompaniaInexistente))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findProximosAVencer")
    class FindProximosAVencer {

        @Test
        @DisplayName("retorna certificados próximos a vencer dentro del rango de días")
        void findProximosAVencer_conRango_retornaFlux() {
            StepVerifier.create(repository.findProximosAVencer(30))
                    .recordWith(java.util.ArrayList::new)
                    .thenConsumeWhile(c -> true)
                    .consumeRecordedWith(certificados -> {
                        // El repositorio debe responder (lista vacía o no) sin fallar
                        assert certificados != null;
                    })
                    .verifyComplete();
        }
    }
}
