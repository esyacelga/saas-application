package com.gymadmin.platform.infrastructure.config;

import java.security.Principal;

public class JwtPrincipal implements Principal {

    private final String userId;
    private final String tipo;
    private final String rolPlataforma;
    private final Long idCompania;
    private final Long idSucursal;

    public JwtPrincipal(String userId, String tipo, String rolPlataforma, Long idCompania, Long idSucursal) {
        this.userId = userId;
        this.tipo = tipo;
        this.rolPlataforma = rolPlataforma;
        this.idCompania = idCompania;
        this.idSucursal = idSucursal;
    }

    @Override
    public String getName() {
        return userId;
    }

    public String getUserId() { return userId; }

    public String getTipo() { return tipo; }

    public String getRolPlataforma() { return rolPlataforma; }

    public Long getIdCompania() { return idCompania; }

    public Long getIdSucursal() { return idSucursal; }

    public boolean isStaff() {
        return "staff".equals(tipo);
    }

    public boolean isSuperAdmin() {
        return "super_admin".equals(rolPlataforma);
    }

    public boolean isSoporte() {
        return "soporte".equals(rolPlataforma);
    }

    public boolean isViewer() {
        return "viewer".equals(rolPlataforma);
    }

    public boolean isAdminCompania() {
        return "admin_compania".equals(rolPlataforma);
    }

    public boolean isPlataforma() {
        return "plataforma".equals(tipo);
    }
}
