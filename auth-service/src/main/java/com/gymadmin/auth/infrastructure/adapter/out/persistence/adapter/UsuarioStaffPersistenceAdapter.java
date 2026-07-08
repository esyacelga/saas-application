package com.gymadmin.auth.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.auth.domain.model.UsuarioStaff;
import com.gymadmin.auth.domain.port.out.UsuarioStaffPort;
import com.gymadmin.auth.dto.response.CompaniaBasicaResponse;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.mapper.UsuarioStaffMapper;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.repository.UsuarioStaffR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class UsuarioStaffPersistenceAdapter implements UsuarioStaffPort {

    private final UsuarioStaffR2dbcRepository repository;
    private final DatabaseClient db;

    private static final String JOIN_SQL = """
            SELECT u.*, r.nombre AS nombre_rol,
                   p.nombre AS nombre_persona, p.foto_url AS foto_url_persona
            FROM seguridad.usuarios u
            LEFT JOIN seguridad.roles r ON u.id_rol = r.id
            LEFT JOIN identidad.personas p ON u.id_persona = p.id
            """;

    @Override
    public Mono<UsuarioStaff> findByCorreoAndIdCompania(String correo, Integer idCompania) {
        return db.sql(JOIN_SQL + "WHERE u.correo = :correo AND u.id_compania = :idCompania")
                .bind("correo", correo)
                .bind("idCompania", idCompania)
                .map((row, meta) -> UsuarioStaffMapper.fromRow(row))
                .one();
    }

    @Override
    public Mono<Boolean> existsByCorreoAndIdCompania(String correo, Integer idCompania) {
        return repository.existsByCorreoAndIdCompania(correo, idCompania);
    }

    @Override
    public Flux<UsuarioStaff> findByIdCompania(Integer idCompania) {
        return db.sql(JOIN_SQL + "WHERE u.id_compania = :idCompania ORDER BY p.nombre")
                .bind("idCompania", idCompania)
                .map((row, meta) -> UsuarioStaffMapper.fromRow(row))
                .all();
    }

    @Override
    public Mono<UsuarioStaff> findByIdAndIdCompania(Integer id, Integer idCompania) {
        return db.sql(JOIN_SQL + "WHERE u.id = :id AND u.id_compania = :idCompania")
                .bind("id", id)
                .bind("idCompania", idCompania)
                .map((row, meta) -> UsuarioStaffMapper.fromRow(row))
                .one();
    }

    @Override
    public Mono<UsuarioStaff> findById(Integer id) {
        return db.sql(JOIN_SQL + "WHERE u.id = :id")
                .bind("id", id)
                .map((row, meta) -> UsuarioStaffMapper.fromRow(row))
                .one();
    }

    @Override
    public Mono<UsuarioStaff> save(UsuarioStaff usuario) {
        return repository.save(UsuarioStaffMapper.toEntity(usuario))
                .flatMap(saved -> findById(saved.getId()));
    }

    @Override
    public Mono<Long> countActiveDuenos(Integer idCompania) {
        return repository.countActiveDuenos(idCompania);
    }

    @Override
    public Mono<Boolean> existsByIdRolInCompania(Integer idRol, Integer idCompania) {
        return repository.existsByIdRolAndIdCompania(idRol, idCompania);
    }

    @Override
    public Flux<UsuarioStaff> findByIdPersona(Integer idPersona) {
        return db.sql(JOIN_SQL + "WHERE u.id_persona = :idPersona ORDER BY p.nombre")
                .bind("idPersona", idPersona)
                .map((row, meta) -> UsuarioStaffMapper.fromRow(row))
                .all();
    }

    @Override
    public Flux<CompaniaBasicaResponse> findCompaniesByCorreo(String correo) {
        String sql = """
                SELECT DISTINCT tc.id, tc.nombre
                FROM seguridad.usuarios su
                JOIN tenant.companias tc ON tc.id = su.id_compania
                WHERE su.correo = :correo
                  AND su.activo = true
                  AND su.eliminado = false
                  AND tc.activo = true
                  AND tc.eliminado = false
                ORDER BY tc.nombre ASC
                """;
        return db.sql(sql)
                .bind("correo", correo)
                .map((row, meta) -> new CompaniaBasicaResponse(
                        row.get("id", Integer.class),
                        row.get("nombre", String.class)))
                .all();
    }
}
