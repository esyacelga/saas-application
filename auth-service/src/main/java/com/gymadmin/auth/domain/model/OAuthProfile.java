package com.gymadmin.auth.domain.model;

/**
 * Perfil basico obtenido tras verificar un token OAuth con el proveedor (Google, Facebook).
 * El {@code nombre} puede venir vacio cuando el proveedor no lo expone en el token verificado;
 * en ese caso el frontend lo solicitara al usuario en la pantalla de completar registro.
 * El {@code fotoUrl} tambien puede venir null si el proveedor no la expone o si el usuario
 * no la tiene configurada; se guarda en {@code identidad.personas.foto_url} solo al crear
 * la persona por primera vez (nunca sobreescribe una foto ya existente).
 */
public record OAuthProfile(String email, String nombre, String fotoUrl) {}
