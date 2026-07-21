package com.gymadmin.platform.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.platform.domain.port.out.MetodoPagoRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.MetodoPagoEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.MetodoPagoR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class MetodoPagoPersistenceAdapter implements MetodoPagoRepository {

    private static final List<String> POR_DEFECTO = List.of("Efectivo", "Tarjeta", "Transferencia");

    private final MetodoPagoR2dbcRepository repository;

    public MetodoPagoPersistenceAdapter(MetodoPagoR2dbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<Void> crearPorDefecto(Long idCompania, Long idSucursal) {
        // Idempotente: la tabla no tiene UNIQUE (id_compania, nombre), así que evitamos
        // duplicados comparando contra los nombres ya existentes de la compañía.
        return repository.findByIdCompaniaAndEliminadoFalse(idCompania)
                .map(MetodoPagoEntity::getNombre)
                .collect(Collectors.toSet())
                .flatMapMany(existentes -> Flux.fromIterable(faltantes(existentes, idCompania, idSucursal)))
                .flatMap(repository::save)
                .then();
    }

    private List<MetodoPagoEntity> faltantes(Set<String> existentes, Long idCompania, Long idSucursal) {
        return POR_DEFECTO.stream()
                .filter(nombre -> !existentes.contains(nombre))
                .map(nombre -> {
                    MetodoPagoEntity e = new MetodoPagoEntity();
                    e.setIdCompania(idCompania);
                    e.setIdSucursal(idSucursal);
                    e.setNombre(nombre);
                    e.setActivo(true);
                    return e;
                })
                .toList();
    }
}
