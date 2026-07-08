package com.gymadmin.auth;

import com.gymadmin.auth.infrastructure.adapter.out.persistence.repository.*;
import com.gymadmin.auth.infrastructure.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
public abstract class IntegrationTestBase {

    protected static final int ID_COMPANIA = 99999;
    protected static final int ID_SUCURSAL = 99999;
    protected static final String TEST_PASSWORD = "password123";

    @LocalServerPort
    protected int port;

    @Autowired
    protected UsuarioPlataformaR2dbcRepository plataformaRepo;
    @Autowired
    protected PersonaR2dbcRepository personaRepo;
    @Autowired
    protected UsuarioStaffR2dbcRepository staffRepo;
    @Autowired
    protected RolR2dbcRepository rolRepo;
    @Autowired
    protected UsuarioAppR2dbcRepository appRepo;
    @Autowired
    protected RefreshTokenR2dbcRepository refreshTokenRepo;
    @Autowired
    protected JwtService jwtService;
    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected WebTestClient webClient;

    @BeforeEach
    void setupWebClient() {
        webClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(15))
                .build();
    }

    protected String platformBearer() {
        return "Bearer " + jwtService.generatePlatformToken(99990, "Test Admin", "super_admin");
    }

    protected String staffBearer(String... permisos) {
        return "Bearer " + jwtService.generateStaffToken(
                99991, ID_COMPANIA, ID_SUCURSAL, 99990, "Test Staff", List.of(permisos));
    }

    protected String staffBearerWithRol(int idRol, String... permisos) {
        return "Bearer " + jwtService.generateStaffToken(
                99991, ID_COMPANIA, ID_SUCURSAL, idRol, "Test Staff", List.of(permisos));
    }
}
