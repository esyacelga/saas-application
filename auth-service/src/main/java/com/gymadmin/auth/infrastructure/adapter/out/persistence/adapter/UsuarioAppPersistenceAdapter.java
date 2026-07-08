package com.gymadmin.auth.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.auth.domain.model.UsuarioApp;
import com.gymadmin.auth.domain.port.out.UsuarioAppPort;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.mapper.UsuarioAppMapper;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.repository.UsuarioAppR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class UsuarioAppPersistenceAdapter implements UsuarioAppPort {

    private final UsuarioAppR2dbcRepository repository;
    private final DatabaseClient db;

    private static final String JOIN_SQL = """
            SELECT ua.*, p.nombre AS nombre_persona, p.foto_url AS foto_url_persona,
                   p.sexo AS sexo_persona
            FROM identidad.usuarios_app ua
            JOIN identidad.personas p ON ua.id_persona = p.id
            """;

    @Override
    public Mono<UsuarioApp> findByLoginAndIdCompania(String login, Integer idCompania) {
        return db.sql(JOIN_SQL + "WHERE ua.login = :login AND ua.id_compania = :idCompania")
                .bind("login", login)
                .bind("idCompania", idCompania)
                .map((row, meta) -> UsuarioAppMapper.fromRow(row))
                .one();
    }

    @Override
    public Mono<UsuarioApp> findById(Integer id) {
        return db.sql(JOIN_SQL + "WHERE ua.id = :id")
                .bind("id", id)
                .map((row, meta) -> UsuarioAppMapper.fromRow(row))
                .one();
    }

    @Override
    public Mono<Boolean> existsByIdPersonaAndIdCompania(Integer idPersona, Integer idCompania) {
        return repository.existsByIdPersonaAndIdCompania(idPersona, idCompania);
    }

    @Override
    public Mono<UsuarioApp> findByPersonaCiAndIdCompania(String ci, Integer idCompania) {
        return db.sql(JOIN_SQL + "WHERE p.ci = :ci AND ua.id_compania = :idCompania")
                .bind("ci", ci)
                .bind("idCompania", idCompania)
                .map((row, meta) -> UsuarioAppMapper.fromRow(row))
                .one();
    }

    @Override
    public Mono<UsuarioApp> findByTokenRecuperacion(String token) {
        return db.sql(JOIN_SQL + "WHERE ua.token_recuperacion = :token")
                .bind("token", token)
                .map((row, meta) -> UsuarioAppMapper.fromRow(row))
                .one();
    }

    @Override
    public Mono<UsuarioApp> save(UsuarioApp usuario) {
        return repository.save(UsuarioAppMapper.toEntity(usuario))
                .flatMap(saved -> findById(saved.getId()));
    }

    @Override
    public Flux<UsuarioApp> findByIdPersona(Integer idPersona) {
        return db.sql(JOIN_SQL + "WHERE ua.id_persona = :idPersona ORDER BY ua.id")
                .bind("idPersona", idPersona)
                .map((row, meta) -> UsuarioAppMapper.fromRow(row))
                .all();
    }
}
