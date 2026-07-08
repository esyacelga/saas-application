package com.gymadmin.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.PersonaEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PersonaIT extends IntegrationTestBase {

    private Integer savedPersonaId;

    @AfterEach
    void cleanup() {
        if (savedPersonaId != null) {
            personaRepo.deleteById(savedPersonaId).block();
        }
    }

    @Test
    void crearPersona_debeInsertarEnBd() {
        String ci = "CI-NEW-" + UUID.randomUUID().toString().substring(0, 8);

        JsonNode response = webClient.post().uri("/api/v1/personas")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "ci", ci,
                        "nombre", "Juan Prueba",
                        "telefono", "0999123456",
                        "correo", "juan.prueba@test.com"
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertNotNull(response);
        savedPersonaId = response.get("id").asInt();
        assertTrue(savedPersonaId > 0, "La respuesta debe contener un ID válido");

        // Verifica que la persona fue insertada en la base de datos
        PersonaEntity inDb = personaRepo.findByCi(ci).block();
        assertNotNull(inDb, "La persona debe existir en la BD");
        assertEquals("Juan Prueba", inDb.getNombre());
        assertEquals("0999123456", inDb.getTelefono());
        assertEquals("juan.prueba@test.com", inDb.getCorreo());
        assertEquals(ci, inDb.getCi());
        assertNotNull(inDb.getCreacionFecha(), "creacion_fecha debe estar registrado");
    }

    @Test
    void actualizarPersona_debeModificarCamposEnBd() {
        String ci = "CI-UPD-" + UUID.randomUUID().toString().substring(0, 8);

        // Insertar persona directamente en BD
        PersonaEntity existing = personaRepo.save(PersonaEntity.builder()
                .ci(ci).nombre("Nombre Original")
                .telefono("0900000000").correo("original@test.com")
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedPersonaId = existing.getId();

        // Ejecutar el PUT
        webClient.put().uri("/api/v1/personas/" + savedPersonaId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", "Nombre Actualizado",
                        "telefono", "0988765432"
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        // Verifica que los datos fueron actualizados en la base de datos
        PersonaEntity inDb = personaRepo.findById(savedPersonaId).block();
        assertNotNull(inDb);
        assertEquals("Nombre Actualizado", inDb.getNombre(),
                "El nombre debe haber sido actualizado en la BD");
        assertEquals("0988765432", inDb.getTelefono(),
                "El teléfono debe haber sido actualizado en la BD");
        // El CI es inmutable: no debe haber cambiado
        assertEquals(ci, inDb.getCi(), "El CI no debe cambiar");
    }

    @Test
    void buscarPorCi_debeRetornarPersonaExistente() {
        String ci = "CI-GET-" + UUID.randomUUID().toString().substring(0, 8);

        PersonaEntity existing = personaRepo.save(PersonaEntity.builder()
                .ci(ci).nombre("Persona Get Test")
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedPersonaId = existing.getId();

        JsonNode response = webClient.get().uri("/api/v1/personas/ci/" + ci)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertNotNull(response);
        assertEquals(ci, response.get("ci").asText());
        assertEquals("Persona Get Test", response.get("nombre").asText());
    }
}
