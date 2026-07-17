package com.gymadmin.core.application.service;

import com.gymadmin.core.domain.model.Cliente;
import com.gymadmin.core.domain.model.Membresia;
import com.gymadmin.core.domain.model.TipoMembresia;
import com.gymadmin.core.domain.port.out.ClienteRepository;
import com.gymadmin.core.domain.port.out.MembresiaRepository;
import com.gymadmin.core.domain.port.out.TipoMembresiaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class ClienteStatusJobService {

    private static final Logger log = LoggerFactory.getLogger(ClienteStatusJobService.class);

    private final ClienteRepository clienteRepository;
    private final MembresiaRepository membresiaRepository;
    private final TipoMembresiaRepository tipoMembresiaRepository;

    @Value("${jobs.run-on-startup:true}")
    private boolean runOnStartup;

    public ClienteStatusJobService(ClienteRepository clienteRepository,
                                   MembresiaRepository membresiaRepository,
                                   TipoMembresiaRepository tipoMembresiaRepository) {
        this.clienteRepository = clienteRepository;
        this.membresiaRepository = membresiaRepository;
        this.tipoMembresiaRepository = tipoMembresiaRepository;
    }

    @Scheduled(cron = "${client.status.job.cron:0 10 0 * * *}")
    public void ejecutar() {
        clienteRepository.findActivosParaJob()
                .flatMap(this::procesarCliente)
                .subscribe();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ejecutarAlIniciar() {
        if (!runOnStartup) {
            log.info("[ClienteStatusJob] Skip startup run (jobs.run-on-startup=false)");
            return;
        }
        log.info("[ClienteStatusJob] Ejecutando al arrancar (recuperación de ventana perdida)");
        ejecutar();
    }

    private Mono<Void> procesarCliente(Cliente cliente) {
        if (Cliente.Estado.congelado.equals(cliente.getEstado())) {
            return Mono.empty();
        }
        // findActivaByIdClienteAndIdCompania filtra por estado_pago='PAGADO' y eliminado=false
        // (GYM-003 §5.2): una PENDIENTE sin fechas no debe marcar al cliente como vencido.
        return membresiaRepository.findActivaByIdClienteAndIdCompania(cliente.getId(), cliente.getIdCompania())
                .flatMap(mem -> tipoMembresiaRepository.findById(mem.getIdTipoMembresia())
                        .flatMap(tipo -> evaluarEstado(cliente, mem, tipo))
                        .thenReturn(cliente)
                )
                .switchIfEmpty(Mono.defer(() -> {
                    cliente.setEstado(Cliente.Estado.vencido);
                    return clienteRepository.save(cliente);
                }))
                .then();
    }

    private Mono<Void> evaluarEstado(Cliente cliente, Membresia mem, TipoMembresia tipo) {
        LocalDate hoy = LocalDate.now();

        if (TipoMembresia.ModoControl.accesos.equals(tipo.getModoControl())) {
            return membresiaRepository.countAsistenciasByIdMembresia(mem.getId())
                    .flatMap(usados -> {
                        if (usados >= mem.getDiasAccesoTotal() || hoy.isAfter(mem.getFechaFin())) {
                            mem.setEstado(Membresia.Estado.vencida);
                            cliente.setEstado(Cliente.Estado.vencido);
                        } else {
                            int restantes = mem.getDiasAccesoTotal() - usados.intValue();
                            cliente.setEstado(restantes <= 3 ? Cliente.Estado.proximo_vencer : Cliente.Estado.activo);
                        }
                        return membresiaRepository.save(mem).then(clienteRepository.save(cliente)).then();
                    });
        }

        long diasRestantes = ChronoUnit.DAYS.between(hoy, mem.getFechaFin());
        if (diasRestantes < 0) {
            mem.setEstado(Membresia.Estado.vencida);
            cliente.setEstado(Cliente.Estado.vencido);
        } else if (diasRestantes <= 3) {
            cliente.setEstado(Cliente.Estado.proximo_vencer);
        } else {
            cliente.setEstado(Cliente.Estado.activo);
        }
        return membresiaRepository.save(mem).then(clienteRepository.save(cliente)).then();
    }
}
