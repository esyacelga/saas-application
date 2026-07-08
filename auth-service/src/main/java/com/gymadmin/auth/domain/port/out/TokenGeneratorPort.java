package com.gymadmin.auth.domain.port.out;

import java.util.List;

public interface TokenGeneratorPort {
    String generatePlatformToken(Integer id, String nombre, String rolPlataforma);
    String generateStaffToken(Integer id, Integer idCompania, Integer idSucursal,
                              Integer idRol, String nombre, List<String> permisos);
    String generateClienteToken(Integer id, Integer idCompania, Integer idPersona,
                                String nombre, String nombreCompania, String logoUrl, String sexo);
    String generateRefreshToken();
}
