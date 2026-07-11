package com.gymadmin.billing.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.billing.IntegrationTestBase;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.ColaEnvioEntity;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.ComprobanteEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@DisplayName("ColaEnvioR2dbcRepository")
class ColaEnvioR2dbcRepositoryIT extends IntegrationTestBase {

    @Autowired
    private ColaEnvioR2dbcRepository repository;

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

    private ColaEnvioEntity buildCola(Long idComprobante, String estado, OffsetDateTime proximaEjecucion) {
        return ColaEnvioEntity.builder()
                .idCompania(ID_COMPANIA)
                .idSucursal(ID_SUCURSAL)
                .idComprobante(idComprobante)
                .estado(estado)
                .proximaEjecucion(proximaEjecucion)
                .intentos((short) 0)
                .maxIntentos((short) 5)
                .build();
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("guarda un item en la cola de envío")
        void save_nuevoItem_seGuardaCorrectamente() {
            StepVerifier.create(comprobanteRepository.save(buildComprobante())
                            .flatMap(comp -> {
                                ColaEnvioEntity cola = buildCola(comp.getId(), "PENDIENTE", OffsetDateTime.now());
                                return repository.save(cola)
                                        .flatMap(saved -> repository.findById(saved.getId()));
                            }))
                    .assertNext(retrieved -> {
                        assert "PENDIENTE".equals(retrieved.getEstado());
                        assert retrieved.getIntentos() == 0;
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findPendientesParaEnviar")
    class FindPendientesParaEnviar {

        @Test
        @DisplayName("retorna items pendientes listos para enviar")
        void findPendientesParaEnviar_conPendientes_retornaFlux() {
            StepVerifier.create(comprobanteRepository.save(buildComprobante())
                            .flatMap(comp -> repository.save(
                                    buildCola(comp.getId(), "PENDIENTE", OffsetDateTime.now().minusMinutes(1))))
                            .thenMany(repository.findPendientesParaEnviar(50)))
                    .recordWith(java.util.ArrayList::new)
                    .thenConsumeWhile(c -> true)
                    .consumeRecordedWith(items -> {
                        assert items.stream().allMatch(c -> "PENDIENTE".equals(c.getEstado()));
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findLatestByIdComprobante")
    class FindLatestByIdComprobante {

        @Test
        @DisplayName("retorna el item de cola asociado al comprobante")
        void findLatestByIdComprobante_conItem_retornaMono() {
            StepVerifier.create(comprobanteRepository.save(buildComprobante())
                            .flatMap(comp -> repository.save(
                                            buildCola(comp.getId(), "PENDIENTE", OffsetDateTime.now()))
                                    .then(repository.findLatestByIdComprobante(comp.getId()))))
                    .assertNext(retrieved -> {
                        assert "PENDIENTE".equals(retrieved.getEstado());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna Mono vacío cuando el comprobante no tiene item en cola")
        void findLatestByIdComprobante_sinItem_retornaMonoVacio() {
            Long idComprobanteInexistente = -1L;
            StepVerifier.create(repository.findLatestByIdComprobante(idComprobanteInexistente))
                    .verifyComplete();
        }
    }
}
