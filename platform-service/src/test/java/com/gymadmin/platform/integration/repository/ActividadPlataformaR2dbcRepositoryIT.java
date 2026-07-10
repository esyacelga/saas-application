package com.gymadmin.platform.integration.repository;

import com.gymadmin.platform.BaseIntegrationTest;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.ActividadPlataformaEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.ActividadPlataformaR2dbcRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;

/**
 * Integration tests for ActividadPlataformaR2dbcRepository.
 * Tests the persistence layer against a real PostgreSQL database.
 * All tests use the saas.actividad_plataforma table.
 * Note: Entity does NOT extend BaseAuditEntity and is NOT @SuperBuilder.
 * Use plain constructor or setters. tipoActor must be UPPERCASE (OWNER/ROOT/STAFF/SISTEMA).
 * usuario es NOT NULL, ip y detalle pueden ser NULL.
 */
@DisplayName("ActividadPlataformaR2dbcRepository")
class ActividadPlataformaR2dbcRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private ActividadPlataformaR2dbcRepository actividadRepository;

    // ── TC-PLATFORM-REPO-041 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("inserta nueva actividad de plataforma correctamente")
        void save_nuevaActividad_seGuardaCorrectamente() {
            ActividadPlataformaEntity entity = new ActividadPlataformaEntity();
            entity.setTipoEvento("COMPANIA_CREADA");
            entity.setModulo("companias");
            entity.setEntidadId(1L);
            entity.setEntidadNombre("Gym Test");
            // detalle (JSONB) e ip (INET) se dejan null: R2DBC los enviaría como
            // varchar y Postgres rechaza el cast implícito en columnas tipadas.
            entity.setUsuario("user@test.com");
            entity.setFecha(OffsetDateTime.now());
            entity.setTipoActor("OWNER");

            StepVerifier.create(actividadRepository.save(entity))
                    .assertNext(saved -> {
                        assert saved.getId() != null : "ID should be auto-generated";
                        assert saved.getTipoEvento().equals("COMPANIA_CREADA");
                        assert saved.getModulo().equals("companias");
                        assert saved.getEntidadId() == 1L;
                        assert saved.getEntidadNombre().equals("Gym Test");
                        assert saved.getTipoActor().equals("OWNER");
                    })
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-042 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("retorna actividad cuando existe")
        void findById_cuandoExiste_retornaActividad() {
            ActividadPlataformaEntity entity = new ActividadPlataformaEntity();
            entity.setTipoEvento("PLAN_ACTUALIZADO");
            entity.setModulo("planes");
            entity.setEntidadId(5L);
            entity.setEntidadNombre("Plan Premium");
            entity.setUsuario("admin@test.com");
            entity.setFecha(OffsetDateTime.now());
            entity.setIdUsuarioActor(200L);
            entity.setTipoActor("ROOT");

            StepVerifier.create(actividadRepository.save(entity)
                    .flatMap(saved -> actividadRepository.findById(saved.getId())))
                    .assertNext(found -> {
                        assert found.getTipoEvento().equals("PLAN_ACTUALIZADO");
                        assert found.getModulo().equals("planes");
                        assert found.getEntidadId() == 5L;
                        assert found.getTipoActor().equals("ROOT");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna empty cuando no existe")
        void findById_cuandoNoExiste_retornaEmpty() {
            StepVerifier.create(actividadRepository.findById(999999L))
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-043 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("save with various tipoActor values")
    class SaveConTipoActor {

        @Test
        @DisplayName("guarda actividad con tipoActor STAFF")
        void save_conTipoActorSTAFF_seGuardaCorrectamente() {
            ActividadPlataformaEntity entity = new ActividadPlataformaEntity();
            entity.setTipoEvento("ASISTENCIA_REGISTRADA");
            entity.setModulo("attendance");
            entity.setEntidadId(10L);
            entity.setEntidadNombre("Evento de Asistencia");
            entity.setUsuario("staff@gym.com");
            entity.setFecha(OffsetDateTime.now());
            entity.setIdUsuarioActor(300L);
            entity.setTipoActor("STAFF");

            StepVerifier.create(actividadRepository.save(entity))
                    .assertNext(saved -> {
                        assert saved.getTipoActor().equals("STAFF");
                        assert saved.getIdUsuarioActor() == 300L;
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("guarda actividad con tipoActor SISTEMA")
        void save_conTipoActorSISTEMA_seGuardaCorrectamente() {
            ActividadPlataformaEntity entity = new ActividadPlataformaEntity();
            entity.setTipoEvento("JOB_EJECUCION");
            entity.setModulo("scheduler");
            entity.setEntidadNombre("Subscription Job");
            entity.setUsuario("system");
            entity.setFecha(OffsetDateTime.now());
            entity.setTipoActor("SISTEMA");

            StepVerifier.create(actividadRepository.save(entity))
                    .assertNext(saved -> {
                        assert saved.getTipoActor().equals("SISTEMA");
                        assert saved.getTipoEvento().equals("JOB_EJECUCION");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("guarda actividad con detalle JSONB nullable")
        void save_conDetalleNull_seGuardaCorrectamente() {
            ActividadPlataformaEntity entity = new ActividadPlataformaEntity();
            entity.setTipoEvento("EVENTO_SIMPLE");
            entity.setModulo("platform");
            entity.setEntidadId(15L);
            entity.setEntidadNombre("Evento Prueba");
            entity.setDetalle(null);
            entity.setUsuario("test@test.com");
            entity.setIp(null);
            entity.setFecha(OffsetDateTime.now());
            entity.setTipoActor("ROOT");

            StepVerifier.create(actividadRepository.save(entity))
                    .assertNext(saved -> {
                        assert saved.getId() != null;
                        assert saved.getDetalle() == null;
                        assert saved.getIp() == null;
                    })
                    .verifyComplete();
        }
    }
}
