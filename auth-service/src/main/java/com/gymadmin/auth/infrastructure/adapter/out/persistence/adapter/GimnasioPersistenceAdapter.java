package com.gymadmin.auth.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.auth.domain.port.out.GimnasioPort;
import com.gymadmin.auth.dto.response.GimnasioPublicoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class GimnasioPersistenceAdapter implements GimnasioPort {

    private final DatabaseClient db;

    @Override
    public Mono<GimnasioPublicoResponse> findByQrToken(String qrToken) {
        return db.sql("""
                        SELECT c.id, s.id AS id_sucursal, c.nombre, s.nombre AS nombre_sucursal, c.logo_url
                        FROM tenant.sucursales s
                        JOIN tenant.companias c ON c.id = s.id_compania
                        WHERE s.qr_token = :qrToken
                          AND s.activo    = true
                          AND c.activo    = true
                          AND c.eliminado = false
                        """)
                .bind("qrToken", qrToken)
                .map((row, meta) -> new GimnasioPublicoResponse(
                        row.get("id", Integer.class),
                        row.get("id_sucursal", Integer.class),
                        row.get("nombre", String.class),
                        row.get("nombre_sucursal", String.class),
                        row.get("logo_url", String.class)))
                .one();
    }

    @Override
    public Mono<GimnasioPublicoResponse> findByIdCompania(Integer idCompania) {
        return db.sql("""
                        SELECT id, nombre, logo_url
                        FROM tenant.companias
                        WHERE id        = :idCompania
                          AND activo    = true
                          AND eliminado = false
                        """)
                .bind("idCompania", idCompania)
                .map((row, meta) -> new GimnasioPublicoResponse(
                        row.get("id", Integer.class),
                        null,
                        row.get("nombre", String.class),
                        null,
                        row.get("logo_url", String.class)))
                .one();
    }
}
