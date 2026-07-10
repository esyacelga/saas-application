package com.gymadmin.platform.integration.repository;

import com.gymadmin.platform.BaseIntegrationTest;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.CompaniaEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.CompaniaR2dbcRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.ConfigNotifR2dbcRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

/**
 * Integration tests for ConfigNotifR2dbcRepository.
 * Tests the persistence layer against a real PostgreSQL database.
 * All tests use the tenant.config_notif_suscripcion table.
 *
 * IMPORTANTE: idCompania es @Id (PK) y se asigna manualmente (= id de una compañía
 * existente). Como la entidad NO implementa Persistable, R2DBC interpreta un id no
 * nulo como UPDATE y {@code save()} no inserta nada. Por eso la fila de configuración
 * se inserta con SQL crudo vía {@code databaseClient} y los tests ejercitan los
 * finders/delete del repositorio (que es lo que realmente toca la BD).
 */
@DisplayName("ConfigNotifR2dbcRepository")
class ConfigNotifR2dbcRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private ConfigNotifR2dbcRepository configNotifRepository;

    @Autowired
    private CompaniaR2dbcRepository companiaRepository;

    /** Inserta una fila de config_notif_suscripcion vía SQL crudo (evita el UPDATE de save()). */
    private void insertarConfigNotif(Long idCompania, int diasAntes, String canal, boolean activo) {
        databaseClient.sql("""
                INSERT INTO tenant.config_notif_suscripcion
                    (id_compania, dias_antes, canal, activo, creacion_usuario, eliminado)
                VALUES (:idCompania, :diasAntes, :canal, :activo, 'test', false)
                """)
                .bind("idCompania", idCompania)
                .bind("diasAntes", diasAntes)
                .bind("canal", canal)
                .bind("activo", activo)
                .then()
                .block();
    }

    private Long crearCompania(String nombre, String ruc) {
        CompaniaEntity compania = companiaRepository.save(CompaniaEntity.builder()
                .nombre(nombre)
                .ruc(ruc)
                .activo(true)
                .trialUsado(false)
                .creacionUsuario("test")
                .eliminado(false)
                .build()).block();
        return compania.getId();
    }

    // ── TC-PLATFORM-REPO-028 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findByIdCompania")
    class FindByIdCompania {

        @Test
        @DisplayName("retorna la configuración de notificación de una compañía")
        void findByIdCompania_conConfiguracion_retorna() {
            Long idCompania = crearCompania("Gym ConfigNotif", "5555444433332");
            insertarConfigNotif(idCompania, 5, "email", true);

            StepVerifier.create(configNotifRepository.findByIdCompania(idCompania))
                    .assertNext(found -> {
                        assert found.getIdCompania().equals(idCompania);
                        assert found.getDiasAntes() == 5;
                        assert found.getCanal().equals("email");
                        assert found.getActivo() == true;
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna lista vacía cuando la compañía no tiene configuraciones")
        void findByIdCompania_sinConfiguraciones_retornaVacio() {
            StepVerifier.create(configNotifRepository.findByIdCompania(999999L))
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-029 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteByIdCompania")
    class DeleteByIdCompania {

        @Test
        @DisplayName("elimina la configuración de una compañía")
        void deleteByIdCompania_eliminaConfiguracion_correctamente() {
            Long idCompania = crearCompania("Gym ConfigNotif Delete", "3333222211110");
            insertarConfigNotif(idCompania, 5, "ambos", true);

            StepVerifier.create(configNotifRepository.deleteByIdCompania(idCompania)
                    .thenMany(configNotifRepository.findByIdCompania(idCompania)))
                    .verifyComplete();
        }
    }
}
