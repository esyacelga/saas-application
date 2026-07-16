package com.gymadmin.auth.infrastructure.adapter.in.web.router;

import com.gymadmin.auth.infrastructure.adapter.in.web.handler.*;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class ApiRouter {

    private static final String V1 = "/api/v1";

    @RouterOperations({
        @RouterOperation(path = "/api/v1/auth/platform/login",    method = RequestMethod.POST,  beanClass = AuthHandler.class, beanMethod = "loginPlatform"),
        @RouterOperation(path = "/api/v1/auth/login",            method = RequestMethod.POST,  beanClass = AuthHandler.class, beanMethod = "loginStaff"),
        @RouterOperation(path = "/api/v1/auth/app/login",        method = RequestMethod.POST,  beanClass = AuthHandler.class, beanMethod = "loginApp"),
        @RouterOperation(path = "/api/v1/auth/refresh",          method = RequestMethod.POST,  beanClass = AuthHandler.class, beanMethod = "refresh"),
        @RouterOperation(path = "/api/v1/auth/logout",           method = RequestMethod.POST,  beanClass = AuthHandler.class, beanMethod = "logout"),
        @RouterOperation(path = "/api/v1/auth/password/reset-request", method = RequestMethod.POST, beanClass = AuthHandler.class, beanMethod = "resetRequest"),
        @RouterOperation(path = "/api/v1/auth/password/reset",   method = RequestMethod.POST,  beanClass = AuthHandler.class, beanMethod = "resetApply"),
        @RouterOperation(path = "/api/v1/auth/companias-por-correo", method = RequestMethod.GET, beanClass = AuthHandler.class, beanMethod = "getCompaniesByCorreo"),
        @RouterOperation(path = "/api/v1/auth/app/oauth/google", method = RequestMethod.POST,  beanClass = AuthHandler.class, beanMethod = "oauthGoogle"),
        @RouterOperation(path = "/api/v1/auth/app/oauth/facebook", method = RequestMethod.POST, beanClass = AuthHandler.class, beanMethod = "oauthFacebook"),
        @RouterOperation(path = "/api/v1/auth/app/registro",     method = RequestMethod.POST,  beanClass = AuthHandler.class, beanMethod = "registro")
    })
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

    @RouterOperations({
        @RouterOperation(path = "/api/v1/personas",                    method = RequestMethod.GET,  beanClass = PersonaHandler.class, beanMethod = "listar"),
        @RouterOperation(path = "/api/v1/personas/ci/{ci}",            method = RequestMethod.GET,  beanClass = PersonaHandler.class, beanMethod = "findByCi"),
        @RouterOperation(path = "/api/v1/personas/correo/{correo}",    method = RequestMethod.GET,  beanClass = PersonaHandler.class, beanMethod = "findByCorreo"),
        @RouterOperation(path = "/api/v1/personas/{id}",               method = RequestMethod.GET,  beanClass = PersonaHandler.class, beanMethod = "findById"),
        @RouterOperation(path = "/api/v1/personas",                    method = RequestMethod.POST, beanClass = PersonaHandler.class, beanMethod = "crear"),
        @RouterOperation(path = "/api/v1/personas/{id}",               method = RequestMethod.PUT,  beanClass = PersonaHandler.class, beanMethod = "actualizar"),
        @RouterOperation(path = "/api/v1/personas/{id}/consentimiento-wa", method = RequestMethod.PATCH, beanClass = PersonaHandler.class, beanMethod = "actualizarConsentimientoWa"),
        @RouterOperation(path = "/api/v1/personas/{id}/foto",          method = RequestMethod.POST, beanClass = PersonaHandler.class, beanMethod = "subirFoto")
    })
    @Bean
    public RouterFunction<ServerResponse> personaRoutes(PersonaHandler h) {
        return RouterFunctions.route()
                .GET(V1 + "/personas", h::listar)
                .GET(V1 + "/personas/ci/{ci}", h::findByCi)
                .GET(V1 + "/personas/correo/{correo}", h::findByCorreo)
                .GET(V1 + "/personas/{id}", h::findById)
                .POST(V1 + "/personas", h::crear)
                .PUT(V1 + "/personas/{id}", h::actualizar)
                .PATCH(V1 + "/personas/{id}/consentimiento-wa", h::actualizarConsentimientoWa)
                .POST(V1 + "/personas/{id}/foto", h::subirFoto)
                .build();
    }

    @RouterOperations({
        @RouterOperation(path = "/api/v1/usuarios",                           method = RequestMethod.GET,   beanClass = UsuarioStaffHandler.class, beanMethod = "listar"),
        @RouterOperation(path = "/api/v1/usuarios",                           method = RequestMethod.POST,  beanClass = UsuarioStaffHandler.class, beanMethod = "crear"),
        @RouterOperation(path = "/api/v1/usuarios/{id}",                      method = RequestMethod.PATCH, beanClass = UsuarioStaffHandler.class, beanMethod = "editar"),
        @RouterOperation(path = "/api/v1/usuarios/{id}/permisos",             method = RequestMethod.GET,   beanClass = UsuarioStaffHandler.class, beanMethod = "verPermisos"),
        @RouterOperation(path = "/api/v1/usuarios/{id}/activar",              method = RequestMethod.PUT,   beanClass = UsuarioStaffHandler.class, beanMethod = "activar"),
        @RouterOperation(path = "/api/v1/usuarios/{id}/desactivar",           method = RequestMethod.PUT,   beanClass = UsuarioStaffHandler.class, beanMethod = "desactivar"),
        @RouterOperation(path = "/api/v1/personas/{idPersona}/usuarios-staff", method = RequestMethod.GET,  beanClass = UsuarioStaffHandler.class, beanMethod = "listarPorPersona")
    })
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

    @RouterOperations({
        @RouterOperation(path = "/api/v1/roles",                method = RequestMethod.GET,    beanClass = RolHandler.class, beanMethod = "listar"),
        @RouterOperation(path = "/api/v1/roles",                method = RequestMethod.POST,   beanClass = RolHandler.class, beanMethod = "crear"),
        @RouterOperation(path = "/api/v1/roles/{id}",           method = RequestMethod.GET,    beanClass = RolHandler.class, beanMethod = "buscarPorId"),
        @RouterOperation(path = "/api/v1/roles/{id}/permisos",  method = RequestMethod.GET,    beanClass = RolHandler.class, beanMethod = "verPermisos"),
        @RouterOperation(path = "/api/v1/roles/{id}/permisos",  method = RequestMethod.PUT,    beanClass = RolHandler.class, beanMethod = "actualizarPermisos"),
        @RouterOperation(path = "/api/v1/roles/{id}",           method = RequestMethod.DELETE, beanClass = RolHandler.class, beanMethod = "eliminar")
    })
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

    @RouterOperations({
        @RouterOperation(path = "/api/v1/permisos",                method = RequestMethod.GET, beanClass = PermisoHandler.class, beanMethod = "listar"),
        @RouterOperation(path = "/api/v1/permisos/by-rol/{idRol}", method = RequestMethod.GET, beanClass = PermisoHandler.class, beanMethod = "porRol")
    })
    @Bean
    public RouterFunction<ServerResponse> permisoRoutes(PermisoHandler h) {
        return RouterFunctions.route()
                .GET(V1 + "/permisos", h::listar)
                .GET(V1 + "/permisos/by-rol/{idRol}", h::porRol)
                .build();
    }

    @RouterOperations({
        @RouterOperation(path = "/api/v1/app-usuarios",                          method = RequestMethod.POST,  beanClass = AppUsuarioHandler.class, beanMethod = "crear"),
        @RouterOperation(path = "/api/v1/app-usuarios/por-ci/{ci}",              method = RequestMethod.GET,   beanClass = AppUsuarioHandler.class, beanMethod = "obtenerPorCi"),
        @RouterOperation(path = "/api/v1/app-usuarios/{id}",                     method = RequestMethod.PATCH, beanClass = AppUsuarioHandler.class, beanMethod = "actualizar"),
        @RouterOperation(path = "/api/v1/app-usuarios/{id}/activar",             method = RequestMethod.PUT,   beanClass = AppUsuarioHandler.class, beanMethod = "activar"),
        @RouterOperation(path = "/api/v1/app-usuarios/{id}/desactivar",          method = RequestMethod.PUT,   beanClass = AppUsuarioHandler.class, beanMethod = "desactivar"),
        @RouterOperation(path = "/api/v1/personas/{idPersona}/usuarios-app",     method = RequestMethod.GET,   beanClass = AppUsuarioHandler.class, beanMethod = "listarPorPersona")
    })
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

    @RouterOperations({
        @RouterOperation(path = "/api/v1/platform/usuarios",                          method = RequestMethod.GET,   beanClass = PlatformUsuarioHandler.class, beanMethod = "listar"),
        @RouterOperation(path = "/api/v1/platform/usuarios",                          method = RequestMethod.POST,  beanClass = PlatformUsuarioHandler.class, beanMethod = "crear"),
        @RouterOperation(path = "/api/v1/platform/usuarios/{id}",                     method = RequestMethod.PATCH, beanClass = PlatformUsuarioHandler.class, beanMethod = "editar"),
        @RouterOperation(path = "/api/v1/platform/usuarios/{id}/foto",                method = RequestMethod.POST,  beanClass = PlatformUsuarioHandler.class, beanMethod = "actualizarFoto"),
        @RouterOperation(path = "/api/v1/platform/usuarios/{id}/desactivar",          method = RequestMethod.PUT,   beanClass = PlatformUsuarioHandler.class, beanMethod = "desactivar"),
        @RouterOperation(path = "/api/v1/personas/{idPersona}/usuarios-plataforma",   method = RequestMethod.GET,   beanClass = PlatformUsuarioHandler.class, beanMethod = "listarPorPersona")
    })
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

    @RouterOperations({
        @RouterOperation(path = "/api/v1/platform/roles",                                            method = RequestMethod.GET,    beanClass = PlatformRolHandler.class, beanMethod = "listarRoles"),
        @RouterOperation(path = "/api/v1/platform/roles",                                            method = RequestMethod.POST,   beanClass = PlatformRolHandler.class, beanMethod = "crear"),
        @RouterOperation(path = "/api/v1/platform/roles/{id}",                                       method = RequestMethod.PUT,    beanClass = PlatformRolHandler.class, beanMethod = "actualizar"),
        @RouterOperation(path = "/api/v1/platform/roles/{id}",                                       method = RequestMethod.DELETE, beanClass = PlatformRolHandler.class, beanMethod = "eliminar"),
        @RouterOperation(path = "/api/v1/platform/roles/{id}/permisos",                              method = RequestMethod.GET,    beanClass = PlatformRolHandler.class, beanMethod = "verPermisos"),
        @RouterOperation(path = "/api/v1/platform/roles/{id}/permisos",                              method = RequestMethod.PUT,    beanClass = PlatformRolHandler.class, beanMethod = "actualizarPermisos"),
        @RouterOperation(path = "/api/v1/platform/roles/{id}/permisos/detalle",                      method = RequestMethod.GET,    beanClass = PlatformRolHandler.class, beanMethod = "verPermisosDetalle"),
        @RouterOperation(path = "/api/v1/platform/roles/{id}/permisos",                              method = RequestMethod.POST,   beanClass = PlatformRolHandler.class, beanMethod = "asignarPermiso"),
        @RouterOperation(path = "/api/v1/platform/roles/{id}/permisos/{idPermiso}",                  method = RequestMethod.DELETE, beanClass = PlatformRolHandler.class, beanMethod = "eliminarPermisoDeRol"),
        @RouterOperation(path = "/api/v1/platform/companias",                                        method = RequestMethod.GET,    beanClass = PlatformRolHandler.class, beanMethod = "listarCompanias"),
        @RouterOperation(path = "/api/v1/platform/companias/{idCompania}/sucursales",                 method = RequestMethod.GET,    beanClass = PlatformRolHandler.class, beanMethod = "listarSucursales"),
        @RouterOperation(path = "/api/v1/platform/companias/{idCompania}/usuarios",                   method = RequestMethod.GET,    beanClass = PlatformRolHandler.class, beanMethod = "listarUsuariosCompania"),
        @RouterOperation(path = "/api/v1/platform/companias/{idCompania}/usuarios/{id}/password",     method = RequestMethod.PUT,    beanClass = PlatformRolHandler.class, beanMethod = "resetPasswordUsuario")
    })
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

    @RouterOperations({
        @RouterOperation(path = "/api/v1/platform/permisos",      method = RequestMethod.GET,    beanClass = PlatformPermisoHandler.class, beanMethod = "listar"),
        @RouterOperation(path = "/api/v1/platform/permisos",      method = RequestMethod.POST,   beanClass = PlatformPermisoHandler.class, beanMethod = "crear"),
        @RouterOperation(path = "/api/v1/platform/permisos/{id}", method = RequestMethod.PUT,    beanClass = PlatformPermisoHandler.class, beanMethod = "actualizar"),
        @RouterOperation(path = "/api/v1/platform/permisos/{id}", method = RequestMethod.DELETE, beanClass = PlatformPermisoHandler.class, beanMethod = "eliminar")
    })
    @Bean
    public RouterFunction<ServerResponse> platformPermisoRoutes(PlatformPermisoHandler h) {
        return RouterFunctions.route()
                .GET(V1 + "/platform/permisos", h::listar)
                .POST(V1 + "/platform/permisos", h::crear)
                .PUT(V1 + "/platform/permisos/{id}", h::actualizar)
                .DELETE(V1 + "/platform/permisos/{id}", h::eliminar)
                .build();
    }

    @RouterOperations({
        @RouterOperation(path = "/api/v1/auth/gimnasio/by-qr/{qrToken}", method = RequestMethod.GET, beanClass = GimnasioHandler.class, beanMethod = "byQr")
    })
    @Bean
    public RouterFunction<ServerResponse> gimnasioRoutes(GimnasioHandler h) {
        return RouterFunctions.route()
                .GET(V1 + "/auth/gimnasio/by-qr/{qrToken}", h::byQr)
                .build();
    }

    @RouterOperations({
        @RouterOperation(path = "/api/v1/bitacora", method = RequestMethod.GET, beanClass = BitacoraHandler.class, beanMethod = "listar")
    })
    @Bean
    public RouterFunction<ServerResponse> bitacoraRoutes(BitacoraHandler h) {
        return RouterFunctions.route()
                .GET(V1 + "/bitacora", h::listar)
                .build();
    }
}
