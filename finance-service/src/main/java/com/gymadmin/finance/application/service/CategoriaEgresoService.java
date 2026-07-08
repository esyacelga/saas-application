package com.gymadmin.finance.application.service;

import com.gymadmin.finance.domain.model.CategoriaEgreso;
import com.gymadmin.finance.domain.port.in.CategoriaEgresoUseCase;
import com.gymadmin.finance.domain.port.out.CategoriaEgresoRepository;
import com.gymadmin.finance.infrastructure.exception.ConflictException;
import com.gymadmin.finance.infrastructure.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class CategoriaEgresoService implements CategoriaEgresoUseCase {

    private final CategoriaEgresoRepository repository;

    @Override
    public Flux<CategoriaEgreso> listar(Integer idCompania, Integer idSucursal) {
        if (idSucursal != null) {
            return repository.findByIdCompaniaAndIdSucursal(idCompania, idSucursal);
        }
        return repository.findByIdCompania(idCompania);
    }

    @Override
    public Mono<CategoriaEgreso> crear(CrearCommand command) {
        CategoriaEgreso categoria = CategoriaEgreso.builder()
                .idCompania(command.idCompania())
                .idSucursal(command.idSucursal())
                .nombre(command.nombre())
                .activo(true)
                .eliminado(false)
                .build();
        return repository.save(categoria);
    }

    @Override
    public Mono<CategoriaEgreso> desactivar(Integer id, Integer idCompania) {
        return repository.findByIdAndIdCompania(id, idCompania)
                .switchIfEmpty(Mono.error(new NotFoundException("Categoria de egreso no encontrada con id: " + id)))
                .flatMap(categoria -> repository.existsEgresosByIdCategoria(id)
                        .flatMap(enUso -> {
                            if (Boolean.TRUE.equals(enUso)) {
                                return Mono.error(new ConflictException("CATEGORIA_EN_USO",
                                        "La categoría está en uso por egresos existentes"));
                            }
                            categoria.setActivo(false);
                            return repository.save(categoria);
                        }));
    }
}
