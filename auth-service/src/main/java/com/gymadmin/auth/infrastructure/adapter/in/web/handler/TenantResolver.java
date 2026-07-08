package com.gymadmin.auth.infrastructure.adapter.in.web.handler;

import com.gymadmin.auth.infrastructure.security.UserPrincipal;

/**
 * Resolves id_compania / id_sucursal with the rule:
 *   - tipo=plataforma: explicit DTO value wins; falls back to token (null for platform tokens)
 *   - any other tipo:  always use the token value, DTO value is ignored
 */
final class TenantResolver {

    private TenantResolver() {}

    static Integer idCompania(Integer fromDto, UserPrincipal principal) {
        if (principal.isPlataforma() && fromDto != null) {
            return fromDto;
        }
        return principal.getIdCompania();
    }

    static Integer idSucursal(Integer fromDto, UserPrincipal principal) {
        if (principal.isPlataforma() && fromDto != null) {
            return fromDto;
        }
        return principal.getIdSucursal();
    }
}
