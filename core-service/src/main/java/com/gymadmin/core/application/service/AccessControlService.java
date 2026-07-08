package com.gymadmin.core.application.service;

import com.gymadmin.core.infrastructure.config.JwtPrincipal;
import com.gymadmin.core.infrastructure.exception.ForbiddenException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AccessControlService {

    public Mono<Void> requireStaff(JwtPrincipal principal) {
        if (principal == null) {
            return Mono.error(new ForbiddenException("Authentication required"));
        }
        return Mono.empty();
    }

    public Mono<Void> requireGymStaff(JwtPrincipal principal, Long idCompania) {
        if (principal == null) {
            return Mono.error(new ForbiddenException("Authentication required"));
        }
        if (principal.isSuperAdmin()) {
            return Mono.empty();
        }
        if (idCompania != null && idCompania.equals(principal.getIdCompania())) {
            return Mono.empty();
        }
        return Mono.error(new ForbiddenException("Access denied to company " + idCompania));
    }

    /**
     * Permite super_admin, admin_compania/Dueño, o un token staff real del auth-service
     * (tipo='staff' sin rol_plataforma — usa permisos[] para control fino en el futuro).
     * Si el token staff ya trae rol_plataforma (tokens de test), se evalúa ese rol.
     */
    public Mono<Void> requireAdminOrDueno(JwtPrincipal principal, Long idCompania) {
        if (principal == null) {
            return Mono.error(new ForbiddenException("Authentication required"));
        }
        if (principal.isSuperAdmin()) {
            return Mono.empty();
        }
        // Token staff real del auth-service: no lleva rol_plataforma, validar por compañía
        if (principal.isStaff() && principal.getRolPlataforma() == null
                && idCompania != null && idCompania.equals(principal.getIdCompania())) {
            return Mono.empty();
        }
        // Token con rol_plataforma explícito (plataforma o tests)
        String rol = principal.getRolPlataforma();
        boolean esAdmin = "admin_compania".equals(rol) || "Dueño".equals(rol);
        if (esAdmin && idCompania != null && idCompania.equals(principal.getIdCompania())) {
            return Mono.empty();
        }
        return Mono.error(new ForbiddenException("Action restricted to admin or Dueño role"));
    }

    /**
     * Permite super_admin, admin/Dueño/Recepción, o un token staff real del auth-service
     * (tipo='staff' sin rol_plataforma).
     */
    public Mono<Void> requireCliente(JwtPrincipal principal, Long idCompania) {
        if (principal == null) {
            return Mono.error(new ForbiddenException("Authentication required"));
        }
        if (principal.isCliente() && idCompania != null && idCompania.equals(principal.getIdCompania())) {
            return Mono.empty();
        }
        return Mono.error(new ForbiddenException("Acceso restringido a miembros de esta compañía"));
    }

    public Mono<Void> requireRecepcionOrAbove(JwtPrincipal principal, Long idCompania) {
        if (principal == null) {
            return Mono.error(new ForbiddenException("Authentication required"));
        }
        if (principal.isSuperAdmin()) {
            return Mono.empty();
        }
        // Token staff real del auth-service
        if (principal.isStaff() && principal.getRolPlataforma() == null
                && idCompania != null && idCompania.equals(principal.getIdCompania())) {
            return Mono.empty();
        }
        // Token con rol_plataforma explícito
        String rol = principal.getRolPlataforma();
        boolean esValido = "admin_compania".equals(rol) || "Dueño".equals(rol) || "Recepción".equals(rol);
        if (esValido && idCompania != null && idCompania.equals(principal.getIdCompania())) {
            return Mono.empty();
        }
        return Mono.error(new ForbiddenException("Access denied"));
    }
}
