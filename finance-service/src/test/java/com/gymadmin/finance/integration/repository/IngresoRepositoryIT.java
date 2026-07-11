package com.gymadmin.finance.integration.repository;

import com.gymadmin.finance.BaseIntegrationTest;
import com.gymadmin.finance.infrastructure.adapter.out.persistence.entity.IngresoEntity;
import com.gymadmin.finance.infrastructure.adapter.out.persistence.repository.IngresoR2dbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Integration tests para {@link IngresoR2dbcRepository} contra el PostgreSQL
 * local real (perfil {@code test}, cargado vía {@code .env}).
 * Solo cubre el camino feliz de los métodos que tocan la BD.
 */
@DisplayName("IngresoR2dbcRepository — IT contra Postgres local")
class IngresoRepositoryIT extends BaseIntegrationTest {

    private static final Integer COMPANIA = 900;
    private static final Integer SUCURSAL = 1;

    @Autowired
    private IngresoR2dbcRepository repository;

    private Integer idCategoria;

    @BeforeEach
    void seedCategoria() {
        idCategoria = insertarCategoriaIngreso(COMPANIA, SUCURSAL, "Mensualidades", true);
    }

    @Nested
    @DisplayName("findByFilters")
    class FindByFilters {

        @Test
        @DisplayName("TC-ING-DB-001: retorna los ingresos de la compañía ordenados por fecha desc")
        void retornaIngresosOrdenados() {
            insertarIngreso(COMPANIA, SUCURSAL, idCategoria, new BigDecimal("100.00"), LocalDate.of(2026, 1, 10));
            insertarIngreso(COMPANIA, SUCURSAL, idCategoria, new BigDecimal("200.00"), LocalDate.of(2026, 1, 20));

            StepVerifier.create(repository.findByFilters(COMPANIA, null, null, null, 50, 0))
                    .assertNext(i -> { assert i.getFecha().equals(LocalDate.of(2026, 1, 20)); })
                    .assertNext(i -> { assert i.getFecha().equals(LocalDate.of(2026, 1, 10)); })
                    .verifyComplete();
        }

        @Test
        @DisplayName("TC-ING-DB-002: filtra por rango de fechas")
        void filtraPorRangoDeFechas() {
            insertarIngreso(COMPANIA, SUCURSAL, idCategoria, new BigDecimal("100.00"), LocalDate.of(2026, 1, 5));
            insertarIngreso(COMPANIA, SUCURSAL, idCategoria, new BigDecimal("200.00"), LocalDate.of(2026, 2, 15));

            StepVerifier.create(repository.findByFilters(
                            COMPANIA, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), null, 50, 0))
                    .assertNext(i -> { assert i.getFecha().equals(LocalDate.of(2026, 2, 15)); })
                    .verifyComplete();
        }

        @Test
        @DisplayName("TC-ING-DB-003: filtra por categoría")
        void filtraPorCategoria() {
            Integer otraCategoria = insertarCategoriaIngreso(COMPANIA, SUCURSAL, "Otros", true);
            insertarIngreso(COMPANIA, SUCURSAL, idCategoria, new BigDecimal("100.00"), LocalDate.now());
            insertarIngreso(COMPANIA, SUCURSAL, otraCategoria, new BigDecimal("300.00"), LocalDate.now());

            StepVerifier.create(repository.findByFilters(COMPANIA, null, null, otraCategoria, 50, 0))
                    .assertNext(i -> { assert i.getIdCategoria().equals(otraCategoria); })
                    .verifyComplete();
        }

        @Test
        @DisplayName("TC-ING-DB-004: aplica limit y offset para paginación")
        void aplicaLimitYOffset() {
            insertarIngreso(COMPANIA, SUCURSAL, idCategoria, new BigDecimal("10.00"), LocalDate.of(2026, 3, 1));
            insertarIngreso(COMPANIA, SUCURSAL, idCategoria, new BigDecimal("20.00"), LocalDate.of(2026, 3, 2));
            insertarIngreso(COMPANIA, SUCURSAL, idCategoria, new BigDecimal("30.00"), LocalDate.of(2026, 3, 3));

            // Ordenado por fecha desc: [3-3, 3-2, 3-1]. limit=1 offset=1 → la del 3-2.
            StepVerifier.create(repository.findByFilters(COMPANIA, null, null, null, 1, 1))
                    .assertNext(i -> { assert i.getFecha().equals(LocalDate.of(2026, 3, 2)); })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("countByFilters")
    class CountByFilters {

        @Test
        @DisplayName("TC-ING-DB-005: cuenta los ingresos que cumplen el filtro")
        void cuentaIngresos() {
            insertarIngreso(COMPANIA, SUCURSAL, idCategoria, new BigDecimal("100.00"), LocalDate.now());
            insertarIngreso(COMPANIA, SUCURSAL, idCategoria, new BigDecimal("200.00"), LocalDate.now());

            StepVerifier.create(repository.countByFilters(COMPANIA, null, null, null))
                    .expectNext(2L)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("sumByFilters")
    class SumByFilters {

        @Test
        @DisplayName("TC-ING-DB-006: suma los montos que cumplen el filtro")
        void sumaMontos() {
            insertarIngreso(COMPANIA, SUCURSAL, idCategoria, new BigDecimal("100.50"), LocalDate.now());
            insertarIngreso(COMPANIA, SUCURSAL, idCategoria, new BigDecimal("200.25"), LocalDate.now());

            StepVerifier.create(repository.sumByFilters(COMPANIA, null, null, null))
                    .assertNext(sum -> { assert sum.compareTo(new BigDecimal("300.75")) == 0; })
                    .verifyComplete();
        }

        @Test
        @DisplayName("TC-ING-DB-007: retorna 0 cuando no hay ingresos que sumar")
        void retornaCeroSinIngresos() {
            StepVerifier.create(repository.sumByFilters(COMPANIA, null, null, null))
                    .assertNext(sum -> { assert sum.compareTo(BigDecimal.ZERO) == 0; })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("TC-ING-DB-008: persiste un nuevo ingreso y genera su id")
        void persisteIngreso() {
            IngresoEntity nuevo = IngresoEntity.builder()
                    .idCompania(COMPANIA)
                    .idSucursal(SUCURSAL)
                    .idCategoria(idCategoria)
                    .monto(new BigDecimal("150.00"))
                    .descripcion("Pago mensualidad")
                    .fecha(LocalDate.now())
                    .eliminado(false)
                    .build();

            Integer generatedId = repository.save(nuevo)
                    .map(saved -> {
                        assert saved.getId() != null;
                        assert saved.getMonto().compareTo(new BigDecimal("150.00")) == 0;
                        return saved.getId();
                    })
                    .block();

            // El auditing pone creacion_usuario='sistema', fuera del teardown selectivo → limpieza explícita.
            repository.deleteById(generatedId).block();
        }
    }
}
