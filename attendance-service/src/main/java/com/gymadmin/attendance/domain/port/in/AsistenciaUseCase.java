package com.gymadmin.attendance.domain.port.in;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.gymadmin.attendance.domain.model.Asistencia;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;

public interface AsistenciaUseCase {

    Mono<AsistenciaQrResult> registrarPorQr(RegistrarQrCommand command);

    Mono<AsistenciaQrResult> registrarPorApp(RegistrarAppCommand command);

    Mono<Asistencia> registrarManual(RegistrarManualCommand command);

    Mono<Asistencia> registrarOverride(RegistrarOverrideCommand command);

    Flux<Asistencia> listarPorCliente(Integer idCliente, Integer idCompania, LocalDate desde, LocalDate hasta, Integer idMembresia);

    Mono<Ultimos30DiasResult> ultimos30Dias(Integer idCliente, Integer idCompania);

    Mono<Ultimos30DiasResult> ultimos30DiasMe(Integer idPersona, Integer idCompania, String bearerToken);

    Mono<java.util.List<Asistencia>> listarPorClienteMe(Integer idPersona, Integer idCompania, String bearerToken, LocalDate desde, LocalDate hasta);

    Mono<AsistenciasHoyResult> asistenciasHoy(Integer idCompania, Integer idSucursal);

    Mono<EstadisticasResult> estadisticas(Integer idCompania, String periodo, Integer anio, Integer mes);

    Mono<RachaPerfectaResult> rachaPerfecta(Integer idCliente, Integer idCompania, Integer meses);

    record RegistrarQrCommand(String qrToken, Integer idClienteJwt, Long idCompaniaJwt, String bearerToken, Integer idPersonaJwt) {}

    record RegistrarAppCommand(Integer idClienteJwt, Long idCompaniaJwt, Integer idSucursal, String nombreSucursal, String bearerToken, Integer idPersonaJwt) {}

    record RegistrarManualCommand(Integer idCliente, LocalDate fecha, LocalTime horaEntrada,
                                   Integer idCompania, Integer idSucursal, String idUsuarioRegistro) {}

    record RegistrarOverrideCommand(Integer idCliente, LocalDate fecha, LocalTime horaEntrada,
                                     Integer idCompania, Integer idSucursal,
                                     String motivoOverride, String idUsuarioRegistro) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record AsistenciaQrResult(
            Long idAsistencia, LocalDate fecha, LocalTime horaEntrada, String sucursal,
            String tipoMembresia, String modoControl,
            Integer accesosUsados, Integer accesosRestantes, LocalDate fechaFin) {}

    record UltimoDiaDetalle(LocalDate fecha, boolean asistio, LocalTime hora) {}

    record Ultimos30DiasResult(Integer clienteId, Integer diasAsistidos, Integer diasAusente,
                                Integer rachaActual, Integer rachaMaximaMes,
                                java.util.List<UltimoDiaDetalle> detalle) {}

    record EntradaResumen(LocalTime hora, Integer clienteId, String nombre, String fotoUrl, String metodo) {}

    record AsistenciasHoyResult(LocalDate fecha, Integer totalEntradas,
                                 java.util.Map<String, Integer> porMetodo,
                                 java.util.List<EntradaResumen> ultimasEntradas) {}

    record EstadisticasResult(String periodo, Integer totalEntradas, Double promedioDiario,
                               Integer clientesActivos, Integer clientesSinAsistir7d,
                               Integer clientesSinAsistir15d,
                               LocalDate diaMasConcurrido, Integer entradasDiaMasConcurrido,
                               String horaPico) {}

    record RachaPerfectaResult(boolean rachaPerfecta, Integer diasAsistidos, Integer diasConMembresia) {}
}
