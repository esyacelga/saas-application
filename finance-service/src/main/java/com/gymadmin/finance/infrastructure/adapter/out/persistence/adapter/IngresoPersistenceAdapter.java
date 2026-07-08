package com.gymadmin.finance.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.finance.domain.model.Ingreso;
import com.gymadmin.finance.domain.port.out.IngresoRepository;
import com.gymadmin.finance.infrastructure.adapter.out.persistence.entity.IngresoEntity;
import com.gymadmin.finance.infrastructure.adapter.out.persistence.repository.IngresoR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class IngresoPersistenceAdapter implements IngresoRepository {

    private final IngresoR2dbcRepository r2dbcRepository;

    @Override
    public Flux<Ingreso> findByFilters(Integer idCompania, LocalDate desde, LocalDate hasta,
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
    public Mono<Ingreso> save(Ingreso ingreso) {
        return r2dbcRepository.save(toEntity(ingreso))
                .map(this::toDomain);
    }

    private Ingreso toDomain(IngresoEntity entity) {
        return Ingreso.builder()
                .id(entity.getId())
                .idCompania(entity.getIdCompania())
                .idSucursal(entity.getIdSucursal())
                .idCategoria(entity.getIdCategoria())
                .idMembresia(entity.getIdMembresia())
                .idVenta(entity.getIdVenta())
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

    private IngresoEntity toEntity(Ingreso domain) {
        return IngresoEntity.builder()
                .id(domain.getId())
                .idCompania(domain.getIdCompania())
                .idSucursal(domain.getIdSucursal())
                .idCategoria(domain.getIdCategoria())
                .idMembresia(domain.getIdMembresia())
                .idVenta(domain.getIdVenta())
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
