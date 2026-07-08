package com.gymadmin.core.unit;

import com.gymadmin.core.application.service.TipoMembresiaService;
import com.gymadmin.core.domain.model.TipoMembresia;
import com.gymadmin.core.domain.port.in.TipoMembresiaUseCase;
import com.gymadmin.core.domain.port.out.TipoMembresiaRepository;
import com.gymadmin.core.infrastructure.exception.BusinessException;
import com.gymadmin.core.infrastructure.exception.ConflictException;
import com.gymadmin.core.infrastructure.exception.NotFoundException;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TipoMembresiaService — gestión de tipos de membresía")
class TipoMembresiaServiceTest {

    @Mock
    private TipoMembresiaRepository tipoMembresiaRepository;

    @InjectMocks
    private TipoMembresiaService tipoMembresiaService;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TipoMembresia buildTipo(Long id, Long idCompania, String nombre,
                                    TipoMembresia.ModoControl modo, Boolean activo) {
        TipoMembresia tipo = new TipoMembresia();
        tipo.setId(id);
        tipo.setIdCompania(idCompania);
        tipo.setNombre(nombre);
        tipo.setModoControl(modo);
        tipo.setDuracionTipo(TipoMembresia.DuracionTipo.meses);
        tipo.setDuracionValor(1);
        tipo.setPrecio(BigDecimal.valueOf(50));
        tipo.setActivo(activo);
        return tipo;
    }

    // -------------------------------------------------------------------------
    // listarActivos
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("listarActivos")
    class ListarActivos {

        @Test
        @DisplayName("retorna los tipos activos de la compañía")
        void retornaLosTiposActivosDeLaCompania() {
            TipoMembresia t1 = buildTipo(1L, 10L, "Mensual", TipoMembresia.ModoControl.calendario, true);
            TipoMembresia t2 = buildTipo(2L, 10L, "Semestral", TipoMembresia.ModoControl.calendario, true);
            when(tipoMembresiaRepository.findActivosByIdCompania(10L))
                    .thenReturn(Flux.just(t1, t2));

            StepVerifier.create(tipoMembresiaService.listarActivos(10L))
                    .expectNext(t1)
                    .expectNext(t2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna Flux vacío cuando no hay tipos activos")
        void retornaFluxVacioCuandoNoHayTipos() {
            when(tipoMembresiaRepository.findActivosByIdCompania(10L))
                    .thenReturn(Flux.empty());

            StepVerifier.create(tipoMembresiaService.listarActivos(10L))
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    // crear
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("crear")
    class Crear {

        @Test
        @DisplayName("crea exitosamente un tipo con modo_control calendario")
        void creaExitosamenteTipoCalendario() {
            TipoMembresiaUseCase.CrearTipoCommand cmd = new TipoMembresiaUseCase.CrearTipoCommand(
                    "Plan Mensual",
                    TipoMembresia.ModoControl.calendario,
                    TipoMembresia.DuracionTipo.meses,
                    1,
                    null,
                    BigDecimal.valueOf(40)
            );
            when(tipoMembresiaRepository.findByNombreAndIdCompania("Plan Mensual", 10L))
                    .thenReturn(Mono.empty());

            TipoMembresia saved = buildTipo(5L, 10L, "Plan Mensual", TipoMembresia.ModoControl.calendario, true);
            when(tipoMembresiaRepository.save(any())).thenReturn(Mono.just(saved));

            StepVerifier.create(tipoMembresiaService.crear(10L, 1L, cmd))
                    .expectNextMatches(t -> t.getId().equals(5L) && t.getActivo())
                    .verifyComplete();

            ArgumentCaptor<TipoMembresia> captor = ArgumentCaptor.forClass(TipoMembresia.class);
            verify(tipoMembresiaRepository).save(captor.capture());
            TipoMembresia persisted = captor.getValue();
            assertThat(persisted.getModoControl()).isEqualTo(TipoMembresia.ModoControl.calendario);
            assertThat(persisted.getActivo()).isTrue();
            assertThat(persisted.getIdCompania()).isEqualTo(10L);
            assertThat(persisted.getDiasAcceso()).isNull();
        }

        @Test
        @DisplayName("crea exitosamente un tipo con modo_control accesos y dias_acceso definido")
        void creaExitosamenteTipoAccesos() {
            TipoMembresiaUseCase.CrearTipoCommand cmd = new TipoMembresiaUseCase.CrearTipoCommand(
                    "Plan Accesos",
                    TipoMembresia.ModoControl.accesos,
                    TipoMembresia.DuracionTipo.dias,
                    30,
                    20,
                    BigDecimal.valueOf(60)
            );
            when(tipoMembresiaRepository.findByNombreAndIdCompania("Plan Accesos", 10L))
                    .thenReturn(Mono.empty());

            TipoMembresia saved = buildTipo(6L, 10L, "Plan Accesos", TipoMembresia.ModoControl.accesos, true);
            saved.setDiasAcceso(20);
            when(tipoMembresiaRepository.save(any())).thenReturn(Mono.just(saved));

            StepVerifier.create(tipoMembresiaService.crear(10L, 1L, cmd))
                    .expectNextMatches(t -> t.getDiasAcceso() != null && t.getDiasAcceso() == 20)
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza BusinessException cuando modo_control es accesos y dias_acceso es null")
        void lanzaBusinessExceptionSiModoAccesosSinDiasAcceso() {
            TipoMembresiaUseCase.CrearTipoCommand cmd = new TipoMembresiaUseCase.CrearTipoCommand(
                    "Sin dias",
                    TipoMembresia.ModoControl.accesos,
                    TipoMembresia.DuracionTipo.dias,
                    30,
                    null, // diasAcceso null — debe fallar
                    BigDecimal.valueOf(30)
            );

            StepVerifier.create(tipoMembresiaService.crear(10L, 1L, cmd))
                    .expectErrorSatisfies(err -> {
                        assertThat(err).isInstanceOf(BusinessException.class);
                        assertThat(err.getMessage()).contains("dias_acceso");
                    })
                    .verify();

            verify(tipoMembresiaRepository, never()).save(any());
        }

        @Test
        @DisplayName("lanza ConflictException cuando ya existe un tipo con el mismo nombre en la compañía")
        void lanzaConflictExceptionSiNombreYaExiste() {
            TipoMembresiaUseCase.CrearTipoCommand cmd = new TipoMembresiaUseCase.CrearTipoCommand(
                    "Plan Mensual",
                    TipoMembresia.ModoControl.calendario,
                    TipoMembresia.DuracionTipo.meses,
                    1,
                    null,
                    BigDecimal.valueOf(40)
            );
            TipoMembresia existing = buildTipo(3L, 10L, "Plan Mensual", TipoMembresia.ModoControl.calendario, true);
            when(tipoMembresiaRepository.findByNombreAndIdCompania("Plan Mensual", 10L))
                    .thenReturn(Mono.just(existing));

            StepVerifier.create(tipoMembresiaService.crear(10L, 1L, cmd))
                    .expectErrorSatisfies(err -> {
                        assertThat(err).isInstanceOf(ConflictException.class);
                        assertThat(err.getMessage()).contains("Plan Mensual");
                    })
                    .verify();

            verify(tipoMembresiaRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // actualizar
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("actualizar")
    class Actualizar {

        @Test
        @DisplayName("actualiza nombre y precio exitosamente")
        void actualizaNombreYPrecioExitosamente() {
            TipoMembresia existing = buildTipo(1L, 10L, "Viejo Nombre", TipoMembresia.ModoControl.calendario, true);
            when(tipoMembresiaRepository.findById(1L)).thenReturn(Mono.just(existing));

            TipoMembresia updated = buildTipo(1L, 10L, "Nuevo Nombre", TipoMembresia.ModoControl.calendario, true);
            updated.setPrecio(BigDecimal.valueOf(99));
            when(tipoMembresiaRepository.save(any())).thenReturn(Mono.just(updated));

            TipoMembresiaUseCase.ActualizarTipoCommand cmd =
                    new TipoMembresiaUseCase.ActualizarTipoCommand("Nuevo Nombre", BigDecimal.valueOf(99));

            StepVerifier.create(tipoMembresiaService.actualizar(1L, 10L, cmd))
                    .expectNextMatches(t -> "Nuevo Nombre".equals(t.getNombre())
                            && t.getPrecio().compareTo(BigDecimal.valueOf(99)) == 0)
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando el tipo no existe")
        void lanzaNotFoundExceptionSiTipoNoExiste() {
            when(tipoMembresiaRepository.findById(99L)).thenReturn(Mono.empty());
            TipoMembresiaUseCase.ActualizarTipoCommand cmd =
                    new TipoMembresiaUseCase.ActualizarTipoCommand("Nombre", BigDecimal.valueOf(50));

            StepVerifier.create(tipoMembresiaService.actualizar(99L, 10L, cmd))
                    .expectErrorSatisfies(err -> {
                        assertThat(err).isInstanceOf(NotFoundException.class);
                        assertThat(err.getMessage()).contains("99");
                    })
                    .verify();
        }

        @Test
        @DisplayName("actualiza solo el precio cuando el nombre del command es null")
        void actualizaSoloPrecioCuandoNombreEsNull() {
            TipoMembresia existing = buildTipo(1L, 10L, "Nombre Original", TipoMembresia.ModoControl.calendario, true);
            when(tipoMembresiaRepository.findById(1L)).thenReturn(Mono.just(existing));
            when(tipoMembresiaRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            TipoMembresiaUseCase.ActualizarTipoCommand cmd =
                    new TipoMembresiaUseCase.ActualizarTipoCommand(null, BigDecimal.valueOf(75));

            StepVerifier.create(tipoMembresiaService.actualizar(1L, 10L, cmd))
                    .expectNextMatches(t -> "Nombre Original".equals(t.getNombre())
                            && t.getPrecio().compareTo(BigDecimal.valueOf(75)) == 0)
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    // desactivar
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("desactivar")
    class Desactivar {

        @Test
        @DisplayName("desactiva el tipo cuando no hay membresías activas de ese tipo")
        void desactivaExitosamenteCuandoNoHayMembresiasActivas() {
            TipoMembresia existing = buildTipo(1L, 10L, "Plan", TipoMembresia.ModoControl.calendario, true);
            when(tipoMembresiaRepository.findById(1L)).thenReturn(Mono.just(existing));
            when(tipoMembresiaRepository.existeMembresiaActivaDeEsteTipo(1L)).thenReturn(Mono.just(false));
            when(tipoMembresiaRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(tipoMembresiaService.desactivar(1L, 10L))
                    .verifyComplete();

            ArgumentCaptor<TipoMembresia> captor = ArgumentCaptor.forClass(TipoMembresia.class);
            verify(tipoMembresiaRepository).save(captor.capture());
            assertThat(captor.getValue().getActivo()).isFalse();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando el tipo no existe")
        void lanzaNotFoundExceptionSiTipoNoExiste() {
            when(tipoMembresiaRepository.findById(99L)).thenReturn(Mono.empty());

            StepVerifier.create(tipoMembresiaService.desactivar(99L, 10L))
                    .expectErrorSatisfies(err -> {
                        assertThat(err).isInstanceOf(NotFoundException.class);
                        assertThat(err.getMessage()).contains("99");
                    })
                    .verify();
        }

        @Test
        @DisplayName("lanza ConflictException cuando existen membresías activas de ese tipo")
        void lanzaConflictExceptionSiHayMembresiasActivas() {
            TipoMembresia existing = buildTipo(1L, 10L, "Plan", TipoMembresia.ModoControl.calendario, true);
            when(tipoMembresiaRepository.findById(1L)).thenReturn(Mono.just(existing));
            when(tipoMembresiaRepository.existeMembresiaActivaDeEsteTipo(1L)).thenReturn(Mono.just(true));

            StepVerifier.create(tipoMembresiaService.desactivar(1L, 10L))
                    .expectErrorSatisfies(err -> {
                        assertThat(err).isInstanceOf(ConflictException.class);
                        assertThat(err.getMessage()).contains("activas");
                    })
                    .verify();

            verify(tipoMembresiaRepository, never()).save(any());
        }
    }
}
