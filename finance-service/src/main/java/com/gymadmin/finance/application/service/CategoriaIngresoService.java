package com.gymadmin.finance.application.service;

import com.gymadmin.finance.domain.model.CategoriaIngreso;
import com.gymadmin.finance.domain.port.in.CategoriaIngresoUseCase;
import com.gymadmin.finance.domain.port.out.CategoriaIngresoRepository;
import com.gymadmin.finance.infrastructure.exception.ConflictException;
import com.gymadmin.finance.infrastructure.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class CategoriaIngresoService implements CategoriaIngresoUseCase {

    private final CategoriaIngresoRepository repository;

    @Override
    public Flux<CategoriaIngreso> listar(Integer idCompania, Integer idSucursal) {
        if (idSucursal != null) {
            return repository.findByIdCompaniaAndIdSucursal(idCompania, idSucursal);
        }
        return repository.findByIdCompania(idCompania);
    }

    @Override
    public Mono<CategoriaIngreso> crear(CrearCommand command) {
        CategoriaIngreso categoria = CategoriaIngreso.builder()
                .idCompania(command.idCompania())
                .idSucursal(command.idSucursal())
                .nombre(command.nombre())
                .activo(true)
                .eliminado(false)
                .build();
        return repository.save(categoria);
    }

    @Override
    public Mono<CategoriaIngreso> desactivar(Integer id, Integer idCompania) {
        return repository.findByIdAndIdCompania(id, idCompania)
                .switchIfEmpty(Mono.error(new NotFoundException("Categoria de ingreso no encontrada con id: " + id)))
                .flatMap(categoria -> repository.existsIngresosByIdCategoria(id)
                        .flatMap(enUso -> {
                            if (Boolean.TRUE.equals(enUso)) {
                                return Mono.error(new ConflictException("CATEGORIA_EN_USO",
                                        "La categoría está en uso por ingresos existentes"));
                            }
                            categoria.setActivo(false);
                            return repository.save(categoria);
                        }));
    }
}
