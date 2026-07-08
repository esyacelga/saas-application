package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.NotifConfigService;
import com.gymadmin.platform.domain.model.ConfigNotifSuscripcion;
import com.gymadmin.platform.domain.port.in.NotifConfigUseCase.ConfigEntry;
import com.gymadmin.platform.domain.port.out.ConfigNotifRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotifConfigService — configuración de notificaciones de suscripción")
class NotifConfigServiceTest {

    @Mock
    private ConfigNotifRepository configNotifRepository;

    @InjectMocks
    private NotifConfigService service;

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("getConfig")
    class GetConfig {

        @Test
        @DisplayName("retorna las entradas de configuración de la compañía")
        void retornaConfiguracionDeLaCompania() {
            ConfigNotifSuscripcion c1 = new ConfigNotifSuscripcion(10L, 7, ConfigNotifSuscripcion.Canal.EMAIL, true);
            ConfigNotifSuscripcion c2 = new ConfigNotifSuscripcion(10L, 3, ConfigNotifSuscripcion.Canal.WHATSAPP, false);
            when(configNotifRepository.findByIdCompania(10L)).thenReturn(Flux.just(c1, c2));

            StepVerifier.create(service.getConfig(10L))
                    .assertNext(c -> {
                        assertThat(c.getDiasAntes()).isEqualTo(7);
                        assertThat(c.getCanal()).isEqualTo(ConfigNotifSuscripcion.Canal.EMAIL);
                        assertThat(c.getActivo()).isTrue();
                    })
                    .assertNext(c -> {
                        assertThat(c.getDiasAntes()).isEqualTo(3);
                        assertThat(c.getCanal()).isEqualTo(ConfigNotifSuscripcion.Canal.WHATSAPP);
                        assertThat(c.getActivo()).isFalse();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna flux vacío cuando la compañía no tiene configuración")
        void retornaFluxVacioCuandoNoHayConfig() {
            when(configNotifRepository.findByIdCompania(999L)).thenReturn(Flux.empty());

            StepVerifier.create(service.getConfig(999L))
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("updateConfig")
    class UpdateConfig {

        @Test
        @DisplayName("invoca replaceAll con los objetos de dominio mapeados desde los entries")
        void actualizaConfiguracionExitosamente() {
            List<ConfigEntry> entries = List.of(
                    new ConfigEntry(7, "EMAIL", true),
                    new ConfigEntry(3, "WHATSAPP", false)
            );

            when(configNotifRepository.replaceAll(eq(10L), anyList())).thenReturn(Mono.empty());

            StepVerifier.create(service.updateConfig(10L, entries))
                    .verifyComplete();
        }

        @Test
        @DisplayName("mapea correctamente el canal a mayúsculas y construye ConfigNotifSuscripcion")
        void mapeaCorrectamenteLosEntries() {
            List<ConfigEntry> entries = List.of(
                    new ConfigEntry(15, "ambos", true)
            );

            when(configNotifRepository.replaceAll(eq(5L), anyList())).thenAnswer(invocation -> {
                List<ConfigNotifSuscripcion> domainConfigs = invocation.getArgument(1);
                assertThat(domainConfigs).hasSize(1);
                assertThat(domainConfigs.get(0).getCanal()).isEqualTo(ConfigNotifSuscripcion.Canal.AMBOS);
                assertThat(domainConfigs.get(0).getDiasAntes()).isEqualTo(15);
                assertThat(domainConfigs.get(0).getActivo()).isTrue();
                assertThat(domainConfigs.get(0).getIdCompania()).isEqualTo(5L);
                return Mono.empty();
            });

            StepVerifier.create(service.updateConfig(5L, entries))
                    .verifyComplete();
        }

        @Test
        @DisplayName("funciona con lista vacía de entries")
        void funcionaConListaVacia() {
            when(configNotifRepository.replaceAll(eq(10L), anyList())).thenReturn(Mono.empty());

            StepVerifier.create(service.updateConfig(10L, List.of()))
                    .verifyComplete();
        }
    }
}
