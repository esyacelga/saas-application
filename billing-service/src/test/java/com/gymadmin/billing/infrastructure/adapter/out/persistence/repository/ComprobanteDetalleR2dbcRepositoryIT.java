package com.gymadmin.billing.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.billing.IntegrationTestBase;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.ComprobanteDetalleEntity;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.ComprobanteEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@DisplayName("ComprobanteDetalleR2dbcRepository")
class ComprobanteDetalleR2dbcRepositoryIT extends IntegrationTestBase {

    @Autowired
    private ComprobanteDetalleR2dbcRepository repository;

    @Autowired
    private ComprobanteR2dbcRepository comprobanteRepository;

    private ComprobanteEntity buildComprobante() {
        String claveAcceso = UUID.randomUUID().toString().replace("-", "").substring(0, 49);
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

    private ComprobanteDetalleEntity buildDetalle(Long idComprobante, int orden, String descripcion) {
        return ComprobanteDetalleEntity.builder()
                .idComprobante(idComprobante)
                .codigoPrincipal("SKU-" + UUID.randomUUID().toString().substring(0, 8))
                .descripcion(descripcion)
                .cantidad(new BigDecimal("1.000000"))
                .precioUnitario(new BigDecimal("50.000000"))
                .descuento(BigDecimal.ZERO)
                .precioTotalSinImpuesto(new BigDecimal("50.00"))
                .orden(orden)
                .build();
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("guarda un detalle asociado a un comprobante")
        void save_nuevoDetalle_seGuardaCorrectamente() {
            StepVerifier.create(comprobanteRepository.save(buildComprobante())
                            .flatMap(comp -> {
                                ComprobanteDetalleEntity detalle = buildDetalle(comp.getId(), 1, "Producto de prueba");
                                return repository.save(detalle)
                                        .flatMap(saved -> repository.findById(saved.getId()));
                            }))
                    .assertNext(retrieved -> {
                        assert "Producto de prueba".equals(retrieved.getDescripcion());
                        assert retrieved.getOrden() == 1;
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByIdComprobante")
    class FindByIdComprobante {

        @Test
        @DisplayName("retorna los detalles asociados al comprobante ordenados por orden ASC")
        void findByIdComprobante_conDetalles_retornaFluxOrdenado() {
            StepVerifier.create(comprobanteRepository.save(buildComprobante())
                            .flatMapMany(comp -> repository.save(buildDetalle(comp.getId(), 2, "Segundo"))
                                    .then(repository.save(buildDetalle(comp.getId(), 1, "Primero")))
                                    .thenMany(repository.findByIdComprobante(comp.getId()))))
                    .recordWith(java.util.ArrayList::new)
                    .thenConsumeWhile(d -> true)
                    .consumeRecordedWith(detalles -> {
                        java.util.List<ComprobanteDetalleEntity> list = new java.util.ArrayList<>(detalles);
                        assert list.size() == 2 : "Debe retornar 2 detalles";
                        assert list.get(0).getOrden() == 1 : "El primer detalle debe ser el de orden 1";
                        assert list.get(1).getOrden() == 2 : "El segundo detalle debe ser el de orden 2";
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna vacío cuando el comprobante no tiene detalles")
        void findByIdComprobante_sinDetalles_retornaFluxVacio() {
            Long idComprobanteInexistente = -1L;
            StepVerifier.create(repository.findByIdComprobante(idComprobanteInexistente))
                    .verifyComplete();
        }
    }
}
