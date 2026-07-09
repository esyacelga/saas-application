package com.gymadmin.billing.infrastructure.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
}
