package com.gymadmin.finance.infrastructure.config;

import java.security.Principal;
import java.util.List;

public class JwtPrincipal implements Principal {

    private final String userId;
    private final String tipo;
    private final String rolPlataforma;
    private final Long idCompania;
    private final Long idSucursal;
    private final String rolGym;
    private final List<String> permisos;

    public JwtPrincipal(String userId, String tipo, String rolPlataforma,
                        Long idCompania, Long idSucursal, String rolGym, List<String> permisos) {
        this.userId = userId;
        this.tipo = tipo;
        this.rolPlataforma = rolPlataforma;
        this.idCompania = idCompania;
        this.idSucursal = idSucursal;
        this.rolGym = rolGym;
        this.permisos = permisos != null ? permisos : List.of();
    }

    @Override public String getName()       { return userId; }
    public String getUserId()               { return userId; }
    public String getTipo()                 { return tipo; }
    public String getRolPlataforma()        { return rolPlataforma; }
    public Long getIdCompania()             { return idCompania; }
    public Long getIdSucursal()             { return idSucursal; }
    public String getRolGym()               { return rolGym; }
    public List<String> getPermisos()       { return permisos; }

    public boolean isStaff()               { return "staff".equals(tipo); }
    public boolean isPlataforma()          { return "plataforma".equals(tipo); }
    public boolean isSuperAdmin()          { return "super_admin".equals(rolPlataforma); }
    public boolean isDueno()               { return "dueno".equals(rolGym) || "admin_compania".equals(rolGym); }
    public boolean isRecepcion()           { return "recepcion".equals(rolGym); }
    public boolean isEntrenador()          { return "entrenador".equals(rolGym); }

    public boolean hasPermiso(String permiso) { return permisos.contains(permiso); }
}
