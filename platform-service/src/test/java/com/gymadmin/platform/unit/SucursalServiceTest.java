package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.QrTokenService;
import com.gymadmin.platform.application.service.SucursalService;
import com.gymadmin.platform.domain.model.Sucursal;
import com.gymadmin.platform.domain.port.in.SucursalUseCase.ActualizarSucursalCommand;
import com.gymadmin.platform.domain.port.in.SucursalUseCase.CrearSucursalCommand;
import com.gymadmin.platform.domain.port.out.SucursalRepository;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SucursalService — gestión de sucursales y tokens QR")
class SucursalServiceTest {

    @Mock
    private SucursalRepository sucursalRepository;

    @Mock
    private QrTokenService qrTokenService;

    @InjectMocks
    private SucursalService service;

    private Sucursal buildSucursal(Long id, Long idCompania, String nombre) {
        Sucursal s = new Sucursal();
        s.setId(id);
        s.setIdCompania(idCompania);
        s.setNombre(nombre);
        s.setDireccion("Av. Principal 123");
        s.setEsPrincipal(false);
        s.setActivo(true);
        s.setQrToken("token-existente-abc");
        s.setQrTokenExpira(LocalDateTime.now().plusYears(1));
        return s;
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("listarSucursales")
    class ListarSucursales {

        @Test
        @DisplayName("retorna todas las sucursales de la compañía")
        void retornaSucursalesDeLaCompania() {
            Sucursal s1 = buildSucursal(1L, 10L, "Casa Matriz");
            Sucursal s2 = buildSucursal(2L, 10L, "Sucursal Norte");
            when(sucursalRepository.findByIdCompania(10L)).thenReturn(Flux.just(s1, s2));

            StepVerifier.create(service.listarSucursales(10L))
                    .assertNext(s -> assertThat(s.getNombre()).isEqualTo("Casa Matriz"))
                    .assertNext(s -> assertThat(s.getNombre()).isEqualTo("Sucursal Norte"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna flux vacío cuando la compañía no tiene sucursales")
        void retornaFluxVacioCuandoNoHaySucursales() {
            when(sucursalRepository.findByIdCompania(99L)).thenReturn(Flux.empty());

            StepVerifier.create(service.listarSucursales(99L))
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("crearSucursal")
    class CrearSucursal {

        @Test
        @DisplayName("crea sucursal con token QR generado y activo=true")
        void creaSucursalConTokenQrGenerado() {
            Long idCompania = 5L;
            CrearSucursalCommand command = new CrearSucursalCommand("Nueva Sucursal", "Calle 456", true);
            JwtPrincipal principal = mock(JwtPrincipal.class);

            String tokenGenerado = "abcdef1234567890abcdef1234567890";
            when(qrTokenService.generateToken()).thenReturn(tokenGenerado);

            Sucursal sucursalGuardada = buildSucursal(7L, idCompania, "Nueva Sucursal");
            sucursalGuardada.setQrToken(tokenGenerado);
            when(sucursalRepository.save(any(Sucursal.class))).thenReturn(Mono.just(sucursalGuardada));

            StepVerifier.create(service.crearSucursal(idCompania, command, principal))
                    .assertNext(s -> {
                        assertThat(s.getId()).isEqualTo(7L);
                        assertThat(s.getIdCompania()).isEqualTo(idCompania);
                        assertThat(s.getQrToken()).isEqualTo(tokenGenerado);
                        assertThat(s.getActivo()).isTrue();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("crea sucursal con esPrincipal=false cuando el command lo omite")
        void creaSucursalConEsPrincipalFalsePorDefecto() {
            Long idCompania = 5L;
            CrearSucursalCommand command = new CrearSucursalCommand("Sucursal Extra", "Calle 789", null);
            JwtPrincipal principal = mock(JwtPrincipal.class);

            when(qrTokenService.generateToken()).thenReturn("token123");

            Sucursal sucursalGuardada = new Sucursal();
            sucursalGuardada.setId(8L);
            sucursalGuardada.setEsPrincipal(false);
            sucursalGuardada.setActivo(true);
            when(sucursalRepository.save(any(Sucursal.class))).thenReturn(Mono.just(sucursalGuardada));

            StepVerifier.create(service.crearSucursal(idCompania, command, principal))
                    .assertNext(s -> assertThat(s.getEsPrincipal()).isFalse())
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("actualizarSucursal")
    class ActualizarSucursal {

        @Test
        @DisplayName("actualiza nombre y dirección cuando la sucursal existe")
        void actualizaSucursalExitosamente() {
            Sucursal existente = buildSucursal(1L, 10L, "Casa Matriz");
            ActualizarSucursalCommand command = new ActualizarSucursalCommand("Casa Matriz Renovada", "Nueva Av. 999");

            Sucursal actualizada = buildSucursal(1L, 10L, "Casa Matriz Renovada");
            actualizada.setDireccion("Nueva Av. 999");

            when(sucursalRepository.findById(1L)).thenReturn(Mono.just(existente));
            when(sucursalRepository.update(any(Sucursal.class))).thenReturn(Mono.just(actualizada));

            StepVerifier.create(service.actualizarSucursal(1L, command))
                    .assertNext(s -> {
                        assertThat(s.getNombre()).isEqualTo("Casa Matriz Renovada");
                        assertThat(s.getDireccion()).isEqualTo("Nueva Av. 999");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando la sucursal no existe")
        void lanzaNotFoundCuandoSucursalNoExiste() {
            when(sucursalRepository.findById(999L)).thenReturn(Mono.empty());
            ActualizarSucursalCommand command = new ActualizarSucursalCommand("Nombre", "Dir");

            StepVerifier.create(service.actualizarSucursal(999L, command))
                    .expectError(NotFoundException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("renovarQrToken")
    class RenovarQrToken {

        @Test
        @DisplayName("renueva el token QR y retorna el nuevo token con su expiración")
        void renovaTokenQrExitosamente() {
            Sucursal existente = buildSucursal(1L, 10L, "Casa Matriz");
            String nuevoToken = "nuevo-token-qr-generado-0000001";
            when(sucursalRepository.findById(1L)).thenReturn(Mono.just(existente));
            when(qrTokenService.generateToken()).thenReturn(nuevoToken);

            Sucursal actualizada = buildSucursal(1L, 10L, "Casa Matriz");
            actualizada.setQrToken(nuevoToken);
            when(sucursalRepository.update(any(Sucursal.class))).thenReturn(Mono.just(actualizada));

            StepVerifier.create(service.renovarQrToken(1L, 48))
                    .assertNext(result -> {
                        assertThat(result.qrToken()).isEqualTo(nuevoToken);
                        assertThat(result.qrTokenExpira()).isNotNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna expiración null cuando expiresInHours es null")
        void renovaTokenQrSinExpiracion() {
            Sucursal existente = buildSucursal(1L, 10L, "Casa Matriz");
            when(sucursalRepository.findById(1L)).thenReturn(Mono.just(existente));
            when(qrTokenService.generateToken()).thenReturn("nuevo-token");
            when(sucursalRepository.update(any(Sucursal.class))).thenReturn(Mono.just(existente));

            StepVerifier.create(service.renovarQrToken(1L, null))
                    .assertNext(result -> {
                        assertThat(result.qrToken()).isEqualTo("nuevo-token");
                        assertThat(result.qrTokenExpira()).isNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando la sucursal no existe")
        void lanzaNotFoundCuandoSucursalNoExiste() {
            when(sucursalRepository.findById(777L)).thenReturn(Mono.empty());

            StepVerifier.create(service.renovarQrToken(777L, 24))
                    .expectError(NotFoundException.class)
                    .verify();
        }
    }
}
