package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.model.NotificacionSuscripcion;
import com.gymadmin.platform.domain.port.in.BannerUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaRepository;
import com.gymadmin.platform.domain.port.out.NotificacionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * REQ-SAAS-001 (Sub-fase 1.5): expone banners in-app y descarte.
 * <p>
 * El mensaje se genera con el mismo criterio que el email — un template corto por
 * {@code dias_antes}. El texto plano de {@code .txt} se reutiliza como mensaje del
 * banner (formato conciso, sin HTML).
 */
@Service
public class BannerService implements BannerUseCase {

    private final NotificacionRepository notificacionRepository;
    private final CompaniaRepository companiaRepository;
    private final String urlComprarPremium;
    private final String urlCtaTexto;

    public BannerService(NotificacionRepository notificacionRepository,
                          CompaniaRepository companiaRepository,
                          @Value("${notificacion.email.urls.comprar-premium:https://gymadmin.app/planes}") String urlComprarPremium,
                          @Value("${notificacion.banner.cta-texto:Renovar mi plan}") String urlCtaTexto) {
        this.notificacionRepository = notificacionRepository;
        this.companiaRepository = companiaRepository;
        this.urlComprarPremium = urlComprarPremium;
        this.urlCtaTexto = urlCtaTexto;
    }

    @Override
    public Flux<BannerActivoView> listarBannersActivos(Long idCompania) {
        return companiaRepository.findById(idCompania)
                .flatMapMany(compania -> notificacionRepository.findBannersActivosHoy(idCompania)
                        .map(notif -> toView(notif, compania)));
    }

    @Override
    public Mono<Boolean> descartarBanner(Long idBanner, Long idCompania) {
        return notificacionRepository.descartarBanner(idBanner, idCompania)
                .map(rows -> rows != null && rows > 0);
    }

    private BannerActivoView toView(NotificacionSuscripcion notif, Compania compania) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("owner_nombre", compania.getNombre() != null ? compania.getNombre() : "");
        vars.put("plan_actual", planActualDeTipo(notif.getTipo()));
        vars.put("plan_destino", "Free");
        vars.put("dias_restantes", notif.getDiasAntes() != null ? notif.getDiasAntes() : 0);
        String mensaje = mensajePorBucket(notif.getDiasAntes(), notif.getTipo());
        return new BannerActivoView(
                notif.getId(),
                notif.getTipo(),
                notif.getDiasAntes(),
                mensaje,
                urlComprarPremium,
                urlCtaTexto
        );
    }

    private static String mensajePorBucket(Integer diasAntes, String tipo) {
        String plan = planActualDeTipo(tipo);
        int d = diasAntes != null ? diasAntes : 0;
        return switch (d) {
            case 0 -> "Tu plan " + plan + " vence hoy — renueva para no perder acceso premium.";
            case 1 -> "Tu plan " + plan + " vence mañana — renueva ahora.";
            case 3 -> "Solo 3 días para el vencimiento de tu plan " + plan + ".";
            case 7 -> "Faltan 7 días para el vencimiento de tu plan " + plan + ".";
            case 15 -> "Tu plan " + plan + " vence en 15 días — renueva a tiempo.";
            default -> "Tu plan " + plan + " vence en " + d + " días.";
        };
    }

    private static String planActualDeTipo(String tipo) {
        if (tipo == null) return "";
        return switch (tipo) {
            case "VENCIMIENTO_TRIAL" -> "Trial";
            case "VENCIMIENTO_PREMIUM" -> "Premium";
            default -> tipo;
        };
    }
}
