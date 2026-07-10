package com.gymadmin.platform.integration.repository;

import com.gymadmin.platform.BaseIntegrationTest;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.CompaniaEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.CompaniaPlanEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.PagoSuscripcionEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.PlanEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.CompaniaR2dbcRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.CompaniaPlanR2dbcRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.PagoSuscripcionR2dbcRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.PlanR2dbcRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Integration tests for PagoSuscripcionR2dbcRepository.
 * Tests the persistence layer against a real PostgreSQL database.
 * All tests use the tenant.pagos_suscripcion table.
 * FK requirement: idCompaniaPlan must reference an existing tenant.compania_planes.id
 */
@DisplayName("PagoSuscripcionR2dbcRepository")
class PagoSuscripcionR2dbcRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private PagoSuscripcionR2dbcRepository pagoRepository;

    @Autowired
    private CompaniaR2dbcRepository companiaRepository;

    @Autowired
    private PlanR2dbcRepository planRepository;

    @Autowired
    private CompaniaPlanR2dbcRepository companiaPlanRepository;

    private CompaniaPlanEntity crearCompaniaPlan(String nombreCompania, String rucCompania,
                                                  String nombrePlan, String codigoPlan) {
        CompaniaEntity compania = companiaRepository.save(CompaniaEntity.builder()
                .nombre(nombreCompania)
                .ruc(rucCompania)
                .activo(true)
                .trialUsado(false)
                .creacionUsuario("test")
                .eliminado(false)
                .build()).block();

        PlanEntity plan = planRepository.save(PlanEntity.builder()
                .nombre(nombrePlan)
                .descripcion("Plan de prueba")
                .precioMensual(new BigDecimal("99.99"))
                .activo(true)
                .codigo(codigoPlan)
                .duracionDias(30)
                .esGratuito(false)
                .maxSucursales(10)
                .maxClientesActivos(500)
                .maxStaff(15)
                .moneda("USD")
                .esLegacy(false)
                .creacionUsuario("test")
                .eliminado(false)
                .build()).block();

        return companiaPlanRepository.save(CompaniaPlanEntity.builder()
                .idCompania(compania.getId())
                .idPlan(plan.getId())
                .fechaInicio(LocalDate.now())
                .fechaFin(LocalDate.now().plusDays(30))
                .diasGracia(5)
                .estado("activo")
                .tipoCambio("nuevo")
                .sobreLimite(false)
                .creditoMonto(BigDecimal.ZERO)
                .creacionUsuario("test")
                .eliminado(false)
                .build()).block();
    }

    // ── TC-PLATFORM-REPO-025 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("inserta nuevo pago de suscripción correctamente")
        void save_nuevoPago_seGuardaCorrectamente() {
            CompaniaPlanEntity companiaPlan = crearCompaniaPlan(
                    "Gym Save Pago", "5555444433332",
                    "Plan Save Pago", "SAVE-PAGO-001");

            PagoSuscripcionEntity entity = PagoSuscripcionEntity.builder()
                    .idCompaniaPlan(companiaPlan.getId())
                    .monto(new BigDecimal("99.99"))
                    .fechaPago(LocalDate.now())
                    .periodoDesde(LocalDate.now())
                    .periodoHasta(LocalDate.now().plusDays(30))
                    .metodoPago("transferencia")
                    .tipoPago("pago_completo")
                    .estado("pagado")
                    .referencia("REF-001")
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(pagoRepository.save(entity))
                    .assertNext(saved -> {
                        assert saved.getId() != null : "ID should be auto-generated";
                        assert saved.getIdCompaniaPlan().equals(companiaPlan.getId());
                        assert saved.getMonto().equals(new BigDecimal("99.99"));
                        assert saved.getEstado().equals("pagado");
                        assert saved.getCreacionFecha() != null : "creacionFecha should be auto-populated";
                    })
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-026 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("retorna pago cuando existe")
        void findById_cuandoExiste_retornaPago() {
            CompaniaPlanEntity companiaPlan = crearCompaniaPlan(
                    "Gym FindById Pago", "4444333322221",
                    "Plan FindById Pago", "FIND-PAGO-001");

            PagoSuscripcionEntity entity = PagoSuscripcionEntity.builder()
                    .idCompaniaPlan(companiaPlan.getId())
                    .monto(new BigDecimal("149.99"))
                    .fechaPago(LocalDate.now())
                    .periodoDesde(LocalDate.now())
                    .periodoHasta(LocalDate.now().plusDays(30))
                    .metodoPago("tarjeta")
                    .tipoPago("renovacion")
                    .estado("pagado")
                    .referencia("REF-002")
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(pagoRepository.save(entity)
                    .flatMap(saved -> pagoRepository.findById(saved.getId())))
                    .assertNext(found -> {
                        assert found.getIdCompaniaPlan().equals(companiaPlan.getId());
                        assert found.getMonto().equals(new BigDecimal("149.99"));
                        assert found.getMetodoPago().equals("tarjeta");
                        assert found.getEstado().equals("pagado");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna empty cuando no existe")
        void findById_cuandoNoExiste_retornaEmpty() {
            StepVerifier.create(pagoRepository.findById(999999L))
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-027 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findByIdCompania")
    class FindByIdCompania {

        @Test
        @DisplayName("retorna todos los pagos de una compañía (JOIN compania_planes)")
        void findByIdCompania_conPagos_retornaLista() {
            CompaniaPlanEntity companiaPlan = crearCompaniaPlan(
                    "Gym Multiple Pagos", "3333222211110",
                    "Plan Multiple Pagos", "MULTI-PAGO-001");

            PagoSuscripcionEntity pago1 = PagoSuscripcionEntity.builder()
                    .idCompaniaPlan(companiaPlan.getId())
                    .monto(new BigDecimal("99.99"))
                    .fechaPago(LocalDate.now().minusDays(30))
                    .periodoDesde(LocalDate.now().minusDays(30))
                    .periodoHasta(LocalDate.now())
                    .metodoPago("efectivo")
                    .tipoPago("pago_completo")
                    .estado("pagado")
                    .referencia("REF-003")
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            PagoSuscripcionEntity pago2 = PagoSuscripcionEntity.builder()
                    .idCompaniaPlan(companiaPlan.getId())
                    .monto(new BigDecimal("99.99"))
                    .fechaPago(LocalDate.now())
                    .periodoDesde(LocalDate.now())
                    .periodoHasta(LocalDate.now().plusDays(30))
                    .metodoPago("transferencia")
                    .tipoPago("pago_completo")
                    .estado("pagado")
                    .referencia("REF-004")
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(pagoRepository.save(pago1)
                    .then(pagoRepository.save(pago2))
                    .thenMany(pagoRepository.findByIdCompania(companiaPlan.getIdCompania())))
                    .expectNextCount(2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna lista vacía cuando compañía no tiene pagos")
        void findByIdCompania_sinPagos_retornaVacio() {
            StepVerifier.create(pagoRepository.findByIdCompania(999999L))
                    .verifyComplete();
        }
    }
}
