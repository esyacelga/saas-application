package com.gymadmin.auth.infrastructure.security;

import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class UserPrincipal implements UserDetails {

    private final Integer id;
    private final String tipo;
    private final Integer idCompania;
    private final Integer idSucursal;
    private final Integer idRol;
    private final String rolPlataforma;
    private final Integer idPersona;
    private final List<String> permisos;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (permisos == null) return List.of();
        return permisos.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
    }

    public boolean isPlataforma() { return "plataforma".equals(tipo); }
    public boolean isStaff()      { return "staff".equals(tipo); }
    public boolean isCliente()    { return "cliente".equals(tipo); }

    public boolean hasPermiso(String permiso) {
        return permisos != null && permisos.contains(permiso);
    }

    public String toIdentifier() { return tipo + ":" + id; }

    @Override public String getPassword()               { return null; }
    @Override public String getUsername()               { return String.valueOf(id); }
    @Override public boolean isAccountNonExpired()      { return true; }
    @Override public boolean isAccountNonLocked()       { return true; }
    @Override public boolean isCredentialsNonExpired()  { return true; }
    @Override public boolean isEnabled()                { return true; }
}
