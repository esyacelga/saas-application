package com.gymadmin.billing.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.billing.IntegrationTestBase;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.ComprobanteEntity;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.EnvioSriEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@DisplayName("EnvioSriR2dbcRepository")
class EnvioSriR2dbcRepositoryIT extends IntegrationTestBase {

    @Autowired
    private EnvioSriR2dbcRepository repository;

    @Autowired
    private ComprobanteR2dbcRepository comprobanteRepository;

    private ComprobanteEntity buildComprobante() {
        String claveAcceso = ((UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "")).substring(0, 49);
        String secuencial = UUID.randomUUID().toString().replace("-", "").substring(0, 9);
        return ComprobanteEntity.builder()
                .idCompania(ID_COMPANIA)
                .idSucursal(ID_SUCURSAL)
                .tipoComprobante("01")
                .claveAcceso(claveAcceso)
                .codEstablecimiento("001")
                .codPuntoEmision("001")
                .secuencial(secuencial)
                .fechaEmision(LocalDate.now())
                .ambiente("1")
                .tipoIdReceptor("05")
                .idReceptor("1712345678")
                .razonSocialReceptor("Cliente de Prueba")
                .subtotalSinImpuesto(new BigDecimal("100.00"))
                .subtotalIva0(BigDecimal.ZERO)
                .subtotalNoObjetoIva(BigDecimal.ZERO)
                .subtotalExentoIva(BigDecimal.ZERO)
                .totalDescuento(BigDecimal.ZERO)
                .totalIce(BigDecimal.ZERO)
                .totalIva(new BigDecimal("15.00"))
                .propina(BigDecimal.ZERO)
                .total(new BigDecimal("115.00"))
                .moneda("DOLAR")
                .estado("GENERADO")
                .build();
    }

    private EnvioSriEntity buildEnvio(Long idComprobante, String tipoOperacion) {
        return EnvioSriEntity.builder()
                .idCompania(ID_COMPANIA)
                .idSucursal(ID_SUCURSAL)
                .idComprobante(idComprobante)
                .tipoOperacion(tipoOperacion)
                .endpointUrl("https://celcer.sri.gob.ec/ws/test")
                .exitoso(true)
                .intentoNumero((short) 1)
                .build();
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("guarda un envío asociado a un comprobante")
        void save_nuevoEnvio_seGuardaCorrectamente() {
            StepVerifier.create(comprobanteRepository.save(buildComprobante())
                            .flatMap(comp -> {
                                EnvioSriEntity envio = buildEnvio(comp.getId(), "RECEPCION");
                                return repository.save(envio)
                                        .flatMap(saved -> repository.findById(saved.getId()));
                            }))
                    .assertNext(retrieved -> {
                        assert "RECEPCION".equals(retrieved.getTipoOperacion());
                        assert Boolean.TRUE.equals(retrieved.getExitoso());
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByIdComprobante")
    class FindByIdComprobante {

        @Test
        @DisplayName("retorna los envíos asociados al comprobante")
        void findByIdComprobante_conEnvios_retornaFlux() {
            StepVerifier.create(comprobanteRepository.save(buildComprobante())
                            .flatMapMany(comp -> repository.save(buildEnvio(comp.getId(), "RECEPCION"))
                                    .then(repository.save(buildEnvio(comp.getId(), "AUTORIZACION")))
                                    .thenMany(repository.findByIdComprobante(comp.getId()))))
                    .recordWith(java.util.ArrayList::new)
                    .thenConsumeWhile(e -> true)
                    .consumeRecordedWith(envios -> {
                        assert envios.size() == 2 : "Debe retornar los 2 envíos";
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna vacío cuando el comprobante no tiene envíos")
        void findByIdComprobante_sinEnvios_retornaFluxVacio() {
            Long idComprobanteInexistente = -1L;
            StepVerifier.create(repository.findByIdComprobante(idComprobanteInexistente))
                    .verifyComplete();
        }
    }
}
