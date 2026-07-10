package com.gymadmin.auth.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.auth.IntegrationTestBase;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.RefreshTokenEntity;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@DisplayName("RefreshTokenR2dbcRepository")
class RefreshTokenR2dbcRepositoryIT extends IntegrationTestBase {

    @Autowired
    private RefreshTokenR2dbcRepository repository;

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("guarda un nuevo refresh token en la base de datos")
        void save_nuevoRefreshToken_seGuardaCorrectamente() {
            String token = UUID.randomUUID().toString();
            RefreshTokenEntity refreshToken = RefreshTokenEntity.builder()
                    .token(token)
                    .tipoUsuario("staff")
                    .idUsuario(1)
                    .idCompania(ID_COMPANIA)
                    .expiraEn(OffsetDateTime.now().plusDays(7))
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(refreshToken)
                    .flatMap(saved -> repository.findById(saved.getId())))
                    .assertNext(retrieved -> {
                        assert retrieved.getToken().equals(token);
                        assert retrieved.getTipoUsuario().equals("staff");
                        assert retrieved.getIdUsuario().equals(1);
                        assert retrieved.getIdCompania().equals(ID_COMPANIA);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findByToken")
    class FindByToken {

        @Test
        @DisplayName("retorna el refresh token cuando existe")
        void findByToken_tokenExiste_retornaMono() {
            String token = UUID.randomUUID().toString();
            RefreshTokenEntity refreshToken = RefreshTokenEntity.builder()
                    .token(token)
                    .tipoUsuario("cliente")
                    .idUsuario(2)
                    .idCompania(ID_COMPANIA)
                    .expiraEn(OffsetDateTime.now().plusDays(7))
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(refreshToken)
                    .then(repository.findByToken(token)))
                    .assertNext(retrieved -> {
                        assert retrieved.getToken().equals(token);
                        assert retrieved.getTipoUsuario().equals("cliente");
                        assert retrieved.getIdUsuario().equals(2);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna Mono vacío cuando el token no existe")
        void findByToken_tokenNoExiste_retornaMonoVacio() {
            String tokenNoExistente = UUID.randomUUID().toString();

            StepVerifier.create(repository.findByToken(tokenNoExistente))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("deleteByIdUsuarioAndTipoUsuario")
    class DeleteByIdUsuarioAndTipoUsuario {

        @Test
        @DisplayName("elimina los refresh tokens de un usuario por tipo")
        void deleteByIdUsuarioAndTipoUsuario_tokenExiste_seElimina() {
            int idUsuario = 3;
            String tipoUsuario = "staff";

            RefreshTokenEntity token1 = RefreshTokenEntity.builder()
                    .token(UUID.randomUUID().toString())
                    .tipoUsuario(tipoUsuario)
                    .idUsuario(idUsuario)
                    .idCompania(ID_COMPANIA)
                    .expiraEn(OffsetDateTime.now().plusDays(7))
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            RefreshTokenEntity token2 = RefreshTokenEntity.builder()
                    .token(UUID.randomUUID().toString())
                    .tipoUsuario(tipoUsuario)
                    .idUsuario(idUsuario)
                    .idCompania(ID_COMPANIA)
                    .expiraEn(OffsetDateTime.now().plusDays(7))
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(token1)
                    .then(repository.save(token2))
                    .then(repository.deleteByIdUsuarioAndTipoUsuario(idUsuario, tipoUsuario))
                    .then(repository.findByToken(token1.getToken())))
                    .verifyComplete();
        }

        @Test
        @DisplayName("no lanza error cuando no hay tokens para eliminar")
        void deleteByIdUsuarioAndTipoUsuario_noExisten_completaCorrectamente() {
            int idUsuarioNoExistente = 88888;
            String tipoUsuario = "cliente";

            StepVerifier.create(repository.deleteByIdUsuarioAndTipoUsuario(idUsuarioNoExistente, tipoUsuario))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("deleteById")
    class DeleteById {

        @Test
        @DisplayName("elimina un refresh token por su id")
        void deleteById_tokenExiste_seElimina() {
            RefreshTokenEntity refreshToken = RefreshTokenEntity.builder()
                    .token(UUID.randomUUID().toString())
                    .tipoUsuario("plataforma")
                    .idUsuario(4)
                    .idCompania(ID_COMPANIA)
                    .expiraEn(OffsetDateTime.now().plusDays(7))
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario("test")
                    .build();

            StepVerifier.create(repository.save(refreshToken)
                    .flatMap(saved -> repository.deleteById(saved.getId())
                            .then(repository.findById(saved.getId()))))
                    .verifyComplete();
        }
    }
}
