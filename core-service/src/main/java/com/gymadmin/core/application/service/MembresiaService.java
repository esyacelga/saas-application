package com.gymadmin.core.application.service;

import com.gymadmin.core.domain.model.Cliente;
import com.gymadmin.core.domain.model.Membresia;
import com.gymadmin.core.domain.model.TipoMembresia;
import com.gymadmin.core.domain.port.in.MembresiaUseCase;
import com.gymadmin.core.domain.port.out.ClienteRepository;
import com.gymadmin.core.domain.port.out.MembresiaRepository;
import com.gymadmin.core.domain.port.out.TipoMembresiaRepository;
import com.gymadmin.core.infrastructure.exception.BusinessException;
import com.gymadmin.core.infrastructure.exception.ConflictException;
import com.gymadmin.core.infrastructure.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
public class MembresiaService implements MembresiaUseCase {

    private final MembresiaRepository membresiaRepository;
    private final TipoMembresiaRepository tipoMembresiaRepository;
    private final ClienteRepository clienteRepository;

    public MembresiaService(MembresiaRepository membresiaRepository,
                            TipoMembresiaRepository tipoMembresiaRepository,
                            ClienteRepository clienteRepository) {
        this.membresiaRepository = membresiaRepository;
        this.tipoMembresiaRepository = tipoMembresiaRepository;
        this.clienteRepository = clienteRepository;
    }

    @Override
    public Flux<Membresia> historialPorCliente(Long idCliente, Long idCompania) {
        return membresiaRepository.findByIdCliente(idCliente);
    }

    @Override
    public Mono<MembresiaDetalleResult> detalle(Long id, Long idCompania) {
        return membresiaRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Membresia", id)))
                .flatMap(mem -> tipoMembresiaRepository.findById(mem.getIdTipoMembresia())
                        .flatMap(tipo -> {
                            if (TipoMembresia.ModoControl.accesos.equals(tipo.getModoControl())) {
                                return membresiaRepository.countAsistenciasByIdMembresia(id)
                                        .map(usados -> {
                                            int restantes = mem.getDiasAccesoTotal() - usados.intValue();
                                            return new MembresiaDetalleResult(mem, tipo.getNombre(),
                                                    tipo.getModoControl().name(), usados.intValue(), restantes);
                                        });
                            }
                            return Mono.just(new MembresiaDetalleResult(mem, tipo.getNombre(),
                                    tipo.getModoControl().name(), null, null));
                        })
                );
    }

    @Override
    public Mono<Membresia> vender(Long idCliente, Long idCompania, Long idSucursal, Long idUsuario, VenderCommand command) {
        return membresiaRepository.findActivaByIdClienteAndIdCompania(idCliente, idCompania)
                .flatMap(existing -> Mono.<Membresia>error(new ConflictException("El cliente ya tiene una membresía activa")))
                .switchIfEmpty(Mono.defer(() ->
                        tipoMembresiaRepository.findById(command.idTipoMembresia())
                                .switchIfEmpty(Mono.error(new NotFoundException("TipoMembresia", command.idTipoMembresia())))
                                .flatMap(tipo -> {
                                    LocalDate fechaFin = calcularFechaFin(command.fechaInicio(), tipo);

                                    Membresia mem = new Membresia();
                                    mem.setIdCompania(idCompania);
                                    mem.setIdSucursal(idSucursal);
                                    mem.setIdCliente(idCliente);
                                    mem.setIdTipoMembresia(tipo.getId());
                                    mem.setIdMetodoPago(command.idMetodoPago());
                                    mem.setIdUsuarioRegistro(idUsuario);
                                    mem.setFechaInicio(command.fechaInicio());
                                    mem.setFechaFin(fechaFin);
                                    mem.setDiasAccesoTotal(TipoMembresia.ModoControl.accesos.equals(tipo.getModoControl())
                                            ? tipo.getDiasAcceso() : null);
                                    java.math.BigDecimal descuento = command.descuentoAplicado() != null
                                            ? command.descuentoAplicado() : java.math.BigDecimal.ZERO;
                                    mem.setPrecioPagado(tipo.getPrecio().subtract(descuento));
                                    mem.setDescuentoAplicado(descuento);
                                    mem.setEstado(Membresia.Estado.activa);

                                    return membresiaRepository.save(mem)
                                            .flatMap(saved -> clienteRepository.findById(idCliente)
                                                    .flatMap(cliente -> {
                                                        cliente.setEstado(Cliente.Estado.activo);
                                                        return clienteRepository.save(cliente).thenReturn(saved);
                                                    })
                                            );
                                })
                ));
    }

    @Override
    public Mono<Void> anular(Long id, Long idCompania, String motivo) {
        return membresiaRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Membresia", id)))
                .flatMap(mem -> {
                    if (!idCompania.equals(mem.getIdCompania())) {
                        return Mono.error(new com.gymadmin.core.infrastructure.exception.ForbiddenException("Access denied"));
                    }
                    mem.setEstado(Membresia.Estado.anulada);
                    return membresiaRepository.save(mem)
                            .flatMap(saved -> clienteRepository.findById(saved.getIdCliente())
                                    .flatMap(cliente -> {
                                        cliente.setEstado(Cliente.Estado.vencido);
                                        return clienteRepository.save(cliente);
                                    })
                            ).then();
                });
    }

    @Override
    public Mono<ValidarAccesoResult> validarAcceso(Long idPersona, Long idCompania) {
        log.info("[validarAcceso] INICIO idPersona={} idCompania={}", idPersona, idCompania);
        return clienteRepository.findByIdPersonaAndIdCompania(idPersona, idCompania)
                .doOnNext(c -> log.info("[validarAcceso] cliente encontrado idCliente={} idPersona={} idCompania={}", c.getId(), idPersona, idCompania))
                .flatMap(cliente -> membresiaRepository.findActivaByIdClienteAndIdCompania(cliente.getId(), idCompania)
                        .doOnNext(m -> log.info("[validarAcceso] membresía activa encontrada idMembresia={} idCliente={} estado={} fechaFin={}", m.getId(), cliente.getId(), m.getEstado(), m.getFechaFin()))
                        .flatMap(mem -> evaluarMembresia(mem, cliente.getId()))
                        .switchIfEmpty(Mono.defer(() -> {
                            log.warn("[validarAcceso] sin membresía activa idCliente={} idPersona={} idCompania={}", cliente.getId(), idPersona, idCompania);
                            return Mono.just(buildDenegado(cliente.getId(), null, "sin_membresia", null, null, null, null));
                        })))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("[validarAcceso] cliente NO encontrado idPersona={} idCompania={}", idPersona, idCompania);
                    return Mono.just(buildDenegado(null, null, "sin_membresia", null, null, null, null));
                }));
    }

    private Mono<ValidarAccesoResult> evaluarMembresia(Membresia mem, Long idCliente) {
        if (Membresia.Estado.congelada.equals(mem.getEstado())) {
            return Mono.just(buildDenegado(idCliente, mem.getFechaFin(), "membresia_congelada", null, null, null, mem.getId()));
        }
        if (Membresia.Estado.vencida.equals(mem.getEstado()) || LocalDate.now().isAfter(mem.getFechaFin())) {
            return Mono.just(buildDenegado(idCliente, mem.getFechaFin(), "membresia_vencida", null, null, null, mem.getId()));
        }
        return tipoMembresiaRepository.findById(mem.getIdTipoMembresia())
                .flatMap(tipo -> {
                    if (TipoMembresia.ModoControl.accesos.equals(tipo.getModoControl())) {
                        return membresiaRepository.countAsistenciasByIdMembresia(mem.getId())
                                .map(usados -> {
                                    if (usados >= mem.getDiasAccesoTotal()) {
                                        return new ValidarAccesoResult(false, idCliente, null,
                                                tipo.getModoControl().name(), tipo.getNombre(), null, mem.getFechaFin(),
                                                "accesos_agotados", usados.intValue(), mem.getDiasAccesoTotal());
                                    }
                                    int restantes = mem.getDiasAccesoTotal() - usados.intValue();
                                    return new ValidarAccesoResult(true, idCliente, mem.getId(),
                                            tipo.getModoControl().name(), tipo.getNombre(), restantes, mem.getFechaFin(),
                                            null, usados.intValue(), mem.getDiasAccesoTotal());
                                });
                    }
                    long diasRestantes = ChronoUnit.DAYS.between(LocalDate.now(), mem.getFechaFin());
                    return Mono.just(new ValidarAccesoResult(true, idCliente, mem.getId(),
                            tipo.getModoControl().name(), tipo.getNombre(), (int) diasRestantes, mem.getFechaFin(),
                            null, null, null));
                });
    }

    private ValidarAccesoResult buildDenegado(Long idCliente, LocalDate fechaFin, String razon,
                                               Integer diasRestantes, Integer accesosUsados,
                                               Integer accesosTotal, Long idMembresia) {
        return new ValidarAccesoResult(false, idCliente, idMembresia, null, null, diasRestantes, fechaFin,
                razon, accesosUsados, accesosTotal);
    }

    @Override
    public Mono<Membresia> actualizarAsistenciasPrevias(Long id, Long idCompania, Integer cantidad) {
        return membresiaRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Membresia", id)))
                .flatMap(mem -> {
                    if (!idCompania.equals(mem.getIdCompania())) {
                        return Mono.error(new com.gymadmin.core.infrastructure.exception.ForbiddenException("Access denied"));
                    }
                    mem.setAsistenciasPrevias(cantidad);
                    return membresiaRepository.save(mem);
                });
    }

    private LocalDate calcularFechaFin(LocalDate fechaInicio, TipoMembresia tipo) {
        return switch (tipo.getDuracionTipo()) {
            case dias -> fechaInicio.plusDays(tipo.getDuracionValor());
            case semanas -> fechaInicio.plusWeeks(tipo.getDuracionValor());
            case meses -> fechaInicio.plusMonths(tipo.getDuracionValor());
            case años -> fechaInicio.plusYears(tipo.getDuracionValor());
        };
    }
}
