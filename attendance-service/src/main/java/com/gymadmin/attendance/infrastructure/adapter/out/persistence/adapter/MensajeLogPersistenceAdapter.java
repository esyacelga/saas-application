package com.gymadmin.attendance.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.attendance.domain.model.MensajeLog;
import com.gymadmin.attendance.domain.port.out.MensajeLogRepository;
import com.gymadmin.attendance.infrastructure.adapter.out.persistence.entity.MensajeLogEntity;
import com.gymadmin.attendance.infrastructure.adapter.out.persistence.repository.MensajeLogR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class MensajeLogPersistenceAdapter implements MensajeLogRepository {

    /** Zona de operación (Ecuador) — el "día de negocio" para la idempotencia C2 se ancla aquí. */
    private static final ZoneId ZONA_OPERACION = ZoneId.of("America/Guayaquil");

    private final MensajeLogR2dbcRepository repository;

    @Override
    public Mono<MensajeLog> save(MensajeLog mensajeLog) {
        return repository.save(toEntity(mensajeLog)).map(this::toDomain);
    }

    @Override
    public Mono<MensajeLog> update(MensajeLog mensajeLog) {
        return repository.save(toEntity(mensajeLog)).map(this::toDomain);
    }

    @Override
    public Mono<MensajeLog> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Flux<MensajeLog> findByFiltros(Integer idCompania, Integer idCliente, String tipo, String estado, LocalDate desde) {
        OffsetDateTime desdeOdt = desde != null ? desde.atStartOfDay().atOffset(ZoneOffset.UTC) : null;
        return repository.findByFiltros(idCompania, idCliente, tipo, estado, desdeOdt).map(this::toDomain);
    }

    @Override
    public Mono<Long> countByClienteAndTipoDesde(Integer idCliente, String tipo, OffsetDateTime desde) {
        return repository.countByClienteAndTipoDesde(idCliente, tipo, desde);
    }

    @Override
    public Mono<Boolean> existsEnviadoHoy(Integer idCliente, String tipo, String canal, LocalDate dia) {
        // El día de negocio se ancla a America/Guayaquil (igual que la fechaCorte de core); el rango
        // [inicio, inicio+1d) se expresa en UTC porque fecha_programada se persiste en UTC.
        OffsetDateTime desde = dia.atStartOfDay(ZONA_OPERACION).toOffsetDateTime()
                .withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime hasta = dia.plusDays(1).atStartOfDay(ZONA_OPERACION).toOffsetDateTime()
                .withOffsetSameInstant(ZoneOffset.UTC);
        return repository.existsEnviadoEnRango(idCliente, tipo, canal, desde, hasta);
    }

    private MensajeLog toDomain(MensajeLogEntity e) {
        MensajeLog m = new MensajeLog();
        m.setId(e.getId());
        m.setIdCompania(e.getIdCompania());
        m.setIdSucursal(e.getIdSucursal());
        m.setIdCliente(e.getIdCliente());
        m.setIdPlantilla(e.getIdPlantilla());
        m.setTipo(e.getTipo());
        m.setCanal(e.getCanal());
        m.setContenido(e.getContenido());
        m.setEstado(e.getEstado());
        m.setFechaProgramada(e.getFechaProgramada());
        m.setFechaEnvio(e.getFechaEnvio());
        m.setIdUsuarioEnvio(e.getIdUsuarioEnvio());
        m.setEliminado(e.getEliminado());
        m.setCreacionFecha(e.getCreacionFecha());
        m.setCreacionUsuario(e.getCreacionUsuario());
        m.setModificaFecha(e.getModificaFecha());
        m.setModificaUsuario(e.getModificaUsuario());
        return m;
    }

    private MensajeLogEntity toEntity(MensajeLog m) {
        return MensajeLogEntity.builder()
                .id(m.getId())
                .idCompania(m.getIdCompania())
                .idSucursal(m.getIdSucursal())
                .idCliente(m.getIdCliente())
                .idPlantilla(m.getIdPlantilla())
                .tipo(m.getTipo())
                .canal(m.getCanal())
                .contenido(m.getContenido())
                .estado(m.getEstado())
                .fechaProgramada(m.getFechaProgramada())
                .fechaEnvio(m.getFechaEnvio())
                .idUsuarioEnvio(m.getIdUsuarioEnvio())
                .creacionFecha(m.getCreacionFecha())
                .creacionUsuario(m.getCreacionUsuario())
                .build();
    }
}
