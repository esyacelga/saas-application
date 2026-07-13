package com.gymadmin.billing.infrastructure.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de cache in-memory para catálogos SRI.
 * <p>
 * <b>Invalidación:</b> los catálogos SRI cambian raras veces (nuevas tarifas,
 * formas de pago) y siempre implican un deploy del servicio, por lo que la
 * invalidación por reinicio de aplicación es aceptable. No se define TTL.
 * <p>
 * <b>Reactor + Cache:</b> los servicios que consumen estos caches manejan la
 * memorización de {@link reactor.core.publisher.Mono} manualmente
 * (ver {@code CatalogoSriService}) para evitar el problema conocido de
 * {@code @Cacheable} sobre tipos reactivos, que serializa el propio publisher.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_TIPO_COMPROBANTE = "sri.tipoComprobante";
    public static final String CACHE_TIPO_IDENTIFICACION = "sri.tipoIdentificacion";
    public static final String CACHE_FORMA_PAGO = "sri.formaPago";
    public static final String CACHE_TIPO_IMPUESTO = "sri.tipoImpuesto";
    public static final String CACHE_TARIFA_IVA = "sri.tarifaIva";
    public static final String CACHE_MOTIVO_ANULACION_NC = "sri.motivoAnulacionNc";

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                CACHE_TIPO_COMPROBANTE,
                CACHE_TIPO_IDENTIFICACION,
                CACHE_FORMA_PAGO,
                CACHE_TIPO_IMPUESTO,
                CACHE_TARIFA_IVA,
                CACHE_MOTIVO_ANULACION_NC
        );
    }
}
