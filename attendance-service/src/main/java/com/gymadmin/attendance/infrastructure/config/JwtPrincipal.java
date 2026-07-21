package com.gymadmin.attendance.infrastructure.config;

import java.security.Principal;

public class JwtPrincipal implements Principal {

    private final String userId;
    private final String tipo;
    private final String rolPlataforma;
    private final Long idCompania;
    private final String rolGym;
    private final Long idPersona;
    private final Long idSucursal;

    public JwtPrincipal(String userId, String tipo, String rolPlataforma, Long idCompania, String rolGym, Long idPersona, Long idSucursal) {
        this.userId = userId;
        this.tipo = tipo;
        this.rolPlataforma = rolPlataforma;
        this.idCompania = idCompania;
        this.rolGym = rolGym;
        this.idPersona = idPersona;
        this.idSucursal = idSucursal;
    }

    @Override
    public String getName() {
        return userId;
    }

    public String getUserId()        { return userId; }
    public String getTipo()          { return tipo; }
    public String getRolPlataforma() { return rolPlataforma; }
    public Long getIdCompania()      { return idCompania; }
    public String getRolGym()        { return rolGym; }
    public Long getIdPersona()       { return idPersona; }
    public Long getIdSucursal()      { return idSucursal; }

    public boolean isCliente()       { return "cliente".equals(tipo); }
    public boolean isStaff()         { return "staff".equals(tipo); }
    public boolean isPlataforma()    { return "plataforma".equals(tipo); }

    public boolean isSuperAdmin()    { return "super_admin".equals(rolPlataforma); }
    public boolean isDueno()         { return "dueno".equals(rolGym) || "admin_compania".equals(rolGym); }
    public boolean isRecepcion()     { return "recepcion".equals(rolGym); }
    public boolean isEntrenador()    { return "entrenador".equals(rolGym); }
}
