package com.gymadmin.finance.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.finance.domain.model.Egreso;
import com.gymadmin.finance.domain.port.out.EgresoRepository;
import com.gymadmin.finance.infrastructure.adapter.out.persistence.entity.EgresoEntity;
import com.gymadmin.finance.infrastructure.adapter.out.persistence.repository.EgresoR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class EgresoPersistenceAdapter implements EgresoRepository {

    private final EgresoR2dbcRepository r2dbcRepository;

    @Override
    public Flux<Egreso> findByFilters(Integer idCompania, LocalDate desde, LocalDate hasta,
                                      Integer idCategoria, int limit, long offset) {
        return r2dbcRepository.findByFilters(idCompania, desde, hasta, idCategoria, limit, offset)
                .map(this::toDomain);
    }

    @Override
    public Mono<Long> countByFilters(Integer idCompania, LocalDate desde, LocalDate hasta, Integer idCategoria) {
        return r2dbcRepository.countByFilters(idCompania, desde, hasta, idCategoria);
    }

    @Override
    public Mono<BigDecimal> sumByFilters(Integer idCompania, LocalDate desde, LocalDate hasta, Integer idCategoria) {
        return r2dbcRepository.sumByFilters(idCompania, desde, hasta, idCategoria);
    }

    @Override
    public Mono<Egreso> save(Egreso egreso) {
        return r2dbcRepository.save(toEntity(egreso))
                .map(this::toDomain);
    }

    private Egreso toDomain(EgresoEntity entity) {
        return Egreso.builder()
                .id(entity.getId())
                .idCompania(entity.getIdCompania())
                .idSucursal(entity.getIdSucursal())
                .idCategoria(entity.getIdCategoria())
                .monto(entity.getMonto())
                .descripcion(entity.getDescripcion())
                .fecha(entity.getFecha())
                .idUsuarioRegistro(entity.getIdUsuarioRegistro())
                .eliminado(entity.getEliminado())
                .creacionFecha(entity.getCreacionFecha())
                .creacionUsuario(entity.getCreacionUsuario())
                .modificaFecha(entity.getModificaFecha())
                .modificaUsuario(entity.getModificaUsuario())
                .build();
    }

    private EgresoEntity toEntity(Egreso domain) {
        return EgresoEntity.builder()
                .id(domain.getId())
                .idCompania(domain.getIdCompania())
                .idSucursal(domain.getIdSucursal())
                .idCategoria(domain.getIdCategoria())
                .monto(domain.getMonto())
                .descripcion(domain.getDescripcion())
                .fecha(domain.getFecha())
                .idUsuarioRegistro(domain.getIdUsuarioRegistro())
                .eliminado(domain.getEliminado() != null ? domain.getEliminado() : false)
                .creacionFecha(domain.getCreacionFecha())
                .creacionUsuario(domain.getCreacionUsuario())
                .modificaFecha(domain.getModificaFecha())
                .modificaUsuario(domain.getModificaUsuario())
                .build();
    }
}
