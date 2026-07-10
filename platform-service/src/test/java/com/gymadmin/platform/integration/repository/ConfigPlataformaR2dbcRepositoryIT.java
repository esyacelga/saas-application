package com.gymadmin.platform.integration.repository;

import com.gymadmin.platform.BaseIntegrationTest;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.ConfigPlataformaEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.ConfigPlataformaR2dbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

/**
 * Integration tests for ConfigPlataformaR2dbcRepository.
 * Tests the persistence layer against a real PostgreSQL database.
 * All tests use the saas.config_plataforma table.
 * Note: PK is String 'clave', entity implements Persistable<String>.
 * Must call setNuevo(true) before save() to force INSERT (not UPDATE).
 * FK constraint: modificado_por → seguridad.usuarios.id (nullable in test)
 * La tabla no está en el cleanup de BaseIntegrationTest (PK String con seeds),
 * por eso borramos las claves de prueba (CONFIG_%) al inicio de cada test.
 */
@DisplayName("ConfigPlataformaR2dbcRepository")
class ConfigPlataformaR2dbcRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private ConfigPlataformaR2dbcRepository configPlataformaRepository;

    @BeforeEach
    void limpiarClavesDePrueba() {
        databaseClient.sql("DELETE FROM saas.config_plataforma WHERE clave LIKE 'CONFIG_%'")
                .then().block();
    }

    // ── TC-PLATFORM-REPO-038 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("inserta nueva configuración de plataforma correctamente")
        void save_nuevaConfigPlataforma_seGuardaCorrectamente() {
            ConfigPlataformaEntity entity = new ConfigPlataformaEntity();
            entity.setClave("CONFIG_TEST_001");
            entity.setValor("valor_test_001");
            entity.setDescripcion("Descripción de configuración test");
            entity.setModificadoPor(null);
            entity.setModificadoAt(null);
            entity.setNuevo(true);
            entity.setCreacionUsuario("test");

            StepVerifier.create(configPlataformaRepository.save(entity))
                    .assertNext(saved -> {
                        assert saved.getClave().equals("CONFIG_TEST_001");
                        assert saved.getValor().equals("valor_test_001");
                        assert saved.getDescripcion().equals("Descripción de configuración test");
                    })
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-039 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("retorna configuración cuando existe por clave")
        void findById_cuandoExiste_retornaConfigPlataforma() {
            ConfigPlataformaEntity entity = new ConfigPlataformaEntity();
            entity.setClave("CONFIG_SEARCH_001");
            entity.setValor("valor_busqueda_001");
            entity.setDescripcion("Configuración de búsqueda");
            entity.setModificadoPor(null);
            entity.setModificadoAt(null);
            entity.setNuevo(true);
            entity.setCreacionUsuario("test");

            StepVerifier.create(configPlataformaRepository.save(entity)
                    .then(configPlataformaRepository.findById("CONFIG_SEARCH_001")))
                    .assertNext(found -> {
                        assert found.getClave().equals("CONFIG_SEARCH_001");
                        assert found.getValor().equals("valor_busqueda_001");
                        assert found.getDescripcion().equals("Configuración de búsqueda");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna empty cuando clave no existe")
        void findById_cuandoNoExiste_retornaEmpty() {
            StepVerifier.create(configPlataformaRepository.findById("NOEXISTE"))
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-040 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("update (save with nuevo=false)")
    class Update {

        @Test
        @DisplayName("actualiza configuración existente")
        void save_configExistente_actualizaValor() {
            // First, insert. Esta entidad NO tiene @CreatedDate: save() en INSERT no
            // repuebla creacion_fecha/modificado_at en el objeto, así que los seteamos
            // explícitamente para que sobrevivan al UPDATE posterior (ambos son NOT NULL).
            ConfigPlataformaEntity entity = new ConfigPlataformaEntity();
            entity.setClave("CONFIG_UPDATE_001");
            entity.setValor("valor_inicial");
            entity.setDescripcion("Configuración para actualizar");
            entity.setModificadoPor(null);
            entity.setModificadoAt(java.time.OffsetDateTime.now());
            entity.setCreacionFecha(java.time.OffsetDateTime.now());
            entity.setNuevo(true);
            entity.setCreacionUsuario("test");

            StepVerifier.create(configPlataformaRepository.save(entity)
                    .flatMap(saved -> {
                        // Now update it. modificado_at es NOT NULL: en INSERT lo llena
                        // el DEFAULT NOW() de la BD, pero un UPDATE explícito enviaría NULL,
                        // así que lo seteamos manualmente.
                        saved.setValor("valor_actualizado");
                        saved.setDescripcion("Configuración actualizada");
                        saved.setModificadoPor(null);
                        saved.setModificadoAt(java.time.OffsetDateTime.now());
                        saved.setNuevo(false);
                        return configPlataformaRepository.save(saved)
                                .then(configPlataformaRepository.findById("CONFIG_UPDATE_001"));
                    }))
                    .assertNext(found -> {
                        assert found.getClave().equals("CONFIG_UPDATE_001");
                        assert found.getValor().equals("valor_actualizado");
                        assert found.getDescripcion().equals("Configuración actualizada");
                    })
                    .verifyComplete();
        }
    }
}
