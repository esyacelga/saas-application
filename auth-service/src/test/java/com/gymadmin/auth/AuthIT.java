package com.gymadmin.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AuthIT extends IntegrationTestBase {

    private Integer savedPlatformaId;
    private Integer savedStaffId;
    private Integer savedRolId;
    private Integer savedPersonaId;
    private Integer savedAppUserId;

    @AfterEach
    void cleanup() {
        if (savedPlatformaId != null) {
            refreshTokenRepo.deleteByIdUsuarioAndTipoUsuario(savedPlatformaId, "plataforma").block();
            plataformaRepo.deleteById(savedPlatformaId).block();
        }
        if (savedStaffId != null) {
            refreshTokenRepo.deleteByIdUsuarioAndTipoUsuario(savedStaffId, "staff").block();
            staffRepo.deleteById(savedStaffId).block();
        }
        if (savedRolId != null) {
            rolRepo.deleteById(savedRolId).block();
        }
        if (savedAppUserId != null) {
            refreshTokenRepo.deleteByIdUsuarioAndTipoUsuario(savedAppUserId, "cliente").block();
            appRepo.deleteById(savedAppUserId).block();
        }
        if (savedPersonaId != null) {
            personaRepo.deleteById(savedPersonaId).block();
        }
    }

    private Integer insertPersona(String nombre) {
        return personaRepo.save(PersonaEntity.builder()
                .ci("CI-AUTH-" + UUID.randomUUID().toString().substring(0, 8))
                .nombre(nombre)
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block().getId();
    }

    @Test
    void loginPlatform_debeRetornarTokensYActualizarUltimoAcceso() {
        String correo = "test-plat-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";

        savedPersonaId = insertPersona("Test Plataforma");
        UsuarioPlataformaEntity entity = UsuarioPlataformaEntity.builder()
                .idPersona(savedPersonaId).correo(correo)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .rol("super_admin").activo(true)
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build();
        savedPlatformaId = plataformaRepo.save(entity).block().getId();

        JsonNode response = webClient.post().uri("/api/v1/auth/platform/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("correo", correo, "password", TEST_PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertNotNull(response);
        assertFalse(response.get("access_token").asText().isBlank(),
                "access_token debe estar presente");
        assertFalse(response.get("refresh_token").asText().isBlank(),
                "refresh_token debe estar presente");

        // Verifica que ultimo_acceso fue actualizado en la base de datos
        UsuarioPlataformaEntity inDb = plataformaRepo.findById(savedPlatformaId).block();
        assertNotNull(inDb.getUltimoAcceso(),
                "ultimo_acceso debe quedar registrado en la BD");

        // Verifica que el refresh token fue persistido en la base de datos
        String rt = response.get("refresh_token").asText();
        assertNotNull(refreshTokenRepo.findByToken(rt).block(),
                "El refresh token debe existir en la BD");
    }

    @Test
    void loginStaff_debeRetornarTokensYActualizarUltimoAcceso() {
        String correo = "test-staff-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";

        RolEntity rol = rolRepo.save(RolEntity.builder()
                .idCompania(ID_COMPANIA).idSucursal(ID_SUCURSAL)
                .nombre("RolTestAuth").descripcion("Rol de prueba")
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedRolId = rol.getId();

        savedPersonaId = insertPersona("Test Staff Auth");
        UsuarioStaffEntity staff = staffRepo.save(UsuarioStaffEntity.builder()
                .idCompania(ID_COMPANIA).idSucursal(ID_SUCURSAL).idRol(savedRolId)
                .idPersona(savedPersonaId).correo(correo)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .activo(true)
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedStaffId = staff.getId();

        JsonNode response = webClient.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("correo", correo, "password", TEST_PASSWORD, "id_compania", ID_COMPANIA))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertNotNull(response);
        assertFalse(response.get("access_token").asText().isBlank(),
                "access_token debe estar presente");
        assertFalse(response.get("refresh_token").asText().isBlank(),
                "refresh_token debe estar presente");

        // Verifica que ultimo_acceso fue actualizado en la base de datos
        UsuarioStaffEntity inDb = staffRepo.findById(savedStaffId).block();
        assertNotNull(inDb.getUltimoAcceso(),
                "ultimo_acceso debe quedar registrado en la BD");

        // Verifica que el refresh token fue persistido en la base de datos
        String rt = response.get("refresh_token").asText();
        assertNotNull(refreshTokenRepo.findByToken(rt).block(),
                "El refresh token debe existir en la BD");
    }

    @Test
    void refresh_debeRetornarNuevoAccessToken() {
        String correo = "refresh-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";

        savedPersonaId = insertPersona("Refresh Test");
        savedPlatformaId = plataformaRepo.save(UsuarioPlataformaEntity.builder()
                .idPersona(savedPersonaId).correo(correo)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .rol("super_admin").activo(true)
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block().getId();

        JsonNode loginResponse = webClient.post().uri("/api/v1/auth/platform/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("correo", correo, "password", TEST_PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertNotNull(loginResponse);
        String refreshToken = loginResponse.get("refresh_token").asText();

        JsonNode refreshResponse = webClient.post().uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refresh_token", refreshToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertNotNull(refreshResponse);
        assertFalse(refreshResponse.get("access_token").asText().isBlank(),
                "El nuevo access_token no debe estar vacío");
        assertTrue(refreshResponse.get("expires_in").asLong() > 0,
                "expires_in debe ser positivo");
    }

    @Test
    void logout_debeRetornar204SinContenido() {
        webClient.post().uri("/api/v1/auth/logout")
                .header("Authorization", platformBearer())
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void resetRequest_conCorreoInexistente_debeRetornar200ConMensaje() {
        webClient.post().uri("/api/v1/auth/password/reset-request")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "correo", "nobody-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com",
                        "id_compania", ID_COMPANIA,
                        "tipo", "staff"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .consumeWith(result -> {
                    JsonNode body = result.getResponseBody();
                    assertNotNull(body);
                    assertFalse(body.get("mensaje").asText().isBlank(),
                            "La respuesta debe incluir un mensaje");
                });
    }

    @Test
    void resetApply_tokenInvalido_debeRetornarError() {
        webClient.post().uri("/api/v1/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "token", "token-invalido-" + UUID.randomUUID(),
                        "nueva_password", "NuevoPassword1234"))
                .exchange()
                .expectStatus().value(status ->
                        assertTrue(status >= 400 && status < 500,
                                "Se esperaba un error 4xx para token inválido, pero se obtuvo: " + status));
    }

    @Test
    void getCompaniesByCorreo_debeRetornarArray() {
        webClient.get()
                .uri(u -> u.path("/api/v1/auth/companias-por-correo")
                        .queryParam("correo", "correo-" + UUID.randomUUID() + "@test.com")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .consumeWith(result -> {
                    JsonNode body = result.getResponseBody();
                    assertNotNull(body);
                    assertTrue(body.isArray(), "La respuesta debe ser un arreglo");
                });
    }

    @Test
    void loginApp_debeRetornarTokensYActualizarUltimoAcceso() {
        String ci = "CI-AUTH-" + UUID.randomUUID().toString().substring(0, 8);
        String login = "apptest-" + UUID.randomUUID().toString().substring(0, 8);

        PersonaEntity persona = personaRepo.save(PersonaEntity.builder()
                .ci(ci).nombre("Test Persona Auth")
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedPersonaId = persona.getId();

        UsuarioAppEntity appUser = appRepo.save(UsuarioAppEntity.builder()
                .idPersona(savedPersonaId).idCompania(ID_COMPANIA)
                .login(login)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .activo(true).requiereCambioPwd(false)
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedAppUserId = appUser.getId();

        JsonNode response = webClient.post().uri("/api/v1/auth/app/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("login", login, "password", TEST_PASSWORD, "id_compania", ID_COMPANIA))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertNotNull(response);
        assertFalse(response.get("access_token").asText().isBlank(),
                "access_token debe estar presente");
        assertFalse(response.get("refresh_token").asText().isBlank(),
                "refresh_token debe estar presente");

        // Verifica que ultimo_acceso fue actualizado en la base de datos
        UsuarioAppEntity inDb = appRepo.findByLoginAndIdCompania(login, ID_COMPANIA).block();
        assertNotNull(inDb.getUltimoAcceso(),
                "ultimo_acceso debe quedar registrado en la BD");

        // Verifica que el refresh token fue persistido en la base de datos
        String rt = response.get("refresh_token").asText();
        assertNotNull(refreshTokenRepo.findByToken(rt).block(),
                "El refresh token debe existir en la BD");
    }

    @Test
    void registrar_conPersonaNueva_debeCrearPersonaYUsuarioAppEnBaseDeDatos() {
        String correo = "nuevo-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";

        JsonNode response = webClient.post().uri("/api/v1/auth/app/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", "Usuario Nuevo",
                        "correo", correo,
                        "password", "Password123",
                        "id_compania", ID_COMPANIA,
                        "telefono", "0988888888"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertNotNull(response);
        assertFalse(response.get("access_token").asText().isBlank(),
                "access_token debe estar presente");
        assertFalse(response.get("refresh_token").asText().isBlank(),
                "refresh_token debe estar presente");

        PersonaEntity persona = personaRepo.findByCorreo(correo).block();
        assertNotNull(persona, "La persona nueva debe haberse creado en la BD");
        assertEquals(correo, persona.getCorreo(), "El correo de la persona debe coincidir");
        assertTrue(persona.getCi().matches("1\\d{9}0"),
                "El CI temporal debe seguir el formato 1XXXXXXXXX0, fue: " + persona.getCi());
        savedPersonaId = persona.getId();

        UsuarioAppEntity usuario = appRepo.findByLoginAndIdCompania(correo, ID_COMPANIA).block();
        assertNotNull(usuario, "El usuario app debe quedar registrado en la BD");
        savedAppUserId = usuario.getId();
        assertEquals(savedPersonaId, usuario.getIdPersona(),
                "El usuario app debe vincularse a la persona recién creada");
        assertTrue(passwordEncoder.matches("Password123", usuario.getPasswordHash()),
                "La contraseña debe almacenarse hasheada");

        String refreshToken = response.get("refresh_token").asText();
        assertNotNull(refreshTokenRepo.findByToken(refreshToken).block(),
                "El refresh token debe persistirse en la BD");
    }

    @Test
    void registrar_conLoginDuplicadoEnMismaCompania_debeRetornar409() {
        String correo = "dup-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";

        PersonaEntity persona = personaRepo.save(PersonaEntity.builder()
                .ci("CI-DUP-" + UUID.randomUUID().toString().substring(0, 8))
                .nombre("Persona Duplicada")
                .correo(correo)
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        assertNotNull(persona);
        savedPersonaId = persona.getId();

        savedAppUserId = appRepo.save(UsuarioAppEntity.builder()
                .idPersona(savedPersonaId).idCompania(ID_COMPANIA)
                .login(correo)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .activo(true).requiereCambioPwd(false)
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block().getId();

        webClient.post().uri("/api/v1/auth/app/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", "Otro Nombre",
                        "correo", correo,
                        "password", "Password123",
                        "id_compania", ID_COMPANIA))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void registrar_conPersonaQueYaTieneCuentaEnCompaniaPorOtroLogin_debeRetornar409() {
        String correoPersona = "persona-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        String otroLogin = "otroLogin-" + UUID.randomUUID().toString().substring(0, 8);

        PersonaEntity persona = personaRepo.save(PersonaEntity.builder()
                .ci("CI-OL-" + UUID.randomUUID().toString().substring(0, 8))
                .nombre("Persona Con Cuenta")
                .correo(correoPersona)
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        assertNotNull(persona);
        savedPersonaId = persona.getId();

        // Misma persona, misma compañía, pero login diferente al correo
        savedAppUserId = appRepo.save(UsuarioAppEntity.builder()
                .idPersona(savedPersonaId).idCompania(ID_COMPANIA)
                .login(otroLogin)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .activo(true).requiereCambioPwd(false)
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block().getId();

        // El registro con correoPersona: login no existe, persona sí existe y ya tiene cuenta
        webClient.post().uri("/api/v1/auth/app/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", "Persona Con Cuenta",
                        "correo", correoPersona,
                        "password", "Password123",
                        "id_compania", ID_COMPANIA))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void registrar_datosInvalidos_debeRetornar400() {
        webClient.post().uri("/api/v1/auth/app/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", "",
                        "correo", "test@test.com",
                        "password", "Password123",
                        "id_compania", ID_COMPANIA))
                .exchange()
                .expectStatus().isBadRequest();

        webClient.post().uri("/api/v1/auth/app/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", "Test",
                        "correo", "no-es-un-correo",
                        "password", "Password123",
                        "id_compania", ID_COMPANIA))
                .exchange()
                .expectStatus().isBadRequest();

        webClient.post().uri("/api/v1/auth/app/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", "Test",
                        "correo", "test@test.com",
                        "password", "corta",
                        "id_compania", ID_COMPANIA))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void registrar_debeCrearPersonaYUsuarioAppEnBaseDeDatos() {
        String correo = "registro-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        PersonaEntity personaExistente = personaRepo.save(PersonaEntity.builder()
                .ci("CI-REG-" + UUID.randomUUID().toString().substring(0, 8))
                .nombre("Persona Existente")
                .correo(correo)
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        assertNotNull(personaExistente);
        savedPersonaId = personaExistente.getId();

        JsonNode response = webClient.post().uri("/api/v1/auth/app/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", "Nuevo Usuario",
                        "correo", correo,
                        "password", "Password123",
                        "id_compania", ID_COMPANIA,
                        "telefono", "0999999999"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertNotNull(response);
        assertFalse(response.get("access_token").asText().isBlank(),
                "access_token debe estar presente");
        assertFalse(response.get("refresh_token").asText().isBlank(),
                "refresh_token debe estar presente");

        PersonaEntity persona = personaRepo.findByCorreo(correo).block();
        assertNotNull(persona, "La persona debe existir en la BD");
        assertEquals(savedPersonaId, persona.getId(),
                "Debe reutilizarse la persona ya existente por correo");

        UsuarioAppEntity usuario = appRepo.findByLoginAndIdCompania(correo, ID_COMPANIA).block();
        assertNotNull(usuario, "El usuario app debe quedar registrado en la BD");
        savedAppUserId = usuario.getId();
        assertEquals(savedPersonaId, usuario.getIdPersona(),
                "El usuario app debe estar asociado a la persona creada");
        assertTrue(passwordEncoder.matches("Password123", usuario.getPasswordHash()),
                "La contraseña debe almacenarse hasheada");

        String refreshToken = response.get("refresh_token").asText();
        assertNotNull(refreshTokenRepo.findByToken(refreshToken).block(),
                "El refresh token debe persistirse en la BD");
    }
}
