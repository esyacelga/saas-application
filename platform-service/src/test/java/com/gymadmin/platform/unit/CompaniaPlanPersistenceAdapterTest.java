package com.gymadmin.platform.unit;

import com.gymadmin.platform.domain.exception.EstadoInvalidoException;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.adapter.CompaniaPlanPersistenceAdapter;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.CompaniaPlanEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.CompaniaPlanR2dbcRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-SAAS-001 (sección 5bis) — máquina de estados de {@link CompaniaPlan.Estado}.
 * <p>
 * El adapter valida transiciones prohibidas antes de persistir un UPDATE:
 * <ul>
 *   <li>CANCELADO → cualquier otro estado es inválido (terminal).</li>
 *   <li>REEMPLAZADA → cualquier otro estado es inválido (terminal).</li>
 *   <li>* → PROGRAMADO en UPDATE es inválido (PROGRAMADO solo puede crearse vía INSERT).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CompaniaPlanPersistenceAdapter — transiciones de estado")
class CompaniaPlanPersistenceAdapterTest {

    @Mock
    private CompaniaPlanR2dbcRepository repository;

    @InjectMocks
    private CompaniaPlanPersistenceAdapter adapter;

    private CompaniaPlanEntity buildEntity(Long id, String estadoDb) {
        CompaniaPlanEntity entity = new CompaniaPlanEntity();
        entity.setId(id);
        entity.setIdCompania(10L);
        entity.setIdPlan(1L);
        entity.setFechaInicio(LocalDate.of(2026, 1, 1));
        entity.setFechaFin(LocalDate.of(2026, 12, 31));
        entity.setEstado(estadoDb);
        return entity;
    }

    private CompaniaPlan buildDomain(Long id, CompaniaPlan.Estado nuevoEstado) {
        CompaniaPlan cp = new CompaniaPlan();
        cp.setId(id);
        cp.setIdCompania(10L);
        cp.setIdPlan(1L);
        cp.setFechaInicio(LocalDate.of(2026, 1, 1));
        cp.setFechaFin(LocalDate.of(2026, 12, 31));
        cp.setEstado(nuevoEstado);
        return cp;
    }

    @Nested
    @DisplayName("save (UPDATE con validación de transiciones)")
    class SaveConTransicion {

        @Test
        @DisplayName("CANCELADO → ACTIVO lanza EstadoInvalidoException — no persiste")
        void canceladoNoPuedeVolverAActivo() {
            CompaniaPlanEntity previous = buildEntity(42L, "cancelado");
            CompaniaPlan next = buildDomain(42L, CompaniaPlan.Estado.ACTIVO);

            when(repository.findById(42L)).thenReturn(Mono.just(previous));

            StepVerifier.create(adapter.save(next))
                    .expectErrorSatisfies(err -> {
                        assertThat(err).isInstanceOf(EstadoInvalidoException.class);
                        assertThat(err.getMessage())
                                .contains("CANCELADO")
                                .contains("terminal")
                                .contains("42");
                    })
                    .verify();

            verify(repository, never()).save(any(CompaniaPlanEntity.class));
        }

        @Test
        @DisplayName("REEMPLAZADA → ACTIVO lanza EstadoInvalidoException")
        void reemplazadaNoPuedeVolverAActivo() {
            CompaniaPlanEntity previous = buildEntity(43L, "reemplazada");
            CompaniaPlan next = buildDomain(43L, CompaniaPlan.Estado.ACTIVO);

            when(repository.findById(43L)).thenReturn(Mono.just(previous));

            StepVerifier.create(adapter.save(next))
                    .expectErrorSatisfies(err -> {
                        assertThat(err).isInstanceOf(EstadoInvalidoException.class);
                        assertThat(err.getMessage()).contains("REEMPLAZADA").contains("terminal");
                    })
                    .verify();

            verify(repository, never()).save(any(CompaniaPlanEntity.class));
        }

        @Test
        @DisplayName("ACTIVO → PROGRAMADO en UPDATE lanza EstadoInvalidoException")
        void programadoSoloEnInsert() {
            CompaniaPlanEntity previous = buildEntity(44L, "activo");
            CompaniaPlan next = buildDomain(44L, CompaniaPlan.Estado.PROGRAMADO);

            when(repository.findById(44L)).thenReturn(Mono.just(previous));

            StepVerifier.create(adapter.save(next))
                    .expectErrorSatisfies(err -> {
                        assertThat(err).isInstanceOf(EstadoInvalidoException.class);
                        assertThat(err.getMessage()).contains("PROGRAMADO").contains("INSERT");
                    })
                    .verify();

            verify(repository, never()).save(any(CompaniaPlanEntity.class));
        }

        @Test
        @DisplayName("ACTIVO → EN_GRACIA es una transición válida — persiste")
        void transicionValidaPersiste() {
            CompaniaPlanEntity previous = buildEntity(45L, "activo");
            CompaniaPlan next = buildDomain(45L, CompaniaPlan.Estado.EN_GRACIA);
            CompaniaPlanEntity persisted = buildEntity(45L, "en_gracia");

            when(repository.findById(45L)).thenReturn(Mono.just(previous));
            when(repository.save(any(CompaniaPlanEntity.class))).thenReturn(Mono.just(persisted));

            StepVerifier.create(adapter.save(next))
                    .assertNext(cp -> {
                        assertThat(cp.getId()).isEqualTo(45L);
                        assertThat(cp.getEstado()).isEqualTo(CompaniaPlan.Estado.EN_GRACIA);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("INSERT (id null) con estado PROGRAMADO no dispara la validación")
        void insertConProgramadoEsValido() {
            CompaniaPlan next = buildDomain(null, CompaniaPlan.Estado.PROGRAMADO);
            CompaniaPlanEntity persisted = buildEntity(99L, "programado");

            when(repository.save(any(CompaniaPlanEntity.class))).thenReturn(Mono.just(persisted));

            StepVerifier.create(adapter.save(next))
                    .assertNext(cp -> {
                        assertThat(cp.getId()).isEqualTo(99L);
                        assertThat(cp.getEstado()).isEqualTo(CompaniaPlan.Estado.PROGRAMADO);
                    })
                    .verifyComplete();

            verify(repository, never()).findById(any(Long.class));
        }
    }
}
