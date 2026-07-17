package com.gymadmin.auth.dto.response;

/**
 * Respuesta unificada para los endpoints de login via OAuth (Google/Facebook).
 * <ul>
 *   <li>Cuando {@code status = "logged_in"} se poblan {@code accessToken}, {@code refreshToken},
 *       {@code expiresIn}, {@code persona} y {@code compania} — los campos {@code email} y
 *       {@code nombre} llegan null.</li>
 *   <li>Cuando {@code status = "registro_pendiente"} se poblan {@code email} y (si el proveedor lo
 *       expone) {@code nombre} para que el frontend pre-llene el formulario de completar registro;
 *       el resto de campos llegan null.</li>
 * </ul>
 */
public record OAuthLoginResponse(
        String status,
        String accessToken,
        String refreshToken,
        Long expiresIn,
        LoginAppResponse.PersonaInfo persona,
        LoginAppResponse.CompaniaInfo compania,
        String email,
        String nombre
) {
    public static final String STATUS_LOGGED_IN = "logged_in";
    public static final String STATUS_REGISTRO_PENDIENTE = "registro_pendiente";

    public static OAuthLoginResponse loggedIn(LoginAppResponse login) {
        return new OAuthLoginResponse(
                STATUS_LOGGED_IN,
                login.accessToken(),
                login.refreshToken(),
                login.expiresIn(),
                login.persona(),
                login.compania(),
                null,
                null);
    }

    public static OAuthLoginResponse registroPendiente(String email, String nombre) {
        return new OAuthLoginResponse(
                STATUS_REGISTRO_PENDIENTE,
                null, null, null, null, null,
                email, nombre);
    }
}
