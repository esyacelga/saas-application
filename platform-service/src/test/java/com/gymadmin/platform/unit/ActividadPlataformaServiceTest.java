package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.ActividadPlataformaService;
import com.gymadmin.platform.domain.model.ActividadPlataforma;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase.ListarQuery;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase.RegistrarCommand;
import com.gymadmin.platform.domain.port.out.ActividadPlataformaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActividadPlataformaService — registro y consulta de auditoría")
class ActividadPlataformaServiceTest {

    @Mock
    private ActividadPlataformaRepository repository;

    @InjectMocks
    private ActividadPlataformaService service;

    private ActividadPlataforma buildActividad(Long id, String tipoEvento, String modulo) {
        ActividadPlataforma a = new ActividadPlataforma();
        a.setId(id);
        a.setTipoEvento(tipoEvento);
        a.setModulo(modulo);
        a.setEntidadId(100L);
        a.setEntidadNombre("Entidad de prueba");
        a.setDetalle("Detalle de la operación");
        a.setUsuario("usuario-test@example.com");
        a.setFecha(OffsetDateTime.now());
        return a;
    }

    private ListarQuery buildQuery() {
        return new ListarQuery("PLANES", "CREAR", "2026-01-01", "2026-12-31", 0, 20);
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("listar")
    class Listar {

        @Test
        @DisplayName("retorna todas las actividades que coinciden con la query")
        void retornaActividadesCoincidentes() {
            ListarQuery query = buildQuery();
            ActividadPlataforma a1 = buildActividad(1L, "CREAR", "PLANES");
            ActividadPlataforma a2 = buildActividad(2L, "ACTUALIZAR", "PLANES");
            when(repository.findAll(query)).thenReturn(Flux.just(a1, a2));

            StepVerifier.create(service.listar(query))
                    .assertNext(a -> {
                        assertThat(a.getId()).isEqualTo(1L);
                        assertThat(a.getTipoEvento()).isEqualTo("CREAR");
                    })
                    .assertNext(a -> {
                        assertThat(a.getId()).isEqualTo(2L);
                        assertThat(a.getTipoEvento()).isEqualTo("ACTUALIZAR");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna flux vacío cuando no hay actividades para la query")
        void retornaFluxVacioCuandoNoHayActividades() {
            ListarQuery query = buildQuery();
            when(repository.findAll(query)).thenReturn(Flux.empty());

            StepVerifier.create(service.listar(query))
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("contar")
    class Contar {

        @Test
        @DisplayName("retorna el número de actividades que coinciden con la query")
        void retornaConteoDeActividades() {
            ListarQuery query = buildQuery();
            when(repository.count(query)).thenReturn(Mono.just(42L));

            StepVerifier.create(service.contar(query))
                    .assertNext(count -> assertThat(count).isEqualTo(42L))
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna cero cuando no hay actividades")
        void retornaCeroCuandoNoHayActividades() {
            ListarQuery query = buildQuery();
            when(repository.count(query)).thenReturn(Mono.just(0L));

            StepVerifier.create(service.contar(query))
                    .assertNext(count -> assertThat(count).isEqualTo(0L))
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("registrar")
    class Registrar {

        @Test
        @DisplayName("guarda la actividad con los datos del command y fecha actual")
        void registraActividadExitosamente() {
            RegistrarCommand command = new RegistrarCommand(
                    "CREAR",
                    "PLANES",
                    5L,
                    "Plan Básico",
                    "Se creó un nuevo plan",
                    "admin@gymadmin.com"
            );

            when(repository.save(any(ActividadPlataforma.class))).thenReturn(Mono.empty());

            StepVerifier.create(service.registrar(command))
                    .verifyComplete();
        }

        @Test
        @DisplayName("registra actividad sin entidadId ni detalle (campos opcionales null)")
        void registraActividadConCamposOpcionales() {
            RegistrarCommand command = new RegistrarCommand(
                    "LOGIN",
                    "AUTH",
                    null,
                    null,
                    null,
                    "operador@gymadmin.com"
            );

            when(repository.save(any(ActividadPlataforma.class))).thenReturn(Mono.empty());

            StepVerifier.create(service.registrar(command))
                    .verifyComplete();
        }
    }
}
