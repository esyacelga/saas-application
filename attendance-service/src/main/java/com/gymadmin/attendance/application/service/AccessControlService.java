package com.gymadmin.attendance.application.service;

import com.gymadmin.attendance.infrastructure.config.JwtPrincipal;
import com.gymadmin.attendance.infrastructure.exception.ForbiddenException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AccessControlService {

    public Mono<Void> requireCliente(JwtPrincipal principal) {
        if (principal == null || !principal.isCliente()) {
            return Mono.error(new ForbiddenException("Solo los clientes pueden usar este endpoint"));
        }
        return Mono.empty();
    }

    public Mono<Void> requireStaff(JwtPrincipal principal) {
        if (principal == null || !principal.isStaff()) {
            return Mono.error(new ForbiddenException("Solo el personal del gym puede usar este endpoint"));
        }
        return Mono.empty();
    }

    public Mono<Void> requireStaffOrPlataforma(JwtPrincipal principal) {
        if (principal == null || (!principal.isStaff() && !principal.isPlataforma())) {
            return Mono.error(new ForbiddenException("Acceso restringido a staff o plataforma"));
        }
        return Mono.empty();
    }

    public Mono<Void> requireDueno(JwtPrincipal principal) {
        if (principal == null || !principal.isDueno()) {
            return Mono.error(new ForbiddenException("Solo el Dueño puede realizar esta acción"));
        }
        return Mono.empty();
    }

    public Mono<Void> requireDuenoOrPlataforma(JwtPrincipal principal) {
        if (principal == null || (!principal.isDueno() && !principal.isPlataforma())) {
            return Mono.error(new ForbiddenException("Solo el Dueño o la plataforma pueden realizar esta acción"));
        }
        return Mono.empty();
    }

    public Mono<Void> requireNotEntrenador(JwtPrincipal principal) {
        if (principal != null && principal.isEntrenador()) {
            return Mono.error(new ForbiddenException("Los entrenadores no pueden registrar asistencia manualmente"));
        }
        return Mono.empty();
    }

    public Mono<Void> requireAccessToCompania(JwtPrincipal principal, Integer idCompania) {
        if (principal == null) {
            return Mono.error(new ForbiddenException("Autenticación requerida"));
        }
        if (principal.isPlataforma()) {
            return Mono.empty();
        }
        if (principal.getIdCompania() != null && principal.getIdCompania().intValue() == idCompania) {
            return Mono.empty();
        }
        return Mono.error(new ForbiddenException("Acceso denegado a la compañía " + idCompania));
    }
}
