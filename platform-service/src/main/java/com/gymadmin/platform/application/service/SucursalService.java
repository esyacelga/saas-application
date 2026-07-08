package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.model.Sucursal;
import com.gymadmin.platform.domain.port.in.SucursalUseCase;
import com.gymadmin.platform.domain.port.out.SucursalRepository;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
public class SucursalService implements SucursalUseCase {

    private final SucursalRepository sucursalRepository;
    private final QrTokenService qrTokenService;

    public SucursalService(SucursalRepository sucursalRepository, QrTokenService qrTokenService) {
        this.sucursalRepository = sucursalRepository;
        this.qrTokenService = qrTokenService;
    }

    @Override
    public Flux<Sucursal> listarSucursales(Long idCompania) {
        return sucursalRepository.findByIdCompania(idCompania);
    }

    @Override
    public Mono<Sucursal> crearSucursal(Long idCompania, CrearSucursalCommand command, JwtPrincipal jwtContext) {
        String token = qrTokenService.generateToken();
        Sucursal sucursal = new Sucursal();
        sucursal.setIdCompania(idCompania);
        sucursal.setNombre(command.nombre());
        sucursal.setDireccion(command.direccion());
        sucursal.setEsPrincipal(command.esPrincipal() != null ? command.esPrincipal() : false);
        sucursal.setActivo(true);
        sucursal.setQrToken(token);
        sucursal.setQrTokenExpira(LocalDateTime.now().plusYears(1));
        return sucursalRepository.save(sucursal);
    }

    @Override
    public Mono<Sucursal> actualizarSucursal(Long id, ActualizarSucursalCommand command) {
        return sucursalRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Sucursal", id)))
                .flatMap(sucursal -> {
                    if (command.nombre() != null) sucursal.setNombre(command.nombre());
                    if (command.direccion() != null) sucursal.setDireccion(command.direccion());
                    return sucursalRepository.update(sucursal);
                });
    }

    @Override
    public Mono<QrRenovarResult> renovarQrToken(Long id, Integer expiresInHours) {
        return sucursalRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Sucursal", id)))
                .flatMap(sucursal -> {
                    String newToken = qrTokenService.generateToken();
                    LocalDateTime expira = expiresInHours != null ? LocalDateTime.now().plusHours(expiresInHours) : null;
                    sucursal.setQrToken(newToken);
                    sucursal.setQrTokenExpira(expira);
                    return sucursalRepository.update(sucursal)
                            .thenReturn(new QrRenovarResult(newToken, expira));
                });
    }
}
