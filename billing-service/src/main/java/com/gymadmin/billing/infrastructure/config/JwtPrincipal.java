package com.gymadmin.billing.infrastructure.config;

import java.security.Principal;
import java.util.List;

public class JwtPrincipal implements Principal {

    private final String userId;
    private final String tipo;
    private final String rolPlataforma;
    private final Long idCompania;
    private final Long idPersona;
    private final List<String> permisos;

    public JwtPrincipal(String userId, String tipo, String rolPlataforma, Long idCompania, Long idPersona, List<String> permisos) {
        this.userId = userId;
        this.tipo = tipo;
        this.rolPlataforma = rolPlataforma;
        this.idCompania = idCompania;
        this.idPersona = idPersona;
        this.permisos = permisos != null ? permisos : List.of();
    }

    @Override
    public String getName() { return userId; }

    public String getUserId() { return userId; }
    public String getTipo() { return tipo; }
    public String getRolPlataforma() { return rolPlataforma; }
    public Long getIdCompania() { return idCompania; }
    public Long getIdPersona() { return idPersona; }
    public List<String> getPermisos() { return permisos; }

    public boolean hasPermiso(String permiso) { return permisos.contains(permiso); }
    public boolean isStaff() { return "staff".equals(tipo); }
    public boolean isCliente() { return "cliente".equals(tipo); }
    public boolean isSuperAdmin() { return "super_admin".equals(rolPlataforma); }
    public boolean isSoporte() { return "soporte".equals(rolPlataforma); }
    public boolean isAdminCompania() { return "admin_compania".equals(rolPlataforma); }
    public boolean isPlataforma() { return "plataforma".equals(tipo); }
}
