package com.gymadmin.finance.application.service;

import com.gymadmin.finance.domain.model.Egreso;
import com.gymadmin.finance.domain.port.in.EgresoUseCase;
import com.gymadmin.finance.domain.port.out.CategoriaEgresoRepository;
import com.gymadmin.finance.domain.port.out.EgresoRepository;
import com.gymadmin.finance.infrastructure.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class EgresoService implements EgresoUseCase {

    private final EgresoRepository egresoRepository;
    private final CategoriaEgresoRepository categoriaRepository;

    @Override
    public Mono<EgresoListResult> listar(ListarCommand command) {
        long offset = (long) (command.page() - 1) * command.limit();

        Mono<Long> countMono = egresoRepository.countByFilters(
                command.idCompania(), command.desde(), command.hasta(), command.idCategoria());

        Mono<BigDecimal> sumMono = egresoRepository.sumByFilters(
                command.idCompania(), command.desde(), command.hasta(), command.idCategoria());

        return Mono.zip(countMono, sumMono)
                .flatMap(tuple -> {
                    long total = tuple.getT1();
                    BigDecimal totalMonto = tuple.getT2() != null ? tuple.getT2() : BigDecimal.ZERO;

                    return egresoRepository.findByFilters(
                                    command.idCompania(), command.desde(), command.hasta(),
                                    command.idCategoria(), command.limit(), offset)
                            .flatMap(egreso -> categoriaRepository
                                    .findByIdAndIdCompania(egreso.getIdCategoria(), command.idCompania())
                                    .map(cat -> new EgresoResumen(
                                            egreso.getId(),
                                            cat.getNombre(),
                                            egreso.getMonto(),
                                            egreso.getDescripcion(),
                                            egreso.getFecha()
                                    ))
                                    .switchIfEmpty(Mono.just(new EgresoResumen(
                                            egreso.getId(),
                                            "Sin categoría",
                                            egreso.getMonto(),
                                            egreso.getDescripcion(),
                                            egreso.getFecha()
                                    )))
                            )
                            .collectList()
                            .map(datos -> new EgresoListResult(totalMonto, total, datos));
                });
    }

    @Override
    public Mono<Egreso> registrar(RegistrarCommand command) {
        return categoriaRepository.findByIdAndIdCompania(command.idCategoria(), command.idCompania())
                .switchIfEmpty(Mono.error(new NotFoundException(
                        "Categoria de egreso no encontrada con id: " + command.idCategoria())))
                .flatMap(cat -> {
                    LocalDate fecha = command.fecha() != null ? command.fecha() : LocalDate.now();
                    Egreso egreso = Egreso.builder()
                            .idCompania(command.idCompania())
                            .idSucursal(command.idSucursal())
                            .idCategoria(command.idCategoria())
                            .monto(command.monto())
                            .descripcion(command.descripcion())
                            .fecha(fecha)
                            .idUsuarioRegistro(command.idUsuarioRegistro())
                            .eliminado(false)
                            .build();
                    return egresoRepository.save(egreso);
                });
    }
}
