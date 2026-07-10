package com.gymadmin.platform.integration.repository;

import com.gymadmin.platform.BaseIntegrationTest;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.CompaniaEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.PagoPendienteValidacionEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.PlanEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.CompaniaR2dbcRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.PagoPendienteValidacionR2dbcRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.PlanR2dbcRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Integration tests for PagoPendienteValidacionR2dbcRepository.
 * Tests the persistence layer against a real PostgreSQL database.
 * All tests use the tenant.pagos_pendientes_validacion table.
 * FK requirements:
 *  - idCompania must reference an existing tenant.companias.id (NOT NULL)
 *  - idPlanDestino must reference an existing saas.planes.id (NOT NULL in DB)
 */
@DisplayName("PagoPendienteValidacionR2dbcRepository")
class PagoPendienteValidacionR2dbcRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private PagoPendienteValidacionR2dbcRepository pagoPendienteRepository;

    @Autowired
    private CompaniaR2dbcRepository companiaRepository;

    @Autowired
    private PlanR2dbcRepository planRepository;

    // ── TC-PLATFORM-REPO-035 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("inserta nuevo pago pendiente de validación correctamente")
        void save_nuevoPagoPendiente_seGuardaCorrectamente() {
            CompaniaEntity compania = companiaRepository.save(CompaniaEntity.builder()
                    .nombre("Gym Save Pago Pendiente")
                    .ruc("5555444433332")
                    .activo(true)
                    .trialUsado(false)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build()).block();

            PlanEntity plan = planRepository.save(PlanEntity.builder()
                    .nombre("Plan Pago Pendiente")
                    .descripcion("Plan de prueba")
                    .precioMensual(new BigDecimal("99.99"))
                    .activo(true)
                    .codigo("PAGO-PEND-001")
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

            String hashIdempotencia = UUID.randomUUID().toString();
            PagoPendienteValidacionEntity entity = PagoPendienteValidacionEntity.builder()
                    .idCompania(compania.getId())
                    .idPlanDestino(plan.getId())
                    .monto(new BigDecimal("500.00"))
                    .moneda("USD")
                    .fechaReporte(OffsetDateTime.now())
                    .fechaTransferencia(LocalDate.now())
                    .comprobanteUrl("https://cloudinary.test/comprobante-001.pdf")
                    .comprobanteHash("hash-comprobante-001")
                    .bancoOrigen("Banco Test")
                    .referencia("REF-TEST-001")
                    .hashIdempotencia(hashIdempotencia)
                    .estado("pendiente")
                    .activacionProgramada(false)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(pagoPendienteRepository.save(entity))
                    .assertNext(saved -> {
                        assert saved.getId() != null : "ID should be auto-generated";
                        assert saved.getIdCompania().equals(compania.getId());
                        assert saved.getIdPlanDestino().equals(plan.getId());
                        assert saved.getMonto().equals(new BigDecimal("500.00"));
                        assert saved.getEstado().equals("pendiente");
                        assert saved.getHashIdempotencia().equals(hashIdempotencia);
                        assert saved.getCreacionFecha() != null : "creacionFecha should be auto-populated";
                    })
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-036 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("retorna pago pendiente cuando existe")
        void findById_cuandoExiste_retornaPagoPendiente() {
            CompaniaEntity compania = companiaRepository.save(CompaniaEntity.builder()
                    .nombre("Gym FindById Pago Pendiente")
                    .ruc("4444333322221")
                    .activo(true)
                    .trialUsado(false)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build()).block();

            PlanEntity plan = planRepository.save(PlanEntity.builder()
                    .nombre("Plan FindById Pago Pendiente")
                    .descripcion("Plan de prueba")
                    .precioMensual(new BigDecimal("99.99"))
                    .activo(true)
                    .codigo("FINDID-PAGO-001")
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

            String hashIdempotencia = UUID.randomUUID().toString();
            PagoPendienteValidacionEntity entity = PagoPendienteValidacionEntity.builder()
                    .idCompania(compania.getId())
                    .idPlanDestino(plan.getId())
                    .monto(new BigDecimal("750.50"))
                    .moneda("USD")
                    .fechaReporte(OffsetDateTime.now())
                    .fechaTransferencia(LocalDate.now())
                    .comprobanteUrl("https://cloudinary.test/comprobante-find.pdf")
                    .bancoOrigen("Banco Búsqueda")
                    .referencia("REF-FIND-001")
                    .hashIdempotencia(hashIdempotencia)
                    .estado("aprobado")
                    .activacionProgramada(true)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(pagoPendienteRepository.save(entity)
                    .flatMap(saved -> pagoPendienteRepository.findById(saved.getId())))
                    .assertNext(found -> {
                        assert found.getIdCompania().equals(compania.getId());
                        assert found.getIdPlanDestino().equals(plan.getId());
                        assert found.getMonto().equals(new BigDecimal("750.50"));
                        assert found.getEstado().equals("aprobado");
                        assert found.getHashIdempotencia().equals(hashIdempotencia);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna empty cuando no existe")
        void findById_cuandoNoExiste_retornaEmpty() {
            StepVerifier.create(pagoPendienteRepository.findById(999999L))
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-037 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findByHashIdempotencia")
    class FindByHashIdempotencia {

        @Test
        @DisplayName("retorna pago pendiente cuando existe por hash (estado pendiente o aprobado)")
        void findByHashIdempotencia_conEstadoPendiente_retornaPago() {
            CompaniaEntity compania = companiaRepository.save(CompaniaEntity.builder()
                    .nombre("Gym Hash Idempotencia")
                    .ruc("3333222211110")
                    .activo(true)
                    .trialUsado(false)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build()).block();

            PlanEntity plan = planRepository.save(PlanEntity.builder()
                    .nombre("Plan Hash Idempotencia")
                    .descripcion("Plan de prueba")
                    .precioMensual(new BigDecimal("99.99"))
                    .activo(true)
                    .codigo("HASH-PAGO-001")
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

            String hashIdempotencia = "HASH_UNIQUE_" + UUID.randomUUID().toString();
            PagoPendienteValidacionEntity entity = PagoPendienteValidacionEntity.builder()
                    .idCompania(compania.getId())
                    .idPlanDestino(plan.getId())
                    .monto(new BigDecimal("1000.00"))
                    .moneda("USD")
                    .fechaReporte(OffsetDateTime.now())
                    .fechaTransferencia(LocalDate.now())
                    .comprobanteUrl("https://cloudinary.test/comprobante-hash.pdf")
                    .bancoOrigen("Banco Hash")
                    .referencia("REF-HASH-001")
                    .hashIdempotencia(hashIdempotencia)
                    .estado("pendiente")
                    .activacionProgramada(false)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(pagoPendienteRepository.save(entity)
                    .then(pagoPendienteRepository.findByHashIdempotencia(hashIdempotencia)))
                    .assertNext(found -> {
                        assert found.getHashIdempotencia().equals(hashIdempotencia);
                        assert found.getEstado().equals("pendiente");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna pago cuando hash existe con estado aprobado")
        void findByHashIdempotencia_conEstadoAprobado_retornaPago() {
            CompaniaEntity compania = companiaRepository.save(CompaniaEntity.builder()
                    .nombre("Gym Hash Aprobado")
                    .ruc("2222111100009")
                    .activo(true)
                    .trialUsado(false)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build()).block();

            PlanEntity plan = planRepository.save(PlanEntity.builder()
                    .nombre("Plan Hash Aprobado")
                    .descripcion("Plan de prueba")
                    .precioMensual(new BigDecimal("99.99"))
                    .activo(true)
                    .codigo("APROBADO-PAGO-001")
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

            String hashIdempotencia = "HASH_APROBADO_" + UUID.randomUUID().toString();
            PagoPendienteValidacionEntity entity = PagoPendienteValidacionEntity.builder()
                    .idCompania(compania.getId())
                    .idPlanDestino(plan.getId())
                    .monto(new BigDecimal("2000.00"))
                    .moneda("USD")
                    .fechaReporte(OffsetDateTime.now())
                    .fechaTransferencia(LocalDate.now())
                    .comprobanteUrl("https://cloudinary.test/comprobante-aprobado.pdf")
                    .bancoOrigen("Banco Aprobado")
                    .referencia("REF-APROBADO-001")
                    .hashIdempotencia(hashIdempotencia)
                    .estado("aprobado")
                    .activacionProgramada(true)
                    .aprobadoPor(1L)
                    .fechaAprobacion(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(pagoPendienteRepository.save(entity)
                    .then(pagoPendienteRepository.findByHashIdempotencia(hashIdempotencia)))
                    .assertNext(found -> {
                        assert found.getHashIdempotencia().equals(hashIdempotencia);
                        assert found.getEstado().equals("aprobado");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna empty cuando hash no existe")
        void findByHashIdempotencia_cuandoNoExiste_retornaEmpty() {
            StepVerifier.create(pagoPendienteRepository.findByHashIdempotencia("NOEXISTE"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("no retorna pago con estado rechazado")
        void findByHashIdempotencia_conEstadoRechazado_noRetorna() {
            CompaniaEntity compania = companiaRepository.save(CompaniaEntity.builder()
                    .nombre("Gym Hash Rechazado")
                    .ruc("1111000099998")
                    .activo(true)
                    .trialUsado(false)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build()).block();

            PlanEntity plan = planRepository.save(PlanEntity.builder()
                    .nombre("Plan Hash Rechazado")
                    .descripcion("Plan de prueba")
                    .precioMensual(new BigDecimal("99.99"))
                    .activo(true)
                    .codigo("RECHAZADO-PAGO-001")
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

            String hashIdempotencia = "HASH_RECHAZADO_" + UUID.randomUUID().toString();
            PagoPendienteValidacionEntity entity = PagoPendienteValidacionEntity.builder()
                    .idCompania(compania.getId())
                    .idPlanDestino(plan.getId())
                    .monto(new BigDecimal("300.00"))
                    .moneda("USD")
                    .fechaReporte(OffsetDateTime.now())
                    .fechaTransferencia(LocalDate.now())
                    .comprobanteUrl("https://cloudinary.test/comprobante-rechazado.pdf")
                    .bancoOrigen("Banco Rechazado")
                    .referencia("REF-RECHAZADO-001")
                    .hashIdempotencia(hashIdempotencia)
                    .estado("rechazado")
                    .motivoRechazo("Fondos insuficientes")
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(pagoPendienteRepository.save(entity)
                    .then(pagoPendienteRepository.findByHashIdempotencia(hashIdempotencia)))
                    .verifyComplete();
        }
    }
}
