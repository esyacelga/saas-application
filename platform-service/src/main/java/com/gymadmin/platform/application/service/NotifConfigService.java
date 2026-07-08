package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.model.ConfigNotifSuscripcion;
import com.gymadmin.platform.domain.port.in.NotifConfigUseCase;
import com.gymadmin.platform.domain.port.out.ConfigNotifRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class NotifConfigService implements NotifConfigUseCase {

    private final ConfigNotifRepository configNotifRepository;

    public NotifConfigService(ConfigNotifRepository configNotifRepository) {
        this.configNotifRepository = configNotifRepository;
    }

    @Override
    public Flux<ConfigNotifSuscripcion> getConfig(Long idCompania) {
        return configNotifRepository.findByIdCompania(idCompania);
    }

    @Override
    public Mono<Void> updateConfig(Long idCompania, List<ConfigEntry> configs) {
        List<ConfigNotifSuscripcion> domainConfigs = configs.stream()
                .map(entry -> new ConfigNotifSuscripcion(
                        idCompania,
                        entry.diasAntes(),
                        ConfigNotifSuscripcion.Canal.valueOf(entry.canal().toUpperCase()),
                        entry.activo()
                ))
                .toList();
        return configNotifRepository.replaceAll(idCompania, domainConfigs);
    }
}
