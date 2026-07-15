package com.gymadmin.platform.domain.port.in;

import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface CompaniaUseCase {

    Flux<Compania> listarCompanias(JwtPrincipal jwtContext);

    Mono<Compania> getCompania(Long id);

    Mono<RegistrarGymResult> registrarGym(RegistrarGymCommand command);

    Mono<Compania> actualizarCompania(Long id, ActualizarCompaniaCommand command, JwtPrincipal jwtContext);

    Mono<Void> suspenderCompania(Long id, String motivo);

    Mono<RegistrarGymWizardResult> registrarGymWizard(RegistrarGymWizardCommand command);

    // Verificación de disponibilidad de correo para el registro público (onBlur).
    // true = el correo ya está en uso por algún usuario.
    Mono<Boolean> correoEnUso(String correo);

    record UsuarioWizardCommand(Long idPersona, String ci, String nombre, String telefono, String correo, String password) {}

    record RegistrarGymWizardCommand(
            String nombre,
            String ruc,
            String logoUrl,
            String telefono,
            String whatsapp,
            String correo,
            Long idPlan,
            String nombreSucursal,
            String direccionSucursal,
            UsuarioWizardCommand usuarioPrincipal,
            List<UsuarioWizardCommand> usuariosAdicionales
    ) {}

    record UsuarioCreadoResult(Long id, Long idPersona, String correo) {}

    record RegistrarGymWizardResult(
            Long idCompania,
            Long idCompaniaPlan,
            Long idSucursal,
            String qrToken,
            UsuarioCreadoResult usuarioPrincipal,
            int usuariosCreados
    ) {}

    record RegistrarGymCommand(
            String nombre,
            String ruc,
            String logoUrl,
            String telefono,
            String whatsapp,
            String correo,
            Long idPlan,
            String nombreSucursal,
            String direccionSucursal
    ) {}

    record ActualizarCompaniaCommand(
            String nombre,
            String logoUrl,
            String telefono,
            String whatsapp,
            String correo
    ) {}

    record RegistrarGymResult(
            Long idCompania,
            Long idCompaniaPlan,
            Long idSucursal,
            String qrToken
    ) {}
}
