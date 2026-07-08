package com.gymadmin.core.application.service;

import com.gymadmin.core.domain.model.Cliente;
import com.gymadmin.core.domain.model.Congelamiento;
import com.gymadmin.core.domain.model.Membresia;
import com.gymadmin.core.domain.port.in.CongelamientoUseCase;
import com.gymadmin.core.domain.port.out.ClienteRepository;
import com.gymadmin.core.domain.port.out.CongelamientoRepository;
import com.gymadmin.core.domain.port.out.MembresiaRepository;
import com.gymadmin.core.infrastructure.exception.BusinessException;
import com.gymadmin.core.infrastructure.exception.ConflictException;
import com.gymadmin.core.infrastructure.exception.ForbiddenException;
import com.gymadmin.core.infrastructure.exception.NotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class CongelamientoService implements CongelamientoUseCase {

    private final CongelamientoRepository congelamientoRepository;
    private final MembresiaRepository membresiaRepository;
    private final ClienteRepository clienteRepository;

    public CongelamientoService(CongelamientoRepository congelamientoRepository,
                                MembresiaRepository membresiaRepository,
                                ClienteRepository clienteRepository) {
        this.congelamientoRepository = congelamientoRepository;
        this.membresiaRepository = membresiaRepository;
        this.clienteRepository = clienteRepository;
    }

    @Override
    public Mono<Congelamiento> congelar(Long idMembresia, Long idCompania, Long idSucursal, Long idUsuario, CongelarCommand command) {
        return membresiaRepository.findById(idMembresia)
                .switchIfEmpty(Mono.error(new NotFoundException("Membresia", idMembresia)))
                .flatMap(mem -> {
                    if (Membresia.Estado.congelada.equals(mem.getEstado())) {
                        return Mono.error(new ConflictException("La membresía ya está congelada"));
                    }
                    if (!Membresia.Estado.activa.equals(mem.getEstado())) {
                        return Mono.error(new BusinessException("Solo se pueden congelar membresías activas"));
                    }
                    if (command.retroactivo() && (command.documentoRespaldo() == null || command.aprobadoPor() == null)) {
                        return Mono.error(new BusinessException("Congelamiento retroactivo requiere documento_respaldo y aprobado_por"));
                    }

                    Congelamiento cong = new Congelamiento();
                    cong.setIdCompania(idCompania);
                    cong.setIdSucursal(idSucursal);
                    cong.setIdMembresia(idMembresia);
                    cong.setFechaInicio(command.fechaInicio());
                    cong.setMotivo(command.motivo());
                    cong.setDetalle(command.detalle());
                    cong.setRetroactivo(command.retroactivo());
                    cong.setDocumentoRespaldo(command.documentoRespaldo());
                    cong.setAprobadoPor(command.aprobadoPor());
                    if (command.aprobadoPor() != null) cong.setFechaAprobacion(LocalDate.now());
                    cong.setIdUsuarioRegistro(idUsuario);

                    return congelamientoRepository.save(cong)
                            .flatMap(saved -> {
                                mem.setEstado(Membresia.Estado.congelada);
                                return membresiaRepository.save(mem)
                                        .flatMap(m -> clienteRepository.findById(m.getIdCliente())
                                                .flatMap(cli -> {
                                                    cli.setEstado(Cliente.Estado.congelado);
                                                    return clienteRepository.save(cli);
                                                })
                                        ).thenReturn(saved);
                            });
                });
    }

    @Override
    public Mono<ReactivarResult> reactivar(Long idCongelamiento, Long idCompania) {
        return congelamientoRepository.findById(idCongelamiento)
                .switchIfEmpty(Mono.error(new NotFoundException("Congelamiento", idCongelamiento)))
                .flatMap(cong -> membresiaRepository.findById(cong.getIdMembresia())
                        .flatMap(mem -> {
                            LocalDate hoy = LocalDate.now();
                            long diasCongelados = ChronoUnit.DAYS.between(cong.getFechaInicio(), hoy);
                            LocalDate fechaFinAnterior = mem.getFechaFin();
                            LocalDate fechaFinNueva = fechaFinAnterior.plusDays(diasCongelados);

                            cong.setFechaFin(hoy);

                            long diasRestantes = ChronoUnit.DAYS.between(hoy, fechaFinNueva);
                            Cliente.Estado nuevoEstadoCliente = diasRestantes <= 3
                                    ? Cliente.Estado.proximo_vencer
                                    : Cliente.Estado.activo;

                            return congelamientoRepository.save(cong)
                                    .flatMap(c -> {
                                        mem.setEstado(Membresia.Estado.activa);
                                        mem.setFechaFin(fechaFinNueva);
                                        return membresiaRepository.save(mem);
                                    })
                                    .flatMap(m -> clienteRepository.findById(m.getIdCliente())
                                            .flatMap(cli -> {
                                                cli.setEstado(nuevoEstadoCliente);
                                                return clienteRepository.save(cli);
                                            })
                                    )
                                    .thenReturn(new ReactivarResult(fechaFinAnterior, (int) diasCongelados, fechaFinNueva));
                        })
                );
    }

    @Override
    public Mono<ReactivarResult> reactivarPorCliente(Long idCongelamiento, Long idPersona, Long idCompania) {
        return congelamientoRepository.findById(idCongelamiento)
                .switchIfEmpty(Mono.error(new NotFoundException("Congelamiento", idCongelamiento)))
                .flatMap(cong -> membresiaRepository.findById(cong.getIdMembresia())
                        .flatMap(mem -> clienteRepository.findById(mem.getIdCliente())
                                .flatMap(cli -> {
                                    if (!idPersona.equals(cli.getIdPersona())) {
                                        return Mono.error(new ForbiddenException("No tienes permiso para esta operación"));
                                    }
                                    LocalDate hoy = LocalDate.now();
                                    long diasCongelados = ChronoUnit.DAYS.between(cong.getFechaInicio(), hoy);
                                    LocalDate fechaFinAnterior = mem.getFechaFin();
                                    LocalDate fechaFinNueva = fechaFinAnterior.plusDays(diasCongelados);
                                    cong.setFechaFin(hoy);
                                    long diasRestantes = ChronoUnit.DAYS.between(hoy, fechaFinNueva);
                                    Cliente.Estado nuevoEstadoCliente = diasRestantes <= 3
                                            ? Cliente.Estado.proximo_vencer
                                            : Cliente.Estado.activo;
                                    return congelamientoRepository.save(cong)
                                            .flatMap(c -> {
                                                mem.setEstado(Membresia.Estado.activa);
                                                mem.setFechaFin(fechaFinNueva);
                                                return membresiaRepository.save(mem);
                                            })
                                            .flatMap(m -> {
                                                cli.setEstado(nuevoEstadoCliente);
                                                return clienteRepository.save(cli);
                                            })
                                            .thenReturn(new ReactivarResult(fechaFinAnterior, (int) diasCongelados, fechaFinNueva));
                                })
                        )
                );
    }

    @Override
    public Flux<Congelamiento> historialPorMembresia(Long idMembresia, Long idCompania) {
        return congelamientoRepository.findByIdMembresia(idMembresia);
    }
}
