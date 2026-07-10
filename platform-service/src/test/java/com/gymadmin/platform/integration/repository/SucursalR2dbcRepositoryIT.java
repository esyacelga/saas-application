package com.gymadmin.platform.integration.repository;

import com.gymadmin.platform.BaseIntegrationTest;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.CompaniaEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.SucursalEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.CompaniaR2dbcRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.SucursalR2dbcRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

/**
 * Integration tests for SucursalR2dbcRepository.
 * Tests the persistence layer against a real PostgreSQL database.
 * All tests use the tenant.sucursales table.
 * FK requirement: idCompania must reference an existing tenant.companias.id.
 */
@DisplayName("SucursalR2dbcRepository")
class SucursalR2dbcRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private SucursalR2dbcRepository sucursalRepository;

    @Autowired
    private CompaniaR2dbcRepository companiaRepository;

    // ── TC-PLATFORM-REPO-012 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("inserta nueva sucursal correctamente")
        void save_nuevaSucursal_seGuardaCorrectamente() {
            CompaniaEntity compania = CompaniaEntity.builder()
                    .nombre("Gym Save Test")
                    .ruc("9999111122223")
                    .activo(true)
                    .trialUsado(false)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(companiaRepository.save(compania)
                    .flatMap(savedCompania -> {
                        SucursalEntity sucursal = SucursalEntity.builder()
                                .idCompania(savedCompania.getId())
                                .nombre("Sucursal Centro")
                                .direccion("Calle Principal 123")
                                .esPrincipal(true)
                                .activo(true)
                                .qrToken("TOKEN123ABC")
                                .creacionUsuario("test")
                                .eliminado(false)
                                .build();
                        return sucursalRepository.save(sucursal);
                    }))
                    .assertNext(saved -> {
                        assert saved.getId() != null : "ID should be auto-generated";
                        assert saved.getNombre().equals("Sucursal Centro");
                        assert saved.getDireccion().equals("Calle Principal 123");
                        assert saved.getEsPrincipal() == true;
                        assert saved.getActivo() == true;
                        assert saved.getCreacionFecha() != null : "creacionFecha should be auto-populated";
                    })
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-013 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("retorna sucursal cuando existe")
        void findById_cuandoExiste_retornaSucursal() {
            CompaniaEntity compania = CompaniaEntity.builder()
                    .nombre("Gym FindById Test")
                    .ruc("8888777766665")
                    .activo(true)
                    .trialUsado(false)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(companiaRepository.save(compania)
                    .flatMap(savedCompania -> {
                        SucursalEntity sucursal = SucursalEntity.builder()
                                .idCompania(savedCompania.getId())
                                .nombre("Sucursal Búsqueda")
                                .direccion("Avenida Secundaria 456")
                                .esPrincipal(false)
                                .activo(true)
                                .qrToken("TOKEN456DEF")
                                .creacionUsuario("test")
                                .eliminado(false)
                                .build();
                        return sucursalRepository.save(sucursal)
                                .flatMap(saved -> sucursalRepository.findById(saved.getId()));
                    }))
                    .assertNext(found -> {
                        assert found.getNombre().equals("Sucursal Búsqueda");
                        assert found.getDireccion().equals("Avenida Secundaria 456");
                        assert found.getActivo() == true;
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna empty cuando no existe")
        void findById_cuandoNoExiste_retornaEmpty() {
            StepVerifier.create(sucursalRepository.findById(999999L))
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-014 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findByIdCompania")
    class FindByIdCompania {

        @Test
        @DisplayName("retorna todas las sucursales de una compañía")
        void findByIdCompania_conSucursales_retornaLista() {
            CompaniaEntity compania = CompaniaEntity.builder()
                    .nombre("Gym Multi Sucursales")
                    .ruc("7777666655554")
                    .activo(true)
                    .trialUsado(false)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(companiaRepository.save(compania)
                    .flatMapMany(savedCompania -> {
                        SucursalEntity sucursal1 = SucursalEntity.builder()
                                .idCompania(savedCompania.getId())
                                .nombre("Sucursal 1")
                                .direccion("Dirección 1")
                                .esPrincipal(true)
                                .activo(true)
                                .qrToken("TOKEN001")
                                .creacionUsuario("test")
                                .eliminado(false)
                                .build();

                        SucursalEntity sucursal2 = SucursalEntity.builder()
                                .idCompania(savedCompania.getId())
                                .nombre("Sucursal 2")
                                .direccion("Dirección 2")
                                .esPrincipal(false)
                                .activo(true)
                                .qrToken("TOKEN002")
                                .creacionUsuario("test")
                                .eliminado(false)
                                .build();

                        return sucursalRepository.save(sucursal1)
                                .then(sucursalRepository.save(sucursal2))
                                .thenMany(sucursalRepository.findByIdCompania(savedCompania.getId()));
                    }))
                    .expectNextCount(2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna lista vacía cuando compañía no tiene sucursales")
        void findByIdCompania_sinSucursales_retornaVacio() {
            StepVerifier.create(sucursalRepository.findByIdCompania(999999L))
                    .verifyComplete();
        }
    }

    // ── TC-PLATFORM-REPO-015 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findByQrToken")
    class FindByQrToken {

        @Test
        @DisplayName("retorna sucursal cuando existe por QR token")
        void findByQrToken_cuandoExiste_retornaSucursal() {
            String qrToken = "UNIQUE_QR_TOKEN_XYZ";
            CompaniaEntity compania = CompaniaEntity.builder()
                    .nombre("Gym QR Test")
                    .ruc("6666555544443")
                    .activo(true)
                    .trialUsado(false)
                    .creacionUsuario("test")
                    .eliminado(false)
                    .build();

            StepVerifier.create(companiaRepository.save(compania)
                    .flatMap(savedCompania -> {
                        SucursalEntity sucursal = SucursalEntity.builder()
                                .idCompania(savedCompania.getId())
                                .nombre("Sucursal QR")
                                .direccion("Calle QR")
                                .esPrincipal(true)
                                .activo(true)
                                .qrToken(qrToken)
                                .creacionUsuario("test")
                                .eliminado(false)
                                .build();
                        return sucursalRepository.save(sucursal)
                                .then(sucursalRepository.findByQrToken(qrToken));
                    }))
                    .assertNext(found -> {
                        assert found.getQrToken().equals(qrToken);
                        assert found.getNombre().equals("Sucursal QR");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna empty cuando QR token no existe")
        void findByQrToken_cuandoNoExiste_retornaEmpty() {
            StepVerifier.create(sucursalRepository.findByQrToken("NOEXISTE"))
                    .verifyComplete();
        }
    }
}
