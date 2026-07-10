package com.gymadmin.platform.integration.repository;

import com.gymadmin.platform.BaseIntegrationTest;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.CompaniaEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.CompaniaPlanEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.NotificacionSuscripcionEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.PlanEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.CompaniaR2dbcRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.CompaniaPlanR2dbcRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.NotificacionR2dbcRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.PlanR2dbcRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Integration tests for NotificacionR2dbcRepository.
 * Tests the persistence layer against a real PostgreSQL database.
 * All tests use the tenant.notificaciones_suscripcion table.
 * FK requirement: idCompaniaPlan must reference an existing tenant.compania_planes.id
 */
@DisplayName("NotificacionR2dbcRepository")
class NotificacionR2dbcRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private NotificacionR2dbcRepository notificacionRepository;

    @Autowired
    private CompaniaR2dbcRepository companiaRepository;

    @Autowired
    private PlanR2dbcRepository planRepository;

    @Autowired
    private CompaniaPlanR2dbcRepository companiaPlanRepository;

    private CompaniaPlanEntity crearCompaniaPlan(String nombreCompania, String rucCompania,
                                                  String nombrePlan, String codigoPlan) {
        CompaniaEntity compania = companiaRepository.save(CompaniaEntity.builder()
                .nombre(nombreCompania)
                .ruc(rucCompania)
                .activo(true)
                .trialUsado(false)
                .creacionUsuario("test")
                .eliminado(false)
                .build()).block();

        PlanEntity plan = planRepository.save(PlanEntity.builder()
                .nombre(nombrePlan)
                .descripcion("Plan de prueba")
                .precioMensual(new BigDecimal("99.99"))
                .activo(true)
                .codigo(codigoPlan)
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

        return companiaPlanRepository.save(CompaniaPlanEntity.builder()
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
                .build()).block();
    }

    // ── TC-PLATFORM-REPO-031 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("inserta nueva notificación de suscripción correctamente")
        void save_nuevaNotificacion_seGuardaCorrectamente() {
            CompaniaPlanEntity companiaPlan = crearCompaniaPlan(
                    "Gym Save Notif", "5555444433332",
                    "Plan Save Notif", "SAVE-NOTIF-001");

            NotificacionSuscripcionEntity entity = NotificacionSuscripcionEntity.builder()
                    .idCompania(companiaPlan.getIdCompania())
                    .idCompaniaPlan(companiaPlan.getId())
                    .tipo("vencimiento")
                    .diasAntes(5)
                    .canal("email")
                    .estado("pendiente")
                    .intentos(0)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(notificacionRepository.save(entity))
                    .assertNext(saved -> {
                        assert saved.getId() != null : "ID should be auto-generated";
                        assert saved.getIdCompaniaPlan().equals(companiaPlan.getId());
                        assert saved.getDiasAntes() == 5;
                        assert saved.getCanal().equals("email");
                        assert saved.getEstado().equals("pendiente");
                        assert saved.getCreacionFecha() != null : "creacionFecha should be auto-populated";
                    })
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-032 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("retorna notificación cuando existe")
        void findById_cuandoExiste_retornaNotificacion() {
            CompaniaPlanEntity companiaPlan = crearCompaniaPlan(
                    "Gym FindById Notif", "4444333322221",
                    "Plan FindById Notif", "FIND-NOTIF-001");

            NotificacionSuscripcionEntity entity = NotificacionSuscripcionEntity.builder()
                    .idCompania(companiaPlan.getIdCompania())
                    .idCompaniaPlan(companiaPlan.getId())
                    .tipo("renovacion")
                    .diasAntes(3)
                    .canal("whatsapp")
                    .estado("pendiente")
                    .intentos(1)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(notificacionRepository.save(entity)
                    .flatMap(saved -> notificacionRepository.findById(saved.getId())))
                    .assertNext(found -> {
                        assert found.getIdCompaniaPlan().equals(companiaPlan.getId());
                        assert found.getTipo().equals("renovacion");
                        assert found.getCanal().equals("whatsapp");
                        assert found.getEstado().equals("pendiente");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna empty cuando no existe")
        void findById_cuandoNoExiste_retornaEmpty() {
            StepVerifier.create(notificacionRepository.findById(999999L))
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-033 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findByIdCompaniaPlan")
    class FindByIdCompaniaPlan {

        @Test
        @DisplayName("retorna notificaciones de una suscripción")
        void findByIdCompaniaPlan_conNotificaciones_retornaLista() {
            CompaniaPlanEntity companiaPlan = crearCompaniaPlan(
                    "Gym Multiple Notif", "3333222211110",
                    "Plan Multiple Notif", "MULTI-NOTIF-001");

            NotificacionSuscripcionEntity notif1 = NotificacionSuscripcionEntity.builder()
                    .idCompania(companiaPlan.getIdCompania())
                    .idCompaniaPlan(companiaPlan.getId())
                    .tipo("vencimiento_pronto")
                    .diasAntes(7)
                    .canal("email")
                    .estado("pendiente")
                    .intentos(0)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            NotificacionSuscripcionEntity notif2 = NotificacionSuscripcionEntity.builder()
                    .idCompania(companiaPlan.getIdCompania())
                    .idCompaniaPlan(companiaPlan.getId())
                    .tipo("vencimiento_urgente")
                    .diasAntes(1)
                    .canal("banner")
                    .estado("pendiente")
                    .intentos(0)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(notificacionRepository.save(notif1)
                    .then(notificacionRepository.save(notif2))
                    .thenMany(notificacionRepository.findByIdCompaniaPlan(companiaPlan.getId())))
                    .expectNextCount(2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna lista vacía cuando no hay notificaciones")
        void findByIdCompaniaPlan_sinNotificaciones_retornaVacio() {
            StepVerifier.create(notificacionRepository.findByIdCompaniaPlan(999999L))
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-034 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("existsByIdCompaniaPlanAndDiasAntes")
    class ExistsByIdCompaniaPlanAndDiasAntes {

        @Test
        @DisplayName("retorna true cuando existe notificación con ese diasAntes")
        void existsByIdCompaniaPlanAndDiasAntes_cuandoExiste_retornaTrue() {
            CompaniaPlanEntity companiaPlan = crearCompaniaPlan(
                    "Gym Exists Check Notif", "2222111100009",
                    "Plan Exists Check Notif", "EXISTS-NOTIF-001");

            NotificacionSuscripcionEntity entity = NotificacionSuscripcionEntity.builder()
                    .idCompania(companiaPlan.getIdCompania())
                    .idCompaniaPlan(companiaPlan.getId())
                    .tipo("dias_check")
                    .diasAntes(10)
                    .canal("email")
                    .estado("pendiente")
                    .intentos(0)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(notificacionRepository.save(entity)
                    .then(notificacionRepository.existsByIdCompaniaPlanAndDiasAntes(
                            companiaPlan.getId(), 10)))
                    .assertNext(exists -> {
                        assert exists == true : "Should exist";
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna false cuando no existe notificación con ese diasAntes")
        void existsByIdCompaniaPlanAndDiasAntes_cuandoNoExiste_retornaFalse() {
            StepVerifier.create(notificacionRepository.existsByIdCompaniaPlanAndDiasAntes(
                            999999L, 99))
                    .assertNext(exists -> {
                        assert exists == false : "Should not exist";
                    })
                    .verifyComplete();
        }
    }
}
