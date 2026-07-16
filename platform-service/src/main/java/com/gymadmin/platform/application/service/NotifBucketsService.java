package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.model.NotifBucketGlobal;
import com.gymadmin.platform.domain.model.NotifBucketGlobal.Destinatario;
import com.gymadmin.platform.domain.port.in.NotifBucketsUseCase;
import com.gymadmin.platform.domain.port.out.NotifBucketGlobalRepository;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Fase 6 (R1): política global de buckets de aviso previo. Solo super_admin (gate en el controller).
 */
@Service
public class NotifBucketsService implements NotifBucketsUseCase {

    private final NotifBucketGlobalRepository repository;

    public NotifBucketsService(NotifBucketGlobalRepository repository) {
        this.repository = repository;
    }

    @Override
    public Flux<NotifBucketGlobal> listar() {
        return repository.findAll();
    }

    @Override
    public Mono<NotifBucketGlobal> actualizar(String destinatario, int diasPrevio, boolean activo,
                                              Long modificadoPor) {
        Destinatario dest;
        try {
            dest = Destinatario.fromCodigo(destinatario);
        } catch (IllegalArgumentException e) {
            return Mono.error(new NotFoundException(
                    "Destinatario inválido: " + destinatario + " (esperado 'socio' o 'dueno')"));
        }
        // La fila siempre existe (seed de la migración); si no, es un estado inesperado → 404.
        return repository.findByDestinatario(dest)
                .switchIfEmpty(Mono.error(new NotFoundException(
                        "No existe la fila de buckets para el destinatario: " + destinatario)))
                .flatMap(existing -> {
                    NotifBucketGlobal actualizado = new NotifBucketGlobal(dest, diasPrevio, activo);
                    actualizado.setModificadoPor(modificadoPor);
                    return repository.save(actualizado);
                });
    }
}
