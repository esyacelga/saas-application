package com.gymadmin.finance.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.finance.domain.model.CategoriaEgreso;
import com.gymadmin.finance.domain.port.out.CategoriaEgresoRepository;
import com.gymadmin.finance.infrastructure.adapter.out.persistence.entity.CategoriaEgresoEntity;
import com.gymadmin.finance.infrastructure.adapter.out.persistence.repository.CategoriaEgresoR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class CategoriaEgresoPersistenceAdapter implements CategoriaEgresoRepository {

    private final CategoriaEgresoR2dbcRepository r2dbcRepository;

    @Override
    public Flux<CategoriaEgreso> findByIdCompania(Integer idCompania) {
        return r2dbcRepository.findByIdCompaniaAndEliminadoFalse(idCompania)
                .map(this::toDomain);
    }

    @Override
    public Flux<CategoriaEgreso> findByIdCompaniaAndIdSucursal(Integer idCompania, Integer idSucursal) {
        return r2dbcRepository.findByIdCompaniaAndIdSucursalAndEliminadoFalse(idCompania, idSucursal)
                .map(this::toDomain);
    }

    @Override
    public Mono<CategoriaEgreso> findByIdAndIdCompania(Integer id, Integer idCompania) {
        return r2dbcRepository.findByIdAndIdCompaniaAndEliminadoFalse(id, idCompania)
                .map(this::toDomain);
    }

    @Override
    public Mono<CategoriaEgreso> save(CategoriaEgreso categoria) {
        return r2dbcRepository.save(toEntity(categoria))
                .map(this::toDomain);
    }

    @Override
    public Mono<Boolean> existsEgresosByIdCategoria(Integer idCategoria) {
        return r2dbcRepository.existsByIdCategoria(idCategoria);
    }

    private CategoriaEgreso toDomain(CategoriaEgresoEntity entity) {
        return CategoriaEgreso.builder()
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

    private CategoriaEgresoEntity toEntity(CategoriaEgreso domain) {
        return CategoriaEgresoEntity.builder()
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
