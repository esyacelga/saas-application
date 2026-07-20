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
import com.gymadmin.core.infrastructure.exception.CodedException;
import com.gymadmin.core.infrastructure.exception.ConflictException;
import com.gymadmin.core.infrastructure.exception.DatosVentaIncompletosException;
import com.gymadmin.core.infrastructure.exception.ErrorCode;
import com.gymadmin.core.infrastructure.exception.NotFoundException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    public Flux<MembresiaHistorialItem> historialPorCliente(Long idCliente, Long idCompania) {
        return membresiaRepository.findAllByIdClienteAndIdCompania(idCliente, idCompania)
                .concatMap(this::enriquecerHistorial);
    }

    @Override
    public Flux<MembresiaHistorialItem> historialPorPersona(Long idPersona, Long idCompania) {
        return clienteRepository.findByIdPersonaAndIdCompania(idPersona, idCompania)
                .switchIfEmpty(Mono.error(new NotFoundException("Cliente en esta compañía", idPersona)))
                .flatMapMany(cliente -> historialPorCliente(cliente.getId(), idCompania));
    }

    /**
     * Enriquece una membresía del historial con:
     * <ul>
     *   <li>{@code tipoNombre}, {@code modoControl} — lookup en {@code core.tipos_membresia}.</li>
     *   <li>{@code montoPagado} / {@code saldoPendiente} — derivados de {@code estado_pago}: cuando
     *       {@code PAGADO} el monto pagado es {@code precio_pagado} y el saldo es 0; cuando
     *       {@code PENDIENTE} el monto pagado es 0 y el saldo es {@code precio_pagado}. Consistente
     *       con la única fuente de verdad de cobro que hay hoy (HU {@code estado-pago-membresias}
     *       §4.2). Cuando se cree {@code core.pagos} (HU-C) esta lógica se re-derivará.</li>
     *   <li>{@code diasAccesoUsados}/{@code diasAccesoRestantes} — solo para {@code modo_control = accesos};
     *       null para {@code calendario}.</li>
     * </ul>
     */
    private Mono<MembresiaHistorialItem> enriquecerHistorial(Membresia mem) {
        BigDecimal precio = mem.getPrecioPagado() != null ? mem.getPrecioPagado() : BigDecimal.ZERO;
        boolean pagado = Membresia.EstadoPago.PAGADO.equals(mem.getEstadoPago());
        BigDecimal montoPagado = pagado ? precio : BigDecimal.ZERO;
        BigDecimal saldoPendiente = pagado ? BigDecimal.ZERO : precio;

        return tipoMembresiaRepository.findById(mem.getIdTipoMembresia())
                .flatMap(tipo -> {
                    if (TipoMembresia.ModoControl.accesos.equals(tipo.getModoControl())
                            && mem.getDiasAccesoTotal() != null) {
                        return membresiaRepository.countAsistenciasByIdMembresia(mem.getId())
                                .map(usados -> new MembresiaHistorialItem(
                                        mem, tipo.getNombre(), tipo.getModoControl().name(),
                                        montoPagado, saldoPendiente,
                                        usados.intValue(), mem.getDiasAccesoTotal() - usados.intValue()
                                ));
                    }
                    return Mono.just(new MembresiaHistorialItem(
                            mem, tipo.getNombre(), tipo.getModoControl().name(),
                            montoPagado, saldoPendiente, null, null
                    ));
                })
                .switchIfEmpty(Mono.just(new MembresiaHistorialItem(
                        mem, null, null, montoPagado, saldoPendiente, null, null
                )));
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
                            mem.setOrigen(Membresia.Origen.staff);
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
     * <p>Cuando la membresía es {@code origen='cliente'} (solicitud autoservicio con
     * placeholders), el {@code command} debe traer {@code id_metodo_pago},
     * {@code precio_pagado} y {@code fecha_inicio}; si falta alguno lanza
     * {@link DatosVentaIncompletosException} (400 + {@code codigo=datos_venta_incompletos}
     * + lista de campos faltantes). {@code descuento_aplicado} default 0.
     *
     * <p>Cuando la membresía es {@code origen='staff'} (venta directa PENDIENTE), el body
     * se ignora completamente y se aplica el comportamiento clásico: {@code fecha_inicio=hoy}
     * y {@code precio_pagado} se preserva del vender().
     *
     * <p>Publica {@link com.gymadmin.core.domain.event.MembresiaPagadaEvent} vía
     * {@link ApplicationEventPublisher} SOLO en la transición real PENDIENTE → PAGADO.
     */
    @Override
    public Mono<Membresia> confirmarPago(Long idMembresia, Long idCompania, Long idUsuarioActuante,
                                          MembresiaUseCase.ConfirmarPagoCommand command) {
        MembresiaUseCase.ConfirmarPagoCommand cmd = command != null
                ? command : MembresiaUseCase.ConfirmarPagoCommand.empty();
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
                    boolean origenCliente = Membresia.Origen.cliente.equals(mem.getOrigen());
                    if (origenCliente) {
                        List<Map<String, String>> faltantes = validarDatosVenta(cmd);
                        if (!faltantes.isEmpty()) {
                            return Mono.error(new DatosVentaIncompletosException(
                                    "Faltan datos de venta para confirmar la solicitud del cliente", faltantes));
                        }
                    }
                    return tipoMembresiaRepository.findById(mem.getIdTipoMembresia())
                            .switchIfEmpty(Mono.error(new NotFoundException("TipoMembresia", mem.getIdTipoMembresia())))
                            .flatMap(tipo -> {
                                LocalDate inicio = origenCliente ? cmd.fechaInicio() : LocalDate.now();
                                LocalDate fin = calcularFechaFin(inicio, tipo);
                                mem.setEstadoPago(Membresia.EstadoPago.PAGADO);
                                mem.setFechaInicio(inicio);
                                mem.setFechaFin(fin);
                                if (mem.getEstado() == null) {
                                    mem.setEstado(Membresia.Estado.activa);
                                }
                                if (origenCliente) {
                                    // Datos ingresados por el staff al completar la solicitud.
                                    mem.setIdMetodoPago(cmd.idMetodoPago());
                                    mem.setPrecioPagado(cmd.precioPagado());
                                    mem.setDescuentoAplicado(cmd.descuentoAplicado() != null
                                            ? cmd.descuentoAplicado() : BigDecimal.ZERO);
                                    if (TipoMembresia.ModoControl.accesos.equals(tipo.getModoControl())
                                            && mem.getDiasAccesoTotal() == null) {
                                        mem.setDiasAccesoTotal(tipo.getDiasAcceso());
                                    }
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
                                                saved.getPrecioPagado(), inicio
                                        )));
                            });
                });
    }

    /**
     * Devuelve la lista de campos faltantes en un body de {@code confirmar-pago} para una
     * solicitud de origen cliente. Cada entrada tiene {@code campo} y {@code mensaje} para
     * que el frontend pinte errores por-campo. {@code descuento_aplicado} es opcional
     * (default 0), no se valida.
     */
    private List<Map<String, String>> validarDatosVenta(MembresiaUseCase.ConfirmarPagoCommand cmd) {
        List<Map<String, String>> faltantes = new ArrayList<>();
        if (cmd.idMetodoPago() == null) {
            faltantes.add(campo("id_metodo_pago", "es obligatorio para completar la venta"));
        }
        if (cmd.precioPagado() == null || cmd.precioPagado().signum() < 0) {
            faltantes.add(campo("precio_pagado", "es obligatorio y debe ser >= 0"));
        }
        if (cmd.fechaInicio() == null) {
            faltantes.add(campo("fecha_inicio", "es obligatoria para iniciar la membresía"));
        }
        return faltantes;
    }

    private Map<String, String> campo(String nombre, String mensaje) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("campo", nombre);
        m.put("mensaje", mensaje);
        return m;
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

    /**
     * Cliente PWA solicita una membresía autoservicio. Valida en orden:
     * <ol>
     *   <li>El {@code id_persona} debe corresponder a un cliente de la compañía (si no → 404).</li>
     *   <li>El cliente no debe tener otra solicitud viva {@code origen=cliente, estado_pago=PENDIENTE}
     *       (si sí → 409 {@code solicitud_ya_existe}).</li>
     *   <li>El cliente no debe tener una membresía activa vigente PAGADA (si sí → 409
     *       {@code membresia_activa_vigente}).</li>
     *   <li>El tipo de membresía debe existir, estar activo y pertenecer a la compañía
     *       (si no → 404 {@code tipo_membresia_no_disponible}).</li>
     * </ol>
     *
     * <p>Crea la fila con {@code estado_pago=PENDIENTE}, {@code origen=cliente}, fechas NULL
     * (respeta el CHECK {@code ck_membresias_fechas_por_estado_pago}), {@code precio_pagado=0}
     * placeholder (columna NOT NULL sin default; se sobrescribe en {@code confirmar-pago}),
     * {@code descuento=0}, {@code id_metodo_pago=NULL}.
     */
    @Override
    public Mono<Membresia> solicitarMembresia(Long idPersona, Long idCompania, Long idSucursal,
                                               Long idTipoMembresia) {
        return clienteRepository.findByIdPersonaAndIdCompania(idPersona, idCompania)
                .switchIfEmpty(Mono.error(new NotFoundException("Cliente en esta compañía", idPersona)))
                .flatMap(cliente -> membresiaRepository
                        .findSolicitudClientePendiente(cliente.getId(), idCompania)
                        .flatMap(existing -> Mono.<Membresia>error(new CodedException(
                                ErrorCode.SOLICITUD_YA_EXISTE,
                                "Ya tienes una solicitud de membresía en revisión. Espera a que sea confirmada o rechazada.")))
                        .switchIfEmpty(Mono.defer(() -> membresiaRepository
                                .findActivaByIdClienteAndIdCompania(cliente.getId(), idCompania)
                                .filter(activa -> activa.getFechaFin() != null
                                        && !activa.getFechaFin().isBefore(LocalDate.now()))
                                .flatMap(activa -> Mono.<Membresia>error(new CodedException(
                                        ErrorCode.MEMBRESIA_ACTIVA_VIGENTE,
                                        "No puedes solicitar una nueva membresía mientras tengas una activa. Espera a que venza.")))
                                .switchIfEmpty(Mono.defer(() -> crearSolicitud(cliente.getId(), idCompania,
                                        idSucursal, idTipoMembresia))))));
    }

    private Mono<Membresia> crearSolicitud(Long idCliente, Long idCompania, Long idSucursal,
                                           Long idTipoMembresia) {
        return tipoMembresiaRepository.findById(idTipoMembresia)
                .filter(tipo -> Boolean.TRUE.equals(tipo.getActivo())
                        && idCompania.equals(tipo.getIdCompania()))
                .switchIfEmpty(Mono.error(new CodedException(
                        ErrorCode.TIPO_MEMBRESIA_NO_DISPONIBLE,
                        "El tipo que solicitaste no está disponible o ya no es ofrecido.")))
                .flatMap(tipo -> {
                    Membresia mem = new Membresia();
                    mem.setIdCompania(idCompania);
                    mem.setIdSucursal(idSucursal);
                    mem.setIdCliente(idCliente);
                    mem.setIdTipoMembresia(tipo.getId());
                    mem.setIdMetodoPago(null);
                    // fechas NULL (CHECK ck_membresias_fechas_por_estado_pago)
                    mem.setFechaInicio(null);
                    mem.setFechaFin(null);
                    // dias_acceso_total se rellena al confirmar-pago si el tipo es accesos
                    mem.setDiasAccesoTotal(null);
                    // precio_pagado NOT NULL sin default → placeholder 0, sobrescrito al confirmar
                    mem.setPrecioPagado(BigDecimal.ZERO);
                    mem.setDescuentoAplicado(BigDecimal.ZERO);
                    mem.setEstado(Membresia.Estado.activa);
                    mem.setEstadoPago(Membresia.EstadoPago.PENDIENTE);
                    mem.setOrigen(Membresia.Origen.cliente);
                    mem.setEliminado(Boolean.FALSE);
                    return membresiaRepository.save(mem);
                });
    }

    /**
     * Cuenta las membresías pendientes de la compañía agrupadas por origen. Devuelve 0
     * para el origen que no tenga filas. Los tokens de plataforma / cross-tenant se
     * bloquean antes de llegar aquí (controlador con {@code requireRecepcionOrAbove}).
     */
    @Override
    public Mono<MembresiaUseCase.ContadorPendientesResult> contarPendientesPorCompania(Long idCompania) {
        return membresiaRepository.contarPendientesPorOrigen(idCompania)
                .map(porOrigen -> {
                    long cliente = porOrigen.getOrDefault(Membresia.Origen.cliente.name(), 0L);
                    long staff = porOrigen.getOrDefault(Membresia.Origen.staff.name(), 0L);
                    return new MembresiaUseCase.ContadorPendientesResult(cliente + staff, cliente, staff);
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
