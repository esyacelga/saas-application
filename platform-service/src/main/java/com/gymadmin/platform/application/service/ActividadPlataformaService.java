package com.gymadmin.platform.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymadmin.platform.domain.model.ActividadPlataforma;
import com.gymadmin.platform.domain.model.ActividadPlataforma.TipoActor;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.out.ActividadPlataformaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
public class ActividadPlataformaService implements ActividadPlataformaUseCase {

    private static final Logger log = LoggerFactory.getLogger(ActividadPlataformaService.class);

    private final ActividadPlataformaRepository repository;
    private final ObjectMapper objectMapper;

    public ActividadPlataformaService(ActividadPlataformaRepository repository,
                                       ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Flux<ActividadPlataforma> listar(ListarQuery query) {
        return repository.findAll(query);
    }

    @Override
    public Mono<Long> contar(ListarQuery query) {
        return repository.count(query);
    }

    @Override
    public Mono<Void> registrar(RegistrarCommand command) {
        ActividadPlataforma actividad = new ActividadPlataforma();
        actividad.setTipoEvento(command.tipoEvento());
        actividad.setModulo(command.modulo());
        actividad.setEntidadId(command.entidadId());
        actividad.setEntidadNombre(command.entidadNombre());
        actividad.setDetalle(command.detalle());
        actividad.setUsuario(command.usuario());
        actividad.setFecha(OffsetDateTime.now());
        return repository.save(actividad);
    }

    /**
     * REQ-SAAS-001 sección 6bis — registra un evento de la lista canónica.
     * El {@code detalle} (Map) se serializa a JSON con Jackson y se persiste en
     * la columna JSONB {@code detalle}.
     */
    @Override
    public Mono<Void> registrar(RegistrarActividadCommand command) {
        ActividadPlataforma actividad = new ActividadPlataforma();
        actividad.setTipoEvento(command.evento());
        actividad.setTipoActor(command.tipoActor() != null ? command.tipoActor() : TipoActor.SISTEMA);
        actividad.setIdUsuarioActor(command.idUsuarioActor());
        actividad.setIp(command.ipActor());
        actividad.setIdCompania(command.idCompania());
        actividad.setDetalle(serializarDetalle(command.detalle()));
        actividad.setFecha(OffsetDateTime.now());
        return repository.save(actividad);
    }

    private String serializarDetalle(Map<String, Object> detalle) {
        if (detalle == null || detalle.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detalle);
        } catch (JsonProcessingException e) {
            log.warn("No se pudo serializar detalle de actividad: {}", e.getMessage());
            return null;
        }
    }
}
