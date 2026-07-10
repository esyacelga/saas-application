package com.gymadmin.platform.domain.port.in;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REQ-SAAS-001 (Sub-fase 1.5): puerto in para banners in-app de vencimiento.
 */
public interface BannerUseCase {

    Flux<BannerActivoView> listarBannersActivos(Long idCompania);

    /**
     * Descarta el banner (setea {@code descartado_at = NOW()}). Retorna:
     * <ul>
     *   <li>{@code true} si se descartó correctamente.</li>
     *   <li>{@code false} si el banner no existe o no pertenece al tenant.</li>
     * </ul>
     */
    Mono<Boolean> descartarBanner(Long idBanner, Long idCompania);

    record BannerActivoView(
            Long id,
            String tipo,
            Integer diasAntes,
            String mensaje,
            String urlCta,
            String urlCtaTexto
    ) {}
}
