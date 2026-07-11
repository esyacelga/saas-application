package com.gymadmin.billing.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.billing.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

@DisplayName("ConfigSriR2dbcRepository")
class ConfigSriR2dbcRepositoryIT extends IntegrationTestBase {

    @Autowired
    private ConfigSriR2dbcRepository repository;

    @Nested
    @DisplayName("findActiveByEmpresa")
    class FindActiveByEmpresa {

        @Test
        @DisplayName("retorna Mono vacío cuando no existe configuración activa para la empresa")
        void findActiveByEmpresa_sinConfiguracion_retornaMonoVacio() {
            int idCompaniaInexistente = 88888;
            StepVerifier.create(repository.findActiveByEmpresa(idCompaniaInexistente, ID_SUCURSAL))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findFirstActiveByCompania")
    class FindFirstActiveByCompania {

        @Test
        @DisplayName("retorna Mono vacío cuando la compañía no tiene ninguna configuración activa")
        void findFirstActiveByCompania_sinConfiguracion_retornaMonoVacio() {
            int idCompaniaInexistente = 88888;
            StepVerifier.create(repository.findFirstActiveByCompania(idCompaniaInexistente))
                    .verifyComplete();
        }
    }
}
