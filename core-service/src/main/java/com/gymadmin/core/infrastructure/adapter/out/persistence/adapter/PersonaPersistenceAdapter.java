package com.gymadmin.core.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.core.domain.port.out.PersonaRepository;
import com.gymadmin.core.domain.validation.CedulaEcuatoriana;
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
    public Mono<String> findNombreById(Long id) {
        return databaseClient.sql("SELECT nombre FROM identidad.personas WHERE id = :id AND eliminado = false")
                .bind("id", id)
                .map((row, meta) -> row.get("nombre", String.class))
                .one();
    }

    @Override
    public Mono<PersonaResult> create(CreatePersonaCommand command) {
        // ci_validada: true solo si el documento pasa el algoritmo del dígito verificador
        // ecuatoriano (módulo 10). Documentos no-EC (pasaporte, RUC, extranjero) → false. Nunca
        // rechaza el registro. Réplica del cálculo que ya hacen platform-service y auth-service al
        // crear persona; aquí cubre la ruta admin (registro de cliente desde el panel).
        boolean ciValidada = CedulaEcuatoriana.esValida(command.ci());

        var spec = databaseClient.sql("""
                INSERT INTO identidad.personas (ci, nombre, telefono, correo, fecha_nacimiento, foto_url, ci_validada)
                VALUES (:ci, :nombre, :telefono, :correo, :fechaNacimiento, :fotoUrl, :ciValidada)
                RETURNING id, ci, nombre, telefono, correo, foto_url
                """)
                .bind("ci", command.ci())
                .bind("nombre", command.nombre())
                .bind("telefono", command.telefono() != null ? command.telefono() : "")
                .bind("correo", command.correo() != null ? command.correo() : "")
                .bind("ciValidada", ciValidada);

        spec = command.fechaNacimiento() != null
                ? spec.bind("fechaNacimiento", command.fechaNacimiento())
                : spec.bindNull("fechaNacimiento", LocalDate.class);

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
