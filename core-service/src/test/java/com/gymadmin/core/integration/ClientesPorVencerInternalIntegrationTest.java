package com.gymadmin.core.integration;

import com.gymadmin.core.BaseIntegrationTest;
import com.gymadmin.core.infrastructure.adapter.out.http.PlatformServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/**
 * REQ-SAAS-001 (Fase 4, C3 + C4): IT del endpoint interno
 * {@code GET /internal/v1/companias/{id}/clientes-por-vencer}.
 *
 * <p>El {@link Clock} se fija vía {@link FixedClockConfig} a un instante UTC que, en zona
 * {@code America/Guayaquil} (UTC-5), cae en el día anterior — así se verifica que la
 * {@code fechaCorte} respeta la zona de negocio (C4) y no la del proceso.
 *
 * <p>Instante fijo: {@code 2026-07-15T04:00:00Z} → en Guayaquil son las {@code 2026-07-14 23:00},
 * por lo que la {@code fechaCorte} de negocio es <b>2026-07-14</b>. Todas las membresías se siembran
 * relativas a esa fecha.
 */
@ContextConfiguration(classes = ClientesPorVencerInternalIntegrationTest.FixedClockConfig.class)
class ClientesPorVencerInternalIntegrationTest extends BaseIntegrationTest {

    /** 2026-07-15T04:00:00Z = 2026-07-14 23:00 en America/Guayaquil (UTC-5). */
    static final Instant INSTANTE_FIJO = Instant.parse("2026-07-15T04:00:00Z");
    /** fechaCorte de negocio esperada (día en Guayaquil, no en UTC). */
    static final LocalDate FECHA_CORTE = LocalDate.of(2026, 7, 14);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(INSTANTE_FIJO, java.time.ZoneId.of("America/Guayaquil"));
        }
    }

    private static final String URL = "/internal/v1/companias/" + TEST_COMPANIA + "/clientes-por-vencer";
    private static final String SECRET = "platform-secret-dev";

    private String iso(LocalDate d) {
        return d.toString();
    }

    /** Marca el estado del cliente (por defecto queda 'activo' al sembrar). */
    private void estadoCliente(Long idCliente, String estado) {
        databaseClient.sql("UPDATE core.clientes SET estado = :e WHERE id = :id")
                .bind("e", estado)
                .bind("id", idCliente)
                .then()
                .block();
    }

    private void optInWhatsapp(Long idPersona) {
        databaseClient.sql(
                "UPDATE identidad.personas SET acepta_whatsapp = TRUE, " +
                "fecha_consentimiento_wa = NOW() WHERE id = :id")
                .bind("id", idPersona)
                .then()
                .block();
    }

    /** Registra N asistencias (fechas distintas para respetar UNIQUE(id_membresia, fecha)). */
    private void seedAsistencias(Long idCliente, Long idMembresia, int n) {
        for (int i = 0; i < n; i++) {
            databaseClient.sql(
                    "INSERT INTO asistencia.asistencias(id_compania, id_sucursal, id_cliente, " +
                    "id_membresia, fecha, hora_entrada, creacion_usuario) " +
                    "VALUES (:comp, :suc, :cli, :mem, :fecha::date, '08:00', 'test')")
                    .bind("comp", TEST_COMPANIA)
                    .bind("suc", TEST_SUCURSAL)
                    .bind("cli", idCliente)
                    .bind("mem", idMembresia)
                    .bind("fecha", FECHA_CORTE.minusDays(i + 1).toString())
                    .then()
                    .block();
        }
    }

    // ── Calendario ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Calendario: vence en 3 días con dias=3 → aparece (diasParaVencer=3)")
    void calendario3d_aparece() {
        Long p = seedPersona("TEST-CAL3", "Ana Calendario");
        Long c = seedCliente(p);
        seedMembresia(c, seedTipoCalendario("Mensual"), iso(FECHA_CORTE), iso(FECHA_CORTE.plusDays(3)));

        getInterno(URL + "?dias=3&modo=calendario", SECRET)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.compania_id").isEqualTo(TEST_COMPANIA)
                .jsonPath("$.fecha_corte").isEqualTo(FECHA_CORTE.toString())
                .jsonPath("$.clientes.length()").isEqualTo(1)
                .jsonPath("$.clientes[0].id_cliente").isEqualTo(c)
                .jsonPath("$.clientes[0].id_persona").isEqualTo(p)
                .jsonPath("$.clientes[0].nombre").isEqualTo("Ana Calendario")
                .jsonPath("$.clientes[0].modo_control").isEqualTo("calendario")
                .jsonPath("$.clientes[0].dias_para_vencer").isEqualTo(3)
                .jsonPath("$.clientes[0].accesos_restantes").doesNotExist();
    }

    @Test
    @DisplayName("Calendario: vence en 10 días con dias=3 → NO aparece")
    void calendario10d_dias3_noAparece() {
        Long p = seedPersona("TEST-CAL10", "Beto Calendario");
        Long c = seedCliente(p);
        seedMembresia(c, seedTipoCalendario("Mensual"), iso(FECHA_CORTE), iso(FECHA_CORTE.plusDays(10)));

        getInterno(URL + "?dias=3&modo=calendario", SECRET)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.clientes.length()").isEqualTo(0);
    }

    // ── Accesos ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Accesos: total 10, usadas 7 → accesosRestantes=3, aparece con dias=3")
    void accesos_restantes3_aparece() {
        Long p = seedPersona("TEST-ACC", "Carla Accesos");
        Long c = seedCliente(p);
        Long m = seedMembresiaAccesos(c, seedTipoAccesos("Pase 10", 10), 10);
        seedAsistencias(c, m, 7);

        getInterno(URL + "?dias=3&modo=accesos", SECRET)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.clientes.length()").isEqualTo(1)
                .jsonPath("$.clientes[0].modo_control").isEqualTo("accesos")
                .jsonPath("$.clientes[0].accesos_restantes").isEqualTo(3);
    }

    @Test
    @DisplayName("Accesos: total 10, usadas 2 → restantes 8 > dias=3 → NO aparece")
    void accesos_restantes8_noAparece() {
        Long p = seedPersona("TEST-ACC8", "Dario Accesos");
        Long c = seedCliente(p);
        Long m = seedMembresiaAccesos(c, seedTipoAccesos("Pase 10", 10), 10);
        seedAsistencias(c, m, 2);

        getInterno(URL + "?dias=3&modo=accesos", SECRET)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.clientes.length()").isEqualTo(0);
    }

    // ── Exclusión de estados ──────────────────────────────────────────────────

    @Test
    @DisplayName("Congelado nunca aparece (RN-05), aunque su membresía esté por vencer")
    void congelado_noAparece() {
        Long p = seedPersona("TEST-CONG", "Eva Congelada");
        Long c = seedCliente(p);
        seedMembresia(c, seedTipoCalendario("Mensual"), iso(FECHA_CORTE), iso(FECHA_CORTE.plusDays(1)));
        estadoCliente(c, "congelado");

        getInterno(URL + "?dias=3&modo=todos", SECRET)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.clientes.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("Vencido nunca aparece")
    void vencido_noAparece() {
        Long p = seedPersona("TEST-VENC", "Fabio Vencido");
        Long c = seedCliente(p);
        seedMembresia(c, seedTipoCalendario("Mensual"), iso(FECHA_CORTE), iso(FECHA_CORTE.plusDays(2)));
        estadoCliente(c, "vencido");

        getInterno(URL + "?dias=3&modo=todos", SECRET)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.clientes.length()").isEqualTo(0);
    }

    // ── Opt-in reflejado ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Opt-in de WhatsApp se refleja en la proyección (acepta_whatsapp=true)")
    void optIn_reflejado() {
        Long p = seedPersona("TEST-OPT", "Gina OptIn");
        Long c = seedCliente(p);
        seedMembresia(c, seedTipoCalendario("Mensual"), iso(FECHA_CORTE), iso(FECHA_CORTE.plusDays(1)));
        optInWhatsapp(p);

        getInterno(URL + "?dias=3&modo=calendario", SECRET)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.clientes.length()").isEqualTo(1)
                .jsonPath("$.clientes[0].acepta_whatsapp").isEqualTo(true)
                .jsonPath("$.clientes[0].fecha_consentimiento_wa").exists();
    }

    @Test
    @DisplayName("Sin opt-in → acepta_whatsapp=false y fecha_consentimiento_wa null")
    void sinOptIn_reflejado() {
        Long p = seedPersona("TEST-NOOPT", "Hugo SinOptIn");
        Long c = seedCliente(p);
        seedMembresia(c, seedTipoCalendario("Mensual"), iso(FECHA_CORTE), iso(FECHA_CORTE.plusDays(1)));

        getInterno(URL + "?dias=3&modo=calendario", SECRET)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.clientes[0].acepta_whatsapp").isEqualTo(false)
                .jsonPath("$.clientes[0].fecha_consentimiento_wa").doesNotExist();
    }

    // ── Zona horaria (C4) ─────────────────────────────────────────────────────

    @Test
    @DisplayName("C4: fechaCorte = día en Guayaquil (2026-07-14), no en UTC (2026-07-15)")
    void zonaHoraria_fechaCorteGuayaquil() {
        getInterno(URL + "?dias=3&modo=todos", SECRET)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.fecha_corte").isEqualTo("2026-07-14");
    }

    // ── Seguridad ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Sin header X-Internal-Call → 403")
    void sinSecreto_403() {
        webTestClient.get().uri(URL + "?dias=3")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("Header X-Internal-Call inválido → 403")
    void secretoInvalido_403() {
        getInterno(URL + "?dias=3", "secreto-malo")
                .expectStatus().isForbidden();
    }

    // ── Validación de parámetros ──────────────────────────────────────────────

    @Test
    @DisplayName("dias fuera de rango (>30) → 400")
    void diasFueraDeRango_400() {
        getInterno(URL + "?dias=99", SECRET)
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("modo inválido → 400")
    void modoInvalido_400() {
        getInterno(URL + "?dias=3&modo=raro", SECRET)
                .expectStatus().isBadRequest();
    }

    // ── helper: GET con header interno ────────────────────────────────────────

    private org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec getInterno(String uri, String secret) {
        return webTestClient.get().uri(uri)
                .header(PlatformServiceClient.HEADER_INTERNAL_CALL, secret)
                .exchange();
    }
}
