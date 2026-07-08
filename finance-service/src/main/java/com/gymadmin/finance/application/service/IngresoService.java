package com.gymadmin.finance.application.service;

import com.gymadmin.finance.domain.model.Ingreso;
import com.gymadmin.finance.domain.port.in.IngresoUseCase;
import com.gymadmin.finance.domain.port.out.CategoriaIngresoRepository;
import com.gymadmin.finance.domain.port.out.IngresoRepository;
import com.gymadmin.finance.infrastructure.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IngresoService implements IngresoUseCase {

    private final IngresoRepository ingresoRepository;
    private final CategoriaIngresoRepository categoriaRepository;

    @Override
    public Mono<IngresoListResult> listar(ListarCommand command) {
        long offset = (long) (command.page() - 1) * command.limit();

        Mono<Long> countMono = ingresoRepository.countByFilters(
                command.idCompania(), command.desde(), command.hasta(), command.idCategoria());

        Mono<BigDecimal> sumMono = ingresoRepository.sumByFilters(
                command.idCompania(), command.desde(), command.hasta(), command.idCategoria());

        return Mono.zip(countMono, sumMono)
                .flatMap(tuple -> {
                    long total = tuple.getT1();
                    BigDecimal totalMonto = tuple.getT2() != null ? tuple.getT2() : BigDecimal.ZERO;

                    return ingresoRepository.findByFilters(
                                    command.idCompania(), command.desde(), command.hasta(),
                                    command.idCategoria(), command.limit(), offset)
                            .flatMap(ingreso -> categoriaRepository
                                    .findByIdAndIdCompania(ingreso.getIdCategoria(), command.idCompania())
                                    .map(cat -> new IngresoResumen(
                                            ingreso.getId(),
                                            cat.getNombre(),
                                            ingreso.getMonto(),
                                            ingreso.getDescripcion(),
                                            ingreso.getFecha(),
                                            resolverOrigen(ingreso),
                                            resolverIdReferencia(ingreso)
                                    ))
                                    .switchIfEmpty(Mono.just(new IngresoResumen(
                                            ingreso.getId(),
                                            "Sin categoría",
                                            ingreso.getMonto(),
                                            ingreso.getDescripcion(),
                                            ingreso.getFecha(),
                                            resolverOrigen(ingreso),
                                            resolverIdReferencia(ingreso)
                                    )))
                            )
                            .collectList()
                            .map(datos -> new IngresoListResult(totalMonto, total, datos));
                });
    }

    @Override
    public Mono<Ingreso> registrar(RegistrarCommand command) {
        return categoriaRepository.findByIdAndIdCompania(command.idCategoria(), command.idCompania())
                .switchIfEmpty(Mono.error(new NotFoundException(
                        "Categoria de ingreso no encontrada con id: " + command.idCategoria())))
                .flatMap(cat -> {
                    LocalDate fecha = command.fecha() != null ? command.fecha() : LocalDate.now();
                    Ingreso ingreso = Ingreso.builder()
                            .idCompania(command.idCompania())
                            .idSucursal(command.idSucursal())
                            .idCategoria(command.idCategoria())
                            .monto(command.monto())
                            .descripcion(command.descripcion())
                            .fecha(fecha)
                            .idMembresia(command.idMembresia())
                            .idVenta(command.idVenta())
                            .idUsuarioRegistro(command.idUsuarioRegistro())
                            .eliminado(false)
                            .build();
                    return ingresoRepository.save(ingreso);
                });
    }

    private String resolverOrigen(Ingreso ingreso) {
        if (ingreso.getIdMembresia() != null) return "membresia";
        if (ingreso.getIdVenta() != null) return "venta";
        return "manual";
    }

    private Integer resolverIdReferencia(Ingreso ingreso) {
        if (ingreso.getIdMembresia() != null) return ingreso.getIdMembresia();
        if (ingreso.getIdVenta() != null) return ingreso.getIdVenta();
        return null;
    }
}
