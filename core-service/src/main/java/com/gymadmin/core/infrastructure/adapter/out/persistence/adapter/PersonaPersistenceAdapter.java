package com.gymadmin.core.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.core.domain.port.out.PersonaRepository;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Component
public class PersonaPersistenceAdapter implements PersonaRepository {

    private final DatabaseClient databaseClient;

    public PersonaPersistenceAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<PersonaResult> findByCi(String ci) {
        return databaseClient.sql("SELECT id, ci, nombre, telefono, correo, foto_url FROM identidad.personas WHERE ci = :ci AND eliminado = false")
                .bind("ci", ci)
                .map((row, meta) -> new PersonaResult(
                        row.get("id", Long.class),
                        row.get("ci", String.class),
                        row.get("nombre", String.class),
                        row.get("telefono", String.class),
                        row.get("correo", String.class),
                        row.get("foto_url", String.class)
                ))
                .one();
    }

    @Override
    public Mono<PersonaResult> create(CreatePersonaCommand command) {
        var spec = databaseClient.sql("""
                INSERT INTO identidad.personas (ci, nombre, telefono, correo, fecha_nacimiento, foto_url)
                VALUES (:ci, :nombre, :telefono, :correo, :fechaNacimiento, :fotoUrl)
                RETURNING id, ci, nombre, telefono, correo, foto_url
                """)
                .bind("ci", command.ci())
                .bind("nombre", command.nombre())
                .bind("telefono", command.telefono() != null ? command.telefono() : "")
                .bind("correo", command.correo() != null ? command.correo() : "")
                .bind("fechaNacimiento", command.fechaNacimiento() != null ? command.fechaNacimiento() : LocalDate.of(1900, 1, 1));

        spec = command.fotoUrl() != null
                ? spec.bind("fotoUrl", command.fotoUrl())
                : spec.bindNull("fotoUrl", String.class);

        return spec.map((row, meta) -> new PersonaResult(
                        row.get("id", Long.class),
                        row.get("ci", String.class),
                        row.get("nombre", String.class),
                        row.get("telefono", String.class),
                        row.get("correo", String.class),
                        row.get("foto_url", String.class)
                ))
                .one();
    }
}
