package com.gymadmin.attendance.integration.repository;

import com.gymadmin.attendance.BaseIntegrationTest;
import com.gymadmin.attendance.infrastructure.adapter.out.persistence.entity.MensajeLogEntity;
import com.gymadmin.attendance.infrastructure.adapter.out.persistence.repository.MensajeLogR2dbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;

/**
 * Integration tests for MensajeLogR2dbcRepository.
 * Tests the persistence layer against a real PostgreSQL database.
 * All tests use idCompania=99999 and idSucursal=1.
 *
 * Valid canal values: 'whatsapp','email','llamada'
 * Valid estado values: 'pendiente','enviado','fallido'
 * idPlantilla is nullable for these tests.
 */
@DisplayName("MensajeLogR2dbcRepository")
class MensajeLogR2dbcRepositoryIT extends BaseIntegrationTest {

    private static final Integer ID_COMPANIA = 99999;
    private static final Integer ID_SUCURSAL = 1;

    @Autowired
    private MensajeLogR2dbcRepository mensajeRepository;

    private Integer idCliente;

    @BeforeEach
    void setup() {
        // Setup FK: persona -> cliente
        idCliente = insertarClienteCore(ID_COMPANIA, ID_SUCURSAL);
    }

    // ── TC-MENSAJE-REPO-001 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("inserta nuevo mensaje log correctamente")
        void save_nuevoMensajeLog_seGuardaCorrectamente() {
            MensajeLogEntity entity = MensajeLogEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idPlantilla(null)
                    .tipo("ausencia_2d")
                    .canal("whatsapp")
                    .contenido("Vuelve pronto")
                    .estado("pendiente")
                    .fechaProgramada(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(mensajeRepository.save(entity))
                    .assertNext(saved -> {
                        assert saved.getId() != null : "ID should be auto-generated";
                        assert saved.getIdCompania().equals(ID_COMPANIA);
                        assert saved.getIdCliente().equals(idCliente);
                        assert saved.getTipo().equals("ausencia_2d");
                        assert saved.getCanal().equals("whatsapp");
                        assert saved.getEstado().equals("pendiente");
                    })
                    .verifyComplete();
        }
    }

    // ── TC-MENSAJE-REPO-002 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("retorna mensaje log cuando existe")
        void findById_cuandoExiste_retornaMensajeLog() {
            MensajeLogEntity entity = MensajeLogEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idPlantilla(null)
                    .tipo("recuperacion_5d")
                    .canal("email")
                    .contenido("Recuperación 5 días")
                    .estado("enviado")
                    .fechaProgramada(OffsetDateTime.now())
                    .fechaEnvio(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(mensajeRepository.save(entity)
                    .flatMap(saved -> mensajeRepository.findById(saved.getId())))
                    .assertNext(found -> {
                        assert found.getTipo().equals("recuperacion_5d");
                        assert found.getCanal().equals("email");
                        assert found.getEstado().equals("enviado");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna empty cuando no existe")
        void findById_cuandoNoExiste_retornaEmpty() {
            StepVerifier.create(mensajeRepository.findById(999999L))
                    .verifyComplete();
        }
    }

    // ── TC-MENSAJE-REPO-003 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByFiltros")
    class FindByFiltros {

        @Test
        @DisplayName("retorna todos los mensajes de una compañía sin filtros adicionales")
        void findByFiltros_soloIdCompania_retornaTodos() {
            MensajeLogEntity entity1 = MensajeLogEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idPlantilla(null)
                    .tipo("ausencia_2d")
                    .canal("whatsapp")
                    .contenido("Mensaje 1")
                    .estado("pendiente")
                    .fechaProgramada(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            MensajeLogEntity entity2 = MensajeLogEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idPlantilla(null)
                    .tipo("vencimiento_3d")
                    .canal("email")
                    .contenido("Mensaje 2")
                    .estado("enviado")
                    .fechaProgramada(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(mensajeRepository.save(entity1)
                    .then(mensajeRepository.save(entity2))
                    .thenMany(mensajeRepository.findByFiltros(ID_COMPANIA, null, null, null, null)))
                    .expectNextCount(2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("filtra por idCliente específico")
        void findByFiltros_conFiltroCliente_retornaSoloDelCliente() {
            Integer idCliente2 = insertarClienteCore(ID_COMPANIA, ID_SUCURSAL);

            MensajeLogEntity entity1 = MensajeLogEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idPlantilla(null)
                    .tipo("ausencia_2d")
                    .canal("whatsapp")
                    .contenido("Para cliente 1")
                    .estado("pendiente")
                    .fechaProgramada(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            MensajeLogEntity entity2 = MensajeLogEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente2)
                    .idPlantilla(null)
                    .tipo("ausencia_2d")
                    .canal("email")
                    .contenido("Para cliente 2")
                    .estado("pendiente")
                    .fechaProgramada(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(mensajeRepository.save(entity1)
                    .then(mensajeRepository.save(entity2))
                    .thenMany(mensajeRepository.findByFiltros(ID_COMPANIA, idCliente, null, null, null)))
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("filtra por tipo específico")
        void findByFiltros_conFiltroTipo_retornaSoloDelTipo() {
            MensajeLogEntity entity1 = MensajeLogEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idPlantilla(null)
                    .tipo("recuperacion_10d")
                    .canal("whatsapp")
                    .contenido("Recuperación 10")
                    .estado("pendiente")
                    .fechaProgramada(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            MensajeLogEntity entity2 = MensajeLogEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idPlantilla(null)
                    .tipo("vencimiento_hoy")
                    .canal("email")
                    .contenido("Vencimiento hoy")
                    .estado("pendiente")
                    .fechaProgramada(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(mensajeRepository.save(entity1)
                    .then(mensajeRepository.save(entity2))
                    .thenMany(mensajeRepository.findByFiltros(ID_COMPANIA, null, "recuperacion_10d", null, null)))
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("filtra por estado específico")
        void findByFiltros_conFiltroEstado_retornaSoloDelEstado() {
            MensajeLogEntity entity1 = MensajeLogEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idPlantilla(null)
                    .tipo("ausencia_2d")
                    .canal("whatsapp")
                    .contenido("Pendiente")
                    .estado("pendiente")
                    .fechaProgramada(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            MensajeLogEntity entity2 = MensajeLogEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idPlantilla(null)
                    .tipo("ausencia_2d")
                    .canal("email")
                    .contenido("Enviado")
                    .estado("enviado")
                    .fechaProgramada(OffsetDateTime.now())
                    .fechaEnvio(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(mensajeRepository.save(entity1)
                    .then(mensajeRepository.save(entity2))
                    .thenMany(mensajeRepository.findByFiltros(ID_COMPANIA, null, null, "enviado", null)))
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("filtra por fecha_programada >= desde")
        void findByFiltros_conFiltroDesde_retornaSoloPosterioresADesde() {
            OffsetDateTime ahora = OffsetDateTime.now();
            OffsetDateTime hace1Hora = ahora.minusHours(1);
            OffsetDateTime despuesDe1Hora = ahora.plusHours(1);

            MensajeLogEntity entity1 = MensajeLogEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idPlantilla(null)
                    .tipo("ausencia_2d")
                    .canal("whatsapp")
                    .contenido("Antiguo")
                    .estado("pendiente")
                    .fechaProgramada(hace1Hora)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            MensajeLogEntity entity2 = MensajeLogEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idPlantilla(null)
                    .tipo("ausencia_2d")
                    .canal("email")
                    .contenido("Nuevo")
                    .estado("pendiente")
                    .fechaProgramada(despuesDe1Hora)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(mensajeRepository.save(entity1)
                    .then(mensajeRepository.save(entity2))
                    .thenMany(mensajeRepository.findByFiltros(ID_COMPANIA, null, null, null, ahora)))
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna lista vacía cuando no hay mensajes que coincidan")
        void findByFiltros_sinCoincidencias_retornaVacio() {
            StepVerifier.create(mensajeRepository.findByFiltros(ID_COMPANIA, 99999, null, null, null))
                    .verifyComplete();
        }
    }

    // ── TC-MENSAJE-REPO-004 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("countByClienteAndTipoDesde")
    class CountByClienteAndTipoDesde {

        @Test
        @DisplayName("cuenta mensajes de cliente, tipo y fecha específica")
        void countByClienteAndTipoDesde_conMensajes_retornaConteo() {
            OffsetDateTime ahora = OffsetDateTime.now();
            OffsetDateTime hace2Horas = ahora.minusHours(2);

            MensajeLogEntity entity1 = MensajeLogEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idPlantilla(null)
                    .tipo("recuperacion_15d")
                    .canal("whatsapp")
                    .contenido("Mensaje 1")
                    .estado("pendiente")
                    .fechaProgramada(hace2Horas)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            MensajeLogEntity entity2 = MensajeLogEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idPlantilla(null)
                    .tipo("recuperacion_15d")
                    .canal("email")
                    .contenido("Mensaje 2")
                    .estado("pendiente")
                    .fechaProgramada(ahora)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(mensajeRepository.save(entity1)
                    .then(mensajeRepository.save(entity2))
                    .then(mensajeRepository.countByClienteAndTipoDesde(
                            idCliente, "recuperacion_15d", hace2Horas)))
                    .assertNext(count -> { assert count >= 2L; })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna cero cuando no hay mensajes que coincidan")
        void countByClienteAndTipoDesde_sinMensajes_retornaCero() {
            OffsetDateTime futura = OffsetDateTime.now().plusHours(24);

            StepVerifier.create(mensajeRepository.countByClienteAndTipoDesde(
                    idCliente, "vencimiento_hoy", futura))
                    .assertNext(count -> { assert count == 0L; })
                    .verifyComplete();
        }

        @Test
        @DisplayName("no cuenta mensajes anteriores a fecha especificada")
        void countByClienteAndTipoDesde_conMensajesAntiguos_noLosCuenta() {
            OffsetDateTime ahora = OffsetDateTime.now();
            OffsetDateTime hace3Horas = ahora.minusHours(3);
            OffsetDateTime hace1Hora = ahora.minusHours(1);

            MensajeLogEntity entity = MensajeLogEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idPlantilla(null)
                    .tipo("motivacional")
                    .canal("whatsapp")
                    .contenido("Mensaje antiguo")
                    .estado("pendiente")
                    .fechaProgramada(hace3Horas)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(mensajeRepository.save(entity)
                    .then(mensajeRepository.countByClienteAndTipoDesde(
                            idCliente, "motivacional", hace1Hora)))
                    .assertNext(count -> { assert count == 0L; })
                    .verifyComplete();
        }
    }

    // ── TC-MENSAJE-REPO-005 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("soft-delete (eliminado)")
    class SoftDelete {

        @Test
        @DisplayName("mensajes eliminados no aparecen en listados")
        void softDelete_mensajeEliminado_noAparece() {
            MensajeLogEntity entity = MensajeLogEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idPlantilla(null)
                    .tipo("ausencia_2d")
                    .canal("whatsapp")
                    .contenido("A eliminar")
                    .estado("pendiente")
                    .fechaProgramada(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(mensajeRepository.save(entity)
                    .flatMap(saved -> {
                        saved.setEliminado(true);
                        return mensajeRepository.save(saved);
                    })
                    .thenMany(mensajeRepository.findByFiltros(ID_COMPANIA, idCliente, null, null, null)))
                    .verifyComplete();
        }
    }
}
