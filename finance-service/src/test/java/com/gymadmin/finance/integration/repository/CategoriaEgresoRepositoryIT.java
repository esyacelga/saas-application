package com.gymadmin.finance.integration.repository;

import com.gymadmin.finance.BaseIntegrationTest;
import com.gymadmin.finance.infrastructure.adapter.out.persistence.entity.CategoriaEgresoEntity;
import com.gymadmin.finance.infrastructure.adapter.out.persistence.repository.CategoriaEgresoR2dbcRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Integration tests para {@link CategoriaEgresoR2dbcRepository} contra el
 * PostgreSQL local real (perfil {@code test}, cargado vía {@code .env}).
 * Solo cubre el camino feliz de los métodos que tocan la BD.
 */
@DisplayName("CategoriaEgresoR2dbcRepository — IT contra Postgres local")
class CategoriaEgresoRepositoryIT extends BaseIntegrationTest {

    private static final Integer COMPANIA = 900;
    private static final Integer SUCURSAL = 1;

    @Autowired
    private CategoriaEgresoR2dbcRepository repository;

    @Nested
    @DisplayName("findByIdCompaniaAndEliminadoFalse")
    class FindByCompania {

        @Test
        @DisplayName("TC-CATEGR-DB-001: retorna las categorías no eliminadas de la compañía ordenadas por nombre")
        void retornaCategoriasDeLaCompania() {
            insertarCategoriaEgreso(COMPANIA, SUCURSAL, "Servicios", true);
            insertarCategoriaEgreso(COMPANIA, SUCURSAL, "Nómina", true);

            StepVerifier.create(repository.findByIdCompaniaAndEliminadoFalse(COMPANIA))
                    .assertNext(c -> { assert "Nómina".equals(c.getNombre()); })
                    .assertNext(c -> { assert "Servicios".equals(c.getNombre()); })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByIdCompaniaAndIdSucursalAndEliminadoFalse")
    class FindByCompaniaAndSucursal {

        @Test
        @DisplayName("TC-CATEGR-DB-002: retorna solo las categorías de la sucursal indicada")
        void retornaCategoriasDeLaSucursal() {
            insertarCategoriaEgreso(COMPANIA, SUCURSAL, "Sucursal 1", true);
            insertarCategoriaEgreso(COMPANIA, 2, "Sucursal 2", true);

            StepVerifier.create(repository.findByIdCompaniaAndIdSucursalAndEliminadoFalse(COMPANIA, SUCURSAL))
                    .assertNext(c -> { assert "Sucursal 1".equals(c.getNombre()); })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByIdAndIdCompaniaAndEliminadoFalse")
    class FindById {

        @Test
        @DisplayName("TC-CATEGR-DB-003: retorna la categoría por id dentro de la compañía")
        void retornaCategoriaPorId() {
            Integer id = insertarCategoriaEgreso(COMPANIA, SUCURSAL, "Mantenimiento", true);

            StepVerifier.create(repository.findByIdAndIdCompaniaAndEliminadoFalse(id, COMPANIA))
                    .assertNext(c -> {
                        assert c.getId().equals(id);
                        assert "Mantenimiento".equals(c.getNombre());
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("existsByIdCategoria")
    class ExistsByCategoria {

        @Test
        @DisplayName("TC-CATEGR-DB-004: true cuando hay egresos activos que referencian la categoría")
        void trueCuandoHayEgresos() {
            Integer idCategoria = insertarCategoriaEgreso(COMPANIA, SUCURSAL, "Con egresos", true);
            insertarEgreso(COMPANIA, SUCURSAL, idCategoria, new BigDecimal("75.00"), LocalDate.now());

            StepVerifier.create(repository.existsByIdCategoria(idCategoria))
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("TC-CATEGR-DB-005: false cuando ningún egreso referencia la categoría")
        void falseCuandoNoHayEgresos() {
            Integer idCategoria = insertarCategoriaEgreso(COMPANIA, SUCURSAL, "Sin egresos", true);

            StepVerifier.create(repository.existsByIdCategoria(idCategoria))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("TC-CATEGR-DB-006: persiste una nueva categoría y genera su id")
        void persisteCategoria() {
            CategoriaEgresoEntity nueva = CategoriaEgresoEntity.builder()
                    .idCompania(COMPANIA)
                    .idSucursal(SUCURSAL)
                    .nombre("Nueva categoría")
                    .activo(true)
                    .eliminado(false)
                    .build();

            Integer generatedId = repository.save(nueva)
                    .map(saved -> {
                        assert saved.getId() != null;
                        assert "Nueva categoría".equals(saved.getNombre());
                        return saved.getId();
                    })
                    .block();

            // El auditing pone creacion_usuario='sistema', fuera del teardown selectivo → limpieza explícita.
            repository.deleteById(generatedId).block();
        }
    }
}
