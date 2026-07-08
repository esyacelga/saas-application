package com.gymadmin.attendance.unit;

import com.gymadmin.attendance.application.service.MensajeLogService;
import com.gymadmin.attendance.domain.model.MensajeLog;
import com.gymadmin.attendance.domain.model.PlantillaMensaje;
import com.gymadmin.attendance.domain.port.in.MensajeLogUseCase.EnviarMensajeCommand;
import com.gymadmin.attendance.domain.port.out.MensajeLogRepository;
import com.gymadmin.attendance.domain.port.out.PlantillaMensajeRepository;
import com.gymadmin.attendance.infrastructure.exception.IllegalArgumentException;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MensajeLogService — Pruebas unitarias")
class MensajeLogServiceTest {

    @Mock
    private MensajeLogRepository mensajeLogRepository;

    @Mock
    private PlantillaMensajeRepository plantillaRepository;

    @InjectMocks
    private MensajeLogService service;

    // -------------------------------------------------------------------------
    // listar
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("listar — consultar mensajes con filtros")
    class Listar {

        @Test
        @DisplayName("debe retornar flux con logs cuando existen resultados")
        void retornaLogsExistentes() {
            MensajeLog log1 = mensajeLog(1L, 10, 1, "enviado");
            MensajeLog log2 = mensajeLog(2L, 10, 2, "fallido");
            when(mensajeLogRepository.findByFiltros(10, null, "ausencia_2d", null, null))
                    .thenReturn(Flux.just(log1, log2));

            StepVerifier.create(service.listar(10, null, "ausencia_2d", null, null))
                    .expectNext(log1)
                    .expectNext(log2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("debe retornar flux vacío cuando no hay resultados con los filtros dados")
        void retornaVacioCuandoNoHayResultados() {
            when(mensajeLogRepository.findByFiltros(10, 999, null, null, LocalDate.now()))
                    .thenReturn(Flux.empty());

            StepVerifier.create(service.listar(10, 999, null, null, LocalDate.now()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("debe delegar todos los parámetros de filtro al repositorio")
        void delegaTodosLosFiltrosAlRepositorio() {
            LocalDate desde = LocalDate.of(2025, 1, 1);
            when(mensajeLogRepository.findByFiltros(10, 5, "vencimiento_3d", "enviado", desde))
                    .thenReturn(Flux.empty());

            StepVerifier.create(service.listar(10, 5, "vencimiento_3d", "enviado", desde))
                    .verifyComplete();

            verify(mensajeLogRepository).findByFiltros(10, 5, "vencimiento_3d", "enviado", desde);
        }
    }

    // -------------------------------------------------------------------------
    // enviarManual
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("enviarManual — enviar mensaje usando plantilla")
    class EnviarManual {

        @Test
        @DisplayName("debe crear log con estado 'enviado' cuando la plantilla existe y el envío es exitoso")
        void creaLogYActualizaAEnviado() {
            PlantillaMensaje plantilla = plantilla(7, 10, "ausencia_2d", "Plantilla bienvenida");
            plantilla.setContenido("Hola {nombre}, te extrañamos");

            MensajeLog logGuardado = mensajeLog(100L, 10, 1, "pendiente");
            logGuardado.setIdPlantilla(7);
            logGuardado.setTipo("ausencia_2d");
            logGuardado.setCanal("whatsapp");

            MensajeLog logActualizado = mensajeLog(100L, 10, 1, "enviado");
            logActualizado.setIdPlantilla(7);

            when(plantillaRepository.findById(7)).thenReturn(Mono.just(plantilla));
            when(mensajeLogRepository.save(any())).thenReturn(Mono.just(logGuardado));
            when(mensajeLogRepository.update(any())).thenReturn(Mono.just(logActualizado));

            EnviarMensajeCommand cmd = new EnviarMensajeCommand(1, "whatsapp", 7, 10, 1, "42");

            StepVerifier.create(service.enviarManual(cmd))
                    .assertNext(result -> assertThat(result.getEstado()).isEqualTo("enviado"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("debe establecer estado 'pendiente' al guardar el log inicial")
        void guardaLogConEstadoPendienteInicial() {
            PlantillaMensaje plantilla = plantilla(7, 10, "ausencia_2d", "Plantilla");
            plantilla.setContenido("Contenido");

            ArgumentCaptor<MensajeLog> captorSave = ArgumentCaptor.forClass(MensajeLog.class);
            MensajeLog logPendiente = mensajeLog(100L, 10, 1, "pendiente");
            MensajeLog logEnviado = mensajeLog(100L, 10, 1, "enviado");

            when(plantillaRepository.findById(7)).thenReturn(Mono.just(plantilla));
            when(mensajeLogRepository.save(captorSave.capture())).thenReturn(Mono.just(logPendiente));
            when(mensajeLogRepository.update(any())).thenReturn(Mono.just(logEnviado));

            EnviarMensajeCommand cmd = new EnviarMensajeCommand(1, "sms", 7, 10, 1, null);

            StepVerifier.create(service.enviarManual(cmd))
                    .expectNextCount(1)
                    .verifyComplete();

            assertThat(captorSave.getValue().getEstado()).isEqualTo("pendiente");
        }

        @Test
        @DisplayName("debe establecer el canal del comando en el log")
        void estableceCanalDelComandoEnElLog() {
            PlantillaMensaje plantilla = plantilla(7, 10, "ausencia_2d", "Plantilla");
            plantilla.setContenido("Contenido");

            ArgumentCaptor<MensajeLog> captorSave = ArgumentCaptor.forClass(MensajeLog.class);
            MensajeLog logPendiente = mensajeLog(100L, 10, 1, "pendiente");
            MensajeLog logEnviado = mensajeLog(100L, 10, 1, "enviado");

            when(plantillaRepository.findById(7)).thenReturn(Mono.just(plantilla));
            when(mensajeLogRepository.save(captorSave.capture())).thenReturn(Mono.just(logPendiente));
            when(mensajeLogRepository.update(any())).thenReturn(Mono.just(logEnviado));

            EnviarMensajeCommand cmd = new EnviarMensajeCommand(1, "email", 7, 10, 1, "15");

            StepVerifier.create(service.enviarManual(cmd))
                    .expectNextCount(1)
                    .verifyComplete();

            assertThat(captorSave.getValue().getCanal()).isEqualTo("email");
        }

        @Test
        @DisplayName("debe lanzar NotFoundException cuando la plantilla no existe")
        void lanzaNotFoundCuandoPlantillaNoExiste() {
            when(plantillaRepository.findById(999)).thenReturn(Mono.empty());

            EnviarMensajeCommand cmd = new EnviarMensajeCommand(1, "whatsapp", 999, 10, 1, null);

            StepVerifier.create(service.enviarManual(cmd))
                    .expectError(NotFoundException.class)
                    .verify();

            verify(mensajeLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("debe guardar log con idUsuarioEnvio cuando el idUsuarioEnvio es numérico")
        void guardaIdUsuarioEnvioNumerico() {
            PlantillaMensaje plantilla = plantilla(7, 10, "ausencia_2d", "Plantilla");
            plantilla.setContenido("Contenido");

            ArgumentCaptor<MensajeLog> captorSave = ArgumentCaptor.forClass(MensajeLog.class);
            MensajeLog logPendiente = mensajeLog(100L, 10, 1, "pendiente");
            MensajeLog logEnviado = mensajeLog(100L, 10, 1, "enviado");

            when(plantillaRepository.findById(7)).thenReturn(Mono.just(plantilla));
            when(mensajeLogRepository.save(captorSave.capture())).thenReturn(Mono.just(logPendiente));
            when(mensajeLogRepository.update(any())).thenReturn(Mono.just(logEnviado));

            EnviarMensajeCommand cmd = new EnviarMensajeCommand(1, "whatsapp", 7, 10, 1, "42");

            StepVerifier.create(service.enviarManual(cmd))
                    .expectNextCount(1)
                    .verifyComplete();

            assertThat(captorSave.getValue().getIdUsuarioEnvio()).isEqualTo(42);
        }

        @Test
        @DisplayName("no debe establecer idUsuarioEnvio cuando el valor no es numérico")
        void noGuardaIdUsuarioEnvioNoNumerico() {
            PlantillaMensaje plantilla = plantilla(7, 10, "ausencia_2d", "Plantilla");
            plantilla.setContenido("Contenido");

            ArgumentCaptor<MensajeLog> captorSave = ArgumentCaptor.forClass(MensajeLog.class);
            MensajeLog logPendiente = mensajeLog(100L, 10, 1, "pendiente");
            MensajeLog logEnviado = mensajeLog(100L, 10, 1, "enviado");

            when(plantillaRepository.findById(7)).thenReturn(Mono.just(plantilla));
            when(mensajeLogRepository.save(captorSave.capture())).thenReturn(Mono.just(logPendiente));
            when(mensajeLogRepository.update(any())).thenReturn(Mono.just(logEnviado));

            EnviarMensajeCommand cmd = new EnviarMensajeCommand(1, "whatsapp", 7, 10, 1, "uuid-no-numerico");

            StepVerifier.create(service.enviarManual(cmd))
                    .expectNextCount(1)
                    .verifyComplete();

            assertThat(captorSave.getValue().getIdUsuarioEnvio()).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // reenviar
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("reenviar — reintento de mensaje fallido")
    class Reenviar {

        @Test
        @DisplayName("debe actualizar a 'enviado' cuando el mensaje tiene estado 'fallido'")
        void actualizaAEnviadoCuandoEsFallido() {
            MensajeLog logFallido = mensajeLog(50L, 10, 1, "fallido");
            MensajeLog logEnviado = mensajeLog(50L, 10, 1, "enviado");

            when(mensajeLogRepository.findById(50L)).thenReturn(Mono.just(logFallido));
            when(mensajeLogRepository.update(any())).thenReturn(Mono.just(logEnviado));

            StepVerifier.create(service.reenviar(50L, 10))
                    .assertNext(result -> assertThat(result.getEstado()).isEqualTo("enviado"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("debe actualizar el estado del log a 'enviado' y asignar fechaEnvio")
        void asignaFechaEnvioAlActualizar() {
            MensajeLog logFallido = mensajeLog(50L, 10, 1, "fallido");
            ArgumentCaptor<MensajeLog> captorUpdate = ArgumentCaptor.forClass(MensajeLog.class);

            when(mensajeLogRepository.findById(50L)).thenReturn(Mono.just(logFallido));
            when(mensajeLogRepository.update(captorUpdate.capture()))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(service.reenviar(50L, 10))
                    .expectNextCount(1)
                    .verifyComplete();

            MensajeLog actualizado = captorUpdate.getValue();
            assertThat(actualizado.getEstado()).isEqualTo("enviado");
            assertThat(actualizado.getFechaEnvio()).isNotNull();
        }

        @Test
        @DisplayName("debe lanzar NotFoundException cuando el log no existe")
        void lanzaNotFoundCuandoLogNoExiste() {
            when(mensajeLogRepository.findById(999L)).thenReturn(Mono.empty());

            StepVerifier.create(service.reenviar(999L, 10))
                    .expectError(NotFoundException.class)
                    .verify();
        }

        @Test
        @DisplayName("debe lanzar IllegalArgumentException cuando el estado es 'pendiente'")
        void lanzaIllegalArgumentCuandoEstadoEsPendiente() {
            MensajeLog logPendiente = mensajeLog(50L, 10, 1, "pendiente");
            when(mensajeLogRepository.findById(50L)).thenReturn(Mono.just(logPendiente));

            StepVerifier.create(service.reenviar(50L, 10))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }

        @Test
        @DisplayName("debe lanzar IllegalArgumentException cuando el estado es 'enviado'")
        void lanzaIllegalArgumentCuandoEstadoEsEnviado() {
            MensajeLog logEnviado = mensajeLog(50L, 10, 1, "enviado");
            when(mensajeLogRepository.findById(50L)).thenReturn(Mono.just(logEnviado));

            StepVerifier.create(service.reenviar(50L, 10))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    // contarEnviadosDesde
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("contarEnviadosDesde — conteo de mensajes enviados")
    class ContarEnviadosDesde {

        @Test
        @DisplayName("debe retornar el conteo delegando al repositorio")
        void retornaConteoDelRepositorio() {
            OffsetDateTime desde = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7);
            when(mensajeLogRepository.countByClienteAndTipoDesde(1, "ausencia_2d", desde))
                    .thenReturn(Mono.just(3L));

            StepVerifier.create(service.contarEnviadosDesde(1, "ausencia_2d", desde))
                    .expectNext(3L)
                    .verifyComplete();
        }

        @Test
        @DisplayName("debe retornar cero cuando no hay mensajes enviados desde la fecha dada")
        void retornaCeroCuandoNoHayMensajes() {
            OffsetDateTime desde = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);
            when(mensajeLogRepository.countByClienteAndTipoDesde(5, "recuperacion_5d", desde))
                    .thenReturn(Mono.just(0L));

            StepVerifier.create(service.contarEnviadosDesde(5, "recuperacion_5d", desde))
                    .expectNext(0L)
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private MensajeLog mensajeLog(Long id, Integer idCompania, Integer idCliente, String estado) {
        MensajeLog log = new MensajeLog();
        log.setId(id);
        log.setIdCompania(idCompania);
        log.setIdCliente(idCliente);
        log.setEstado(estado);
        log.setTipo("ausencia_2d");
        log.setCanal("whatsapp");
        log.setContenido("Contenido de prueba");
        log.setFechaProgramada(OffsetDateTime.now(ZoneOffset.UTC));
        return log;
    }

    private PlantillaMensaje plantilla(Integer id, Integer idCompania, String tipo, String nombre) {
        PlantillaMensaje p = new PlantillaMensaje();
        p.setId(id);
        p.setIdCompania(idCompania);
        p.setTipo(tipo);
        p.setNombre(nombre);
        p.setActivo(true);
        return p;
    }
}
