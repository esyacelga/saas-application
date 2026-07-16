package com.gymadmin.attendance.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient coreWebClient(@Value("${services.core-service.url}") String coreServiceUrl) {
        return WebClient.builder()
                .baseUrl(coreServiceUrl)
                .build();
    }

    @Bean
    public WebClient authWebClient(@Value("${services.auth-service.url}") String authServiceUrl) {
        return WebClient.builder()
                .baseUrl(authServiceUrl)
                .build();
    }

    /** Fase 6 (R1): cliente hacia platform-service para leer el bucket global de aviso previo del socio. */
    @Bean
    public WebClient platformWebClient(@Value("${services.platform-service.url}") String platformServiceUrl) {
        return WebClient.builder()
                .baseUrl(platformServiceUrl)
                .build();
    }
}
