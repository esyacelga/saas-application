package com.gymadmin.auth.infrastructure.adapter.in.web.router;

import com.gymadmin.auth.infrastructure.adapter.in.web.handler.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class ApiRouter {

    private static final String V1 = "/api/v1";

    @Bean
    public RouterFunction<ServerResponse> authRoutes(AuthHandler h) {
        return RouterFunctions.route()
                .POST(V1 + "/auth/platform/login", h::loginPlatform)
                .POST(V1 + "/auth/login", h::loginStaff)
                .POST(V1 + "/auth/app/login", h::loginApp)
                .POST(V1 + "/auth/refresh", h::refresh)
                .POST(V1 + "/auth/logout", h::logout)
                .POST(V1 + "/auth/password/reset-request", h::resetRequest)
                .POST(V1 + "/auth/password/reset", h::resetApply)
                .GET(V1 + "/auth/companias-por-correo", h::getCompaniesByCorreo)
                .POST(V1 + "/auth/app/oauth/google", h::oauthGoogle)
                .POST(V1 + "/auth/app/oauth/facebook", h::oauthFacebook)
                .POST(V1 + "/auth/app/registro", h::registro)
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> personaRoutes(PersonaHandler h) {
        return RouterFunctions.route()
                .GET(V1 + "/personas", h::listar)
                .GET(V1 + "/personas/ci/{ci}", h::findByCi)
                .GET(V1 + "/personas/correo/{correo}", h::findByCorreo)
                .GET(V1 + "/personas/{id}", h::findById)
                .POST(V1 + "/personas", h::crear)
                .PUT(V1 + "/personas/{id}", h::actualizar)
                .POST(V1 + "/personas/{id}/foto", h::subirFoto)
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> usuarioStaffRoutes(UsuarioStaffHandler h) {
        return RouterFunctions.route()
                .GET(V1 + "/usuarios", h::listar)
                .POST(V1 + "/usuarios", h::crear)
                .PATCH(V1 + "/usuarios/{id}", h::editar)
                .GET(V1 + "/usuarios/{id}/permisos", h::verPermisos)
                .PUT(V1 + "/usuarios/{id}/activar", h::activar)
                .PUT(V1 + "/usuarios/{id}/desactivar", h::desactivar)
                .GET(V1 + "/personas/{idPersona}/usuarios-staff", h::listarPorPersona)
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> rolRoutes(RolHandler h) {
        return RouterFunctions.route()
                .GET(V1 + "/roles", h::listar)
                .POST(V1 + "/roles", h::crear)
                .GET(V1 + "/roles/{id}", h::buscarPorId)
                .GET(V1 + "/roles/{id}/permisos", h::verPermisos)
                .PUT(V1 + "/roles/{id}/permisos", h::actualizarPermisos)
                .DELETE(V1 + "/roles/{id}", h::eliminar)
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> permisoRoutes(PermisoHandler h) {
        return RouterFunctions.route()
                .GET(V1 + "/permisos", h::listar)
                .GET(V1 + "/permisos/by-rol/{idRol}", h::porRol)
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> appUsuarioRoutes(AppUsuarioHandler h) {
        return RouterFunctions.route()
                .POST(V1 + "/app-usuarios", h::crear)
                .GET(V1 + "/app-usuarios/por-ci/{ci}", h::obtenerPorCi)
                .PATCH(V1 + "/app-usuarios/{id}", h::actualizar)
                .PUT(V1 + "/app-usuarios/{id}/activar", h::activar)
                .PUT(V1 + "/app-usuarios/{id}/desactivar", h::desactivar)
                .GET(V1 + "/personas/{idPersona}/usuarios-app", h::listarPorPersona)
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> platformRoutes(PlatformUsuarioHandler h) {
        return RouterFunctions.route()
                .GET(V1 + "/platform/usuarios", h::listar)
                .POST(V1 + "/platform/usuarios", h::crear)
                .PATCH(V1 + "/platform/usuarios/{id}", h::editar)
                .POST(V1 + "/platform/usuarios/{id}/foto", h::actualizarFoto)
                .PUT(V1 + "/platform/usuarios/{id}/desactivar", h::desactivar)
                .GET(V1 + "/personas/{idPersona}/usuarios-plataforma", h::listarPorPersona)
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> platformRolRoutes(PlatformRolHandler h) {
        return RouterFunctions.route()
                .GET(V1 + "/platform/roles", h::listarRoles)
                .POST(V1 + "/platform/roles", h::crear)
                .PUT(V1 + "/platform/roles/{id}", h::actualizar)
                .DELETE(V1 + "/platform/roles/{id}", h::eliminar)
                .GET(V1 + "/platform/roles/{id}/permisos", h::verPermisos)
                .PUT(V1 + "/platform/roles/{id}/permisos", h::actualizarPermisos)
                .GET(V1 + "/platform/roles/{id}/permisos/detalle", h::verPermisosDetalle)
                .POST(V1 + "/platform/roles/{id}/permisos", h::asignarPermiso)
                .DELETE(V1 + "/platform/roles/{id}/permisos/{idPermiso}", h::eliminarPermisoDeRol)
                .GET(V1 + "/platform/companias", h::listarCompanias)
                .GET(V1 + "/platform/companias/{idCompania}/sucursales", h::listarSucursales)
                .GET(V1 + "/platform/companias/{idCompania}/usuarios", h::listarUsuariosCompania)
                .PUT(V1 + "/platform/companias/{idCompania}/usuarios/{id}/password", h::resetPasswordUsuario)
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> platformPermisoRoutes(PlatformPermisoHandler h) {
        return RouterFunctions.route()
                .GET(V1 + "/platform/permisos", h::listar)
                .POST(V1 + "/platform/permisos", h::crear)
                .PUT(V1 + "/platform/permisos/{id}", h::actualizar)
                .DELETE(V1 + "/platform/permisos/{id}", h::eliminar)
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> gimnasioRoutes(GimnasioHandler h) {
        return RouterFunctions.route()
                .GET(V1 + "/auth/gimnasio/by-qr/{qrToken}", h::byQr)
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> bitacoraRoutes(BitacoraHandler h) {
        return RouterFunctions.route()
                .GET(V1 + "/bitacora", h::listar)
                .build();
    }
}
