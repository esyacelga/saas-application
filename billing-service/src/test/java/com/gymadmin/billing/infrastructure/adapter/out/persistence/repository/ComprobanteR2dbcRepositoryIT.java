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
import java.time.OffsetDateTime;
import java.util.UUID;

@DisplayName("ComprobanteR2dbcRepository")
class ComprobanteR2dbcRepositoryIT extends IntegrationTestBase {

    @Autowired
    private ComprobanteR2dbcRepository repository;

    private static String randomClaveAcceso() {
        String s = (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "");
        return s.substring(0, 49);
    }

    private static String randomSecuencial() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 9);
    }

    private static String randomNumeroAutorizacion() {
        String s = (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "");
        return s.substring(0, 49);
    }

    private ComprobanteEntity buildComprobante(String claveAcceso, String secuencial, String estado) {
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
                .estado(estado)
                .build();
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("guarda un nuevo comprobante en la base de datos")
        void save_nuevoComprobante_seGuardaCorrectamente() {
            String claveAcceso = randomClaveAcceso();
            ComprobanteEntity comprobante = buildComprobante(claveAcceso, randomSecuencial(), "GENERADO");

            StepVerifier.create(repository.save(comprobante)
                            .flatMap(saved -> repository.findById(saved.getId())))
                    .assertNext(retrieved -> {
                        assert retrieved.getClaveAcceso().equals(claveAcceso);
                        assert retrieved.getIdCompania().equals(ID_COMPANIA);
                        assert retrieved.getEstado().equals("GENERADO");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByClaveAcceso")
    class FindByClaveAcceso {

        @Test
        @DisplayName("retorna el comprobante cuando existe con esa clave de acceso")
        void findByClaveAcceso_comprobanteExiste_retornaMono() {
            String claveAcceso = randomClaveAcceso();
            ComprobanteEntity comprobante = buildComprobante(claveAcceso, randomSecuencial(), "GENERADO");

            StepVerifier.create(repository.save(comprobante)
                            .then(repository.findByClaveAcceso(claveAcceso)))
                    .assertNext(retrieved -> {
                        assert retrieved.getClaveAcceso().equals(claveAcceso);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna Mono vacío cuando no existe comprobante con esa clave")
        void findByClaveAcceso_comprobanteNoExiste_retornaMonoVacio() {
            String claveInexistente = randomClaveAcceso();

            StepVerifier.create(repository.findByClaveAcceso(claveInexistente))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByEmpresa")
    class FindByEmpresa {

        @Test
        @DisplayName("retorna los comprobantes de la empresa")
        void findByEmpresa_conComprobantes_retornaFlux() {
            ComprobanteEntity comprobante = buildComprobante(randomClaveAcceso(), randomSecuencial(), "GENERADO");

            StepVerifier.create(repository.save(comprobante)
                            .thenMany(repository.findByEmpresa(ID_COMPANIA, ID_SUCURSAL, null, 50, 0)))
                    .recordWith(java.util.ArrayList::new)
                    .thenConsumeWhile(c -> true)
                    .consumeRecordedWith(items -> {
                        assert items.stream().anyMatch(c -> ID_COMPANIA == c.getIdCompania());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("filtra por estado cuando se especifica")
        void findByEmpresa_filtrarPorEstado_retornaFlux() {
            ComprobanteEntity comprobante = buildComprobante(randomClaveAcceso(), randomSecuencial(), "AUTORIZADO");

            StepVerifier.create(repository.save(comprobante)
                            .thenMany(repository.findByEmpresa(ID_COMPANIA, ID_SUCURSAL, "AUTORIZADO", 50, 0)))
                    .recordWith(java.util.ArrayList::new)
                    .thenConsumeWhile(c -> true)
                    .consumeRecordedWith(items -> {
                        assert items.stream().allMatch(c -> "AUTORIZADO".equals(c.getEstado()));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna vacío cuando la compañía no tiene comprobantes")
        void findByEmpresa_sinComprobantes_retornaFluxVacio() {
            int idCompaniaInexistente = 88888;
            StepVerifier.create(repository.findByEmpresa(idCompaniaInexistente, null, null, 50, 0))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("countByEmpresa")
    class CountByEmpresa {

        @Test
        @DisplayName("retorna el conteo de comprobantes de la empresa")
        void countByEmpresa_conComprobantes_retornaLong() {
            ComprobanteEntity comprobante = buildComprobante(randomClaveAcceso(), randomSecuencial(), "GENERADO");

            StepVerifier.create(repository.save(comprobante)
                            .then(repository.countByEmpresa(ID_COMPANIA, ID_SUCURSAL, null)))
                    .assertNext(count -> {
                        assert count >= 1 : "Debe contar al menos 1 comprobante";
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna 0 cuando la compañía no tiene comprobantes")
        void countByEmpresa_sinComprobantes_retorna0() {
            int idCompaniaInexistente = 88888;
            StepVerifier.create(repository.countByEmpresa(idCompaniaInexistente, null, null))
                    .expectNext(0L)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("updateEstadoById")
    class UpdateEstadoById {

        @Test
        @DisplayName("actualiza el estado y los campos de autorización del comprobante")
        void updateEstadoById_comprobanteExiste_actualizaCampos() {
            ComprobanteEntity comprobante = buildComprobante(randomClaveAcceso(), randomSecuencial(), "GENERADO");
            OffsetDateTime fechaAut = OffsetDateTime.now();
            String numeroAut = randomNumeroAutorizacion();

            StepVerifier.create(repository.save(comprobante)
                            .flatMap(saved -> repository.updateEstadoById(
                                    saved.getId(),
                                    "AUTORIZADO",
                                    "/tmp/firmado.xml",
                                    "/tmp/autorizado.xml",
                                    "/tmp/ride.pdf",
                                    fechaAut,
                                    numeroAut)
                                    .then(repository.findById(saved.getId()))))
                    .assertNext(retrieved -> {
                        assert "AUTORIZADO".equals(retrieved.getEstado());
                        assert "/tmp/firmado.xml".equals(retrieved.getXmlFirmadoPath());
                        assert "/tmp/autorizado.xml".equals(retrieved.getXmlAutorizadoPath());
                        assert "/tmp/ride.pdf".equals(retrieved.getRidePdfPath());
                        assert numeroAut.equals(retrieved.getNumeroAutorizacion());
                    })
                    .verifyComplete();
        }
    }
}
