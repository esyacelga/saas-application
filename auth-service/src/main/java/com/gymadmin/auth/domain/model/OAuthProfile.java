package com.gymadmin.auth.domain.model;

/**
 * Perfil basico obtenido tras verificar un token OAuth con el proveedor (Google, Facebook).
 * El {@code nombre} puede venir vacio cuando el proveedor no lo expone en el token verificado;
 * en ese caso el frontend lo solicitara al usuario en la pantalla de completar registro.
 */
public record OAuthProfile(String email, String nombre) {}
