package com.gymadmin.finance.integration.repository;

import com.gymadmin.finance.BaseIntegrationTest;
import com.gymadmin.finance.infrastructure.adapter.out.persistence.entity.CategoriaIngresoEntity;
import com.gymadmin.finance.infrastructure.adapter.out.persistence.repository.CategoriaIngresoR2dbcRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Integration tests para {@link CategoriaIngresoR2dbcRepository} contra el
 * PostgreSQL local real (perfil {@code test}, cargado vía {@code .env}).
 * Solo cubre el camino feliz de los métodos que tocan la BD.
 */
@DisplayName("CategoriaIngresoR2dbcRepository — IT contra Postgres local")
class CategoriaIngresoRepositoryIT extends BaseIntegrationTest {

    private static final Integer COMPANIA = 900;
    private static final Integer SUCURSAL = 1;

    @Autowired
    private CategoriaIngresoR2dbcRepository repository;

    @Nested
    @DisplayName("findByIdCompaniaAndEliminadoFalse")
    class FindByCompania {

        @Test
        @DisplayName("TC-CATING-DB-001: retorna las categorías no eliminadas de la compañía ordenadas por nombre")
        void retornaCategoriasDeLaCompania() {
            insertarCategoriaIngreso(COMPANIA, SUCURSAL, "Ventas", true);
            insertarCategoriaIngreso(COMPANIA, SUCURSAL, "Mensualidades", true);

            StepVerifier.create(repository.findByIdCompaniaAndEliminadoFalse(COMPANIA))
                    .assertNext(c -> { assert "Mensualidades".equals(c.getNombre()); })
                    .assertNext(c -> { assert "Ventas".equals(c.getNombre()); })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByIdCompaniaAndIdSucursalAndEliminadoFalse")
    class FindByCompaniaAndSucursal {

        @Test
        @DisplayName("TC-CATING-DB-002: retorna solo las categorías de la sucursal indicada")
        void retornaCategoriasDeLaSucursal() {
            insertarCategoriaIngreso(COMPANIA, SUCURSAL, "Sucursal 1", true);
            insertarCategoriaIngreso(COMPANIA, 2, "Sucursal 2", true);

            StepVerifier.create(repository.findByIdCompaniaAndIdSucursalAndEliminadoFalse(COMPANIA, SUCURSAL))
                    .assertNext(c -> { assert "Sucursal 1".equals(c.getNombre()); })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByIdAndIdCompaniaAndEliminadoFalse")
    class FindById {

        @Test
        @DisplayName("TC-CATING-DB-003: retorna la categoría por id dentro de la compañía")
        void retornaCategoriaPorId() {
            Integer id = insertarCategoriaIngreso(COMPANIA, SUCURSAL, "Otros", true);

            StepVerifier.create(repository.findByIdAndIdCompaniaAndEliminadoFalse(id, COMPANIA))
                    .assertNext(c -> {
                        assert c.getId().equals(id);
                        assert "Otros".equals(c.getNombre());
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("existsByIdCategoria")
    class ExistsByCategoria {

        @Test
        @DisplayName("TC-CATING-DB-004: true cuando hay ingresos activos que referencian la categoría")
        void trueCuandoHayIngresos() {
            Integer idCategoria = insertarCategoriaIngreso(COMPANIA, SUCURSAL, "Con ingresos", true);
            insertarIngreso(COMPANIA, SUCURSAL, idCategoria, new BigDecimal("50.00"), LocalDate.now());

            StepVerifier.create(repository.existsByIdCategoria(idCategoria))
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("TC-CATING-DB-005: false cuando ningún ingreso referencia la categoría")
        void falseCuandoNoHayIngresos() {
            Integer idCategoria = insertarCategoriaIngreso(COMPANIA, SUCURSAL, "Sin ingresos", true);

            StepVerifier.create(repository.existsByIdCategoria(idCategoria))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("TC-CATING-DB-006: persiste una nueva categoría y genera su id")
        void persisteCategoria() {
            CategoriaIngresoEntity nueva = CategoriaIngresoEntity.builder()
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
