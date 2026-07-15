package com.gymadmin.core.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.core.domain.model.Cliente;
import com.gymadmin.core.domain.model.ClienteDetalle;
import com.gymadmin.core.domain.model.ClienteListItem;
import com.gymadmin.core.domain.model.ClientePorVencer;
import com.gymadmin.core.domain.port.out.ClienteRepository;
import com.gymadmin.core.infrastructure.adapter.out.persistence.entity.ClienteEntity;
import com.gymadmin.core.infrastructure.adapter.out.persistence.repository.ClienteR2dbcRepository;
import io.r2dbc.spi.Row;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Component
public class ClientePersistenceAdapter implements ClienteRepository {

    private final ClienteR2dbcRepository repository;
    private final DatabaseClient databaseClient;

    public ClientePersistenceAdapter(ClienteR2dbcRepository repository, DatabaseClient databaseClient) {
        this.repository = repository;
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<Cliente> findByIdCompania(Long idCompania, String estado, String buscar, int offset, int limit) {
        return repository.findByIdCompania(idCompania, estado, buscar, offset, limit).map(this::toDomain);
    }

    @Override
    public Mono<Long> countByIdCompania(Long idCompania, String estado, String buscar) {
        return repository.countByIdCompania(idCompania, estado, buscar);
    }

    @Override
    public Flux<ClienteListItem> findListItems(Long idCompania, String estado, String buscar, int offset, int limit, Boolean sinMembresia) {
        String buscarLike = buscar != null && !buscar.isBlank() ? "%" + buscar.toLowerCase() + "%" : null;
        String sinMembresiaClause = Boolean.TRUE.equals(sinMembresia) ? "AND m.id IS NULL " : "";

        String sql = "SELECT c.id, p.nombre, p.ci, p.telefono, c.estado, " +
                     "m.id AS membresia_id, tm.nombre AS membresia_tipo, " +
                     "tm.modo_control AS membresia_modo_control, " +
                     "m.fecha_fin AS membresia_fecha_fin, " +
                     "GREATEST(0, (m.fecha_fin - CURRENT_DATE)::int) AS membresia_dias_restantes, " +
                     "CASE WHEN tm.modo_control = 'accesos' " +
                     "THEN GREATEST(0, m.dias_acceso_total - COUNT(a.id)) ELSE NULL END AS membresia_accesos_restantes " +
                     "FROM core.clientes c " +
                     "JOIN identidad.personas p ON p.id = c.id_persona " +
                     "LEFT JOIN core.membresias m ON m.id_cliente = c.id AND m.estado = 'activa' AND m.eliminado = false " +
                     "LEFT JOIN core.tipos_membresia tm ON tm.id = m.id_tipo_membresia " +
                     "LEFT JOIN asistencia.asistencias a ON a.id_membresia = m.id " +
                     "WHERE c.id_compania = :idCompania AND c.eliminado = false " +
                     "AND (:estado IS NULL OR c.estado = :estado) " +
                     "AND (:buscar IS NULL OR lower(p.nombre) LIKE :buscar OR lower(p.ci) LIKE :buscar) " +
                     sinMembresiaClause +
                     "GROUP BY c.id, p.nombre, p.ci, p.telefono, c.estado, " +
                     "m.id, tm.nombre, tm.modo_control, m.fecha_fin, m.dias_acceso_total " +
                     "ORDER BY c.id DESC LIMIT :limit OFFSET :offset";

        var spec = databaseClient.sql(sql)
            .bind("idCompania", idCompania)
            .bind("limit", limit)
            .bind("offset", offset);

        spec = estado != null ? spec.bind("estado", estado) : spec.bindNull("estado", String.class);
        spec = buscarLike != null ? spec.bind("buscar", buscarLike) : spec.bindNull("buscar", String.class);

        return spec.map((row, meta) -> rowToListItem(row)).all();
    }

    @Override
    public Mono<Long> countListItems(Long idCompania, String estado, String buscar, Boolean sinMembresia) {
        String buscarLike = buscar != null && !buscar.isBlank() ? "%" + buscar.toLowerCase() + "%" : null;
        String membresiaJoin = Boolean.TRUE.equals(sinMembresia)
                ? "LEFT JOIN core.membresias m ON m.id_cliente = c.id AND m.estado = 'activa' AND m.eliminado = false "
                : "";
        String sinMembresiaClause = Boolean.TRUE.equals(sinMembresia) ? "AND m.id IS NULL " : "";

        String sql = "SELECT COUNT(*) FROM core.clientes c " +
                     "JOIN identidad.personas p ON p.id = c.id_persona " +
                     membresiaJoin +
                     "WHERE c.id_compania = :idCompania AND c.eliminado = false " +
                     "AND (:estado IS NULL OR c.estado = :estado) " +
                     "AND (:buscar IS NULL OR lower(p.nombre) LIKE :buscar OR lower(p.ci) LIKE :buscar) " +
                     sinMembresiaClause;

        var spec = databaseClient.sql(sql)
            .bind("idCompania", idCompania);

        spec = estado != null ? spec.bind("estado", estado) : spec.bindNull("estado", String.class);
        spec = buscarLike != null ? spec.bind("buscar", buscarLike) : spec.bindNull("buscar", String.class);

        return spec.map(row -> row.get(0, Long.class)).one();
    }

    @Override
    public Mono<ClienteDetalle> findDetalleById(Long id, Long idCompania) {
        return databaseClient.sql("""
            SELECT c.id,
                   p.ci, p.nombre, p.telefono, p.correo,
                   c.peso_kg, c.altura_cm, c.objetivos, c.lesiones,
                   c.estado, c.fecha_ingreso, c.codigo_carnet, c.sexo,
                   m.id               AS membresia_id,
                   tm.nombre          AS membresia_tipo,
                   tm.modo_control    AS membresia_modo_control,
                   m.fecha_inicio     AS membresia_fecha_inicio,
                   m.fecha_fin        AS membresia_fecha_fin,
                   GREATEST(0, (m.fecha_fin - CURRENT_DATE)::int) AS membresia_dias_restantes,
                   m.estado           AS membresia_estado
            FROM core.clientes c
            JOIN identidad.personas p ON p.id = c.id_persona
            LEFT JOIN core.membresias m
                   ON m.id_cliente = c.id AND m.estado = 'activa' AND m.eliminado = false
            LEFT JOIN core.tipos_membresia tm ON tm.id = m.id_tipo_membresia
            WHERE c.id = :id AND c.id_compania = :idCompania AND c.eliminado = false
            """)
            .bind("id", id)
            .bind("idCompania", idCompania)
            .map((row, meta) -> {
                Long membresiaId = row.get("membresia_id", Long.class);
                ClienteDetalle.MembresiaActiva mem = null;
                if (membresiaId != null) {
                    LocalDate fi = row.get("membresia_fecha_inicio", LocalDate.class);
                    LocalDate ff = row.get("membresia_fecha_fin", LocalDate.class);
                    Integer dias = row.get("membresia_dias_restantes", Integer.class);
                    mem = new ClienteDetalle.MembresiaActiva(
                            membresiaId,
                            row.get("membresia_tipo", String.class),
                            row.get("membresia_modo_control", String.class),
                            fi != null ? fi.toString() : null,
                            ff != null ? ff.toString() : null,
                            dias != null ? dias : 0,
                            row.get("membresia_estado", String.class)
                    );
                }
                LocalDate fechaIngreso = row.get("fecha_ingreso", LocalDate.class);
                return new ClienteDetalle(
                        row.get("id", Long.class),
                        new ClienteDetalle.Persona(
                                row.get("ci", String.class),
                                row.get("nombre", String.class),
                                row.get("telefono", String.class),
                                row.get("correo", String.class)
                        ),
                        row.get("peso_kg", java.math.BigDecimal.class),
                        row.get("altura_cm", java.math.BigDecimal.class),
                        row.get("objetivos", String.class),
                        row.get("lesiones", String.class),
                        row.get("estado", String.class),
                        fechaIngreso != null ? fechaIngreso.toString() : null,
                        row.get("codigo_carnet", String.class),
                        row.get("sexo", String.class),
                        mem
                );
            })
            .one();
    }

    @Override
    public Mono<Cliente> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Mono<Cliente> findByIdAndIdCompania(Long id, Long idCompania) {
        return repository.findByIdAndIdCompania(id, idCompania).map(this::toDomain);
    }

    @Override
    public Mono<Cliente> findByIdPersonaAndIdCompania(Long idPersona, Long idCompania) {
        return repository.findByIdPersonaAndIdCompania(idPersona, idCompania).map(this::toDomain);
    }

    @Override
    public Mono<Cliente> save(Cliente cliente) {
        if (cliente.getId() != null) {
            return repository.findById(cliente.getId())
                    .map(existing -> mergeIntoEntity(existing, cliente))
                    .flatMap(repository::save)
                    .map(this::toDomain);
        }
        return repository.save(toEntity(cliente)).map(this::toDomain);
    }

    @Override
    public Flux<Cliente> findByIdPersona(Long idPersona) {
        return repository.findByIdPersona(idPersona).map(this::toDomain);
    }

    @Override
    public Mono<Void> deleteById(Long id) {
        return repository.findById(id)
                .flatMap(entity -> {
                    entity.setEliminado(true);
                    return repository.save(entity);
                })
                .then();
    }

    @Override
    public Flux<Cliente> findActivosParaJob() {
        return repository.findActivosParaJob().map(this::toDomain);
    }

    @Override
    public Flux<ClientePorVencer> findClientesPorVencer(Long idCompania, LocalDate fechaCorte, int dias, String modo) {
        boolean incluyeCalendario = "calendario".equals(modo) || "todos".equals(modo);
        boolean incluyeAccesos = "accesos".equals(modo) || "todos".equals(modo);

        // El "por vencer" difiere por modo: calendario mira los días al fecha_fin; accesos mira las
        // entradas restantes. Con :fechaCorte (no CURRENT_DATE) el cálculo respeta la zona de negocio (C4).
        String sql = "SELECT c.id AS id_cliente, c.id_persona, c.id_sucursal, c.estado AS estado_cliente, " +
                     "       p.nombre, p.telefono, p.correo, " +
                     "       p.acepta_whatsapp, p.fecha_consentimiento_wa, " +
                     "       tm.modo_control, m.fecha_fin, " +
                     "       (m.fecha_fin - :fechaCorte)::int AS dias_para_vencer, " +
                     "       CASE WHEN tm.modo_control = 'accesos' " +
                     "            THEN GREATEST(0, m.dias_acceso_total - COUNT(a.id)) ELSE NULL END AS accesos_restantes " +
                     "FROM core.clientes c " +
                     "JOIN identidad.personas p ON p.id = c.id_persona " +
                     "JOIN core.membresias m ON m.id_cliente = c.id AND m.estado = 'activa' AND m.eliminado = false " +
                     "JOIN core.tipos_membresia tm ON tm.id = m.id_tipo_membresia " +
                     "LEFT JOIN asistencia.asistencias a ON a.id_membresia = m.id " +
                     "WHERE c.id_compania = :idCompania AND c.eliminado = false " +
                     "  AND c.estado NOT IN ('congelado','vencido') " +
                     "GROUP BY c.id, c.id_persona, c.id_sucursal, c.estado, " +
                     "         p.nombre, p.telefono, p.correo, p.acepta_whatsapp, p.fecha_consentimiento_wa, " +
                     "         tm.modo_control, m.fecha_fin, m.dias_acceso_total " +
                     "HAVING (" +
                     "   (:incluyeCalendario AND tm.modo_control = 'calendario' " +
                     "        AND (m.fecha_fin - :fechaCorte)::int BETWEEN 0 AND :dias) " +
                     "   OR " +
                     "   (:incluyeAccesos AND tm.modo_control = 'accesos' " +
                     "        AND GREATEST(0, m.dias_acceso_total - COUNT(a.id)) <= :dias) " +
                     ") " +
                     "ORDER BY c.id";

        return databaseClient.sql(sql)
                .bind("idCompania", idCompania)
                .bind("fechaCorte", fechaCorte)
                .bind("dias", dias)
                .bind("incluyeCalendario", incluyeCalendario)
                .bind("incluyeAccesos", incluyeAccesos)
                .map((row, meta) -> rowToClientePorVencer(row))
                .all();
    }

    // ── mapping helpers ───────────────────────────────────────────────────────

    private ClienteListItem rowToListItem(Row row) {
        Long membresiaId = row.get("membresia_id", Long.class);
        ClienteListItem.MembresiaResumen membresia = null;
        if (membresiaId != null) {
            LocalDate fechaFin = row.get("membresia_fecha_fin", LocalDate.class);
            Integer dias = row.get("membresia_dias_restantes", Integer.class);
            Integer accesos = row.get("membresia_accesos_restantes", Integer.class);
            membresia = new ClienteListItem.MembresiaResumen(
                    membresiaId,
                    row.get("membresia_tipo", String.class),
                    row.get("membresia_modo_control", String.class),
                    fechaFin != null ? fechaFin.toString() : null,
                    dias != null ? dias : 0,
                    accesos
            );
        }
        return new ClienteListItem(
                row.get("id", Long.class),
                row.get("nombre", String.class),
                row.get("ci", String.class),
                row.get("telefono", String.class),
                row.get("estado", String.class),
                membresia
        );
    }

    private ClientePorVencer rowToClientePorVencer(Row row) {
        Boolean acepta = row.get("acepta_whatsapp", Boolean.class);
        return new ClientePorVencer(
                row.get("id_cliente", Long.class),
                row.get("id_persona", Long.class),
                row.get("id_sucursal", Long.class),
                row.get("nombre", String.class),
                row.get("telefono", String.class),
                row.get("correo", String.class),
                row.get("modo_control", String.class),
                row.get("fecha_fin", LocalDate.class),
                row.get("dias_para_vencer", Integer.class),
                row.get("accesos_restantes", Integer.class),
                row.get("estado_cliente", String.class),
                Boolean.TRUE.equals(acepta),
                row.get("fecha_consentimiento_wa", OffsetDateTime.class)
        );
    }

    private Cliente toDomain(ClienteEntity e) {
        Cliente c = new Cliente();
        c.setId(e.getId());
        c.setIdPersona(e.getIdPersona());
        c.setIdCompania(e.getIdCompania());
        c.setIdSucursal(e.getIdSucursal());
        c.setPesoKg(e.getPesoKg());
        c.setAlturaCm(e.getAlturaCm());
        c.setObjetivos(e.getObjetivos());
        c.setLesiones(e.getLesiones());
        c.setEstado(e.getEstado() != null ? Cliente.Estado.valueOf(e.getEstado()) : null);
        c.setFechaIngreso(e.getFechaIngreso());
        c.setCodigoCarnet(e.getCodigoCarnet());
        c.setSexo(e.getSexo() != null ? Cliente.Sexo.valueOf(e.getSexo()) : null);
        c.setCreatedAt(e.getCreacionFecha());
        c.setUpdatedAt(e.getModificaFecha());
        return c;
    }

    private ClienteEntity toEntity(Cliente c) {
        return ClienteEntity.builder()
                .id(c.getId())
                .idPersona(c.getIdPersona())
                .idCompania(c.getIdCompania())
                .idSucursal(c.getIdSucursal())
                .pesoKg(c.getPesoKg())
                .alturaCm(c.getAlturaCm())
                .objetivos(c.getObjetivos())
                .lesiones(c.getLesiones())
                .estado(c.getEstado() != null ? c.getEstado().name() : null)
                .fechaIngreso(c.getFechaIngreso())
                .codigoCarnet(c.getCodigoCarnet())
                .sexo(c.getSexo() != null ? c.getSexo().name() : null)
                .build();
    }

    private ClienteEntity mergeIntoEntity(ClienteEntity existing, Cliente c) {
        if (c.getIdPersona() != null)    existing.setIdPersona(c.getIdPersona());
        if (c.getIdCompania() != null)   existing.setIdCompania(c.getIdCompania());
        if (c.getIdSucursal() != null)   existing.setIdSucursal(c.getIdSucursal());
        if (c.getPesoKg() != null)       existing.setPesoKg(c.getPesoKg());
        if (c.getAlturaCm() != null)     existing.setAlturaCm(c.getAlturaCm());
        if (c.getObjetivos() != null)    existing.setObjetivos(c.getObjetivos());
        if (c.getLesiones() != null)     existing.setLesiones(c.getLesiones());
        if (c.getEstado() != null)       existing.setEstado(c.getEstado().name());
        if (c.getFechaIngreso() != null) existing.setFechaIngreso(c.getFechaIngreso());
        if (c.getCodigoCarnet() != null) existing.setCodigoCarnet(c.getCodigoCarnet());
        if (c.getSexo() != null)         existing.setSexo(c.getSexo().name());
        return existing;
    }
}
