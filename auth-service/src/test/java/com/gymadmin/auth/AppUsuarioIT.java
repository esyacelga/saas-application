package com.gymadmin.auth;

import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.PersonaEntity;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.UsuarioAppEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AppUsuarioIT extends IntegrationTestBase {

    private Integer savedPersonaId;
    private Integer savedAppUserId;

    @AfterEach
    void cleanup() {
        // comentado para inspección manual en BD
        // if (savedAppUserId != null) {
        //     refreshTokenRepo.deleteByIdUsuarioAndTipoUsuario(savedAppUserId, "cliente").block();
        //     appRepo.deleteById(savedAppUserId).block();
        // }
        // if (savedPersonaId != null) {
        //     personaRepo.deleteById(savedPersonaId).block();
        // }
    }

    private PersonaEntity insertPersona() {
        String ci = "CI-APP-" + UUID.randomUUID().toString().substring(0, 8);
        PersonaEntity persona = personaRepo.save(PersonaEntity.builder()
                .ci(ci).nombre("Persona App IT")
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedPersonaId = persona.getId();
        return persona;
    }

    @Test
    void crearUsuarioApp_debeInsertarEnBd() {
        PersonaEntity persona = insertPersona();
        String login = "app-" + UUID.randomUUID().toString().substring(0, 8);

        webClient.post().uri("/api/v1/app-usuarios")
                .header("Authorization", staffBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "id_persona", persona.getId(),
                        "login", login,
                        "password", "Password1234"
                ))
                .exchange()
                .expectStatus().isCreated();

        UsuarioAppEntity inDb = appRepo.findByLoginAndIdCompania(login, ID_COMPANIA).block();
        assertNotNull(inDb, "El usuario app debe existir en la BD");
        savedAppUserId = inDb.getId();
        assertEquals(persona.getId(), inDb.getIdPersona());
        assertEquals(ID_COMPANIA, inDb.getIdCompania());
        assertEquals(login, inDb.getLogin());
        assertTrue(inDb.getActivo(), "El usuario debe estar activo");
        assertNotNull(inDb.getPasswordHash(), "El password hash debe estar almacenado");
        assertNotNull(inDb.getCreacionFecha(), "creacion_fecha debe estar registrado");
    }

    @Test
    void desactivarUsuarioApp_debeActualizarActivoEnBd() {
        PersonaEntity persona = insertPersona();
        String login = "app-deact-" + UUID.randomUUID().toString().substring(0, 6);

        UsuarioAppEntity appUser = appRepo.save(UsuarioAppEntity.builder()
                .idPersona(persona.getId()).idCompania(ID_COMPANIA)
                .login(login)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .activo(true).requiereCambioPwd(false)
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedAppUserId = appUser.getId();

        webClient.put().uri("/api/v1/app-usuarios/" + savedAppUserId + "/desactivar")
                .header("Authorization", staffBearer())
                .exchange()
                .expectStatus().isOk();

        UsuarioAppEntity inDb = appRepo.findById(savedAppUserId).block();
        assertNotNull(inDb);
        assertFalse(inDb.getActivo(), "El usuario app debe estar desactivado en la BD");
    }

    @Test
    void activarUsuarioApp_debeActualizarActivoEnBd() {
        PersonaEntity persona = insertPersona();
        String login = "app-act-" + UUID.randomUUID().toString().substring(0, 6);

        UsuarioAppEntity appUser = appRepo.save(UsuarioAppEntity.builder()
                .idPersona(persona.getId()).idCompania(ID_COMPANIA)
                .login(login)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .activo(false).requiereCambioPwd(false)
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedAppUserId = appUser.getId();

        webClient.put().uri("/api/v1/app-usuarios/" + savedAppUserId + "/activar")
                .header("Authorization", staffBearer())
                .exchange()
                .expectStatus().isOk();

        UsuarioAppEntity inDb = appRepo.findById(savedAppUserId).block();
        assertNotNull(inDb);
        assertTrue(inDb.getActivo(), "El usuario app debe estar activo en la BD");
    }
}
