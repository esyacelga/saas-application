package com.gymadmin.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.PersonaEntity;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.UsuarioPlataformaEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlatformUsuarioIT extends IntegrationTestBase {

    private Integer savedId;
    private Integer savedPersonaId;

    @BeforeEach
    void seedPersona() {
        PersonaEntity persona = personaRepo.save(PersonaEntity.builder()
                .ci("CI-PLAT-" + UUID.randomUUID().toString().substring(0, 8))
                .nombre("Persona Plataforma IT")
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedPersonaId = persona.getId();
    }

    @AfterEach
    void cleanup() {
        if (savedId != null) {
            plataformaRepo.deleteById(savedId).block();
        }
        if (savedPersonaId != null) {
            personaRepo.deleteById(savedPersonaId).block();
        }
    }

    @Test
    void crearUsuarioPlataforma_debeInsertarEnBd() {
        String correo = "new-admin-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";

        JsonNode response = webClient.post().uri("/api/v1/platform/usuarios")
                .header("Authorization", platformBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "id_persona", savedPersonaId,
                        "correo", correo,
                        "password", "Password1234",
                        "rol", "soporte"
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertNotNull(response);
        savedId = response.get("id").asInt();
        assertTrue(savedId > 0, "La respuesta debe contener un ID válido");

        // Verifica que el usuario fue insertado en saas.usuarios_plataforma
        UsuarioPlataformaEntity inDb = plataformaRepo.findById(savedId).block();
        assertNotNull(inDb, "El usuario plataforma debe existir en la BD");
        assertEquals(savedPersonaId, inDb.getIdPersona());
        assertEquals(correo, inDb.getCorreo());
        assertEquals("soporte", inDb.getRol());
        assertTrue(inDb.getActivo(), "El usuario debe estar activo");
        assertNotNull(inDb.getPasswordHash(), "El password hash debe estar almacenado");
        assertNotNull(inDb.getCreacionFecha(), "creacion_fecha debe estar registrado");
    }

    @Test
    void listarUsuariosPlataforma_debeRetornarLista() {
        webClient.get().uri("/api/v1/platform/usuarios")
                .header("Authorization", platformBearer())
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
    void desactivarUsuarioPlataforma_debeActualizarActivoEnBd() {
        String correo = "deact-admin-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";

        UsuarioPlataformaEntity entity = plataformaRepo.save(
                UsuarioPlataformaEntity.builder()
                        .idPersona(savedPersonaId).correo(correo)
                        .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                        .rol("soporte").activo(true)
                        .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                        .build()).block();
        savedId = entity.getId();

        webClient.put().uri("/api/v1/platform/usuarios/" + savedId + "/desactivar")
                .header("Authorization", platformBearer())
                .exchange()
                .expectStatus().isOk();

        UsuarioPlataformaEntity inDb = plataformaRepo.findById(savedId).block();
        assertNotNull(inDb);
        assertFalse(inDb.getActivo(), "El usuario debe estar desactivado en la BD");
    }
}
