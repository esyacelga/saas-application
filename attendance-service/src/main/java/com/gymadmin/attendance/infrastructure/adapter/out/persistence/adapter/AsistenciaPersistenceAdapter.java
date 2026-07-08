package com.gymadmin.attendance.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.attendance.domain.model.Asistencia;
import com.gymadmin.attendance.domain.port.out.AsistenciaRepository;
import com.gymadmin.attendance.infrastructure.adapter.out.persistence.entity.AsistenciaEntity;
import com.gymadmin.attendance.infrastructure.adapter.out.persistence.repository.AsistenciaR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;

@Component
@RequiredArgsConstructor
public class AsistenciaPersistenceAdapter implements AsistenciaRepository {

    private final AsistenciaR2dbcRepository repository;
    private final DatabaseClient databaseClient;

    @Override
    public Mono<Asistencia> save(Asistencia asistencia) {
        return repository.save(toEntity(asistencia)).map(this::toDomain);
    }

    @Override
    public Flux<Asistencia> findByClienteAndPeriodo(Integer idCliente, Integer idCompania,
                                                     LocalDate desde, LocalDate hasta, Integer idMembresia) {
        return repository.findByClienteAndPeriodo(idCliente, idCompania, desde, hasta, idMembresia)
                .map(this::toDomain);
    }

    @Override
    public Flux<Asistencia> findByClienteUltimos30Dias(Integer idCliente, Integer idCompania, LocalDate desde) {
        return repository.findByClienteUltimos30Dias(idCliente, idCompania, desde).map(this::toDomain);
    }

    @Override
    public Flux<Asistencia> findByCompaniaAndFecha(Integer idCompania, Integer idSucursal, LocalDate fecha) {
        return repository.findByCompaniaAndFecha(idCompania, idSucursal, fecha).map(this::toDomain);
    }

    @Override
    public Mono<Long> countByCompaniaAndPeriodo(Integer idCompania, LocalDate desde, LocalDate hasta) {
        return repository.countByCompaniaAndPeriodo(idCompania, desde, hasta);
    }

    @Override
    public Mono<Long> countByCliente(Integer idCliente, LocalDate desde, LocalDate hasta) {
        return repository.findByClienteAndPeriodo(idCliente, null, desde, hasta, null).count();
    }

    @Override
    public Mono<LocalDate> findUltimaAsistencia(Integer idCliente, Integer idCompania) {
        return repository.findUltimaAsistencia(idCliente, idCompania);
    }

    @Override
    public Flux<Asistencia> findByPersonaUltimos30Dias(Long idPersona, Integer idCompania, LocalDate desde) {
        return repository.findByPersonaUltimos30Dias(idPersona, idCompania, desde).map(this::toDomain);
    }

    @Override
    public Flux<Asistencia> findByPersonaAndPeriodo(Long idPersona, Integer idCompania, LocalDate desde, LocalDate hasta) {
        return repository.findByPersonaAndPeriodo(idPersona, idCompania, desde, hasta).map(this::toDomain);
    }

    @Override
    public Flux<Asistencia> findClientesConMembresia(Integer idCompania, LocalDate desde, LocalDate hasta) {
        return repository.findByClienteAndPeriodo(null, idCompania, desde, hasta, null).map(this::toDomain);
    }

    @Override
    public Flux<EntradaEnriquecida> findUltimasEntradas(Integer idCompania, Integer idSucursal, LocalDate fecha) {
        String sucursalClause = idSucursal != null ? "AND a.id_sucursal = :idSucursal" : "";
        String sql = """
                SELECT a.hora_entrada, a.id_cliente, a.metodo_registro,
                       COALESCE(p.nombre, 'Cliente ' || a.id_cliente::text) AS nombre,
                       p.foto_url
                FROM asistencia.asistencias a
                LEFT JOIN core.clientes c ON c.id = a.id_cliente AND c.id_compania = a.id_compania
                LEFT JOIN identidad.personas p ON p.id = c.id_persona
                WHERE a.id_compania = :idCompania
                  %s
                  AND a.fecha = :fecha
                  AND a.eliminado = false
                ORDER BY a.hora_entrada DESC
                LIMIT 10
                """.formatted(sucursalClause);

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
                .bind("idCompania", idCompania)
                .bind("fecha", fecha);
        if (idSucursal != null) {
            spec = spec.bind("idSucursal", idSucursal);
        }

        return spec.map((row, meta) -> new EntradaEnriquecida(
                row.get("hora_entrada", LocalTime.class),
                row.get("id_cliente", Integer.class),
                row.get("nombre", String.class),
                row.get("foto_url", String.class),
                row.get("metodo_registro", String.class)
        )).all();
    }

    private Asistencia toDomain(AsistenciaEntity e) {
        Asistencia a = new Asistencia();
        a.setId(e.getId());
        a.setIdCompania(e.getIdCompania());
        a.setIdSucursal(e.getIdSucursal());
        a.setIdCliente(e.getIdCliente());
        a.setIdMembresia(e.getIdMembresia());
        a.setFecha(e.getFecha());
        a.setHoraEntrada(e.getHoraEntrada());
        a.setMetodoRegistro(e.getMetodoRegistro());
        a.setEliminado(e.getEliminado());
        a.setCreacionFecha(e.getCreacionFecha());
        a.setCreacionUsuario(e.getCreacionUsuario());
        a.setModificaFecha(e.getModificaFecha());
        a.setModificaUsuario(e.getModificaUsuario());
        return a;
    }

    private AsistenciaEntity toEntity(Asistencia a) {
        return AsistenciaEntity.builder()
                .id(a.getId())
                .idCompania(a.getIdCompania())
                .idSucursal(a.getIdSucursal())
                .idCliente(a.getIdCliente())
                .idMembresia(a.getIdMembresia())
                .fecha(a.getFecha())
                .horaEntrada(a.getHoraEntrada())
                .metodoRegistro(a.getMetodoRegistro())
                .build();
    }
}
