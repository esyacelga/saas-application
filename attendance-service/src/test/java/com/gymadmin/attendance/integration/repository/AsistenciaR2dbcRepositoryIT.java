package com.gymadmin.attendance.integration.repository;

import com.gymadmin.attendance.BaseIntegrationTest;
import com.gymadmin.attendance.infrastructure.adapter.out.persistence.entity.AsistenciaEntity;
import com.gymadmin.attendance.infrastructure.adapter.out.persistence.repository.AsistenciaR2dbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Integration tests for AsistenciaR2dbcRepository.
 * Tests the persistence layer against a real PostgreSQL database.
 * All tests use idCompania=99999 and idSucursal=1.
 */
@DisplayName("AsistenciaR2dbcRepository")
class AsistenciaR2dbcRepositoryIT extends BaseIntegrationTest {

    private static final Integer ID_COMPANIA = 99999;
    private static final Integer ID_SUCURSAL = 1;

    @Autowired
    private AsistenciaR2dbcRepository asistenciaRepository;

    private Integer idCliente;
    private Integer idMembresia;

    @BeforeEach
    void setup() {
        // Setup FK chain: persona -> cliente -> membresia
        idCliente = insertarClienteCore(ID_COMPANIA, ID_SUCURSAL);
        idMembresia = insertarMembresia(idCliente, ID_COMPANIA);
    }

    // ── TC-ASI-REPO-001 ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("inserta nueva asistencia correctamente")
        void save_nuevaAsistencia_seGuardaCorrectamente() {
            AsistenciaEntity entity = AsistenciaEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idMembresia(idMembresia)
                    .fecha(LocalDate.now())
                    .horaEntrada(LocalTime.of(8, 0))
                    .metodoRegistro("qr_cliente")
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(asistenciaRepository.save(entity))
                    .assertNext(saved -> {
                        assert saved.getId() != null : "ID should be auto-generated";
                        assert saved.getIdCompania().equals(ID_COMPANIA);
                        assert saved.getIdCliente().equals(idCliente);
                        assert saved.getMetodoRegistro().equals("qr_cliente");
                        assert saved.getEliminado() == false;
                    })
                    .verifyComplete();
        }
    }

    // ── TC-ASI-REPO-002 ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("retorna asistencia cuando existe")
        void findById_cuandoExiste_retornaAsistencia() {
            AsistenciaEntity entity = AsistenciaEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idMembresia(idMembresia)
                    .fecha(LocalDate.now())
                    .horaEntrada(LocalTime.of(9, 30))
                    .metodoRegistro("manual")
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(asistenciaRepository.save(entity)
                    .flatMap(saved -> asistenciaRepository.findById(saved.getId())))
                    .assertNext(found -> {
                        assert found.getIdCliente().equals(idCliente);
                        assert found.getMetodoRegistro().equals("manual");
                        assert found.getFecha().equals(LocalDate.now());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna empty cuando no existe")
        void findById_cuandoNoExiste_retornaEmpty() {
            StepVerifier.create(asistenciaRepository.findById(999999L))
                    .verifyComplete();
        }
    }

    // ── TC-ASI-REPO-003 ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByClienteAndPeriodo")
    class FindByClienteAndPeriodo {

        @Test
        @DisplayName("retorna asistencias en rango de fechas sin filtro de membresía")
        void findByClienteAndPeriodo_sinFiltroMembresia_retornaAsistencias() {
            LocalDate hoy = LocalDate.now();
            LocalDate ayer = hoy.minusDays(1);

            AsistenciaEntity ayer_entity = AsistenciaEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idMembresia(idMembresia)
                    .fecha(ayer)
                    .horaEntrada(LocalTime.of(7, 0))
                    .metodoRegistro("biometrico")
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            AsistenciaEntity hoy_entity = AsistenciaEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idMembresia(idMembresia)
                    .fecha(hoy)
                    .horaEntrada(LocalTime.of(15, 0))
                    .metodoRegistro("app_cliente")
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(asistenciaRepository.save(ayer_entity)
                    .then(asistenciaRepository.save(hoy_entity))
                    .thenMany(asistenciaRepository.findByClienteAndPeriodo(
                            idCliente, ID_COMPANIA, ayer, hoy, null)))
                    .expectNextCount(2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna asistencias con filtro de membresía específica")
        void findByClienteAndPeriodo_conFiltroMembresia_retornaAsistenciasFiltradasPorMembresia() {
            LocalDate hoy = LocalDate.now();
            Integer idCliente2 = insertarClienteCore(ID_COMPANIA, ID_SUCURSAL);
            Integer idMembresia2 = insertarMembresia(idCliente2, ID_COMPANIA);

            AsistenciaEntity entity1 = AsistenciaEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idMembresia(idMembresia)
                    .fecha(hoy)
                    .horaEntrada(LocalTime.of(8, 0))
                    .metodoRegistro("qr_cliente")
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(asistenciaRepository.save(entity1)
                    .thenMany(asistenciaRepository.findByClienteAndPeriodo(
                            idCliente, ID_COMPANIA, hoy, hoy, idMembresia)))
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna lista vacía cuando no hay asistencias en rango")
        void findByClienteAndPeriodo_sinAsistenciasEnRango_retornaVacio() {
            LocalDate futura = LocalDate.now().plusDays(30);

            StepVerifier.create(asistenciaRepository.findByClienteAndPeriodo(
                    idCliente, ID_COMPANIA, futura, futura, null))
                    .verifyComplete();
        }
    }

    // ── TC-ASI-REPO-004 ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByClienteUltimos30Dias")
    class FindByClienteUltimos30Dias {

        @Test
        @DisplayName("retorna asistencias desde fecha especificada")
        void findByClienteUltimos30Dias_conAsistencias_retornaListaOrdenada() {
            LocalDate hoy = LocalDate.now();
            LocalDate hace5 = hoy.minusDays(5);

            AsistenciaEntity entity1 = AsistenciaEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idMembresia(idMembresia)
                    .fecha(hace5)
                    .horaEntrada(LocalTime.of(7, 0))
                    .metodoRegistro("biometrico")
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            AsistenciaEntity entity2 = AsistenciaEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idMembresia(idMembresia)
                    .fecha(hoy)
                    .horaEntrada(LocalTime.of(16, 0))
                    .metodoRegistro("app_cliente")
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(asistenciaRepository.save(entity1)
                    .then(asistenciaRepository.save(entity2))
                    .thenMany(asistenciaRepository.findByClienteUltimos30Dias(
                            idCliente, ID_COMPANIA, hace5)))
                    .expectNextCount(2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna lista vacía cuando no hay asistencias recientes")
        void findByClienteUltimos30Dias_sinAsistencias_retornaVacio() {
            LocalDate futura = LocalDate.now().plusDays(10);

            StepVerifier.create(asistenciaRepository.findByClienteUltimos30Dias(
                    idCliente, ID_COMPANIA, futura))
                    .verifyComplete();
        }
    }

    // ── TC-ASI-REPO-005 ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByCompaniaAndFecha")
    class FindByCompaniaAndFecha {

        @Test
        @DisplayName("retorna asistencias de la compañía en una fecha específica sin filtro sucursal")
        void findByCompaniaAndFecha_sinFiltroSucursal_retornaAsistenciasDelDia() {
            LocalDate hoy = LocalDate.now();

            AsistenciaEntity entity = AsistenciaEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idMembresia(idMembresia)
                    .fecha(hoy)
                    .horaEntrada(LocalTime.of(8, 15))
                    .metodoRegistro("qr_cliente")
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(asistenciaRepository.save(entity)
                    .thenMany(asistenciaRepository.findByCompaniaAndFecha(
                            ID_COMPANIA, null, hoy)))
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna asistencias con filtro de sucursal específica")
        void findByCompaniaAndFecha_conFiltroSucursal_retornaAsistenciasDelDiaYSucursal() {
            LocalDate hoy = LocalDate.now();

            AsistenciaEntity entity = AsistenciaEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idMembresia(idMembresia)
                    .fecha(hoy)
                    .horaEntrada(LocalTime.of(9, 0))
                    .metodoRegistro("manual")
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(asistenciaRepository.save(entity)
                    .thenMany(asistenciaRepository.findByCompaniaAndFecha(
                            ID_COMPANIA, ID_SUCURSAL, hoy)))
                    .expectNextCount(1)
                    .verifyComplete();
        }
    }

    // ── TC-ASI-REPO-006 ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("findUltimaAsistencia")
    class FindUltimaAsistencia {

        @Test
        @DisplayName("retorna fecha de última asistencia cuando existe")
        void findUltimaAsistencia_conAsistencias_retornaFechaMaxima() {
            LocalDate hace3 = LocalDate.now().minusDays(3);
            LocalDate ayer = LocalDate.now().minusDays(1);

            AsistenciaEntity entity1 = AsistenciaEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idMembresia(idMembresia)
                    .fecha(hace3)
                    .horaEntrada(LocalTime.of(7, 0))
                    .metodoRegistro("biometrico")
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            AsistenciaEntity entity2 = AsistenciaEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idMembresia(idMembresia)
                    .fecha(ayer)
                    .horaEntrada(LocalTime.of(17, 0))
                    .metodoRegistro("app_cliente")
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(asistenciaRepository.save(entity1)
                    .then(asistenciaRepository.save(entity2))
                    .then(asistenciaRepository.findUltimaAsistencia(idCliente, ID_COMPANIA)))
                    .assertNext(fecha -> { assert fecha.equals(ayer); })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna empty cuando no hay asistencias")
        void findUltimaAsistencia_sinAsistencias_retornaEmpty() {
            StepVerifier.create(asistenciaRepository.findUltimaAsistencia(idCliente, ID_COMPANIA))
                    .verifyComplete();
        }
    }

    // ── TC-ASI-REPO-007 ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("countByCompaniaAndPeriodo")
    class CountByCompaniaAndPeriodo {

        @Test
        @DisplayName("cuenta asistencias en rango de fechas")
        void countByCompaniaAndPeriodo_conAsistenciasEnRango_retornaConnteo() {
            LocalDate desde = LocalDate.now().minusDays(5);
            LocalDate hasta = LocalDate.now();

            AsistenciaEntity entity = AsistenciaEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idMembresia(idMembresia)
                    .fecha(LocalDate.now())
                    .horaEntrada(LocalTime.of(8, 0))
                    .metodoRegistro("qr_cliente")
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(asistenciaRepository.save(entity)
                    .then(asistenciaRepository.countByCompaniaAndPeriodo(ID_COMPANIA, desde, hasta)))
                    .assertNext(count -> { assert count >= 1L; })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna cero cuando no hay asistencias en rango")
        void countByCompaniaAndPeriodo_sinAsistenciasEnRango_retornaCero() {
            LocalDate desde = LocalDate.now().plusDays(30);
            LocalDate hasta = LocalDate.now().plusDays(60);

            StepVerifier.create(asistenciaRepository.countByCompaniaAndPeriodo(
                    ID_COMPANIA, desde, hasta))
                    .assertNext(count -> { assert count == 0L; })
                    .verifyComplete();
        }
    }

    // ── TC-ASI-REPO-008 ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByPersonaUltimos30Dias")
    class FindByPersonaUltimos30Dias {

        @Test
        @DisplayName("retorna asistencias de persona desde fecha especificada")
        void findByPersonaUltimos30Dias_conAsistencias_retornaAsistenciasDelPersona() {
            // Query idPersona from core.clientes
            Integer idPersona = databaseClient.sql(
                    "SELECT id_persona FROM core.clientes WHERE id = :id")
                    .bind("id", idCliente)
                    .map(row -> row.get(0, Integer.class))
                    .one()
                    .block();

            LocalDate desde = LocalDate.now().minusDays(10);

            AsistenciaEntity entity = AsistenciaEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idMembresia(idMembresia)
                    .fecha(LocalDate.now())
                    .horaEntrada(LocalTime.of(10, 0))
                    .metodoRegistro("biometrico")
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(asistenciaRepository.save(entity)
                    .thenMany(asistenciaRepository.findByPersonaUltimos30Dias(
                            idPersona.longValue(), ID_COMPANIA, desde)))
                    .expectNextCount(1)
                    .verifyComplete();
        }
    }

    // ── TC-ASI-REPO-009 ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByPersonaAndPeriodo")
    class FindByPersonaAndPeriodo {

        @Test
        @DisplayName("retorna asistencias de persona en rango de fechas")
        void findByPersonaAndPeriodo_conAsistencias_retornaAsistenciasEnRango() {
            Integer idPersona = databaseClient.sql(
                    "SELECT id_persona FROM core.clientes WHERE id = :id")
                    .bind("id", idCliente)
                    .map(row -> row.get(0, Integer.class))
                    .one()
                    .block();

            LocalDate desde = LocalDate.now().minusDays(5);
            LocalDate hasta = LocalDate.now();

            AsistenciaEntity entity = AsistenciaEntity.builder()
                    .idCompania(ID_COMPANIA)
                    .idSucursal(ID_SUCURSAL)
                    .idCliente(idCliente)
                    .idMembresia(idMembresia)
                    .fecha(LocalDate.now())
                    .horaEntrada(LocalTime.of(14, 0))
                    .metodoRegistro("app_cliente")
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(asistenciaRepository.save(entity)
                    .thenMany(asistenciaRepository.findByPersonaAndPeriodo(
                            idPersona.longValue(), ID_COMPANIA, desde, hasta)))
                    .expectNextCount(1)
                    .verifyComplete();
        }
    }
}
