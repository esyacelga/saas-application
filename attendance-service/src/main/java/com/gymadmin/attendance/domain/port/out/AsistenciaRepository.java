package com.gymadmin.attendance.domain.port.out;

import com.gymadmin.attendance.domain.model.Asistencia;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;

public interface AsistenciaRepository {

    record EntradaEnriquecida(LocalTime hora, Integer idCliente, String nombre, String fotoUrl, String metodo) {}


    Mono<Asistencia> save(Asistencia asistencia);

    Flux<Asistencia> findByClienteAndPeriodo(Integer idCliente, Integer idCompania,
                                              LocalDate desde, LocalDate hasta, Integer idMembresia);

    Flux<Asistencia> findByClienteUltimos30Dias(Integer idCliente, Integer idCompania, LocalDate desde);

    Flux<Asistencia> findByPersonaUltimos30Dias(Long idPersona, Integer idCompania, LocalDate desde);

    Flux<Asistencia> findByPersonaAndPeriodo(Long idPersona, Integer idCompania, LocalDate desde, LocalDate hasta);

    Flux<Asistencia> findByCompaniaAndFecha(Integer idCompania, Integer idSucursal, LocalDate fecha);

    Mono<Long> countByCompaniaAndPeriodo(Integer idCompania, LocalDate desde, LocalDate hasta);

    Mono<Long> countByCliente(Integer idCliente, LocalDate desde, LocalDate hasta);

    Mono<LocalDate> findUltimaAsistencia(Integer idCliente, Integer idCompania);

    Flux<Asistencia> findClientesConMembresia(Integer idCompania, LocalDate desde, LocalDate hasta);

    Flux<EntradaEnriquecida> findUltimasEntradas(Integer idCompania, Integer idSucursal, LocalDate fecha);
}
