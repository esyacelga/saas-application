package com.gymadmin.attendance.unit;

import com.gymadmin.attendance.application.service.PlantillaMensajeService;
import com.gymadmin.attendance.domain.model.PlantillaMensaje;
import com.gymadmin.attendance.domain.port.in.PlantillaMensajeUseCase.ActualizarPlantillaCommand;
import com.gymadmin.attendance.domain.port.in.PlantillaMensajeUseCase.CrearPlantillaCommand;
import com.gymadmin.attendance.domain.port.out.PlantillaMensajeRepository;
import com.gymadmin.attendance.infrastructure.exception.ConflictException;
import com.gymadmin.attendance.infrastructure.exception.ForbiddenException;
import com.gymadmin.attendance.infrastructure.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlantillaMensajeService — Pruebas unitarias")
class PlantillaMensajeServiceTest {

    @Mock
    private PlantillaMensajeRepository plantillaRepository;

    @InjectMocks
    private PlantillaMensajeService service;

    // -------------------------------------------------------------------------
    // listar
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("listar — obtener plantillas por compañía")
    class Listar {

        @Test
        @DisplayName("debe retornar flux con las plantillas de la compañía")
        void retornaPlantillasExistentes() {
            PlantillaMensaje p1 = plantilla(1, 10, "ausencia_2d", "Plantilla A", true);
            PlantillaMensaje p2 = plantilla(2, 10, "vencimiento_3d", "Plantilla B", true);
            when(plantillaRepository.findByCompania(10)).thenReturn(Flux.just(p1, p2));

            StepVerifier.create(service.listar(10))
                    .expectNext(p1)
                    .expectNext(p2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("debe retornar flux vacío cuando la compañía no tiene plantillas")
        void retornaVacioCuandoNoHayPlantillas() {
            when(plantillaRepository.findByCompania(99)).thenReturn(Flux.empty());

            StepVerifier.create(service.listar(99))
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    // crear
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("crear — nueva plantilla de mensaje")
    class Crear {

        @Test
        @DisplayName("debe guardar la plantilla con activo=true y eliminado=null (valor por defecto)")
        void creaPlantillaConActivoTrue() {
            CrearPlantillaCommand cmd = new CrearPlantillaCommand(10, 1, "ausencia_2d", "Mi Plantilla", "Hola {nombre}");

            PlantillaMensaje guardada = plantilla(5, 10, "ausencia_2d", "Mi Plantilla", true);
            guardada.setContenido("Hola {nombre}");

            ArgumentCaptor<PlantillaMensaje> captor = ArgumentCaptor.forClass(PlantillaMensaje.class);
            when(plantillaRepository.save(captor.capture())).thenReturn(Mono.just(guardada));

            StepVerifier.create(service.crear(cmd))
                    .expectNext(guardada)
                    .verifyComplete();

            PlantillaMensaje enviada = captor.getValue();
            assertThat(enviada.getIdCompania()).isEqualTo(10);
            assertThat(enviada.getIdSucursal()).isEqualTo(1);
            assertThat(enviada.getTipo()).isEqualTo("ausencia_2d");
            assertThat(enviada.getNombre()).isEqualTo("Mi Plantilla");
            assertThat(enviada.getContenido()).isEqualTo("Hola {nombre}");
            assertThat(enviada.getActivo()).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // actualizar
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("actualizar — modificar plantilla existente")
    class Actualizar {

        @Test
        @DisplayName("debe actualizar contenido, activo y nombre cuando todos se proporcionan")
        void actualizaCamposProporcionados() {
            PlantillaMensaje existente = plantilla(3, 10, "ausencia_2d", "Original", true);
            existente.setContenido("Contenido original");
            when(plantillaRepository.findById(3)).thenReturn(Mono.just(existente));

            PlantillaMensaje actualizada = plantilla(3, 10, "ausencia_2d", "Nuevo nombre", false);
            actualizada.setContenido("Nuevo contenido");
            when(plantillaRepository.update(any())).thenReturn(Mono.just(actualizada));

            ActualizarPlantillaCommand cmd = new ActualizarPlantillaCommand("Nuevo contenido", false, "Nuevo nombre");

            StepVerifier.create(service.actualizar(3, cmd, 10))
                    .expectNext(actualizada)
                    .verifyComplete();

            verify(plantillaRepository).update(existente);
            assertThat(existente.getContenido()).isEqualTo("Nuevo contenido");
            assertThat(existente.getActivo()).isFalse();
            assertThat(existente.getNombre()).isEqualTo("Nuevo nombre");
        }

        @Test
        @DisplayName("no debe modificar campos que vienen como null en el comando")
        void noModificaCamposNulos() {
            PlantillaMensaje existente = plantilla(3, 10, "ausencia_2d", "Nombre original", true);
            existente.setContenido("Contenido original");
            when(plantillaRepository.findById(3)).thenReturn(Mono.just(existente));
            when(plantillaRepository.update(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            ActualizarPlantillaCommand cmd = new ActualizarPlantillaCommand(null, null, null);

            StepVerifier.create(service.actualizar(3, cmd, 10))
                    .assertNext(p -> {
                        assertThat(p.getContenido()).isEqualTo("Contenido original");
                        assertThat(p.getActivo()).isTrue();
                        assertThat(p.getNombre()).isEqualTo("Nombre original");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("debe lanzar NotFoundException cuando la plantilla no existe")
        void lanzaNotFoundCuandoNoExiste() {
            when(plantillaRepository.findById(999)).thenReturn(Mono.empty());

            ActualizarPlantillaCommand cmd = new ActualizarPlantillaCommand("contenido", true, "nombre");

            StepVerifier.create(service.actualizar(999, cmd, 10))
                    .expectError(NotFoundException.class)
                    .verify();
        }

        @Test
        @DisplayName("debe lanzar ForbiddenException cuando la compañía del comando no coincide con la plantilla")
        void lanzaForbiddenCuandoCompaniaNoCoincide() {
            PlantillaMensaje existente = plantilla(3, 10, "ausencia_2d", "Plantilla", true);
            when(plantillaRepository.findById(3)).thenReturn(Mono.just(existente));

            ActualizarPlantillaCommand cmd = new ActualizarPlantillaCommand("nuevo", true, "nombre");

            StepVerifier.create(service.actualizar(3, cmd, 99))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    // eliminar
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("eliminar — borrado lógico de plantilla")
    class Eliminar {

        @Test
        @DisplayName("debe llamar softDelete cuando hay más de una plantilla activa del mismo tipo")
        void eliminaCuandoHayMasDeUnaActiva() {
            PlantillaMensaje existente = plantilla(4, 10, "ausencia_2d", "Plantilla", true);
            when(plantillaRepository.findById(4)).thenReturn(Mono.just(existente));
            when(plantillaRepository.countActivasByTipo(10, "ausencia_2d")).thenReturn(Mono.just(2L));
            when(plantillaRepository.softDelete(4)).thenReturn(Mono.empty());

            StepVerifier.create(service.eliminar(4, 10))
                    .verifyComplete();

            verify(plantillaRepository).softDelete(4);
        }

        @Test
        @DisplayName("debe permitir eliminar plantilla inactiva aunque sea la única del tipo")
        void permitEliminarseLaUnicaInactiva() {
            PlantillaMensaje existente = plantilla(4, 10, "ausencia_2d", "Plantilla", false);
            when(plantillaRepository.findById(4)).thenReturn(Mono.just(existente));
            when(plantillaRepository.countActivasByTipo(10, "ausencia_2d")).thenReturn(Mono.just(1L));
            when(plantillaRepository.softDelete(4)).thenReturn(Mono.empty());

            // activo=false, count=1 → la condición es count<=1 AND activo=true → NO aplica
            StepVerifier.create(service.eliminar(4, 10))
                    .verifyComplete();

            verify(plantillaRepository).softDelete(4);
        }

        @Test
        @DisplayName("debe lanzar NotFoundException cuando la plantilla no existe")
        void lanzaNotFoundCuandoNoExiste() {
            when(plantillaRepository.findById(999)).thenReturn(Mono.empty());

            StepVerifier.create(service.eliminar(999, 10))
                    .expectError(NotFoundException.class)
                    .verify();
        }

        @Test
        @DisplayName("debe lanzar ForbiddenException cuando la compañía no coincide")
        void lanzaForbiddenCuandoCompaniaNoCoincide() {
            PlantillaMensaje existente = plantilla(4, 10, "ausencia_2d", "Plantilla", true);
            when(plantillaRepository.findById(4)).thenReturn(Mono.just(existente));

            StepVerifier.create(service.eliminar(4, 55))
                    .expectError(ForbiddenException.class)
                    .verify();
        }

        @Test
        @DisplayName("debe lanzar ConflictException al intentar eliminar la única plantilla activa del tipo")
        void lanzaConflictAlEliminarUnicaActiva() {
            PlantillaMensaje existente = plantilla(4, 10, "ausencia_2d", "Plantilla", true);
            when(plantillaRepository.findById(4)).thenReturn(Mono.just(existente));
            when(plantillaRepository.countActivasByTipo(10, "ausencia_2d")).thenReturn(Mono.just(1L));

            StepVerifier.create(service.eliminar(4, 10))
                    .expectErrorSatisfies(ex -> {
                        assertThat(ex).isInstanceOf(ConflictException.class);
                        assertThat(((ConflictException) ex).getCodigo()).isEqualTo("ultima_plantilla");
                    })
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private PlantillaMensaje plantilla(Integer id, Integer idCompania, String tipo, String nombre, Boolean activo) {
        PlantillaMensaje p = new PlantillaMensaje();
        p.setId(id);
        p.setIdCompania(idCompania);
        p.setTipo(tipo);
        p.setNombre(nombre);
        p.setActivo(activo);
        p.setEliminado(false);
        return p;
    }
}
