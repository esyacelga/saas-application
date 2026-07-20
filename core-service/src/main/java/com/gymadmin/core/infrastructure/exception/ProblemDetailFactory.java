package com.gymadmin.core.infrastructure.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Punto único de construcción del "sobre" de error estandarizado
 * (RFC 7807 {@link ProblemDetail} + extensiones propias).
 *
 * <p>Contrato — ver {@code docs/gym-administrator/architecture/error-contract.md}:
 * <ul>
 *   <li>Campos RFC 7807 estándar: {@code type, title, status, detail, instance}.</li>
 *   <li>Extensiones en <b>snake_case literal</b> ({@code codigo}, {@code mensaje},
 *       {@code timestamp}, {@code errores}). {@code ProblemDetail} NO aplica la
 *       estrategia SNAKE_CASE de Jackson a sus propiedades extra, así que las
 *       claves deben pasarse ya en snake_case (hallazgo #2 del contrato).</li>
 *   <li>{@code mensaje} es alias de {@code detail} durante el período de gracia
 *       (hallazgo #5): muchos accesos inline del panel admin leen {@code .mensaje}.</li>
 * </ul>
 */
public final class ProblemDetailFactory {

    private ProblemDetailFactory() {
    }

    /** Sobre básico: status + codigo + detail. */
    public static ProblemDetail create(HttpStatus status, String codigo, String detail, ServerWebExchange exchange) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setProperty("codigo", codigo);
        pd.setProperty("mensaje", detail); // alias de detail (período de gracia)
        pd.setProperty("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
        if (exchange != null) {
            pd.setInstance(URI.create(exchange.getRequest().getPath().value()));
        }
        return pd;
    }

    /** Atajo desde un {@link ErrorCode}. */
    public static ProblemDetail create(ErrorCode code, String detail, ServerWebExchange exchange) {
        return create(code.status(), code.codigo(), detail, exchange);
    }

    /**
     * Sobre de validación (400): incluye {@code errores[]} como lista de
     * {@code {campo, mensaje}}. {@code detail} conserva un resumen legible para
     * UIs que solo muestran {@code detail} (hallazgo #8).
     */
    public static ProblemDetail validacion(String detail, List<Map<String, String>> errores, ServerWebExchange exchange) {
        ProblemDetail pd = create(ErrorCode.VALIDACION, detail, exchange);
        pd.setProperty("errores", errores);
        return pd;
    }

    /** Añade una propiedad extra en snake_case (metadata de negocio, p.ej. límite de plan). */
    public static ProblemDetail withProperty(ProblemDetail pd, String snakeCaseKey, Object value) {
        pd.setProperty(snakeCaseKey, value);
        return pd;
    }

    /**
     * Aplana el {@link ProblemDetail} al shape JSON final del contrato: los 5
     * campos RFC 7807 al nivel raíz + las extensiones al nivel raíz (NO anidadas
     * bajo {@code properties}).
     *
     * <p>Es el punto único de serialización. Un {@code ObjectMapper} plano
     * serializa {@code ProblemDetail.getProperties()} como un objeto anidado
     * {@code "properties": {...}}; solo el mixin de Spring lo aplana. Construir el
     * mapa aquí garantiza el contrato plano en snake_case sin depender de esa
     * configuración (hallazgo #2 — verificado por test).
     */
    public static Map<String, Object> toMap(ProblemDetail pd) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (pd.getType() != null) {
            map.put("type", pd.getType().toString());
        }
        map.put("title", pd.getTitle());
        map.put("status", pd.getStatus());
        map.put("detail", pd.getDetail());
        if (pd.getInstance() != null) {
            map.put("instance", pd.getInstance().toString());
        }
        Map<String, Object> props = pd.getProperties();
        if (props != null) {
            map.putAll(props); // claves ya en snake_case
        }
        return map;
    }
}
