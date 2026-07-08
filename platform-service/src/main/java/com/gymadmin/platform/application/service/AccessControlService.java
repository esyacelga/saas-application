package com.gymadmin.platform.application.service;

import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import com.gymadmin.platform.infrastructure.exception.ForbiddenException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AccessControlService {

    public Mono<Void> requirePlataforma(JwtPrincipal principal) {
        if (principal == null || !principal.isPlataforma()) {
            return Mono.error(new ForbiddenException("Access restricted to platform users"));
        }
        return Mono.empty();
    }

    public Mono<Void> requireSuperAdmin(JwtPrincipal principal) {
        if (principal == null || !principal.isSuperAdmin()) {
            return Mono.error(new ForbiddenException("Access restricted to super_admin role"));
        }
        return Mono.empty();
    }

    public Mono<Void> requireSuperAdminOrSoporte(JwtPrincipal principal) {
        if (principal == null || (!principal.isSuperAdmin() && !principal.isSoporte())) {
            return Mono.error(new ForbiddenException("Access restricted to super_admin or soporte role"));
        }
        return Mono.empty();
    }

    public Mono<Void> requireStaff(JwtPrincipal principal) {
        if (principal == null || !principal.isStaff()) {
            return Mono.error(new ForbiddenException("Access restricted to staff users"));
        }
        return Mono.empty();
    }

    public Mono<Void> requireAccessToCompania(JwtPrincipal principal, Long idCompania) {
        if (principal == null) {
            return Mono.error(new ForbiddenException("Authentication required"));
        }
        if (principal.isPlataforma()) {
            return Mono.empty();
        }
        if (principal.isAdminCompania() && idCompania.equals(principal.getIdCompania())) {
            return Mono.empty();
        }
        return Mono.error(new ForbiddenException("Access denied to company " + idCompania));
    }
}
