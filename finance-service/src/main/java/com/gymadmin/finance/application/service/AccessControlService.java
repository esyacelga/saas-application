package com.gymadmin.finance.application.service;

import com.gymadmin.finance.infrastructure.config.JwtPrincipal;
import com.gymadmin.finance.infrastructure.exception.ForbiddenException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AccessControlService {

    /**
     * finanzas:leer OR isDueno() OR isPlataforma()
     */
    public Mono<Void> requireFinanzasLeer(JwtPrincipal principal) {
        if (principal == null) {
            return Mono.error(new ForbiddenException("Autenticación requerida"));
        }
        if (principal.hasPermiso("finanzas:leer") || principal.isDueno() || principal.isPlataforma()) {
            return Mono.empty();
        }
        return Mono.error(new ForbiddenException("Acceso denegado: se requiere permiso finanzas:leer"));
    }

    /**
     * finanzas:crear OR isDueno() OR isPlataforma()
     */
    public Mono<Void> requireFinanzasCrear(JwtPrincipal principal) {
        if (principal == null) {
            return Mono.error(new ForbiddenException("Autenticación requerida"));
        }
        if (principal.hasPermiso("finanzas:crear") || principal.isDueno() || principal.isPlataforma()) {
            return Mono.empty();
        }
        return Mono.error(new ForbiddenException("Acceso denegado: se requiere permiso finanzas:crear"));
    }

    /**
     * finanzas:crear OR isDueno() OR isRecepcion() OR isPlataforma()
     * Special case for POST /finanzas/ingresos
     */
    public Mono<Void> requireFinanzasCrearORecepcion(JwtPrincipal principal) {
        if (principal == null) {
            return Mono.error(new ForbiddenException("Autenticación requerida"));
        }
        if (principal.hasPermiso("finanzas:crear") || principal.isDueno()
                || principal.isRecepcion() || principal.isPlataforma()) {
            return Mono.empty();
        }
        return Mono.error(new ForbiddenException("Acceso denegado: se requiere permiso finanzas:crear o rol recepcion"));
    }

    /**
     * finanzas:exportar OR finanzas:leer OR isDueno() OR isPlataforma()
     */
    public Mono<Void> requireFinanzasReportes(JwtPrincipal principal) {
        if (principal == null) {
            return Mono.error(new ForbiddenException("Autenticación requerida"));
        }
        if (principal.hasPermiso("finanzas:exportar") || principal.hasPermiso("finanzas:leer")
                || principal.isDueno() || principal.isPlataforma()) {
            return Mono.empty();
        }
        return Mono.error(new ForbiddenException("Acceso denegado: se requiere permiso finanzas:leer o finanzas:exportar"));
    }
}
