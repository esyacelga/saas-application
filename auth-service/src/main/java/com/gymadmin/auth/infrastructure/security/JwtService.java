package com.gymadmin.auth.infrastructure.security;

import com.gymadmin.auth.domain.port.out.TokenGeneratorPort;
import com.gymadmin.auth.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService implements TokenGeneratorPort {

    private final JwtProperties props;

    private SecretKey signingKey() {
        byte[] keyBytes = Decoders.BASE64.decode(props.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public String generatePlatformToken(Integer id, String nombre, String rolPlataforma) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tipo", "plataforma");
        claims.put("rol_plataforma", rolPlataforma);
        claims.put("nombre", nombre);
        return build(claims, String.valueOf(id), props.getExpiryStaffSeconds());
    }

    @Override
    public String generateStaffToken(Integer id, Integer idCompania, Integer idSucursal,
                                     Integer idRol, String nombre, List<String> permisos) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tipo", "staff");
        claims.put("id_compania", idCompania);
        claims.put("id_sucursal", idSucursal);
        claims.put("id_rol", idRol);
        claims.put("nombre", nombre);
        claims.put("permisos", permisos);
        return build(claims, String.valueOf(id), props.getExpiryStaffSeconds());
    }

    @Override
    public String generateClienteToken(Integer id, Integer idCompania, Integer idPersona,
                                       String nombre, String nombreCompania, String logoUrl, String sexo) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tipo", "cliente");
        claims.put("id_compania", idCompania);
        claims.put("id_persona", idPersona);
        claims.put("nombre", nombre);
        claims.put("nombre_compania", nombreCompania);
        if (logoUrl != null) claims.put("logo_url", logoUrl);
        if (sexo != null) claims.put("sexo", sexo);
        return build(claims, String.valueOf(id), props.getExpiryClienteSeconds());
    }

    @Override
    public String generateRefreshToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private String build(Map<String, Object> claims, String subject, long expirySeconds) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirySeconds * 1000))
                .signWith(signingKey())
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try { extractClaims(token); return true; }
        catch (JwtException e) { return false; }
    }

    public UserPrincipal toPrincipal(Claims claims) {
        String tipo = claims.get("tipo", String.class);
        Integer id  = Integer.valueOf(claims.getSubject());

        UserPrincipal.UserPrincipalBuilder builder = UserPrincipal.builder().id(id).tipo(tipo);

        switch (tipo) {
            case "plataforma" -> builder.rolPlataforma(claims.get("rol_plataforma", String.class));
            case "staff" -> {
                builder.idCompania(claims.get("id_compania", Integer.class))
                       .idSucursal(claims.get("id_sucursal", Integer.class))
                       .idRol(claims.get("id_rol", Integer.class));
                @SuppressWarnings("unchecked")
                List<String> permisos = (List<String>) claims.get("permisos");
                builder.permisos(permisos);
            }
            case "cliente" -> builder
                    .idCompania(claims.get("id_compania", Integer.class))
                    .idPersona(claims.get("id_persona", Integer.class));
        }
        return builder.build();
    }
}
