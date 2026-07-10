package com.gymadmin.attendance.integration.repository;

import com.gymadmin.attendance.BaseIntegrationTest;
import com.gymadmin.attendance.infrastructure.adapter.out.persistence.entity.PlantillaMensajeEntity;
import com.gymadmin.attendance.infrastructure.adapter.out.persistence.repository.PlantillaMensajeR2dbcRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

/**
 * Integration tests for PlantillaMensajeR2dbcRepository.
 * Tests the persistence layer against a real PostgreSQL database.
 * All tests use idCompania=99999 and idSucursal=1.
 *
 * Valid tipo values: 'motivacional','ausencia_2d','recuperacion_5d','recuperacion_10d',
 * 'recuperacion_15d','vencimiento_3d','vencimiento_hoy'
 */
@DisplayName("PlantillaMensajeR2dbcRepository")
class PlantillaMensajeR2dbcRepositoryIT extends BaseIntegrationTest {

    private static final Integer ID_COMPANIA = 99999;
    private static final Integer ID_SUCURSAL = 1;

    @Autowired
    private PlantillaMensajeR2dbcRepository plantillaRepository;

    // ── TC-PLANTILLA-REPO-001 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("inserta nueva plantilla correctamente")
        void save_nuevaPlantilla_seGuardaCorrectamente() {
            PlantillaMensajeEntity entity = PlantillaMensajeEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .tipo("motivacional")
                    .nombre("Plantilla Motivación")
                    .contenido("Hola {nombre}, sigue adelante!")
                    .activo(true)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(plantillaRepository.save(entity))
                    .assertNext(saved -> {
                        assert saved.getId() != null : "ID should be auto-generated";
                        assert saved.getIdCompania().equals(ID_COMPANIA);
                        assert saved.getTipo().equals("motivacional");
                        assert saved.getActivo() == true;
                        assert saved.getEliminado() == false;
                    })
                    .verifyComplete();
        }
    }

    // ── TC-PLANTILLA-REPO-002 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("retorna plantilla cuando existe")
        void findById_cuandoExiste_retornaPlantilla() {
            PlantillaMensajeEntity entity = PlantillaMensajeEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .tipo("ausencia_2d")
                    .nombre("Recuperación 2 días")
                    .contenido("Vuelve pronto {nombre}")
                    .activo(true)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(plantillaRepository.save(entity)
                    .flatMap(saved -> plantillaRepository.findById(saved.getId())))
                    .assertNext(found -> {
                        assert found.getTipo().equals("ausencia_2d");
                        assert found.getNombre().equals("Recuperación 2 días");
                        assert found.getActivo() == true;
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna empty cuando no existe")
        void findById_cuandoNoExiste_retornaEmpty() {
            StepVerifier.create(plantillaRepository.findById(999999))
                    .verifyComplete();
        }
    }

    // ── TC-PLANTILLA-REPO-003 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findByCompania")
    class FindByCompania {

        @Test
        @DisplayName("retorna todas las plantillas activas de una compañía")
        void findByCompania_conPlantillas_retornaLista() {
            PlantillaMensajeEntity entity1 = PlantillaMensajeEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .tipo("recuperacion_5d")
                    .nombre("Recuperación 5 días")
                    .contenido("Vuelve en {dias} días")
                    .activo(true)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            PlantillaMensajeEntity entity2 = PlantillaMensajeEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .tipo("vencimiento_3d")
                    .nombre("Vencimiento próximo")
                    .contenido("Tu membresía vence el {fecha_vencimiento}")
                    .activo(true)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(plantillaRepository.save(entity1)
                    .then(plantillaRepository.save(entity2))
                    .thenMany(plantillaRepository.findByCompania(ID_COMPANIA)))
                    .expectNextCount(2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna lista vacía cuando no hay plantillas")
        void findByCompania_sinPlantillas_retornaVacio() {
            Integer ottraCompania = 88888;

            StepVerifier.create(plantillaRepository.findByCompania(ottraCompania))
                    .verifyComplete();
        }
    }

    // ── TC-PLANTILLA-REPO-004 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("countActivasByTipo")
    class CountActivasByTipo {

        @Test
        @DisplayName("cuenta plantillas activas de un tipo específico")
        void countActivasByTipo_conPlantillasActivas_retornaConteo() {
            PlantillaMensajeEntity entity1 = PlantillaMensajeEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .tipo("recuperacion_10d")
                    .nombre("Recuperación 10 días v1")
                    .contenido("Contenido 1")
                    .activo(true)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            PlantillaMensajeEntity entity2 = PlantillaMensajeEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .tipo("recuperacion_10d")
                    .nombre("Recuperación 10 días v2")
                    .contenido("Contenido 2")
                    .activo(true)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(plantillaRepository.save(entity1)
                    .then(plantillaRepository.save(entity2))
                    .then(plantillaRepository.countActivasByTipo(ID_COMPANIA, "recuperacion_10d")))
                    .assertNext(count -> { assert count >= 2L; })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna cero cuando no hay plantillas activas del tipo")
        void countActivasByTipo_sinPlantillasActivas_retornaCero() {
            StepVerifier.create(plantillaRepository.countActivasByTipo(ID_COMPANIA, "vencimiento_hoy"))
                    .assertNext(count -> { assert count == 0L; })
                    .verifyComplete();
        }

        @Test
        @DisplayName("no cuenta plantillas inactivas")
        void countActivasByTipo_conPlantillasInactivas_noLasCuenta() {
            PlantillaMensajeEntity entity = PlantillaMensajeEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .tipo("motivacional")
                    .nombre("Motivación inactiva")
                    .contenido("Contenido")
                    .activo(false)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(plantillaRepository.save(entity)
                    .then(plantillaRepository.countActivasByTipo(ID_COMPANIA, "motivacional")))
                    .assertNext(count -> { assert count == 0L; })
                    .verifyComplete();
        }
    }

    // ── TC-PLANTILLA-REPO-005 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findRandomActivaByTipo")
    class FindRandomActivaByTipo {

        @Test
        @DisplayName("retorna una plantilla activa aleatoria del tipo")
        void findRandomActivaByTipo_conPlantillaActiva_retornaUna() {
            PlantillaMensajeEntity entity = PlantillaMensajeEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .tipo("recuperacion_15d")
                    .nombre("Recuperación 15 días")
                    .contenido("Vuelve cuando puedas {nombre}")
                    .activo(true)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(plantillaRepository.save(entity)
                    .then(plantillaRepository.findRandomActivaByTipo(ID_COMPANIA, "recuperacion_15d")))
                    .assertNext(found -> {
                        assert found.getTipo().equals("recuperacion_15d");
                        assert found.getActivo() == true;
                        assert found.getEliminado() == false;
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna empty cuando no hay plantillas activas")
        void findRandomActivaByTipo_sinPlantillasActivas_retornaEmpty() {
            StepVerifier.create(plantillaRepository.findRandomActivaByTipo(ID_COMPANIA, "ausencia_2d"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("no retorna plantillas inactivas")
        void findRandomActivaByTipo_conPlantillaInactiva_noLaRetorna() {
            PlantillaMensajeEntity entity = PlantillaMensajeEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .tipo("vencimiento_3d")
                    .nombre("Vencimiento inactivo")
                    .contenido("Contenido")
                    .activo(false)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(plantillaRepository.save(entity)
                    .then(plantillaRepository.findRandomActivaByTipo(ID_COMPANIA, "vencimiento_3d")))
                    .verifyComplete();
        }
    }

    // ── TC-PLANTILLA-REPO-006 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("soft-delete (eliminado)")
    class SoftDelete {

        @Test
        @DisplayName("plantillas eliminadas no aparecen en listados")
        void softDelete_plantillaEliminada_noAparece() {
            PlantillaMensajeEntity entity = PlantillaMensajeEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .tipo("motivacional")
                    .nombre("Plantilla a eliminar")
                    .contenido("Contenido")
                    .activo(true)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(plantillaRepository.save(entity)
                    .flatMap(saved -> {
                        saved.setEliminado(true);
                        return plantillaRepository.save(saved);
                    })
                    .thenMany(plantillaRepository.findByCompania(ID_COMPANIA)))
                    .verifyComplete();
        }
    }
}
