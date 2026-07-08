package com.gymadmin.core.application.service;

import com.gymadmin.core.domain.model.TipoMembresia;
import com.gymadmin.core.domain.port.in.TipoMembresiaUseCase;
import com.gymadmin.core.domain.port.out.TipoMembresiaRepository;
import com.gymadmin.core.infrastructure.exception.BusinessException;
import com.gymadmin.core.infrastructure.exception.ConflictException;
import com.gymadmin.core.infrastructure.exception.NotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class TipoMembresiaService implements TipoMembresiaUseCase {

    private final TipoMembresiaRepository tipoMembresiaRepository;

    public TipoMembresiaService(TipoMembresiaRepository tipoMembresiaRepository) {
        this.tipoMembresiaRepository = tipoMembresiaRepository;
    }

    @Override
    public Flux<TipoMembresia> listarActivos(Long idCompania) {
        return tipoMembresiaRepository.findActivosByIdCompania(idCompania);
    }

    @Override
    public Mono<TipoMembresia> crear(Long idCompania, Long idSucursal, CrearTipoCommand command) {
        if (TipoMembresia.ModoControl.accesos.equals(command.modoControl()) && command.diasAcceso() == null) {
            return Mono.error(new BusinessException("dias_acceso es requerido para modo_control='accesos'"));
        }
        return tipoMembresiaRepository.findByNombreAndIdCompania(command.nombre(), idCompania)
                .flatMap(existing -> Mono.<TipoMembresia>error(new ConflictException("Ya existe un tipo de membresía con el nombre: " + command.nombre())))
                .switchIfEmpty(Mono.defer(() -> {
                    TipoMembresia tipo = new TipoMembresia();
                    tipo.setIdCompania(idCompania);
                    tipo.setIdSucursal(idSucursal);
                    tipo.setNombre(command.nombre());
                    tipo.setModoControl(command.modoControl());
                    tipo.setDuracionTipo(command.duracionTipo());
                    tipo.setDuracionValor(command.duracionValor());
                    tipo.setDiasAcceso(command.diasAcceso());
                    tipo.setPrecio(command.precio());
                    tipo.setActivo(true);
                    return tipoMembresiaRepository.save(tipo);
                }));
    }

    @Override
    public Mono<TipoMembresia> actualizar(Long id, Long idCompania, ActualizarTipoCommand command) {
        return tipoMembresiaRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("TipoMembresia", id)))
                .flatMap(tipo -> {
                    if (!idCompania.equals(tipo.getIdCompania())) {
                        return Mono.error(new com.gymadmin.core.infrastructure.exception.ForbiddenException("Access denied"));
                    }
                    if (command.nombre() != null) tipo.setNombre(command.nombre());
                    if (command.precio() != null) tipo.setPrecio(command.precio());
                    return tipoMembresiaRepository.save(tipo);
                });
    }

    @Override
    public Mono<Void> desactivar(Long id, Long idCompania) {
        return tipoMembresiaRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("TipoMembresia", id)))
                .flatMap(tipo -> {
                    if (!idCompania.equals(tipo.getIdCompania())) {
                        return Mono.error(new com.gymadmin.core.infrastructure.exception.ForbiddenException("Access denied"));
                    }
                    return tipoMembresiaRepository.existeMembresiaActivaDeEsteTipo(id)
                            .flatMap(existe -> {
                                if (existe) {
                                    return Mono.error(new ConflictException("No se puede desactivar: existen membresías activas de este tipo"));
                                }
                                tipo.setActivo(false);
                                return tipoMembresiaRepository.save(tipo).then();
                            });
                });
    }
}
