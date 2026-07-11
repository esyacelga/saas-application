package com.gymadmin.attendance.application.service;

import com.gymadmin.attendance.domain.model.Asistencia;
import com.gymadmin.attendance.domain.port.in.AsistenciaUseCase;
import com.gymadmin.attendance.domain.port.out.AsistenciaRepository;
import com.gymadmin.attendance.infrastructure.adapter.out.auth.AuthServiceClient;
import com.gymadmin.attendance.infrastructure.adapter.out.core.CoreServiceClient;
import com.gymadmin.attendance.infrastructure.exception.ConflictException;
import com.gymadmin.attendance.infrastructure.exception.ForbiddenException;
import com.gymadmin.attendance.infrastructure.exception.GoneException;
import com.gymadmin.attendance.infrastructure.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsistenciaService implements AsistenciaUseCase {

    private final AsistenciaRepository asistenciaRepository;
    private final CoreServiceClient coreServiceClient;
    private final AuthServiceClient authServiceClient;

    @Override
    public Mono<AsistenciaQrResult> registrarPorQr(RegistrarQrCommand command) {
        log.info("[registrarPorQr] INICIO idCliente={} idPersona={} idCompania={}", command.idClienteJwt(), command.idPersonaJwt(), command.idCompaniaJwt());
        return authServiceClient.buscarSucursalPorQr(command.qrToken())
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("[registrarPorQr] QR no encontrado en auth-service idCliente={}", command.idClienteJwt());
                    return Mono.error(new NotFoundException("QR inválido"));
                }))
                .flatMap(sucursal -> {
                    if (sucursal.getQrTokenExpira() != null) {
                        try {
                            java.time.OffsetDateTime expira = java.time.OffsetDateTime.parse(sucursal.getQrTokenExpira());
                            if (expira.isBefore(java.time.OffsetDateTime.now())) {
                                log.warn("[registrarPorQr] QR expirado idCliente={} expiraba={}", command.idClienteJwt(), sucursal.getQrTokenExpira());
                                return Mono.error(new GoneException("QR expirado"));
                            }
                        } catch (Exception ignored) {}
                    }

                    if (!sucursal.getIdCompania().equals(command.idCompaniaJwt().intValue())) {
                        log.warn("[registrarPorQr] QR de otra compania sucursal.idCompania={} jwt.idCompania={}", sucursal.getIdCompania(), command.idCompaniaJwt());
                        return Mono.error(new ForbiddenException("QR pertenece a otro gimnasio"));
                    }

                    log.debug("[registrarPorQr] llamando validarAcceso idPersona={} idCompania={} tokenPresente={}", command.idPersonaJwt(), command.idCompaniaJwt(), command.bearerToken() != null && !command.bearerToken().isBlank());
                    return coreServiceClient.validarAcceso(command.idPersonaJwt(), command.idCompaniaJwt().intValue(), command.bearerToken())
                            .flatMap(acceso -> {
                                if (!acceso.isPermitido()) {
                                    log.warn("[registrarPorQr] membresía denegada idCliente={} razon='{}' idMembresia={} fechaFin={} modoControl='{}'",
                                            command.idClienteJwt(), acceso.getRazon(), acceso.getIdMembresia(), acceso.getFechaFin(), acceso.getModoControl());
                                    return Mono.error(new ForbiddenException(
                                            acceso.getRazon() != null ? acceso.getRazon() : "Membresía sin acceso"));
                                }

                                Asistencia asistencia = new Asistencia();
                                asistencia.setIdCompania(sucursal.getIdCompania());
                                asistencia.setIdSucursal(sucursal.getIdSucursal());
                                asistencia.setIdCliente(acceso.getIdCliente());
                                asistencia.setIdMembresia(acceso.getIdMembresia());
                                asistencia.setFecha(LocalDate.now());
                                asistencia.setHoraEntrada(LocalTime.now().withNano(0));
                                asistencia.setMetodoRegistro("qr_cliente");

                                return asistenciaRepository.save(asistencia)
                                        .onErrorMap(e -> {
                                            if (e.getMessage() != null && e.getMessage().contains("unique")) {
                                                log.warn("[registrarPorQr] duplicado idCliente={} fecha={}", command.idClienteJwt(), LocalDate.now());
                                                return new ConflictException("ya_registrado_hoy",
                                                        "Ya registraste tu entrada hoy");
                                            }
                                            log.error("[registrarPorQr] error al guardar idCliente={} causa='{}'", command.idClienteJwt(), e.getMessage());
                                            return e;
                                        })
                                        .map(saved -> {
                                            log.info("[registrarPorQr] OK id={} idCliente={} sucursal='{}' fecha={}", saved.getId(), saved.getIdCliente(), sucursal.getNombreSucursal(), saved.getFecha());
                                            return new AsistenciaQrResult(
                                                    saved.getId(),
                                                    saved.getFecha(),
                                                    saved.getHoraEntrada(),
                                                    sucursal.getNombreSucursal(),
                                                    acceso.getTipoMembresia(),
                                                    acceso.getModoControl(),
                                                    acceso.getAccesosUsados(),
                                                    acceso.getDiasAccesoRestantes(),
                                                    acceso.getFechaFin() != null
                                                            ? LocalDate.parse(acceso.getFechaFin()) : null
                                            );
                                        });
                            });
                });
    }

    @Override
    public Mono<AsistenciaQrResult> registrarPorApp(RegistrarAppCommand command) {
        log.info("[registrarPorApp] INICIO idCliente={} idPersona={} idCompania={} idSucursal={} tokenPresente={}",
                command.idClienteJwt(), command.idPersonaJwt(), command.idCompaniaJwt(), command.idSucursal(),
                command.bearerToken() != null && !command.bearerToken().isBlank());
        return coreServiceClient.validarAcceso(command.idPersonaJwt(), command.idCompaniaJwt().intValue(), command.bearerToken())
                .flatMap(acceso -> {
                    if (!acceso.isPermitido()) {
                        log.warn("[registrarPorApp] membresía denegada idCliente={} razon='{}' idMembresia={} fechaFin={} modoControl='{}'",
                                command.idClienteJwt(), acceso.getRazon(), acceso.getIdMembresia(), acceso.getFechaFin(), acceso.getModoControl());
                        return Mono.error(new ForbiddenException(
                                acceso.getRazon() != null ? acceso.getRazon() : "Membresía sin acceso"));
                    }

                    Asistencia asistencia = new Asistencia();
                    asistencia.setIdCompania(command.idCompaniaJwt().intValue());
                    asistencia.setIdSucursal(command.idSucursal());
                    asistencia.setIdCliente(acceso.getIdCliente());
                    asistencia.setIdMembresia(acceso.getIdMembresia());
                    asistencia.setFecha(LocalDate.now());
                    asistencia.setHoraEntrada(LocalTime.now().withNano(0));
                    asistencia.setMetodoRegistro("app_cliente");

                    return asistenciaRepository.save(asistencia)
                            .onErrorMap(e -> {
                                if (e.getMessage() != null && e.getMessage().contains("unique")) {
                                    log.warn("[registrarPorApp] duplicado idCliente={} fecha={}", command.idClienteJwt(), LocalDate.now());
                                    return new ConflictException("ya_registrado_hoy",
                                            "Ya registraste tu entrada hoy");
                                }
                                log.error("[registrarPorApp] error al guardar idCliente={} causa='{}'", command.idClienteJwt(), e.getMessage());
                                return e;
                            })
                            .map(saved -> {
                                log.info("[registrarPorApp] OK id={} idCliente={} fecha={}", saved.getId(), saved.getIdCliente(), saved.getFecha());
                                return new AsistenciaQrResult(
                                        saved.getId(),
                                        saved.getFecha(),
                                        saved.getHoraEntrada(),
                                        command.nombreSucursal() != null ? command.nombreSucursal() : "—",
                                        acceso.getTipoMembresia(),
                                        acceso.getModoControl(),
                                        acceso.getAccesosUsados(),
                                        acceso.getDiasAccesoRestantes(),
                                        acceso.getFechaFin() != null
                                                ? LocalDate.parse(acceso.getFechaFin()) : null
                                );
                            });
                });
    }

    @Override
    public Mono<Asistencia> registrarManual(RegistrarManualCommand command) {
        log.info("[registrarManual] INICIO idCliente={} idCompania={} fecha={}", command.idCliente(), command.idCompania(), command.fecha());
        return coreServiceClient.validarAcceso(command.idCliente(), command.idCompania(), "")
                .flatMap(acceso -> {
                    if (!acceso.isPermitido()) {
                        return Mono.error(new ForbiddenException(
                                acceso.getRazon() != null ? acceso.getRazon() : "Membresía sin acceso"));
                    }

                    Asistencia asistencia = new Asistencia();
                    asistencia.setIdCompania(command.idCompania());
                    asistencia.setIdSucursal(command.idSucursal());
                    asistencia.setIdCliente(command.idCliente());
                    asistencia.setIdMembresia(acceso.getIdMembresia());
                    asistencia.setFecha(command.fecha() != null ? command.fecha() : LocalDate.now());
                    asistencia.setHoraEntrada(command.horaEntrada() != null ? command.horaEntrada() : LocalTime.now().withNano(0));
                    asistencia.setMetodoRegistro("manual");

                    return asistenciaRepository.save(asistencia)
                            .onErrorMap(e -> {
                                if (e.getMessage() != null && e.getMessage().contains("unique")) {
                                    return new ConflictException("ya_registrado_hoy",
                                            "Ya existe una entrada registrada para este cliente hoy");
                                }
                                return e;
                            });
                });
    }

    @Override
    public Mono<Asistencia> registrarOverride(RegistrarOverrideCommand command) {
        // Override: NO valida acceso/membresía activa, pero id_membresia es NOT NULL
        // en la BD, así que se asocia la membresía más relevante del cliente (activa
        // primero, luego la de vencimiento más reciente). Si el cliente no tiene
        // ninguna membresía, el override no puede registrarse.
        return asistenciaRepository.findMembresiaParaOverride(command.idCliente(), command.idCompania())
                .switchIfEmpty(Mono.error(new NotFoundException(
                        "El cliente no tiene ninguna membresía registrada; no se puede aplicar override")))
                .flatMap(idMembresia -> {
                    Asistencia asistencia = new Asistencia();
                    asistencia.setIdCompania(command.idCompania());
                    asistencia.setIdSucursal(command.idSucursal());
                    asistencia.setIdCliente(command.idCliente());
                    asistencia.setIdMembresia(idMembresia);
                    asistencia.setFecha(command.fecha() != null ? command.fecha() : LocalDate.now());
                    asistencia.setHoraEntrada(command.horaEntrada() != null ? command.horaEntrada() : LocalTime.now().withNano(0));
                    asistencia.setMetodoRegistro("manual");

                    return asistenciaRepository.save(asistencia)
                            .onErrorMap(e -> {
                                if (e.getMessage() != null && e.getMessage().contains("unique")) {
                                    return new ConflictException("ya_registrado_hoy",
                                            "Ya existe una entrada registrada para este cliente hoy");
                                }
                                return e;
                            });
                });
    }

    @Override
    public Flux<Asistencia> listarPorCliente(Integer idCliente, Integer idCompania,
                                              LocalDate desde, LocalDate hasta, Integer idMembresia) {
        LocalDate desdeEfectivo = desde != null ? desde : LocalDate.now().minusMonths(1);
        LocalDate hastaEfectivo = hasta != null ? hasta : LocalDate.now();
        return asistenciaRepository.findByClienteAndPeriodo(idCliente, idCompania,
                desdeEfectivo, hastaEfectivo, idMembresia);
    }

    @Override
    public Mono<Ultimos30DiasResult> ultimos30Dias(Integer idCliente, Integer idCompania) {
        LocalDate desde = LocalDate.now().minusDays(29);

        return asistenciaRepository.findByClienteUltimos30Dias(idCliente, idCompania, desde)
                .collectList()
                .map(asistencias -> {
                    Map<LocalDate, LocalTime> mapa = new HashMap<>();
                    for (Asistencia a : asistencias) {
                        mapa.put(a.getFecha(), a.getHoraEntrada());
                    }

                    List<UltimoDiaDetalle> detalle = new ArrayList<>();
                    int diasAsistidos = 0;
                    int rachaActual = 0;
                    int rachaMaxima = 0;
                    int rachaTemp = 0;

                    for (int i = 0; i < 30; i++) {
                        LocalDate dia = LocalDate.now().minusDays(i);
                        boolean asistio = mapa.containsKey(dia);
                        detalle.add(new UltimoDiaDetalle(dia, asistio, mapa.get(dia)));

                        if (asistio) {
                            diasAsistidos++;
                            rachaTemp++;
                            if (i == 0 || mapa.containsKey(dia.plusDays(1)) || rachaTemp == 1) {
                                rachaActual = rachaTemp;
                            }
                        } else {
                            if (i == 0) rachaActual = 0;
                            rachaTemp = 0;
                        }
                        rachaMaxima = Math.max(rachaMaxima, rachaTemp);
                    }

                    return new Ultimos30DiasResult(
                            idCliente, diasAsistidos, 30 - diasAsistidos,
                            rachaActual, rachaMaxima, detalle);
                });
    }

    @Override
    public Mono<Ultimos30DiasResult> ultimos30DiasMe(Integer idPersona, Integer idCompania, String bearerToken) {
        LocalDate desde = LocalDate.now().minusDays(29);
        return asistenciaRepository.findByPersonaUltimos30Dias(idPersona.longValue(), idCompania, desde)
                .collectList()
                .map(asistencias -> {
                    Map<LocalDate, LocalTime> mapa = new HashMap<>();
                    for (Asistencia a : asistencias) mapa.put(a.getFecha(), a.getHoraEntrada());

                    List<UltimoDiaDetalle> detalle = new ArrayList<>();
                    int diasAsistidos = 0, rachaActual = 0, rachaMaxima = 0, rachaTemp = 0;

                    for (int i = 0; i < 30; i++) {
                        LocalDate dia = LocalDate.now().minusDays(i);
                        boolean asistio = mapa.containsKey(dia);
                        detalle.add(new UltimoDiaDetalle(dia, asistio, mapa.get(dia)));

                        if (asistio) {
                            diasAsistidos++;
                            rachaTemp++;
                            if (i == 0 || mapa.containsKey(dia.plusDays(1)) || rachaTemp == 1) {
                                rachaActual = rachaTemp;
                            }
                        } else {
                            if (i == 0) rachaActual = 0;
                            rachaTemp = 0;
                        }
                        rachaMaxima = Math.max(rachaMaxima, rachaTemp);
                    }

                    log.info("[ultimos30DiasMe] idPersona={} idCompania={} diasAsistidos={}", idPersona, idCompania, diasAsistidos);
                    return new Ultimos30DiasResult(idPersona, diasAsistidos, 30 - diasAsistidos,
                            rachaActual, rachaMaxima, detalle);
                });
    }

    @Override
    public Mono<java.util.List<Asistencia>> listarPorClienteMe(Integer idPersona, Integer idCompania,
                                                                 String bearerToken, LocalDate desde, LocalDate hasta) {
        LocalDate desdeEfectivo = desde != null ? desde : LocalDate.now().minusMonths(1);
        LocalDate hastaEfectivo = hasta != null ? hasta : LocalDate.now();
        return asistenciaRepository.findByPersonaAndPeriodo(idPersona.longValue(), idCompania, desdeEfectivo, hastaEfectivo)
                .collectList()
                .doOnNext(lista -> log.info("[listarPorClienteMe] idPersona={} idCompania={} encontrados={}", idPersona, idCompania, lista.size()));
    }

    @Override
    public Mono<AsistenciasHoyResult> asistenciasHoy(Integer idCompania, Integer idSucursal) {
        LocalDate hoy = LocalDate.now();
        return Mono.zip(
                asistenciaRepository.findByCompaniaAndFecha(idCompania, idSucursal, hoy).collectList(),
                asistenciaRepository.findUltimasEntradas(idCompania, idSucursal, hoy).collectList()
        ).map(tuple -> {
            List<Asistencia> lista = tuple.getT1();
            List<AsistenciaRepository.EntradaEnriquecida> enriquecidas = tuple.getT2();

            Map<String, Integer> porMetodo = new HashMap<>();
            for (Asistencia a : lista) {
                porMetodo.merge(a.getMetodoRegistro(), 1, Integer::sum);
            }

            List<EntradaResumen> ultimas = enriquecidas.stream()
                    .map(e -> new EntradaResumen(e.hora(), e.idCliente(), e.nombre(), e.fotoUrl(), e.metodo()))
                    .toList();

            return new AsistenciasHoyResult(hoy, lista.size(), porMetodo, ultimas);
        });
    }

    @Override
    public Mono<EstadisticasResult> estadisticas(Integer idCompania, String periodo, Integer anio, Integer mes) {
        boolean anual = "anio".equalsIgnoreCase(periodo);

        LocalDate desde;
        LocalDate hasta;
        String periodoLabel;
        if (anual) {
            desde = LocalDate.of(anio, 1, 1);
            hasta = LocalDate.of(anio, 12, 31);
            periodoLabel = String.valueOf(anio);
        } else {
            desde = LocalDate.of(anio, mes, 1);
            hasta = desde.withDayOfMonth(desde.lengthOfMonth());
            periodoLabel = anio + "-" + String.format("%02d", mes);
        }

        long diasPeriodo = ChronoUnit.DAYS.between(desde, hasta) + 1;

        return asistenciaRepository.countByCompaniaAndPeriodo(idCompania, desde, hasta)
                .map(total -> {
                    double promedio = diasPeriodo > 0 ? (double) total / diasPeriodo : 0;
                    return new EstadisticasResult(
                            periodoLabel,
                            total.intValue(),
                            Math.round(promedio * 10.0) / 10.0,
                            0, 0, 0,
                            null, 0, "N/A");
                });
    }

    @Override
    public Mono<RachaPerfectaResult> rachaPerfecta(Integer idCliente, Integer idCompania, Integer meses) {
        LocalDate desde = LocalDate.now().minusMonths(meses != null ? meses : 1);
        LocalDate hasta = LocalDate.now();

        return asistenciaRepository.countByCliente(idCliente, desde, hasta)
                .map(diasAsistidos -> {
                    long diasTotales = java.time.temporal.ChronoUnit.DAYS.between(desde, hasta) + 1;
                    boolean rachaPerfecta = diasAsistidos >= diasTotales;
                    return new RachaPerfectaResult(rachaPerfecta, diasAsistidos.intValue(), (int) diasTotales);
                });
    }
}
