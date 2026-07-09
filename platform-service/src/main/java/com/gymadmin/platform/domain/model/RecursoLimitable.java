package com.gymadmin.platform.domain.model;

/**
 * REQ-SAAS-001 (RN-05): recursos con límites duros por plan
 * ({@code saas.planes.max_sucursales / max_clientes_activos / max_staff}).
 */
public enum RecursoLimitable {
    SUCURSALES,
    CLIENTES_ACTIVOS,
    STAFF
}
