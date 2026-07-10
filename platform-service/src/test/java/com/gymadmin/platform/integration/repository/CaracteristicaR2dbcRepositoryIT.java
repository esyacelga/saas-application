package com.gymadmin.platform.integration.repository;

import com.gymadmin.platform.BaseIntegrationTest;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.CaracteristicaEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.CaracteristicaR2dbcRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

/**
 * Integration tests for CaracteristicaR2dbcRepository.
 * Tests the persistence layer against a real PostgreSQL database.
 * All tests use the saas.caracteristicas table.
 */
@DisplayName("CaracteristicaR2dbcRepository")
class CaracteristicaR2dbcRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private CaracteristicaR2dbcRepository caracteristicaRepository;

    // ── TC-PLATFORM-REPO-009 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("inserta nueva característica correctamente")
        void save_nuevaCaracteristica_seGuardaCorrectamente() {
            CaracteristicaEntity entity = CaracteristicaEntity.builder()
                    .codigo("ATTENDANCE_TRACKING")
                    .nombre("Seguimiento de Asistencia")
                    .modulo("attendance")
                    .activo(true)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(caracteristicaRepository.save(entity))
                    .assertNext(saved -> {
                        assert saved.getId() != null : "ID should be auto-generated";
                        assert saved.getCodigo().equals("ATTENDANCE_TRACKING");
                        assert saved.getNombre().equals("Seguimiento de Asistencia");
                        assert saved.getModulo().equals("attendance");
                        assert saved.getActivo() == true;
                        assert saved.getCreacionFecha() != null : "creacionFecha should be auto-populated";
                    })
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-010 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("retorna característica cuando existe")
        void findById_cuandoExiste_retornaCaracteristica() {
            CaracteristicaEntity entity = CaracteristicaEntity.builder()
                    .codigo("FEATURE_SEARCH")
                    .nombre("Característica de Búsqueda")
                    .modulo("core")
                    .activo(true)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(caracteristicaRepository.save(entity)
                    .flatMap(saved -> caracteristicaRepository.findById(saved.getId())))
                    .assertNext(found -> {
                        assert found.getCodigo().equals("FEATURE_SEARCH");
                        assert found.getNombre().equals("Característica de Búsqueda");
                        assert found.getModulo().equals("core");
                        assert found.getActivo() == true;
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna empty cuando no existe")
        void findById_cuandoNoExiste_retornaEmpty() {
            StepVerifier.create(caracteristicaRepository.findById(999999L))
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-011 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findByCodigo")
    class FindByCodigo {

        @Test
        @DisplayName("retorna característica cuando existe por código")
        void findByCodigo_cuandoExiste_retornaCaracteristica() {
            String codigo = "UNIQUE_FEATURE_001";
            CaracteristicaEntity entity = CaracteristicaEntity.builder()
                    .codigo(codigo)
                    .nombre("Feature Única")
                    .modulo("platform")
                    .activo(true)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(caracteristicaRepository.save(entity)
                    .then(caracteristicaRepository.findByCodigo(codigo)))
                    .assertNext(found -> {
                        assert found.getCodigo().equals(codigo);
                        assert found.getNombre().equals("Feature Única");
                        assert found.getModulo().equals("platform");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna empty cuando código no existe")
        void findByCodigo_cuandoNoExiste_retornaEmpty() {
            StepVerifier.create(caracteristicaRepository.findByCodigo("NOEXISTE"))
                    .verifyComplete();
        }
    }
}
