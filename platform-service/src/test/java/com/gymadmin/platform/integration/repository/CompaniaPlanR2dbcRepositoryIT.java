package com.gymadmin.platform.integration.repository;

import com.gymadmin.platform.BaseIntegrationTest;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.CompaniaEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.CompaniaPlanEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.PlanEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.CompaniaR2dbcRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.CompaniaPlanR2dbcRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.PlanR2dbcRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Integration tests for CompaniaPlanR2dbcRepository.
 * Tests the persistence layer against a real PostgreSQL database.
 * All tests use the tenant.compania_planes table.
 * FK requirements:
 *  - idCompania must reference an existing tenant.companias.id
 *  - idPlan must reference an existing saas.planes.id
 * CONSTRAINT: Only one activo/en_gracia subscription per company (unique constraint)
 */
@DisplayName("CompaniaPlanR2dbcRepository")
class CompaniaPlanR2dbcRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private CompaniaPlanR2dbcRepository companiaPlanRepository;

    @Autowired
    private CompaniaR2dbcRepository companiaRepository;

    @Autowired
    private PlanR2dbcRepository planRepository;

    private CompaniaEntity crearCompania(String nombre, String ruc) {
        return companiaRepository.save(CompaniaEntity.builder()
                .nombre(nombre)
                .ruc(ruc)
                .activo(true)
                .trialUsado(false)
                .creacionUsuario("test")
                .eliminado(false)
                .build()).block();
    }

    private PlanEntity crearPlan(String nombre, String codigo) {
        return planRepository.save(PlanEntity.builder()
                .nombre(nombre)
                .descripcion("Plan de prueba")
                .precioMensual(new BigDecimal("99.99"))
                .activo(true)
                .codigo(codigo)
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
    }

    // ── TC-PLATFORM-REPO-016 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("inserta nueva suscripción de compañía correctamente")
        void save_nuevaCompaniaPlan_seGuardaCorrectamente() {
            CompaniaEntity compania = crearCompania("Gym Save CP", "5555444433332");
            PlanEntity plan = crearPlan("Plan Save", "SAVE-001");

            CompaniaPlanEntity entity = CompaniaPlanEntity.builder()
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
                    .build();

            StepVerifier.create(companiaPlanRepository.save(entity))
                    .assertNext(saved -> {
                        assert saved.getId() != null : "ID should be auto-generated";
                        assert saved.getIdCompania().equals(compania.getId());
                        assert saved.getIdPlan().equals(plan.getId());
                        assert saved.getEstado().equals("activo");
                        assert saved.getTipoCambio().equals("nuevo");
                        assert saved.getCreacionFecha() != null : "creacionFecha should be auto-populated";
                    })
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-017 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("retorna suscripción cuando existe")
        void findById_cuandoExiste_retornaCompaniaPlan() {
            CompaniaEntity compania = crearCompania("Gym FindById CP", "4444333322221");
            PlanEntity plan = crearPlan("Plan FindById", "FIND-001");

            CompaniaPlanEntity entity = CompaniaPlanEntity.builder()
                    .idCompania(compania.getId())
                    .idPlan(plan.getId())
                    .fechaInicio(LocalDate.now())
                    .fechaFin(LocalDate.now().plusDays(30))
                    .diasGracia(5)
                    .estado("activo")
                    .tipoCambio("renovacion")
                    .sobreLimite(false)
                    .creditoMonto(BigDecimal.ZERO)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(companiaPlanRepository.save(entity)
                    .flatMap(saved -> companiaPlanRepository.findById(saved.getId())))
                    .assertNext(found -> {
                        assert found.getIdCompania().equals(compania.getId());
                        assert found.getEstado().equals("activo");
                        assert found.getTipoCambio().equals("renovacion");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna empty cuando no existe")
        void findById_cuandoNoExiste_retornaEmpty() {
            StepVerifier.create(companiaPlanRepository.findById(999999L))
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-018 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findActivoByIdCompania")
    class FindActivoByIdCompania {

        @Test
        @DisplayName("retorna suscripción activa o en gracia de una compañía")
        void findActivoByIdCompania_conSuscripcionActiva_retornaCompaniaPlan() {
            CompaniaEntity compania = crearCompania("Gym Activo CP", "3333222211110");
            PlanEntity plan = crearPlan("Plan Activo", "ACTIVO-001");

            CompaniaPlanEntity entity = CompaniaPlanEntity.builder()
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
                    .build();

            StepVerifier.create(companiaPlanRepository.save(entity)
                    .then(companiaPlanRepository.findActivoByIdCompania(compania.getId())))
                    .assertNext(found -> {
                        assert found.getIdCompania().equals(compania.getId());
                        assert found.getEstado().equals("activo");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna empty cuando compañía no tiene suscripción activa")
        void findActivoByIdCompania_sinSuscripcionActiva_retornaEmpty() {
            StepVerifier.create(companiaPlanRepository.findActivoByIdCompania(999999L))
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-019 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findByIdCompania")
    class FindByIdCompania {

        @Test
        @DisplayName("retorna todas las suscripciones de una compañía")
        void findByIdCompania_conSuscripciones_retornaLista() {
            CompaniaEntity compania = crearCompania("Gym Multiple CP", "2222111100009");
            PlanEntity plan = crearPlan("Plan Multiple", "MULTI-001");

            CompaniaPlanEntity suscripcion = CompaniaPlanEntity.builder()
                    .idCompania(compania.getId())
                    .idPlan(plan.getId())
                    .fechaInicio(LocalDate.now().minusDays(30))
                    .fechaFin(LocalDate.now())
                    .diasGracia(5)
                    .estado("vencido")
                    .tipoCambio("renovacion")
                    .sobreLimite(false)
                    .creditoMonto(BigDecimal.ZERO)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(companiaPlanRepository.save(suscripcion)
                    .thenMany(companiaPlanRepository.findByIdCompania(compania.getId())))
                    .expectNextCount(1)
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-020 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findByEstado")
    class FindByEstado {

        @Test
        @DisplayName("retorna suscripciones con estado específico")
        void findByEstado_conSuscripcionesEnEstado_retornaLista() {
            CompaniaEntity compania = crearCompania("Gym Estado CP", "1111000099998");
            PlanEntity plan = crearPlan("Plan Estado", "ESTADO-001");

            CompaniaPlanEntity entity = CompaniaPlanEntity.builder()
                    .idCompania(compania.getId())
                    .idPlan(plan.getId())
                    .fechaInicio(LocalDate.now())
                    .fechaFin(LocalDate.now().plusDays(30))
                    .diasGracia(5)
                    .estado("suspendido")
                    .tipoCambio("nuevo")
                    .sobreLimite(false)
                    .creditoMonto(BigDecimal.ZERO)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(companiaPlanRepository.save(entity)
                    .thenMany(companiaPlanRepository.findByEstado("suspendido")))
                    .assertNext(found -> {
                        assert found.getEstado().equals("suspendido");
                    })
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-021 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findActivosVencidos")
    class FindActivosVencidos {

        @Test
        @DisplayName("retorna suscripciones activas vencidas antes de una fecha")
        void findActivosVencidos_conSuscripcionesVencidas_retornaLista() {
            CompaniaEntity compania = crearCompania("Gym Vencido CP", "0000999988887");
            PlanEntity plan = crearPlan("Plan Vencido", "VENCIDO-001");

            LocalDate hoy = LocalDate.now();
            CompaniaPlanEntity entity = CompaniaPlanEntity.builder()
                    .idCompania(compania.getId())
                    .idPlan(plan.getId())
                    .fechaInicio(hoy.minusDays(60))
                    .fechaFin(hoy.minusDays(1))
                    .diasGracia(5)
                    .estado("activo")
                    .tipoCambio("nuevo")
                    .sobreLimite(false)
                    .creditoMonto(BigDecimal.ZERO)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(companiaPlanRepository.save(entity)
                    .thenMany(companiaPlanRepository.findActivosVencidos(hoy)))
                    .assertNext(found -> {
                        assert found.getEstado().equals("activo");
                        assert found.getFechaFin().isBefore(hoy);
                    })
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-022 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("updateEstadoById")
    class UpdateEstadoById {

        @Test
        @DisplayName("actualiza estado y motivo de suspensión")
        void updateEstadoById_actualizaEstado_correctamente() {
            CompaniaEntity compania = crearCompania("Gym Update CP", "9999888877776");
            PlanEntity plan = crearPlan("Plan Update", "UPDATE-001");

            CompaniaPlanEntity entity = CompaniaPlanEntity.builder()
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
                    .build();

            StepVerifier.create(companiaPlanRepository.save(entity)
                    .flatMap(saved -> companiaPlanRepository.updateEstadoById(
                            saved.getId(), "suspendido", "Pago rechazado")
                            .then(companiaPlanRepository.findById(saved.getId()))))
                    .assertNext(found -> {
                        assert found.getEstado().equals("suspendido");
                        assert found.getMotivoSuspension().equals("Pago rechazado");
                    })
                    .verifyComplete();
        }
    }
}
