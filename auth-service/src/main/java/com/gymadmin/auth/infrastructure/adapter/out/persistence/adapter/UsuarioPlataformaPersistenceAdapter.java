package com.gymadmin.auth.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.auth.domain.model.UsuarioPlataforma;
import com.gymadmin.auth.domain.port.out.UsuarioPlataformaPort;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.mapper.UsuarioPlataformaMapper;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.repository.UsuarioPlataformaR2dbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class UsuarioPlataformaPersistenceAdapter implements UsuarioPlataformaPort {

    private final UsuarioPlataformaR2dbcRepository repository;
    private final DatabaseClient db;

    private static final String JOIN_SQL = """
            SELECT up.*, p.nombre AS nombre_persona, p.foto_url AS foto_url_persona
            FROM saas.usuarios_plataforma up
            JOIN identidad.personas p ON up.id_persona = p.id
            """;

    @Override
    public Mono<UsuarioPlataforma> findByCorreo(String correo) {
        return db.sql(JOIN_SQL + "WHERE up.correo = :correo")
                .bind("correo", correo)
                .map((row, meta) -> UsuarioPlataformaMapper.fromRow(row))
                .one();
    }

    @Override
    public Mono<UsuarioPlataforma> findById(Integer id) {
        return db.sql(JOIN_SQL + "WHERE up.id = :id")
                .bind("id", id)
                .map((row, meta) -> UsuarioPlataformaMapper.fromRow(row))
                .one();
    }

    @Override
    public Mono<Boolean> existsByCorreo(String correo) {
        return repository.existsByCorreo(correo);
    }

    @Override
    public Flux<UsuarioPlataforma> findAll() {
        return db.sql(JOIN_SQL + "ORDER BY p.nombre")
                .map((row, meta) -> UsuarioPlataformaMapper.fromRow(row))
                .all();
    }

    @Override
    public Mono<UsuarioPlataforma> save(UsuarioPlataforma usuario) {
        return repository.save(UsuarioPlataformaMapper.toEntity(usuario))
                .flatMap(saved -> findById(saved.getId()))
                .doOnError(ex -> log.error("Error al guardar UsuarioPlataforma correo={}: {}", usuario.getCorreo(), ex.getMessage(), ex))
                .onErrorMap(ex -> new RuntimeException("Error al guardar UsuarioPlataforma", ex));
    }

    @Override
    public Mono<Long> countByRolAndActivoTrue(String rol) {
        return repository.countByRolAndActivoTrue(rol);
    }

    @Override
    public Flux<UsuarioPlataforma> findByIdPersona(Integer idPersona) {
        return db.sql(JOIN_SQL + "WHERE up.id_persona = :idPersona ORDER BY up.id")
                .bind("idPersona", idPersona)
                .map((row, meta) -> UsuarioPlataformaMapper.fromRow(row))
                .all();
    }
}
