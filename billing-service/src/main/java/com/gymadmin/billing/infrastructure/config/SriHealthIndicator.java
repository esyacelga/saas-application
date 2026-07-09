package com.gymadmin.billing.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component("sri")
@RequiredArgsConstructor
@Slf4j
public class SriHealthIndicator implements ReactiveHealthIndicator {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final String PING_ENVELOPE = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" \
            xmlns:ec="http://ec.gob.sri.ws.recepcion">
              <soapenv:Header/>
              <soapenv:Body>
                <ec:validarComprobante>
                  <xml></xml>
                </ec:validarComprobante>
              </soapenv:Body>
            </soapenv:Envelope>""";

    private final WebClient sriWebClient;
    private final SriAmbienteConfig sriAmbienteConfig;

    @Override
    public Mono<Health> health() {
        String url = sriAmbienteConfig.getUrlRecepcionEfectiva();
        String ambiente = sriAmbienteConfig.getAmbiente();

        return sriWebClient.post()
                .uri(url)
                .contentType(MediaType.valueOf("text/xml; charset=UTF-8"))
                .header("SOAPAction", "validarComprobante")
                .bodyValue(PING_ENVELOPE)
                .retrieve()
                .toBodilessEntity()
                .timeout(TIMEOUT)
                .map(response -> Health.up()
                        .withDetail("ambiente", ambiente)
                        .withDetail("url", url)
                        .build())
                .onErrorResume(e -> {
                    log.debug("SRI health check failed: {}", e.getMessage());
                    return Mono.just(Health.down()
                            .withDetail("error", e.getMessage())
                            .withDetail("ambiente", ambiente)
                            .withDetail("url", url)
                            .build());
                });
    }
}
