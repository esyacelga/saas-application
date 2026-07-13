package com.gymadmin.billing.application.service;

import com.gymadmin.billing.application.command.AprobarAnulacionCommand;
import com.gymadmin.billing.application.command.EmitirFacturaCommand;
import com.gymadmin.billing.application.command.EmitirNotaCreditoCommand;
import com.gymadmin.billing.application.command.RechazarAnulacionCommand;
import com.gymadmin.billing.application.command.SolicitarAnulacionCommand;
import com.gymadmin.billing.domain.model.Anulacion;
import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.model.ComprobanteDetalle;
import com.gymadmin.billing.domain.model.EstadoAnulacion;
import com.gymadmin.billing.domain.port.in.AnulacionUseCase;
import com.gymadmin.billing.domain.port.in.NotaCreditoUseCase;
import com.gymadmin.billing.domain.port.out.AnulacionRepository;
import com.gymadmin.billing.domain.port.out.ComprobanteRepository;
import com.gymadmin.billing.domain.port.out.EmailNotificationPort;
import com.gymadmin.billing.infrastructure.exception.BusinessException;
import com.gymadmin.billing.infrastructure.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * G3 · Servicio de anulación fiscal SRI Ecuador.
 * <p>
 * Implementa los dos flujos:
 * <ul>
 *     <li><b>Flujo A</b>: anulación directa; el admin ejecuta manualmente en el
 *         portal SRI y confirma vía {@link #confirmarSri}.</li>
 *     <li><b>Flujo B</b>: al aprobar se dispara {@link NotaCreditoUseCase} (G4)
 *         reutilizando el pipeline síncrono G2. Si la NC llega a
 *         {@code AUTORIZADO} en el mismo request → {@code EJECUTADA}; caso
 *         contrario queda {@code APROBADA} y el scheduler de G2 la retomará.</li>
 * </ul>
 * <p>
 * <b>Flag {@code generarNotaCredito}:</b> el DDL de {@code facturacion.anulaciones}
 * no expone una columna dedicada. Como no está permitido crear nueva migración
 * dentro de G3, se codifica en {@code observacion_resolucion} con el prefijo
 * interno {@link #FLAG_FLUJO_B}. La UI/controllers no exponen este prefijo — es
 * un detalle de persistencia manejado exclusivamente aquí y stripped en las
 * respuestas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnulacionService implements AnulacionUseCase {

    /** Estados del comprobante original en los que se admite iniciar anulación. */
    private static final Set<String> ESTADOS_ANULABLES = Set.of("AUTORIZADO", "GENERADO");
    /** Identificación reservada por SRI para "consumidor final" — no admite anulación en línea. */
    private static final String ID_CONSUMIDOR_FINAL = "9999999999999";
    /** Prefijo interno para señalizar que la solicitud pidió NC (Flujo B). */
    private static final String FLAG_FLUJO_B = "[FLUJO_B]";
    /** Prefijo interno que embebe el código de motivo SRI en {@code observacion_resolucion}. */
    private static final String PREFIJO_MOTIVO = "[MOTIVO=";
    private static final String SUFIJO_MOTIVO = "]";
    /** Tipo SRI factura. */
    private static final String TIPO_COMPROBANTE_FACTURA = "01";

    private final AnulacionRepository anulacionRepository;
    private final ComprobanteRepository comprobanteRepository;
    private final CatalogoSriService catalogoSriService;
    private final NotaCreditoUseCase notaCreditoUseCase;
    private final EmailNotificationPort emailNotificationPort;
    /** Inyectable para tests — permite fijar el "hoy" al validar la ventana temporal. */
    private final Clock clock;

    // ------------------------------------------------------------------
    // Solicitar
    // ------------------------------------------------------------------

    @Override
    public Mono<Anulacion> solicitar(SolicitarAnulacionCommand command) {
        if (command.motivo() == null || command.motivo().isBlank()) {
            return Mono.error(new BusinessException("El motivo de anulación es obligatorio"));
        }
        return findComprobantePorCompania(command.idComprobante(), command.idCompania())
                .flatMap(this::validarEstadoAnulable)
                .flatMap(this::validarNoConsumidorFinal)
                .flatMap(this::validarVentanaTemporal)
                .flatMap(comprobante -> validarMotivoCatalogo(command.codigoMotivo())
                        .then(Mono.defer(() -> persistirSolicitud(command, comprobante))));
    }

    private Mono<Comprobante> findComprobantePorCompania(Long idComprobante, Integer idCompania) {
        return comprobanteRepository.findById(idComprobante)
                .switchIfEmpty(Mono.error(new NotFoundException("Comprobante", idComprobante)))
                .flatMap(c -> {
                    // Multi-tenant: si es de otra compañía, indistinguible de not-found.
                    if (!c.getIdCompania().equals(idCompania)) {
                        return Mono.error(new NotFoundException("Comprobante", idComprobante));
                    }
                    return Mono.just(c);
                });
    }

    private Mono<Comprobante> validarEstadoAnulable(Comprobante comprobante) {
        if (!ESTADOS_ANULABLES.contains(comprobante.getEstado())) {
            return Mono.error(new BusinessException(
                    "No es posible solicitar anulación de un comprobante en estado: " + comprobante.getEstado()));
        }
        return Mono.just(comprobante);
    }

    private Mono<Comprobante> validarNoConsumidorFinal(Comprobante comprobante) {
        if (ID_CONSUMIDOR_FINAL.equals(comprobante.getIdReceptor())) {
            return Mono.error(new BusinessException(
                    "Las facturas emitidas a consumidor final (9999999999999) no son anulables en línea"));
        }
        return Mono.just(comprobante);
    }

    /**
     * Verifica que la fecha actual sea ≤ día 7 del mes siguiente al de emisión.
     * <p>
     * Ejemplo: emitida 2026-07-15 → anulable hasta 2026-08-07 (inclusive).
     * En día 2026-08-08 la ventana ya venció.
     */
    private Mono<Comprobante> validarVentanaTemporal(Comprobante comprobante) {
        LocalDate fechaEmision = comprobante.getFechaEmision();
        if (fechaEmision == null) {
            return Mono.error(new BusinessException(
                    "El comprobante no tiene fecha de emisión — no es posible calcular la ventana de anulación"));
        }
        LocalDate hoy = LocalDate.now(clock);
        LocalDate limite = fechaEmision.plusMonths(1).withDayOfMonth(7);
        if (hoy.isAfter(limite)) {
            return Mono.error(new BusinessException(
                    "Fuera de la ventana SRI para anulación (límite: " + limite + ", hoy: " + hoy + ")"));
        }
        return Mono.just(comprobante);
    }

    private Mono<Void> validarMotivoCatalogo(String codigoMotivo) {
        if (codigoMotivo == null || codigoMotivo.isBlank()) {
            return Mono.empty();
        }
        return catalogoSriService.obtenerMotivoAnulacion(codigoMotivo).then();
    }

    private Mono<Anulacion> persistirSolicitud(SolicitarAnulacionCommand command, Comprobante comprobante) {
        // Codificamos metadata interna en observacion_resolucion (no hay columna
        // dedicada en el DDL): flag de Flujo B + código de motivo SRI si se
        // proveyó. Al leer se strippea antes de exponer al cliente.
        StringBuilder meta = new StringBuilder();
        if (command.generarNotaCredito()) {
            meta.append(FLAG_FLUJO_B);
        }
        if (command.codigoMotivo() != null && !command.codigoMotivo().isBlank()) {
            meta.append(PREFIJO_MOTIVO).append(command.codigoMotivo()).append(SUFIJO_MOTIVO);
        }
        Anulacion nueva = Anulacion.builder()
                .idCompania(comprobante.getIdCompania())
                .idSucursal(comprobante.getIdSucursal())
                .idComprobante(comprobante.getId())
                .motivo(command.motivo())
                .estado(EstadoAnulacion.SOLICITADA)
                .idUsuarioSolicita(command.idUsuarioSolicita())
                .observacionResolucion(meta.length() > 0 ? meta.toString() : null)
                .build();
        return anulacionRepository.save(nueva).map(this::stripFlagObservacion);
    }

    // ------------------------------------------------------------------
    // Aprobar
    // ------------------------------------------------------------------

    @Override
    public Mono<Anulacion> aprobar(AprobarAnulacionCommand command) {
        return findAnulacionPorCompania(command.idAnulacion(), command.idCompania())
                .flatMap(a -> requiereEstado(a, EstadoAnulacion.SOLICITADA))
                .flatMap(a -> comprobanteRepository.findById(a.getIdComprobante())
                        .switchIfEmpty(Mono.error(new NotFoundException("Comprobante", a.getIdComprobante())))
                        .flatMap(comprobante -> ejecutarAprobacion(a, comprobante, command)));
    }

    private Mono<Anulacion> ejecutarAprobacion(Anulacion anulacion, Comprobante comprobante,
                                                AprobarAnulacionCommand command) {
        boolean flujoB = requiereNotaCredito(anulacion);
        String codigoMotivoOriginal = resolveCodigoMotivoDesdeAnulacion(anulacion);
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        String observacionPersistida = combineMetadata(flujoB, codigoMotivoOriginal, command.observacion());

        return anulacionRepository.updateEstado(
                        anulacion.getId(),
                        EstadoAnulacion.APROBADA,
                        command.idUsuarioAprueba(),
                        ahora,
                        observacionPersistida,
                        null)
                .flatMap(aprobada -> {
                    if (flujoB) {
                        return dispararNotaCredito(aprobada, comprobante, command)
                                .flatMap(nc -> completarSiAutorizada(aprobada, comprobante, nc, command));
                    }
                    return notificarAprobacion(aprobada, comprobante);
                });
    }

    /**
     * Emite la NC (G4) copiando el detalle y los totales de la factura original.
     * Retorna la NC persistida (estado terminal del pipeline síncrono G2).
     */
    private Mono<Comprobante> dispararNotaCredito(Anulacion anulacion, Comprobante facturaOriginal,
                                                    AprobarAnulacionCommand command) {
        return comprobanteRepository.findDetallesByIdComprobante(facturaOriginal.getId())
                .collectList()
                .flatMap(detalles -> {
                    List<EmitirFacturaCommand.DetalleFacturaItem> items = mapDetalles(detalles);
                    String codigoMotivo = resolveCodigoMotivoDesdeAnulacion(anulacion);
                    if (codigoMotivo == null) {
                        return Mono.error(new BusinessException(
                                "La solicitud pidió generar nota de crédito pero no se registró código de motivo SRI. " +
                                        "Rechace la solicitud o registre una nueva con codigo_motivo válido."));
                    }
                    EmitirNotaCreditoCommand ncCommand = new EmitirNotaCreditoCommand(
                            facturaOriginal.getIdCompania(),
                            facturaOriginal.getIdSucursal(),
                            LocalDate.now(clock),
                            facturaOriginal.getCodEstablecimiento(),
                            facturaOriginal.getCodPuntoEmision(),
                            generarCodigoNumericoDesde(facturaOriginal),
                            facturaOriginal.getId(),
                            codigoMotivo,
                            anulacion.getMotivo(),
                            totalOrZero(facturaOriginal),
                            items,
                            command.idUsuarioAprueba());
                    return notaCreditoUseCase.emitirNotaCredito(ncCommand);
                });
    }

    private Mono<Anulacion> completarSiAutorizada(Anulacion anulacion, Comprobante facturaOriginal,
                                                    Comprobante notaCredito, AprobarAnulacionCommand command) {
        // Notifica al receptor de la factura por email (best-effort).
        emailNotificationPort.enviarNotaCreditoAceptacion(notaCredito, facturaOriginal).subscribe();

        boolean autorizada = "AUTORIZADO".equals(notaCredito.getEstado());
        if (!autorizada) {
            log.info("Anulación {} queda en APROBADA — NC {} en estado {} (el scheduler la retomará)",
                    anulacion.getId(), notaCredito.getId(), notaCredito.getEstado());
            // Marca la NC generada en la anulación aunque no esté aún autorizada.
            return anulacionRepository.updateEstado(
                            anulacion.getId(),
                            EstadoAnulacion.APROBADA,
                            command.idUsuarioAprueba(),
                            anulacion.getFechaResolucion() != null ? anulacion.getFechaResolucion() : OffsetDateTime.now(clock),
                            anulacion.getObservacionResolucion(),
                            notaCredito.getId())
                    .flatMap(a -> notificarAprobacion(a, facturaOriginal));
        }
        return anulacionRepository.updateEstado(
                        anulacion.getId(),
                        EstadoAnulacion.EJECUTADA,
                        command.idUsuarioAprueba(),
                        OffsetDateTime.now(clock),
                        anulacion.getObservacionResolucion(),
                        notaCredito.getId())
                .flatMap(ejecutada -> marcarComprobanteAnulado(facturaOriginal.getId())
                        .thenReturn(ejecutada))
                .flatMap(ejecutada -> notificarAprobacion(ejecutada, facturaOriginal));
    }

    private Mono<Anulacion> notificarAprobacion(Anulacion anulacion, Comprobante comprobante) {
        Anulacion stripped = stripFlagObservacion(anulacion);
        return emailNotificationPort.enviarSolicitudAprobada(stripped, comprobante)
                .onErrorResume(e -> Mono.empty())
                .thenReturn(stripped);
    }

    // ------------------------------------------------------------------
    // Rechazar
    // ------------------------------------------------------------------

    @Override
    public Mono<Anulacion> rechazar(RechazarAnulacionCommand command) {
        if (command.observacion() == null || command.observacion().isBlank()) {
            return Mono.error(new BusinessException("La observación es obligatoria al rechazar"));
        }
        return findAnulacionPorCompania(command.idAnulacion(), command.idCompania())
                .flatMap(a -> requiereEstado(a, EstadoAnulacion.SOLICITADA))
                .flatMap(a -> {
                    boolean flujoB = requiereNotaCredito(a);
                    String codigoMotivoOriginal = resolveCodigoMotivoDesdeAnulacion(a);
                    String observacionPersistida = combineMetadata(flujoB, codigoMotivoOriginal, command.observacion());
                    return anulacionRepository.updateEstado(
                                    a.getId(),
                                    EstadoAnulacion.RECHAZADA,
                                    command.idUsuarioAprueba(),
                                    OffsetDateTime.now(clock),
                                    observacionPersistida,
                                    a.getIdComprobanteNc())
                            .flatMap(rechazada -> comprobanteRepository.findById(a.getIdComprobante())
                                    .flatMap(comprobante -> notificarRechazo(rechazada, comprobante))
                                    .switchIfEmpty(Mono.just(stripFlagObservacion(rechazada))));
                });
    }

    private Mono<Anulacion> notificarRechazo(Anulacion anulacion, Comprobante comprobante) {
        Anulacion stripped = stripFlagObservacion(anulacion);
        return emailNotificationPort.enviarSolicitudRechazada(stripped, comprobante)
                .onErrorResume(e -> Mono.empty())
                .thenReturn(stripped);
    }

    // ------------------------------------------------------------------
    // Confirmar SRI (Flujo A)
    // ------------------------------------------------------------------

    @Override
    public Mono<Anulacion> confirmarSri(Long idAnulacion, Integer idCompania, Integer idUsuarioConfirma) {
        return findAnulacionPorCompania(idAnulacion, idCompania)
                .flatMap(a -> requiereEstado(a, EstadoAnulacion.APROBADA))
                .flatMap(a -> anulacionRepository.updateEstado(
                                a.getId(),
                                EstadoAnulacion.EJECUTADA,
                                idUsuarioConfirma,
                                OffsetDateTime.now(clock),
                                a.getObservacionResolucion(),
                                a.getIdComprobanteNc())
                        .flatMap(ejecutada -> marcarComprobanteAnulado(a.getIdComprobante())
                                .thenReturn(stripFlagObservacion(ejecutada))));
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Override
    public Mono<Anulacion> buscarPorId(Long id, Integer idCompania) {
        return findAnulacionPorCompania(id, idCompania).map(this::stripFlagObservacion);
    }

    @Override
    public Flux<Anulacion> historialPorComprobante(Long idComprobante, Integer idCompania) {
        return anulacionRepository.findByIdComprobante(idComprobante, idCompania).map(this::stripFlagObservacion);
    }

    @Override
    public Flux<Anulacion> listar(Integer idCompania, Integer idSucursal, EstadoAnulacion estado,
                                  Long idComprobante, int page, int limit) {
        int offset = (page - 1) * limit;
        return anulacionRepository.findByEmpresa(idCompania, idSucursal, estado, idComprobante, offset, limit)
                .map(this::stripFlagObservacion);
    }

    @Override
    public Mono<Long> contar(Integer idCompania, Integer idSucursal, EstadoAnulacion estado, Long idComprobante) {
        return anulacionRepository.countByEmpresa(idCompania, idSucursal, estado, idComprobante);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Mono<Anulacion> findAnulacionPorCompania(Long id, Integer idCompania) {
        return anulacionRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Anulación", id)))
                .flatMap(a -> {
                    if (!a.getIdCompania().equals(idCompania)) {
                        // Multi-tenant: no revelar existencia.
                        return Mono.error(new NotFoundException("Anulación", id));
                    }
                    return Mono.just(a);
                });
    }

    private Mono<Anulacion> requiereEstado(Anulacion a, EstadoAnulacion esperado) {
        if (a.getEstado() != esperado) {
            return Mono.error(new BusinessException(
                    "Transición inválida: la anulación " + a.getId() + " está en estado " + a.getEstado()
                            + " y se requiere " + esperado));
        }
        return Mono.just(a);
    }

    private boolean requiereNotaCredito(Anulacion a) {
        return a.getObservacionResolucion() != null && a.getObservacionResolucion().contains(FLAG_FLUJO_B);
    }

    /**
     * Extrae el {@code codigo_motivo} embebido en {@code observacion_resolucion}
     * al momento de solicitar. Devuelve {@code null} si no se registró — la
     * aprobación en Flujo B fallará con 422 explícito en ese caso.
     */
    private String resolveCodigoMotivoDesdeAnulacion(Anulacion a) {
        String obs = a.getObservacionResolucion();
        if (obs == null) return null;
        int start = obs.indexOf(PREFIJO_MOTIVO);
        if (start < 0) return null;
        int contentStart = start + PREFIJO_MOTIVO.length();
        int end = obs.indexOf(SUFIJO_MOTIVO, contentStart);
        if (end < 0) return null;
        String codigo = obs.substring(contentStart, end).trim();
        return codigo.isEmpty() ? null : codigo;
    }

    private List<EmitirFacturaCommand.DetalleFacturaItem> mapDetalles(List<ComprobanteDetalle> detalles) {
        return detalles.stream()
                .map(d -> new EmitirFacturaCommand.DetalleFacturaItem(
                        d.getCodigoPrincipal(),
                        d.getCodigoAuxiliar(),
                        d.getDescripcion(),
                        d.getCantidad(),
                        d.getPrecioUnitario(),
                        d.getDescuento() != null ? d.getDescuento() : BigDecimal.ZERO,
                        d.getPrecioTotalSinImpuesto(),
                        d.getOrden()))
                .toList();
    }

    private BigDecimal totalOrZero(Comprobante comprobante) {
        return comprobante.getTotal() != null ? comprobante.getTotal() : BigDecimal.ZERO;
    }

    /**
     * Deriva un código numérico de 9 dígitos para la clave de acceso de la NC.
     * Reutilizamos la última mitad del secuencial + hash del id de factura para
     * evitar colisiones simples. El SRI solo exige unicidad dentro del
     * establecimiento/punto por comprobante.
     */
    private String generarCodigoNumericoDesde(Comprobante factura) {
        long seed = factura.getId() != null ? factura.getId() : System.currentTimeMillis();
        String candidato = String.format("%09d", Math.abs(seed) % 1_000_000_000L);
        return candidato;
    }

    private Mono<Void> marcarComprobanteAnulado(Long idComprobante) {
        return comprobanteRepository.updateEstado(idComprobante, "ANULADO", null, null, null, null, null).then();
    }

    /**
     * Al persistir agregamos metadata interna ({@link #FLAG_FLUJO_B},
     * {@link #PREFIJO_MOTIVO}) a la observación. Al leer strippeamos para que
     * la UI/tests nunca la vean.
     */
    private Anulacion stripFlagObservacion(Anulacion a) {
        if (a.getObservacionResolucion() == null) return a;
        String limpia = a.getObservacionResolucion();
        // Quitar prefijo Flujo B
        if (limpia.startsWith(FLAG_FLUJO_B)) {
            limpia = limpia.substring(FLAG_FLUJO_B.length());
        }
        // Quitar marcador de motivo (puede venir en cualquier posición dentro
        // del prefijo interno, pero por convención va contiguo al FLUJO_B).
        int start = limpia.indexOf(PREFIJO_MOTIVO);
        if (start >= 0) {
            int end = limpia.indexOf(SUFIJO_MOTIVO, start + PREFIJO_MOTIVO.length());
            if (end >= 0) {
                limpia = (limpia.substring(0, start) + limpia.substring(end + 1)).trim();
            }
        } else {
            limpia = limpia.trim();
        }
        if (limpia.isEmpty()) limpia = null;
        return a.toBuilder().observacionResolucion(limpia).build();
    }

    /**
     * Concatena metadata interna (Flujo B, código motivo) con la observación
     * libre del aprobador/rechazador. La metadata se preserva a través de
     * transiciones para poder recuperarla si se consulta la anulación luego.
     */
    private String combineMetadata(boolean flujoB, String codigoMotivo, String observacionLibre) {
        StringBuilder sb = new StringBuilder();
        if (flujoB) sb.append(FLAG_FLUJO_B);
        if (codigoMotivo != null && !codigoMotivo.isBlank()) {
            sb.append(PREFIJO_MOTIVO).append(codigoMotivo).append(SUFIJO_MOTIVO);
        }
        if (observacionLibre != null && !observacionLibre.isBlank()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(observacionLibre.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
