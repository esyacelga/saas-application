package com.gymadmin.auth.unit;

import com.gymadmin.auth.application.service.ResolverQrApplicationService;
import com.gymadmin.auth.domain.exception.ResourceNotFoundException;
import com.gymadmin.auth.domain.port.out.GimnasioPort;
import com.gymadmin.auth.dto.response.GimnasioPublicoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResolverQrApplicationService")
class ResolverQrApplicationServiceTest {

    @Mock
    private GimnasioPort gimnasioPort;

    @InjectMocks
    private ResolverQrApplicationService service;

    private GimnasioPublicoResponse gimnasioFijura;

    @BeforeEach
    void setUp() {
        gimnasioFijura = new GimnasioPublicoResponse(
                1,
                2,
                "Gym Power",
                "Sucursal Norte",
                "https://example.com/logo.png"
        );
    }

    @Nested
    @DisplayName("resolverQr")
    class ResolverQr {

        @Test
        @DisplayName("retorna informacion del gimnasio cuando el QR es valido")
        void retornaGimnasioCuandoQrValido() {
            when(gimnasioPort.findByQrToken("token-valido-123"))
                    .thenReturn(Mono.just(gimnasioFijura));

            StepVerifier.create(service.resolverQr("token-valido-123"))
                    .expectNextMatches(r ->
                            r.idCompania().equals(1)
                                    && r.idSucursal().equals(2)
                                    && r.nombreCompania().equals("Gym Power")
                                    && r.nombreSucursal().equals("Sucursal Norte")
                    )
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ResourceNotFoundException cuando el QR no es valido o el gimnasio esta inactivo")
        void lanzaExcepcionCuandoQrInvalido() {
            when(gimnasioPort.findByQrToken("token-invalido"))
                    .thenReturn(Mono.empty());

            StepVerifier.create(service.resolverQr("token-invalido"))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }
    }
}
