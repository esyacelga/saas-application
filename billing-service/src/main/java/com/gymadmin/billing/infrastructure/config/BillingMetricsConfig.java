package com.gymadmin.billing.infrastructure.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BillingMetricsConfig {

    @Bean
    public Counter comprobantesEmitidosFactura(MeterRegistry registry) {
        return Counter.builder("billing.comprobantes.emitidos")
                .tag("tipo", "FACTURA")
                .description("Total de facturas electrónicas emitidas")
                .register(registry);
    }

    @Bean
    public Counter comprobantesEmitidosNotaCredito(MeterRegistry registry) {
        return Counter.builder("billing.comprobantes.emitidos")
                .tag("tipo", "NOTA_CREDITO")
                .description("Total de notas de crédito emitidas")
                .register(registry);
    }

    @Bean
    public Counter comprobantesAutorizados(MeterRegistry registry) {
        return Counter.builder("billing.comprobantes.autorizados")
                .description("Total de comprobantes autorizados por el SRI")
                .register(registry);
    }

    @Bean
    public Counter comprobantesErroresSri(MeterRegistry registry) {
        return Counter.builder("billing.comprobantes.errores_sri")
                .description("Total de errores de comunicación o rechazo del SRI")
                .register(registry);
    }

    @Bean
    public Counter comprobantesReintentos(MeterRegistry registry) {
        return Counter.builder("billing.comprobantes.reintentos")
                .description("Total de reintentos de envío al SRI")
                .register(registry);
    }

    /**
     * G2 · Transmisión inmediata. Mide la duración del pipeline síncrono
     * firma → envío → autorización invocado desde
     * {@code POST /api/v1/comprobantes/facturas}.
     * <p>
     * SLO objetivo:
     * <ul>
     *     <li>p95 &lt; 30 s en primer envío.</li>
     *     <li>p99 &lt; 5 min (contando fallback a cola).</li>
     * </ul>
     */
    @Bean
    public Timer sriEmisionDuracion(MeterRegistry registry) {
        return Timer.builder("sri.emision.duracion")
                .description("Duración del pipeline síncrono de emisión inmediata al SRI (G2)")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    /**
     * G2 · Transmisión inmediata. Se incrementa cuando el pipeline síncrono
     * excede el timeout configurado (por defecto {@code sri.timeout.envio-seconds = 15}).
     */
    @Bean
    public Counter sriEmisionTimeouts(MeterRegistry registry) {
        return Counter.builder("sri.emision.timeouts")
                .description("Total de timeouts del pipeline síncrono de emisión inmediata al SRI (G2)")
                .register(registry);
    }
}
