package com.gymadmin.platform.integration.repository;

import com.gymadmin.platform.BaseIntegrationTest;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.PlanEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.PlanR2dbcRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

/**
 * Integration tests for PlanR2dbcRepository.
 * Tests the persistence layer against a real PostgreSQL database.
 * All tests use the saas.planes table.
 */
@DisplayName("PlanR2dbcRepository")
class PlanR2dbcRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private PlanR2dbcRepository planRepository;

    // ── TC-PLATFORM-REPO-004 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("inserta nuevo plan correctamente")
        void save_nuevoPlan_seGuardaCorrectamente() {
            PlanEntity entity = PlanEntity.builder()
                    .nombre("Plan Premium")
                    .descripcion("Plan con máximas características")
                    .precioMensual(new BigDecimal("99.99"))
                    .activo(true)
                    .codigo("PREMIUM-001")
                    .duracionDias(30)
                    .esGratuito(false)
                    .maxSucursales(10)
                    .maxClientesActivos(500)
                    .maxStaff(15)
                    .moneda("USD")
                    .esLegacy(false)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(planRepository.save(entity))
                    .assertNext(saved -> {
                        assert saved.getId() != null : "ID should be auto-generated";
                        assert saved.getNombre().equals("Plan Premium");
                        assert saved.getCodigo().equals("PREMIUM-001");
                        assert saved.getActivo() == true;
                        assert saved.getEsLegacy() == false;
                        assert saved.getCreacionFecha() != null : "creacionFecha should be auto-populated";
                    })
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-005 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("retorna plan cuando existe")
        void findById_cuandoExiste_retornaPlan() {
            PlanEntity entity = PlanEntity.builder()
                    .nombre("Plan Búsqueda")
                    .descripcion("Para búsqueda por ID")
                    .precioMensual(new BigDecimal("50.00"))
                    .activo(true)
                    .codigo("SEARCH-001")
                    .duracionDias(30)
                    .esGratuito(false)
                    .maxSucursales(5)
                    .maxClientesActivos(100)
                    .maxStaff(5)
                    .moneda("USD")
                    .esLegacy(false)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(planRepository.save(entity)
                    .flatMap(saved -> planRepository.findById(saved.getId())))
                    .assertNext(found -> {
                        assert found.getNombre().equals("Plan Búsqueda");
                        assert found.getCodigo().equals("SEARCH-001");
                        assert found.getActivo() == true;
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna empty cuando no existe")
        void findById_cuandoNoExiste_retornaEmpty() {
            StepVerifier.create(planRepository.findById(999999L))
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-006 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findByActivoTrue")
    class FindByActivoTrue {

        @Test
        @DisplayName("retorna todos los planes activos")
        void findByActivoTrue_conPlanesActivos_retornaLista() {
            PlanEntity plan1 = PlanEntity.builder()
                    .nombre("Plan Activo 1")
                    .descripcion("Activo 1")
                    .precioMensual(new BigDecimal("29.99"))
                    .activo(true)
                    .codigo("ACTIVE-1")
                    .duracionDias(30)
                    .esGratuito(false)
                    .maxSucursales(3)
                    .maxClientesActivos(50)
                    .maxStaff(3)
                    .moneda("USD")
                    .esLegacy(false)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            PlanEntity plan2 = PlanEntity.builder()
                    .nombre("Plan Activo 2")
                    .descripcion("Activo 2")
                    .precioMensual(new BigDecimal("49.99"))
                    .activo(true)
                    .codigo("ACTIVE-2")
                    .duracionDias(30)
                    .esGratuito(false)
                    .maxSucursales(5)
                    .maxClientesActivos(100)
                    .maxStaff(5)
                    .moneda("USD")
                    .esLegacy(false)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(planRepository.save(plan1)
                    .then(planRepository.save(plan2))
                    .thenMany(planRepository.findByActivoTrue()))
                    .expectNextCount(2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna lista vacía cuando no hay planes activos")
        void findByActivoTrue_sinPlanesActivos_retornaVacio() {
            // All plans are inactive by default for this test
            StepVerifier.create(planRepository.findByActivoTrue())
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-007 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findByCodigo")
    class FindByCodigo {

        @Test
        @DisplayName("retorna plan cuando existe por código")
        void findByCodigo_cuandoExiste_retornaPlan() {
            String codigo = "UNIQUE-CODE-123";
            PlanEntity entity = PlanEntity.builder()
                    .nombre("Plan por Código")
                    .descripcion("Búsqueda por código")
                    .precioMensual(new BigDecimal("75.00"))
                    .activo(true)
                    .codigo(codigo)
                    .duracionDias(30)
                    .esGratuito(false)
                    .maxSucursales(7)
                    .maxClientesActivos(200)
                    .maxStaff(8)
                    .moneda("USD")
                    .esLegacy(false)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(planRepository.save(entity)
                    .then(planRepository.findByCodigo(codigo)))
                    .assertNext(found -> {
                        assert found.getCodigo().equals(codigo);
                        assert found.getNombre().equals("Plan por Código");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna empty cuando código no existe")
        void findByCodigo_cuandoNoExiste_retornaEmpty() {
            StepVerifier.create(planRepository.findByCodigo("NOEXISTE"))
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-008 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findByActivoTrueAndEsLegacyFalse")
    class FindByActivoTrueAndEsLegacyFalse {

        @Test
        @DisplayName("retorna planes activos y no legacy")
        void findByActivoTrueAndEsLegacyFalse_conPlanesNoLegacy_retornaLista() {
            PlanEntity planNoLegacy = PlanEntity.builder()
                    .nombre("Plan Moderno")
                    .descripcion("No legacy")
                    .precioMensual(new BigDecimal("59.99"))
                    .activo(true)
                    .codigo("MODERN-001")
                    .duracionDias(30)
                    .esGratuito(false)
                    .maxSucursales(8)
                    .maxClientesActivos(300)
                    .maxStaff(10)
                    .moneda("USD")
                    .esLegacy(false)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            PlanEntity planLegacy = PlanEntity.builder()
                    .nombre("Plan Antiguo")
                    .descripcion("Legacy")
                    .precioMensual(new BigDecimal("19.99"))
                    .activo(true)
                    .codigo("LEGACY-001")
                    .duracionDias(30)
                    .esGratuito(false)
                    .maxSucursales(2)
                    .maxClientesActivos(20)
                    .maxStaff(2)
                    .moneda("USD")
                    .esLegacy(true)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(planRepository.save(planNoLegacy)
                    .then(planRepository.save(planLegacy))
                    .thenMany(planRepository.findByActivoTrueAndEsLegacyFalse()))
                    .assertNext(found -> {
                        assert found.getEsLegacy() == false : "Should not return legacy plans";
                        assert found.getActivo() == true : "Should be active";
                    })
                    .verifyComplete();
        }
    }
}
