package com.gymadmin.auth.infrastructure.security;

import com.gymadmin.auth.domain.exception.ForbiddenException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Mono<UserPrincipal> currentUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> (UserPrincipal) ctx.getAuthentication().getPrincipal());
    }

    public static Mono<String> currentUserIdentifier() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> {
                    var auth = ctx.getAuthentication();
                    if (auth == null || !auth.isAuthenticated()
                            || !(auth.getPrincipal() instanceof UserPrincipal p))
                        return "sistema";
                    return p.toIdentifier();
                })
                .defaultIfEmpty("sistema");
    }

    public static Mono<UserPrincipal> requireStaff() {
        return currentUser()
                .flatMap(p -> p.isStaff()
                        ? Mono.just(p)
                        : Mono.error(new ForbiddenException("Endpoint requires staff token")));
    }

    public static Mono<UserPrincipal> requirePlataforma() {
        return currentUser()
                .flatMap(p -> p.isPlataforma()
                        ? Mono.just(p)
                        : Mono.error(new ForbiddenException("Endpoint requires plataforma token")));
    }

    public static Mono<UserPrincipal> requirePermiso(String permiso) {
        return currentUser()
                .flatMap(p -> p.hasPermiso(permiso)
                        ? Mono.just(p)
                        : Mono.error(new ForbiddenException("Missing permission: " + permiso)));
    }

    public static Mono<UserPrincipal> requireStaffWithPermiso(String permiso) {
        return requireStaff().flatMap(p -> requirePermiso(permiso));
    }

    public static Mono<UserPrincipal> requireSuperAdmin() {
        return requirePlataforma()
                .flatMap(p -> "super_admin".equals(p.getRolPlataforma())
                        ? Mono.just(p)
                        : Mono.error(new ForbiddenException("Endpoint requires super_admin role")));
    }
}
