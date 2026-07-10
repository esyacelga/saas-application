package com.gymadmin.platform.integration.repository;

import com.gymadmin.platform.BaseIntegrationTest;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.CompaniaEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.CompaniaR2dbcRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

/**
 * Integration tests for CompaniaR2dbcRepository.
 * Tests the persistence layer against a real PostgreSQL database.
 * All tests use the tenant.companias table.
 */
@DisplayName("CompaniaR2dbcRepository")
class CompaniaR2dbcRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private CompaniaR2dbcRepository companiaRepository;

    // ── TC-PLATFORM-REPO-001 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("inserta nueva compañía correctamente")
        void save_nuevaCompania_seGuardaCorrectamente() {
            CompaniaEntity entity = CompaniaEntity.builder()
                    .nombre("Gym Test S.A.")
                    .ruc("1234567890001")
                    .logoUrl("https://cloudinary.com/logo.jpg")
                    .telefono("+593991234567")
                    .whatsapp("+593991234567")
                    .correo("test@gym.com")
                    .activo(true)
                    .trialUsado(false)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(companiaRepository.save(entity))
                    .assertNext(saved -> {
                        assert saved.getId() != null : "ID should be auto-generated";
                        assert saved.getNombre().equals("Gym Test S.A.");
                        assert saved.getRuc().equals("1234567890001");
                        assert saved.getActivo() == true;
                        assert saved.getEliminado() == false;
                        assert saved.getCreacionFecha() != null : "creacionFecha should be auto-populated";
                    })
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-002 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("retorna compañía cuando existe")
        void findById_cuandoExiste_retornaCompania() {
            CompaniaEntity entity = CompaniaEntity.builder()
                    .nombre("Gym Búsqueda")
                    .ruc("9876543210001")
                    .activo(true)
                    .trialUsado(false)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(companiaRepository.save(entity)
                    .flatMap(saved -> companiaRepository.findById(saved.getId())))
                    .assertNext(found -> {
                        assert found.getNombre().equals("Gym Búsqueda");
                        assert found.getRuc().equals("9876543210001");
                        assert found.getActivo() == true;
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna empty cuando no existe")
        void findById_cuandoNoExiste_retornaEmpty() {
            StepVerifier.create(companiaRepository.findById(999999L))
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-003 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findByRuc")
    class FindByRuc {

        @Test
        @DisplayName("retorna compañía cuando existe por RUC")
        void findByRuc_cuandoExiste_retornaCompania() {
            String ruc = "TESTRUC12345001";
            CompaniaEntity entity = CompaniaEntity.builder()
                    .nombre("Gym RUC Search")
                    .ruc(ruc)
                    .activo(true)
                    .trialUsado(false)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(companiaRepository.save(entity)
                    .then(companiaRepository.findByRuc(ruc)))
                    .assertNext(found -> {
                        assert found.getRuc().equals(ruc);
                        assert found.getNombre().equals("Gym RUC Search");
                        assert found.getActivo() == true;
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna empty cuando RUC no existe")
        void findByRuc_cuandoNoExiste_retornaEmpty() {
            StepVerifier.create(companiaRepository.findByRuc("NOEXISTE"))
                    .verifyComplete();
        }
    }
}
