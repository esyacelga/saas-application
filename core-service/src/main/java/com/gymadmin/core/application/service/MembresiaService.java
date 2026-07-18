package com.gymadmin.core.application.service;

import com.gymadmin.core.domain.event.MembresiaPagadaEvent;
import com.gymadmin.core.domain.model.Cliente;
import com.gymadmin.core.domain.model.Membresia;
import com.gymadmin.core.domain.model.TipoMembresia;
import com.gymadmin.core.domain.port.in.MembresiaUseCase;
import com.gymadmin.core.domain.port.out.ClienteRepository;
import com.gymadmin.core.domain.port.out.MembresiaRepository;
import com.gymadmin.core.domain.port.out.PersonaRepository;
import com.gymadmin.core.domain.port.out.TipoMembresiaRepository;
import com.gymadmin.core.infrastructure.exception.BusinessException;
import com.gymadmin.core.infrastructure.exception.ConflictException;
import com.gymadmin.core.infrastructure.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final PersonaRepository personaRepository;
    private final ApplicationEventPublisher eventPublisher;

    public MembresiaService(MembresiaRepository membresiaRepository,
                            TipoMembresiaRepository tipoMembresiaRepository,
                            ClienteRepository clienteRepository,
                            PersonaRepository personaRepository,
                            ApplicationEventPublisher eventPublisher) {
        this.membresiaRepository = membresiaRepository;
        this.tipoMembresiaRepository = tipoMembresiaRepository;
        this.clienteRepository = clienteRepository;
        this.personaRepository = personaRepository;
        this.eventPublisher = eventPublisher;
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
        Membresia.EstadoPago estadoPago = command.estadoPago() != null
                ? command.estadoPago() : Membresia.EstadoPago.PAGADO;

        Mono<Void> gate = (estadoPago == Membresia.EstadoPago.PENDIENTE)
                ? membresiaRepository.findPendienteVivaByIdCliente(idCliente, idCompania)
                        .flatMap(existing -> Mono.<Void>error(new ConflictException(
                                "El cliente ya tiene una membresía pendiente de pago")))
                        .switchIfEmpty(Mono.empty())
                        .then()
                : membresiaRepository.findActivaByIdClienteAndIdCompania(idCliente, idCompania)
                        .flatMap(existing -> Mono.<Void>error(new ConflictException(
                                "El cliente ya tiene una membresía activa")))
                        .switchIfEmpty(Mono.empty())
                        .then();

        return gate.then(Mono.defer(() ->
                tipoMembresiaRepository.findById(command.idTipoMembresia())
                        .switchIfEmpty(Mono.error(new NotFoundException("TipoMembresia", command.idTipoMembresia())))
                        .flatMap(tipo -> {
                            boolean pagado = estadoPago == Membresia.EstadoPago.PAGADO;
                            LocalDate inicio = pagado ? command.fechaInicio() : null;
                            LocalDate fin    = pagado ? calcularFechaFin(command.fechaInicio(), tipo) : null;

                            Membresia mem = new Membresia();
                            mem.setIdCompania(idCompania);
                            mem.setIdSucursal(idSucursal);
                            mem.setIdCliente(idCliente);
                            mem.setIdTipoMembresia(tipo.getId());
                            mem.setIdMetodoPago(command.idMetodoPago());
                            mem.setIdUsuarioRegistro(idUsuario);
                            mem.setFechaInicio(inicio);
                            mem.setFechaFin(fin);
                            mem.setDiasAccesoTotal(TipoMembresia.ModoControl.accesos.equals(tipo.getModoControl())
                                    ? tipo.getDiasAcceso() : null);
                            java.math.BigDecimal descuento = command.descuentoAplicado() != null
                                    ? command.descuentoAplicado() : java.math.BigDecimal.ZERO;
                            mem.setPrecioPagado(tipo.getPrecio().subtract(descuento));
                            mem.setDescuentoAplicado(descuento);
                            mem.setEstado(Membresia.Estado.activa);
                            mem.setEstadoPago(estadoPago);
                            mem.setEliminado(Boolean.FALSE);

                            return membresiaRepository.save(mem)
                                    .flatMap(saved -> {
                                        if (!pagado) {
                                            return Mono.just(saved);
                                        }
                                        return clienteRepository.findById(idCliente)
                                                .flatMap(cliente -> {
                                                    cliente.setEstado(Cliente.Estado.activo);
                                                    return clienteRepository.save(cliente).thenReturn(saved);
                                                })
                                                .defaultIfEmpty(saved)
                                                .doOnNext(m -> eventPublisher.publishEvent(new MembresiaPagadaEvent(
                                                        m.getId(), m.getIdCliente(), m.getIdCompania(),
                                                        m.getPrecioPagado(), LocalDate.now()
                                                )));
                                    });
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
                .flatMap(cliente -> resolverAcceso(cliente.getId(), idCompania))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("[validarAcceso] cliente NO encontrado idPersona={} idCompania={}", idPersona, idCompania);
                    return Mono.just(buildDenegado(null, null, "sin_membresia", null, null, null, null));
                }));
    }

    private Mono<ValidarAccesoResult> resolverAcceso(Long idCliente, Long idCompania) {
        // Orden de evaluación (§4.5): pago_pendiente y membresia_rechazada ANTES de sin_membresia/vencida.
        return membresiaRepository.findPendienteVivaByIdCliente(idCliente, idCompania)
                .<ValidarAccesoResult>map(pendiente -> {
                    log.info("[validarAcceso] membresía PENDIENTE viva idMembresia={} idCliente={}", pendiente.getId(), idCliente);
                    return buildDenegado(idCliente, null, "pago_pendiente", null, null, null, pendiente.getId());
                })
                .switchIfEmpty(Mono.defer(() -> membresiaRepository.findUltimaRechazadaByIdCliente(idCliente, idCompania)
                        .<ValidarAccesoResult>map(rechazada -> {
                            log.info("[validarAcceso] membresía RECHAZADA idMembresia={} idCliente={}", rechazada.getId(), idCliente);
                            return buildDenegado(idCliente, null, "membresia_rechazada", null, null, null, rechazada.getId());
                        })))
                .switchIfEmpty(Mono.defer(() -> membresiaRepository.findActivaByIdClienteAndIdCompania(idCliente, idCompania)
                        .doOnNext(m -> log.info("[validarAcceso] membresía activa encontrada idMembresia={} idCliente={} estado={} fechaFin={}",
                                m.getId(), idCliente, m.getEstado(), m.getFechaFin()))
                        .flatMap(mem -> evaluarMembresia(mem, idCliente))
                        .switchIfEmpty(Mono.defer(() -> {
                            log.warn("[validarAcceso] sin membresía activa idCliente={}", idCliente);
                            return Mono.just(buildDenegado(idCliente, null, "sin_membresia", null, null, null, null));
                        }))));
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

    /**
     * §4.6 — Confirma el pago de una membresía. Idempotente: si ya está PAGADO
     * devuelve el recurso sin re-calcular fechas ni re-emitir eventos. Si está
     * eliminada (rechazada), lanza {@link ConflictException}.
     *
     * <p>Publica {@link com.gymadmin.core.domain.event.MembresiaPagadaEvent} vía
     * {@link ApplicationEventPublisher} SOLO en la transición real PENDIENTE → PAGADO.
     */
    @Override
    public Mono<Membresia> confirmarPago(Long idMembresia, Long idCompania, Long idUsuarioActuante) {
        return membresiaRepository.findById(idMembresia)
                .switchIfEmpty(Mono.error(new NotFoundException("Membresia", idMembresia)))
                .flatMap(mem -> {
                    if (!idCompania.equals(mem.getIdCompania())) {
                        return Mono.error(new com.gymadmin.core.infrastructure.exception.ForbiddenException("Access denied"));
                    }
                    if (Boolean.TRUE.equals(mem.getEliminado())) {
                        return Mono.error(new ConflictException("La membresía fue rechazada y no puede confirmarse"));
                    }
                    if (Membresia.EstadoPago.PAGADO.equals(mem.getEstadoPago())) {
                        // Idempotente: retorna el recurso actual sin recalcular fechas ni emitir evento.
                        return Mono.just(mem);
                    }
                    return tipoMembresiaRepository.findById(mem.getIdTipoMembresia())
                            .switchIfEmpty(Mono.error(new NotFoundException("TipoMembresia", mem.getIdTipoMembresia())))
                            .flatMap(tipo -> {
                                LocalDate hoy = LocalDate.now();
                                LocalDate fin = calcularFechaFin(hoy, tipo);
                                mem.setEstadoPago(Membresia.EstadoPago.PAGADO);
                                mem.setFechaInicio(hoy);
                                mem.setFechaFin(fin);
                                if (mem.getEstado() == null) {
                                    mem.setEstado(Membresia.Estado.activa);
                                }
                                return membresiaRepository.save(mem)
                                        .flatMap(saved -> clienteRepository.findById(saved.getIdCliente())
                                                .flatMap(cliente -> {
                                                    cliente.setEstado(Cliente.Estado.activo);
                                                    return clienteRepository.save(cliente).thenReturn(saved);
                                                })
                                                .defaultIfEmpty(saved))
                                        .doOnNext(saved -> eventPublisher.publishEvent(new MembresiaPagadaEvent(
                                                saved.getId(), saved.getIdCliente(), saved.getIdCompania(),
                                                saved.getPrecioPagado(), hoy
                                        )));
                            });
                });
    }

    /**
     * §4.7 — Rechaza una membresía PENDIENTE (soft-delete con auditoría). No aplica
     * si la membresía ya está PAGADA (409) o ya fue eliminada (409).
     */
    @Override
    public Mono<Membresia> rechazar(Long idMembresia, Long idCompania, Long idUsuarioActuante,
                                    Membresia.MotivoEliminacion motivoEliminacion) {
        if (motivoEliminacion == null) {
            return Mono.error(new BusinessException("motivo_eliminacion es obligatorio"));
        }
        return membresiaRepository.findById(idMembresia)
                .switchIfEmpty(Mono.error(new NotFoundException("Membresia", idMembresia)))
                .flatMap(mem -> {
                    if (!idCompania.equals(mem.getIdCompania())) {
                        return Mono.error(new com.gymadmin.core.infrastructure.exception.ForbiddenException("Access denied"));
                    }
                    if (Boolean.TRUE.equals(mem.getEliminado())) {
                        return Mono.error(new ConflictException("La membresía ya fue rechazada"));
                    }
                    if (Membresia.EstadoPago.PAGADO.equals(mem.getEstadoPago())) {
                        return Mono.error(new ConflictException(
                                "No se puede rechazar una membresía pagada; usar anulación"));
                    }
                    mem.setEliminado(Boolean.TRUE);
                    mem.setFechaEliminacion(java.time.OffsetDateTime.now());
                    mem.setEliminadoPor(idUsuarioActuante != null ? idUsuarioActuante.intValue() : null);
                    mem.setMotivoEliminacion(motivoEliminacion);
                    return membresiaRepository.save(mem);
                });
    }

    /**
     * §4.8 — Lista de membresías PENDIENTES vivas por compañía (dashboard "Ventas pendientes").
     * Enriquece cada fila con el nombre y modo de control del tipo de membresía.
     */
    @Override
    public Flux<MembresiaPendienteResult> listarPendientesPorCompania(Long idCompania) {
        return membresiaRepository.findPendientesPorCompania(idCompania)
                .flatMap(mem -> {
                    Mono<java.util.Optional<TipoMembresia>> tipoMono = tipoMembresiaRepository
                            .findById(mem.getIdTipoMembresia())
                            .map(java.util.Optional::of)
                            .defaultIfEmpty(java.util.Optional.empty());
                    Mono<java.util.Optional<String>> nombreMono = clienteRepository.findById(mem.getIdCliente())
                            .flatMap(cliente -> personaRepository.findNombreById(cliente.getIdPersona()))
                            .map(java.util.Optional::of)
                            .defaultIfEmpty(java.util.Optional.empty());
                    return Mono.zip(tipoMono, nombreMono).map(tuple -> {
                        String tipoNombre = tuple.getT1().map(TipoMembresia::getNombre).orElse(null);
                        String modoControl = tuple.getT1()
                                .map(TipoMembresia::getModoControl)
                                .map(Enum::name)
                                .orElse(null);
                        String nombreCliente = tuple.getT2().orElse(null);
                        return new MembresiaPendienteResult(mem, tipoNombre, modoControl, nombreCliente);
                    });
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
