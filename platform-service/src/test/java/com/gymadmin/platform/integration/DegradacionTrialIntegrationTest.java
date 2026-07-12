package com.gymadmin.platform.integration;

import com.gymadmin.platform.BaseIntegrationTest;
import com.gymadmin.platform.application.service.SubscriptionJobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * REQ-SAAS-001 (Sub-fase 1.6, item #5, RN-03): degradación automática del Trial
 * al vencer el día 61 mediante time-travel con {@link Clock}.
 *
 * <p><b>Flujo:</b>
 * <ol>
 *   <li>Fijamos el clock a día X = {@code 2026-05-09}.</li>
 *   <li>Activamos el Trial mediante {@code POST /companias/{id}/suscripcion/trial}
 *       — la fila {@code compania_planes} queda ACTIVO con {@code fecha_fin = X + 60}.</li>
 *   <li>Movemos el clock a X + 61 = {@code 2026-07-09}.</li>
 *   <li>Invocamos directamente {@link SubscriptionJobService#procesarSuscripciones(LocalDate)}
 *       (package-private, expuesto para tests) sin esperar al cron.</li>
 *   <li>Verificamos que el Trial pasó a VENCIDO y se creó una nueva fila Free
 *       ACTIVO con {@code tipo_cambio = 'degradacion_auto'} y evento
 *       {@code PLAN_DEGRADADO_AUTO} en {@code saas.actividad_plataforma}.</li>
 * </ol>
 *
 * <p><b>Time-travel:</b> el bean {@code Clock} se sustituye por un {@link MutableClock}
 * bajo {@link TestConfiguration} con {@code @Primary}. Todos los servicios que
 * inyecten {@code Clock} ({@code ActivarTrialService}, {@code SubscriptionJobService})
 * usarán la misma referencia mutable durante el test.
 */
@DisplayName("Degradación Trial día 61 — time-travel Clock (Sub-fase 1.6 item #5)")
@Import(DegradacionTrialIntegrationTest.ClockTestConfig.class)
class DegradacionTrialIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MutableClock mutableClock;

    @Autowired
    private SubscriptionJobService subscriptionJobService;

    @Test
    @DisplayName("Trial activado día X, avanza a X+61 → Trial VENCIDO, Free ACTIVO, evento PLAN_DEGRADADO_AUTO")
    void degradaTrialAutomaticamenteDia61() throws Exception {
        LocalDate diaActivacion = LocalDate.of(2026, 5, 9);
        LocalDate diaVencimiento = diaActivacion.plusDays(61); // 2026-07-09

        // ── 1) Fijamos Clock al día X y preparamos gym Free + planes ────────
        mutableClock.setInstant(diaActivacion.atStartOfDay(ZoneOffset.UTC).toInstant());

        // Insertamos Free y Trial vía SQL directo para poder fijar codigo='FREE'/'TRIAL'
        // exactos y esGratuito=true (el endpoint /planes rechaza precioMensual=0).
        Long planFreeId = ensurePlanCodigoFree();
        Long planTrialCanonicoId = ensurePlanCodigoTrial(planFreeId);

        Long idCompania = crearGymConPlanBasico("Gym Trial Deg", "1713000000001", planFreeId);

        // La compañía nace con planFreeId activo. Debemos poner esa suscripción en estado
        // no-activo para que ActivarTrialService pueda crear el trial (no permite duplicados).
        databaseClient.sql(
                "UPDATE tenant.compania_planes SET estado = 'reemplazada' " +
                "WHERE id_compania = :id AND estado = 'activo'")
                .bind("id", idCompania)
                .then().block();

        // Marcamos la compañía con trial_usado=false (por si el wizard la creó como true).
        databaseClient.sql(
                "UPDATE tenant.companias SET trial_usado = false, fecha_trial_usado = NULL WHERE id = :id")
                .bind("id", idCompania)
                .then().block();

        // ── 2) Activamos el Trial con el clock en X ──────────────────────────
        webTestClient.post()
                .uri("/api/v1/companias/{id}/suscripcion/trial", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtOwnerCompania(idCompania)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.estado").isEqualTo("ACTIVO");

        // Verificación: la fila trial quedó fechada X..X+60
        Map<String, Object> trialActivo = databaseClient.sql(
                "SELECT id, id_plan, fecha_inicio, fecha_fin, estado FROM tenant.compania_planes " +
                "WHERE id_compania = :id AND id_plan = :idTrial AND estado = 'activo'")
                .bind("id", idCompania)
                .bind("idTrial", planTrialCanonicoId)
                .fetch()
                .one()
                .block();
        org.assertj.core.api.Assertions.assertThat(trialActivo)
                .as("el Trial debe estar activo tras la activación")
                .isNotNull();
        LocalDate fechaFinTrial = (LocalDate) trialActivo.get("fecha_fin");
        org.assertj.core.api.Assertions.assertThat(fechaFinTrial).isEqualTo(diaActivacion.plusDays(60));

        // ── 3) Avanzamos el Clock a X + 61 ───────────────────────────────────
        mutableClock.setInstant(diaVencimiento.atStartOfDay(ZoneOffset.UTC).toInstant());

        // ── 4) Ejecutamos el job manualmente (procesarSuscripciones es package-private) ─
        Method metodo = SubscriptionJobService.class.getDeclaredMethod(
                "procesarSuscripciones", LocalDate.class);
        metodo.setAccessible(true);
        @SuppressWarnings("unchecked")
        Mono<Void> resultado = (Mono<Void>) metodo.invoke(subscriptionJobService, diaVencimiento);

        StepVerifier.create(resultado).verifyComplete();

        // ── 5) Verificaciones post-job ───────────────────────────────────────
        // 5a) El Trial ahora está VENCIDO
        String estadoTrial = databaseClient.sql(
                "SELECT estado FROM tenant.compania_planes WHERE id = :id")
                .bind("id", ((Number) trialActivo.get("id")).longValue())
                .map((row, meta) -> row.get("estado", String.class))
                .one().block();
        org.assertj.core.api.Assertions.assertThat(estadoTrial)
                .as("el Trial debe pasar a vencido tras el job")
                .isEqualTo("vencido");

        // 5b) Se creó una fila Free ACTIVO con tipo_cambio = 'degradacion_auto'
        Map<String, Object> freeActivo = databaseClient.sql(
                "SELECT id, tipo_cambio, causa_degradacion FROM tenant.compania_planes " +
                "WHERE id_compania = :id AND id_plan = :idFree AND estado = 'activo'")
                .bind("id", idCompania)
                .bind("idFree", planFreeId)
                .fetch()
                .one()
                .block();
        org.assertj.core.api.Assertions.assertThat(freeActivo)
                .as("debe existir una nueva fila Free ACTIVO tras la degradación")
                .isNotNull();
        org.assertj.core.api.Assertions.assertThat(freeActivo.get("tipo_cambio"))
                .isEqualTo("degradacion_auto");

        // 5c) Se registró evento PLAN_DEGRADADO_AUTO
        Long eventos = databaseClient.sql(
                "SELECT COUNT(*) AS c FROM saas.actividad_plataforma " +
                "WHERE id_compania = :id AND tipo_evento = 'PLAN_DEGRADADO_AUTO'")
                .bind("id", idCompania)
                .map((row, meta) -> {
                    Number n = row.get("c", Number.class);
                    return n == null ? 0L : n.longValue();
                })
                .one().block();
        org.assertj.core.api.Assertions.assertThat(eventos)
                .as("debe registrarse el evento PLAN_DEGRADADO_AUTO")
                .isGreaterThanOrEqualTo(1L);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Long crearPlanConCodigo(String nombre, String codigo, double precio,
                                     boolean esGratuito, Integer duracionDias, Long planDegradacionId) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("nombre", nombre);
        payload.put("descripcion", "Plan " + nombre);
        payload.put("precioMensual", precio);
        payload.put("codigo", codigo);
        payload.put("esGratuito", esGratuito);
        if (duracionDias != null) payload.put("duracionDias", duracionDias);
        if (planDegradacionId != null) payload.put("planDegradacionId", planDegradacionId);

        @SuppressWarnings("rawtypes")
        Map body = webTestClient.post()
                .uri("/api/v1/planes")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        return Long.valueOf(body.get("id").toString());
    }

    /**
     * SubscriptionJobService detecta destino Free cuando {@code planDestino.getCodigo() == "FREE"}
     * o {@code esGratuito == true}. Insertamos vía SQL para poder fijar codigo='FREE' exacto y
     * precio_mensual=0 (el endpoint /planes rechaza precio 0).
     */
    private Long ensurePlanCodigoFree() {
        return databaseClient.sql(
                "INSERT INTO saas.planes (nombre, descripcion, precio_mensual, codigo, duracion_dias, " +
                "es_gratuito, plan_degradacion_id, moneda, es_legacy, activo, eliminado, creacion_usuario) " +
                "VALUES ('Free Canónico', 'Free perpetuo', 0, 'FREE', NULL, true, NULL, 'USD', false, true, false, 'test') " +
                "RETURNING id")
                .map((row, meta) -> row.get("id", Long.class))
                .one().block();
    }

    /**
     * ActivarTrialService busca el plan por {@code findByCodigo("TRIAL")}. Necesitamos
     * que exista una fila con codigo='TRIAL', duracion_dias=60 y plan_degradacion_id apuntando
     * al Free recién creado. Como {@code codigo} tiene UNIQUE constraint y {@code BaseIntegrationTest}
     * borra todos los planes en cada setUp, insertamos aquí uno con codigo='TRIAL' exacto.
     */
    private Long ensurePlanCodigoTrial(Long planFreeId) {
        return databaseClient.sql(
                "INSERT INTO saas.planes (nombre, descripcion, precio_mensual, codigo, duracion_dias, " +
                "es_gratuito, plan_degradacion_id, moneda, es_legacy, activo, eliminado, creacion_usuario) " +
                "VALUES ('Trial Canónico', 'trial 60 días', 0, 'TRIAL', 60, true, :idFree, 'USD', false, true, false, 'test') " +
                "RETURNING id")
                .bind("idFree", planFreeId)
                .map((row, meta) -> row.get("id", Long.class))
                .one().block();
    }

    private Long crearGymConPlanBasico(String nombreGym, String ruc, Long planId) {
        @SuppressWarnings("rawtypes")
        Map registro = webTestClient.post()
                .uri("/api/v1/companias")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", nombreGym,
                        "ruc", ruc,
                        "correo", "deg@test.com",
                        "idPlan", planId,
                        "nombreSucursal", "Sede Deg",
                        "direccionSucursal", "Calle Deg 1"
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        return Long.valueOf(registro.get("idCompania").toString());
    }

    // ── Time-travel Clock ────────────────────────────────────────────────────

    /**
     * Clock mutable: los servicios reciben esta única instancia por inyección
     * ({@code @Primary} en el {@code Clock} bean expuesto) y todas las lecturas
     * subsecuentes ({@code Instant.now(clock)}, {@code LocalDate.now(clock)})
     * usan el instante fijado con {@link #setInstant(Instant)}.
     */
    public static final class MutableClock extends Clock {
        private volatile Instant instant = Instant.parse("2026-01-01T00:00:00Z");
        private final ZoneId zone = ZoneOffset.UTC;

        public void setInstant(Instant newInstant) {
            this.instant = newInstant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class ClockTestConfig {

        @Bean
        public MutableClock mutableClock() {
            return new MutableClock();
        }

        /**
         * Nombre del bean distinto ({@code testClock}) para no colisionar con el
         * bean {@code clock} de {@link com.gymadmin.platform.infrastructure.config.ClockConfig}.
         * Con {@code @Primary} este es el que Spring inyecta en cualquier consumidor.
         */
        @Bean("testClock")
        @Primary
        public Clock testClock(MutableClock mutableClock) {
            return mutableClock;
        }
    }
}
