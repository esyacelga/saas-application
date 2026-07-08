package com.gymadmin.finance.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.finance.domain.model.CategoriaIngreso;
import com.gymadmin.finance.domain.port.out.CategoriaIngresoRepository;
import com.gymadmin.finance.infrastructure.adapter.out.persistence.entity.CategoriaIngresoEntity;
import com.gymadmin.finance.infrastructure.adapter.out.persistence.repository.CategoriaIngresoR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class CategoriaIngresoPersistenceAdapter implements CategoriaIngresoRepository {

    private final CategoriaIngresoR2dbcRepository r2dbcRepository;

    @Override
    public Flux<CategoriaIngreso> findByIdCompania(Integer idCompania) {
        return r2dbcRepository.findByIdCompaniaAndEliminadoFalse(idCompania)
                .map(this::toDomain);
    }

    @Override
    public Flux<CategoriaIngreso> findByIdCompaniaAndIdSucursal(Integer idCompania, Integer idSucursal) {
        return r2dbcRepository.findByIdCompaniaAndIdSucursalAndEliminadoFalse(idCompania, idSucursal)
                .map(this::toDomain);
    }

    @Override
    public Mono<CategoriaIngreso> findByIdAndIdCompania(Integer id, Integer idCompania) {
        return r2dbcRepository.findByIdAndIdCompaniaAndEliminadoFalse(id, idCompania)
                .map(this::toDomain);
    }

    @Override
    public Mono<CategoriaIngreso> save(CategoriaIngreso categoria) {
        return r2dbcRepository.save(toEntity(categoria))
                .map(this::toDomain);
    }

    @Override
    public Mono<Boolean> existsIngresosByIdCategoria(Integer idCategoria) {
        return r2dbcRepository.existsByIdCategoria(idCategoria);
    }

    private CategoriaIngreso toDomain(CategoriaIngresoEntity entity) {
        return CategoriaIngreso.builder()
                .id(entity.getId())
                .idCompania(entity.getIdCompania())
                .idSucursal(entity.getIdSucursal())
                .nombre(entity.getNombre())
                .activo(entity.getActivo())
                .eliminado(entity.getEliminado())
                .creacionFecha(entity.getCreacionFecha())
                .creacionUsuario(entity.getCreacionUsuario())
                .modificaFecha(entity.getModificaFecha())
                .modificaUsuario(entity.getModificaUsuario())
                .build();
    }

    private CategoriaIngresoEntity toEntity(CategoriaIngreso domain) {
        return CategoriaIngresoEntity.builder()
                .id(domain.getId())
                .idCompania(domain.getIdCompania())
                .idSucursal(domain.getIdSucursal())
                .nombre(domain.getNombre())
                .activo(domain.getActivo())
                .eliminado(domain.getEliminado() != null ? domain.getEliminado() : false)
                .creacionFecha(domain.getCreacionFecha())
                .creacionUsuario(domain.getCreacionUsuario())
                .modificaFecha(domain.getModificaFecha())
                .modificaUsuario(domain.getModificaUsuario())
                .build();
    }
}
