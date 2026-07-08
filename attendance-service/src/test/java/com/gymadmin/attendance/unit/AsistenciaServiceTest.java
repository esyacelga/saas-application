package com.gymadmin.attendance.unit;

import com.gymadmin.attendance.application.service.AsistenciaService;
import com.gymadmin.attendance.domain.model.Asistencia;
import com.gymadmin.attendance.domain.port.in.AsistenciaUseCase.RegistrarManualCommand;
import com.gymadmin.attendance.domain.port.in.AsistenciaUseCase.RegistrarOverrideCommand;
import com.gymadmin.attendance.domain.port.out.AsistenciaRepository;
import com.gymadmin.attendance.infrastructure.adapter.out.auth.AuthServiceClient;
import com.gymadmin.attendance.infrastructure.adapter.out.core.CoreServiceClient;
import com.gymadmin.attendance.infrastructure.adapter.out.core.CoreServiceClient.ValidarAccesoResponse;
import com.gymadmin.attendance.infrastructure.exception.ConflictException;
import com.gymadmin.attendance.infrastructure.exception.ForbiddenException;
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

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsistenciaService — registro de asistencias")
class AsistenciaServiceTest {

    @Mock
    private AsistenciaRepository asistenciaRepository;

    @Mock
    private CoreServiceClient coreServiceClient;

    @Mock
    private AuthServiceClient authServiceClient;

    @InjectMocks
    private AsistenciaService service;

    private ValidarAccesoResponse mockAccesoPermitido(Integer idCliente, Integer idMembresia) {
        ValidarAccesoResponse r = mock(ValidarAccesoResponse.class);
        when(r.isPermitido()).thenReturn(true);
        when(r.getIdCliente()).thenReturn(idCliente);
        when(r.getIdMembresia()).thenReturn(idMembresia);
        when(r.getTipoMembresia()).thenReturn("Mensual");
        when(r.getModoControl()).thenReturn("calendario");
        when(r.getFechaFin()).thenReturn(null);
        when(r.getAccesosUsados()).thenReturn(null);
        when(r.getDiasAccesoRestantes()).thenReturn(null);
        return r;
    }

    private ValidarAccesoResponse mockAccesoDenegado(String razon) {
        ValidarAccesoResponse r = mock(ValidarAccesoResponse.class);
        when(r.isPermitido()).thenReturn(false);
        when(r.getRazon()).thenReturn(razon);
        return r;
    }

    private Asistencia buildAsistencia(Long id, Integer idCliente, Integer idMembresia) {
        Asistencia a = new Asistencia();
        a.setId(id);
        a.setIdCliente(idCliente);
        a.setIdMembresia(idMembresia);
        a.setIdCompania(1);
        a.setIdSucursal(1);
        a.setFecha(LocalDate.now());
        a.setHoraEntrada(LocalTime.now().withNano(0));
        a.setMetodoRegistro("manual");
        return a;
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("registrarManual")
    class RegistrarManual {

        @Test
        @DisplayName("registra asistencia manual cuando el cliente tiene acceso permitido")
        void registraExitosamente() {
            // RegistrarManualCommand(idCliente, fecha, horaEntrada, idCompania, idSucursal, idUsuarioRegistro)
            RegistrarManualCommand cmd = new RegistrarManualCommand(
                    10, LocalDate.now(), LocalTime.of(9, 0), 1, 1, "recepcion-user"
            );
            Asistencia saved = buildAsistencia(1L, 10, 5);

            when(coreServiceClient.validarAcceso(10, 1, "")).thenReturn(Mono.just(mockAccesoPermitido(10, 5)));
            when(asistenciaRepository.save(any())).thenReturn(Mono.just(saved));

            StepVerifier.create(service.registrarManual(cmd))
                    .assertNext(a -> {
                        assertThat(a.getId()).isEqualTo(1L);
                        assertThat(a.getIdCliente()).isEqualTo(10);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("usa la hora actual cuando horaEntrada del comando es nula")
        void usaHoraActualCuandoHoraEsNula() {
            RegistrarManualCommand cmd = new RegistrarManualCommand(
                    10, LocalDate.now(), null, 1, 1, "recepcion-user"
            );
            Asistencia saved = buildAsistencia(2L, 10, 5);

            when(coreServiceClient.validarAcceso(10, 1, "")).thenReturn(Mono.just(mockAccesoPermitido(10, 5)));
            when(asistenciaRepository.save(any())).thenReturn(Mono.just(saved));

            StepVerifier.create(service.registrarManual(cmd))
                    .assertNext(a -> assertThat(a).isNotNull())
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ForbiddenException cuando el acceso está denegado por membresía vencida")
        void lanzaForbiddenCuandoAccesoDenegado() {
            RegistrarManualCommand cmd = new RegistrarManualCommand(
                    10, LocalDate.now(), null, 1, 1, "recepcion-user"
            );
            when(coreServiceClient.validarAcceso(10, 1, ""))
                    .thenReturn(Mono.just(mockAccesoDenegado("membresia_vencida")));

            StepVerifier.create(service.registrarManual(cmd))
                    .expectErrorSatisfies(e -> {
                        assertThat(e).isInstanceOf(ForbiddenException.class);
                        assertThat(e.getMessage()).contains("membresia_vencida");
                    })
                    .verify();
        }

        @Test
        @DisplayName("lanza ConflictException cuando ya existe asistencia hoy (violación de restricción única)")
        void lanzaConflictCuandoYaRegistradoHoy() {
            RegistrarManualCommand cmd = new RegistrarManualCommand(
                    10, LocalDate.now(), null, 1, 1, "recepcion-user"
            );
            when(coreServiceClient.validarAcceso(10, 1, "")).thenReturn(Mono.just(mockAccesoPermitido(10, 5)));
            when(asistenciaRepository.save(any())).thenReturn(
                    Mono.error(new RuntimeException("unique constraint violation"))
            );

            StepVerifier.create(service.registrarManual(cmd))
                    .expectError(ConflictException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("registrarOverride")
    class RegistrarOverride {

        @Test
        @DisplayName("registra override sin validar membresía; idMembresia queda null")
        void registraOverrideSinValidarMembresia() {
            // RegistrarOverrideCommand(idCliente, fecha, horaEntrada, idCompania, idSucursal, motivoOverride, idUsuarioRegistro)
            RegistrarOverrideCommand cmd = new RegistrarOverrideCommand(
                    10, LocalDate.now(), LocalTime.of(10, 0), 1, 1, "Autorización especial", "dueno"
            );
            Asistencia saved = buildAsistencia(5L, 10, null);
            saved.setIdMembresia(null);

            when(asistenciaRepository.save(any())).thenReturn(Mono.just(saved));

            StepVerifier.create(service.registrarOverride(cmd))
                    .assertNext(a -> {
                        assertThat(a.getId()).isEqualTo(5L);
                        assertThat(a.getIdMembresia()).isNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza ConflictException cuando ya existe asistencia hoy en override")
        void lanzaConflictCuandoDuplicadoEnOverride() {
            RegistrarOverrideCommand cmd = new RegistrarOverrideCommand(
                    10, LocalDate.now(), null, 1, 1, "Override", "dueno"
            );
            when(asistenciaRepository.save(any())).thenReturn(
                    Mono.error(new RuntimeException("unique constraint"))
            );

            StepVerifier.create(service.registrarOverride(cmd))
                    .expectError(ConflictException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("listarPorCliente")
    class ListarPorCliente {

        @Test
        @DisplayName("retorna el historial filtrado por período para el cliente")
        void retornaHistorialFiltrado() {
            Asistencia a1 = buildAsistencia(1L, 10, 5);
            Asistencia a2 = buildAsistencia(2L, 10, 5);
            LocalDate desde = LocalDate.now().minusMonths(1);
            LocalDate hasta = LocalDate.now();

            when(asistenciaRepository.findByClienteAndPeriodo(10, 1, desde, hasta, null))
                    .thenReturn(Flux.just(a1, a2));

            StepVerifier.create(service.listarPorCliente(10, 1, desde, hasta, null))
                    .expectNext(a1)
                    .expectNext(a2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("usa el mes anterior como rango por defecto cuando desde/hasta son nulos")
        void usaRangoPorDefectoCuandoFechasNulas() {
            Asistencia a = buildAsistencia(1L, 10, 5);
            when(asistenciaRepository.findByClienteAndPeriodo(
                    anyInt(), anyInt(), any(LocalDate.class), any(LocalDate.class), any()))
                    .thenReturn(Flux.just(a));

            StepVerifier.create(service.listarPorCliente(10, 1, null, null, null))
                    .expectNext(a)
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("estadisticas")
    class Estadisticas {

        @Test
        @DisplayName("retorna estadísticas del mes con total y promedio diario calculado")
        void retornaEstadisticasDelMes() {
            when(asistenciaRepository.countByCompaniaAndPeriodo(anyInt(), any(), any()))
                    .thenReturn(Mono.just(31L));

            StepVerifier.create(service.estadisticas(1, 2026, 1))
                    .assertNext(r -> {
                        assertThat(r.totalEntradas()).isEqualTo(31);
                        assertThat(r.periodo()).isEqualTo("2026-01");
                        assertThat(r.promedioDiario()).isGreaterThan(0.0);
                    })
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("rachaPerfecta")
    class RachaPerfecta {

        @Test
        @DisplayName("retorna rachaPerfecta=true cuando el cliente asistió todos los días del período")
        void rachaEsTrueCuandoAsisteToLosLosDias() {
            when(asistenciaRepository.countByCliente(anyInt(), any(), any()))
                    .thenReturn(Mono.just(32L));

            StepVerifier.create(service.rachaPerfecta(10, 1, 1))
                    .assertNext(r -> assertThat(r.rachaPerfecta()).isTrue())
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna rachaPerfecta=false cuando hay días sin asistencia")
        void rachaEsFalseCuandoHayDiasFaltados() {
            when(asistenciaRepository.countByCliente(anyInt(), any(), any()))
                    .thenReturn(Mono.just(20L));

            StepVerifier.create(service.rachaPerfecta(10, 1, 1))
                    .assertNext(r -> assertThat(r.rachaPerfecta()).isFalse())
                    .verifyComplete();
        }
    }
}
