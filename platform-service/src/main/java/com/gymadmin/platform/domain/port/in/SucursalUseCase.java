package com.gymadmin.platform.domain.port.in;

import com.gymadmin.platform.domain.model.Sucursal;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SucursalUseCase {

    Flux<Sucursal> listarSucursales(Long idCompania);

    Mono<Sucursal> crearSucursal(Long idCompania, CrearSucursalCommand command, JwtPrincipal jwtContext);

    Mono<Sucursal> actualizarSucursal(Long id, ActualizarSucursalCommand command);

    Mono<QrRenovarResult> renovarQrToken(Long id, Integer expiresInHours);

    record CrearSucursalCommand(
            String nombre,
            String direccion,
            Boolean esPrincipal
    ) {}

    record ActualizarSucursalCommand(
            String nombre,
            String direccion
    ) {}

    record QrRenovarResult(
            String qrToken,
            java.time.LocalDateTime qrTokenExpira
    ) {}
}
