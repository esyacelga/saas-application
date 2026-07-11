package com.gymadmin.billing.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.billing.IntegrationTestBase;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.ComprobanteEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@DisplayName("ComprobanteReporteR2dbcRepository")
class ComprobanteReporteR2dbcRepositoryIT extends IntegrationTestBase {

    @Autowired
    private ComprobanteReporteR2dbcRepository repository;

    @Autowired
    private ComprobanteR2dbcRepository comprobanteRepository;

    private ComprobanteEntity buildComprobanteAutorizado(LocalDate fechaEmision) {
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
                .fechaEmision(fechaEmision)
                .ambiente("1")
                .tipoIdReceptor("05")
                .idReceptor("1712345678")
                .razonSocialReceptor("Cliente Reporte")
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
                .estado("AUTORIZADO")
                .build();
    }

    @Nested
    @DisplayName("findAutorizadosPorMes")
    class FindAutorizadosPorMes {

        @Test
        @DisplayName("retorna los comprobantes autorizados del mes indicado")
        void findAutorizadosPorMes_conComprobantes_retornaFlux() {
            LocalDate fechaEmision = LocalDate.of(2026, 3, 15);

            StepVerifier.create(comprobanteRepository.save(buildComprobanteAutorizado(fechaEmision))
                            .thenMany(repository.findAutorizadosPorMes(ID_COMPANIA, 2026, 3)))
                    .recordWith(java.util.ArrayList::new)
                    .thenConsumeWhile(c -> true)
                    .consumeRecordedWith(items -> {
                        assert items.stream().anyMatch(c ->
                                "AUTORIZADO".equals(c.getEstado())
                                        && c.getFechaEmision().getYear() == 2026
                                        && c.getFechaEmision().getMonthValue() == 3);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna vacío cuando no hay comprobantes autorizados en el período")
        void findAutorizadosPorMes_sinComprobantes_retornaFluxVacio() {
            int idCompaniaInexistente = 88888;
            StepVerifier.create(repository.findAutorizadosPorMes(idCompaniaInexistente, 1900, 1))
                    .verifyComplete();
        }
    }
}
