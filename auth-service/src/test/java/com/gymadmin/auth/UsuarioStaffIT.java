package com.gymadmin.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.PersonaEntity;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.RolEntity;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.UsuarioStaffEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UsuarioStaffIT extends IntegrationTestBase {

    private Integer savedRolId;
    private Integer savedStaffId;
    private Integer savedPersonaId;

    @BeforeEach
    void seed() {
        PersonaEntity persona = personaRepo.save(PersonaEntity.builder()
                .ci("CI-STAFF-" + UUID.randomUUID().toString().substring(0, 8))
                .nombre("Persona Staff IT")
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedPersonaId = persona.getId();

        RolEntity rol = rolRepo.save(RolEntity.builder()
                .idCompania(ID_COMPANIA).idSucursal(ID_SUCURSAL)
                .nombre("RolStaffIT-" + UUID.randomUUID().toString().substring(0, 6))
                .descripcion("Rol de prueba staff")
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedRolId = rol.getId();
    }

    @AfterEach
    void cleanup() {
        if (savedStaffId != null) {
            refreshTokenRepo.deleteByIdUsuarioAndTipoUsuario(savedStaffId, "staff").block();
            staffRepo.deleteById(savedStaffId).block();
        }
        if (savedRolId != null) {
            rolRepo.deleteById(savedRolId).block();
        }
        if (savedPersonaId != null) {
            personaRepo.deleteById(savedPersonaId).block();
        }
    }

    @Test
    void crearUsuarioStaff_debeInsertarEnBd() {
        String correo = "staff-new-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";

        JsonNode response = webClient.post().uri("/api/v1/usuarios")
                .header("Authorization", staffBearerWithRol(savedRolId, "usuarios:crear"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "id_persona", savedPersonaId,
                        "correo", correo,
                        "id_rol", savedRolId,
                        "password_temporal", "Temporal1234"
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertNotNull(response);
        savedStaffId = response.get("id").asInt();
        assertTrue(savedStaffId > 0, "La respuesta debe contener un ID válido");

        // Verifica que el usuario fue insertado en seguridad.usuarios
        UsuarioStaffEntity inDb = staffRepo.findByCorreoAndIdCompania(correo, ID_COMPANIA).block();
        assertNotNull(inDb, "El usuario staff debe existir en la BD");
        assertEquals(savedPersonaId, inDb.getIdPersona());
        assertEquals(correo, inDb.getCorreo());
        assertEquals(ID_COMPANIA, inDb.getIdCompania());
        assertEquals(savedRolId, inDb.getIdRol());
        assertTrue(inDb.getActivo(), "El usuario debe estar activo");
        assertNotNull(inDb.getPasswordHash(), "El password hash debe estar almacenado");
        assertNotNull(inDb.getCreacionFecha(), "creacion_fecha debe estar registrado");
    }

    @Test
    void desactivarUsuarioStaff_debeActualizarActivoEnBd() {
        UsuarioStaffEntity staff = staffRepo.save(UsuarioStaffEntity.builder()
                .idCompania(ID_COMPANIA).idSucursal(ID_SUCURSAL).idRol(savedRolId)
                .idPersona(savedPersonaId)
                .correo("deact-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .activo(true)
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedStaffId = staff.getId();

        webClient.put().uri("/api/v1/usuarios/" + savedStaffId + "/desactivar")
                .header("Authorization", staffBearerWithRol(savedRolId, "usuarios:crear"))
                .exchange()
                .expectStatus().isOk();

        UsuarioStaffEntity inDb = staffRepo.findByIdAndIdCompania(savedStaffId, ID_COMPANIA).block();
        assertNotNull(inDb);
        assertFalse(inDb.getActivo(), "El usuario debe estar desactivado en la BD");
    }

    @Test
    void activarUsuarioStaff_debeActualizarActivoEnBd() {
        UsuarioStaffEntity staff = staffRepo.save(UsuarioStaffEntity.builder()
                .idCompania(ID_COMPANIA).idSucursal(ID_SUCURSAL).idRol(savedRolId)
                .idPersona(savedPersonaId)
                .correo("act-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .activo(false)
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedStaffId = staff.getId();

        webClient.put().uri("/api/v1/usuarios/" + savedStaffId + "/activar")
                .header("Authorization", staffBearerWithRol(savedRolId, "usuarios:crear"))
                .exchange()
                .expectStatus().isOk();

        UsuarioStaffEntity inDb = staffRepo.findByIdAndIdCompania(savedStaffId, ID_COMPANIA).block();
        assertNotNull(inDb);
        assertTrue(inDb.getActivo(), "El usuario debe estar activo en la BD");
    }

    @Test
    void editarUsuarioStaff_debeActualizarCorreoEnBd() {
        UsuarioStaffEntity staff = staffRepo.save(UsuarioStaffEntity.builder()
                .idCompania(ID_COMPANIA).idSucursal(ID_SUCURSAL).idRol(savedRolId)
                .idPersona(savedPersonaId)
                .correo("before-edit-" + UUID.randomUUID().toString().substring(0, 6) + "@test.com")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .activo(true)
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedStaffId = staff.getId();

        String nuevoCorreo = "after-edit-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";

        webClient.patch().uri("/api/v1/usuarios/" + savedStaffId)
                .header("Authorization", staffBearerWithRol(savedRolId, "usuarios:editar"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("correo", nuevoCorreo))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .consumeWith(result -> {
                    JsonNode body = result.getResponseBody();
                    assertNotNull(body);
                    assertEquals(nuevoCorreo, body.get("correo").asText());
                });

        UsuarioStaffEntity inDb = staffRepo.findByCorreoAndIdCompania(nuevoCorreo, ID_COMPANIA).block();
        assertNotNull(inDb, "El usuario debe existir con el nuevo correo");
        assertEquals(savedStaffId, inDb.getId());
    }

    @Test
    void verPermisosUsuarioStaff_debeRetornarRolConPermisos() {
        UsuarioStaffEntity staff = staffRepo.save(UsuarioStaffEntity.builder()
                .idCompania(ID_COMPANIA).idSucursal(ID_SUCURSAL).idRol(savedRolId)
                .idPersona(savedPersonaId)
                .correo("perms-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .activo(true)
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedStaffId = staff.getId();

        webClient.get().uri("/api/v1/usuarios/" + savedStaffId + "/permisos")
                .header("Authorization", staffBearerWithRol(savedRolId, "usuarios:leer"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .consumeWith(result -> {
                    JsonNode body = result.getResponseBody();
                    assertNotNull(body);
                    assertNotNull(body.get("usuario"), "La respuesta debe incluir el campo 'usuario'");
                    assertNotNull(body.get("rol"), "La respuesta debe incluir el campo 'rol'");
                    assertNotNull(body.get("permisos"), "La respuesta debe incluir el campo 'permisos'");
                    assertTrue(body.get("permisos").isArray(), "'permisos' debe ser un arreglo");
                });
    }

    @Test
    void listarUsuariosStaff_debeRetornarUsuariosDeCompania() {
        UsuarioStaffEntity staff = staffRepo.save(UsuarioStaffEntity.builder()
                .idCompania(ID_COMPANIA).idSucursal(ID_SUCURSAL).idRol(savedRolId)
                .idPersona(savedPersonaId)
                .correo("list-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .activo(true)
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedStaffId = staff.getId();

        webClient.get().uri("/api/v1/usuarios")
                .header("Authorization", staffBearerWithRol(savedRolId, "usuarios:leer"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .consumeWith(result -> {
                    JsonNode body = result.getResponseBody();
                    assertNotNull(body);
                    assertTrue(body.isArray(), "La respuesta debe ser un arreglo");
                    assertTrue(body.size() >= 1, "Debe haber al menos un usuario");
                });
    }
}
