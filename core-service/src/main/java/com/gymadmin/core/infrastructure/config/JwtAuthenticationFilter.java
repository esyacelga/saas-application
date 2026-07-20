package com.gymadmin.core.infrastructure.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.List;

@Component
public class JwtAuthenticationFilter implements WebFilter {

    private final SecretKey secretKey;
    private final ApiAuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret,
                                   ApiAuthenticationEntryPoint authenticationEntryPoint) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String tipo = claims.get("tipo", String.class);
            String rolPlataforma = claims.get("rol_plataforma", String.class);
            Long idCompania = null;
            Object idCompaniaObj = claims.get("id_compania");
            if (idCompaniaObj instanceof Number number) {
                idCompania = number.longValue();
            }
            Long idPersona = null;
            Object idPersonaObj = claims.get("id_persona");
            if (idPersonaObj instanceof Number number) {
                idPersona = number.longValue();
            }
            List<String> permisos = List.of();
            Object permisosRaw = claims.get("permisos");
            if (permisosRaw instanceof List<?> list) {
                permisos = list.stream().map(Object::toString).toList();
            }

            JwtPrincipal principal = new JwtPrincipal(userId, tipo, rolPlataforma, idCompania, idPersona, permisos);

            String role = rolPlataforma != null ? rolPlataforma : "user";
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    );

            return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));

        } catch (JwtException e) {
            // Token inválido/expirado: emitir el sobre estándar (codigo=no_autenticado)
            // en lugar de un 401 vacío (contrato de errores, hallazgo #1).
            return authenticationEntryPoint.commence(
                    exchange, new BadCredentialsException("Token inválido", e));
        }
    }
}
