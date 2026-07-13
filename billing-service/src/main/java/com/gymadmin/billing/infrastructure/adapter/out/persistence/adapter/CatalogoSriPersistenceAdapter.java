package com.gymadmin.billing.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.billing.domain.model.sri.FormaPagoSri;
import com.gymadmin.billing.domain.model.sri.MotivoAnulacionNcSri;
import com.gymadmin.billing.domain.model.sri.TarifaIvaSri;
import com.gymadmin.billing.domain.model.sri.TipoComprobanteSri;
import com.gymadmin.billing.domain.model.sri.TipoIdentificacionSri;
import com.gymadmin.billing.domain.model.sri.TipoImpuestoSri;
import com.gymadmin.billing.domain.port.out.CatalogoSriRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Adaptador R2DBC de solo lectura para los 6 catálogos SRI del schema {@code sri}.
 * Sigue el mismo patrón que {@link SecuencialPersistenceAdapter} y
 * {@link ReportePersistenceAdapter}: SQL nativo por {@link DatabaseClient} y
 * mapeo directo a records de dominio.
 * <p>
 * No hay filtrado por {@code tenant_id} porque los catálogos SRI son globales
 * (definidos por la administración tributaria nacional, no por tenant).
 */
@Component
@RequiredArgsConstructor
public class CatalogoSriPersistenceAdapter implements CatalogoSriRepository {

    private static final String SQL_TIPO_COMPROBANTE = """
            SELECT codigo, nombre, version, activo
              FROM sri.tipos_comprobante
             WHERE codigo = :codigo
            """;

    private static final String SQL_TIPO_IDENTIFICACION = """
            SELECT codigo, nombre
              FROM sri.tipos_identificacion_comprador
             WHERE codigo = :codigo
            """;

    private static final String SQL_FORMA_PAGO = """
            SELECT codigo, nombre, activo
              FROM sri.formas_pago
             WHERE codigo = :codigo
            """;

    private static final String SQL_TIPO_IMPUESTO = """
            SELECT codigo, nombre
              FROM sri.tipos_impuesto
             WHERE codigo = :codigo
            """;

    private static final String SQL_TARIFA_IVA = """
            SELECT codigo, nombre, porcentaje, vigente_desde, vigente_hasta
              FROM sri.tarifas_iva
             WHERE codigo = :codigo
            """;

    private static final String SQL_MOTIVO_ANULACION_NC = """
            SELECT id, codigo, descripcion
              FROM sri.motivos_anulacion_nc
             WHERE codigo = :codigo
            """;

    private static final String SQL_LIST_MOTIVOS_ANULACION_NC = """
            SELECT id, codigo, descripcion
              FROM sri.motivos_anulacion_nc
             ORDER BY id
            """;

    private final DatabaseClient databaseClient;

    @Override
    public Mono<TipoComprobanteSri> findTipoComprobante(String codigo) {
        return databaseClient.sql(SQL_TIPO_COMPROBANTE)
                .bind("codigo", codigo)
                .map(row -> new TipoComprobanteSri(
                        trim(row.get("codigo", String.class)),
                        row.get("nombre", String.class),
                        row.get("version", String.class),
                        Boolean.TRUE.equals(row.get("activo", Boolean.class))
                ))
                .one();
    }

    @Override
    public Mono<TipoIdentificacionSri> findTipoIdentificacion(String codigo) {
        return databaseClient.sql(SQL_TIPO_IDENTIFICACION)
                .bind("codigo", codigo)
                .map(row -> new TipoIdentificacionSri(
                        trim(row.get("codigo", String.class)),
                        row.get("nombre", String.class)
                ))
                .one();
    }

    @Override
    public Mono<FormaPagoSri> findFormaPago(String codigo) {
        return databaseClient.sql(SQL_FORMA_PAGO)
                .bind("codigo", codigo)
                .map(row -> new FormaPagoSri(
                        trim(row.get("codigo", String.class)),
                        row.get("nombre", String.class),
                        Boolean.TRUE.equals(row.get("activo", Boolean.class))
                ))
                .one();
    }

    @Override
    public Mono<TipoImpuestoSri> findTipoImpuesto(String codigo) {
        return databaseClient.sql(SQL_TIPO_IMPUESTO)
                .bind("codigo", codigo)
                .map(row -> new TipoImpuestoSri(
                        trim(row.get("codigo", String.class)),
                        row.get("nombre", String.class)
                ))
                .one();
    }

    @Override
    public Mono<TarifaIvaSri> findTarifaIva(String codigo) {
        return databaseClient.sql(SQL_TARIFA_IVA)
                .bind("codigo", codigo)
                .map(row -> new TarifaIvaSri(
                        trim(row.get("codigo", String.class)),
                        row.get("nombre", String.class),
                        row.get("porcentaje", BigDecimal.class),
                        row.get("vigente_desde", LocalDate.class),
                        row.get("vigente_hasta", LocalDate.class)
                ))
                .one();
    }

    @Override
    public Mono<MotivoAnulacionNcSri> findMotivoAnulacionNc(String codigo) {
        return databaseClient.sql(SQL_MOTIVO_ANULACION_NC)
                .bind("codigo", codigo)
                .map(row -> new MotivoAnulacionNcSri(
                        row.get("id", Integer.class),
                        row.get("codigo", String.class),
                        row.get("descripcion", String.class)
                ))
                .one();
    }

    @Override
    public Flux<MotivoAnulacionNcSri> listMotivosAnulacionNc() {
        return databaseClient.sql(SQL_LIST_MOTIVOS_ANULACION_NC)
                .map(row -> new MotivoAnulacionNcSri(
                        row.get("id", Integer.class),
                        row.get("codigo", String.class),
                        row.get("descripcion", String.class)
                ))
                .all();
    }

    /**
     * Los códigos SRI se almacenan como CHAR(1)/CHAR(2), que Postgres/R2DBC
     * pueden devolver con padding a la derecha en algunos drivers. Se recorta
     * para que el dominio nunca vea espacios sobrantes.
     */
    private static String trim(String value) {
        return value != null ? value.trim() : null;
    }
}
