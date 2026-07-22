package com.gymadmin.core.domain.port.in;

import com.gymadmin.core.domain.model.Cliente;
import com.gymadmin.core.domain.model.ClienteDetalle;
import com.gymadmin.core.domain.model.ClienteListItem;
import com.gymadmin.core.domain.model.Membresia;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ClienteUseCase {

    Flux<Cliente> listar(Long idCompania, String estado, String buscar, int page, int limit);

    Mono<Long> contarTotal(Long idCompania, String estado, String buscar);

    Flux<ClienteListItem> listarItems(Long idCompania, String estado, String buscar, int page, int limit, Boolean sinMembresia);

    Mono<Long> contarListItems(Long idCompania, String estado, String buscar, Boolean sinMembresia);

    Mono<ClienteDetalle> buscarDetalle(Long id, Long idCompania);

    Mono<Cliente> buscarPorId(Long id, Long idCompania);

    Mono<BusquedaCiResult> buscarPorCi(String ci, Long idCompania);

    Mono<Cliente> registrar(Long idCompania, RegistrarClienteCommand command);

    Mono<Cliente> registrarDesdeApp(Long idPersona, Long idCompania, Long idSucursal);

    Mono<Cliente> actualizar(Long id, Long idCompania, ActualizarClienteCommand command);

    Flux<Cliente> listarPorPersona(Long idPersona);

    Mono<Cliente> registrarDesdePlataforma(Long idCompania, RegistrarClienteCommand command);

    Mono<Cliente> actualizarPorPlataforma(Long id, ActualizarClientePlataformaCommand command);

    Mono<Void> eliminar(Long id);

    record ActualizarClientePlataformaCommand(Long idCompania, String estado) {}

    Mono<MiPerfilResult> miPerfil(Long idPersona, Long idCompania);

    record MiPerfilResult(
        Long idCliente,
        Cliente.Estado estadoCliente,
        MembresiaInfo membresiaActiva,
        CongelamientoInfo congelamientoActivo
    ) {
        public record MembresiaInfo(
            Long id,
            String tipoNombre,
            String modoControl,
            Membresia.Estado estado,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            Integer diasAccesoUsados,
            Integer diasAccesoRestantes
        ) {}
        public record CongelamientoInfo(Long id, LocalDate fechaInicio) {}
    }

    record RegistrarClienteCommand(
        String ci,
        String nombre,
        String telefono,
        String correo,
        java.time.LocalDate fechaNacimiento,
        BigDecimal pesoKg,
        BigDecimal alturaCm,
        String objetivos,
        String lesiones,
        Long idSucursal,
        String sexo,
        // Opt-in de WhatsApp del socio (Fase 6). Sin él, `identidad.personas.acepta_whatsapp`
        // queda en FALSE y el socio NUNCA recibe el aviso de vencimiento de su membresía.
        boolean aceptaWhatsapp
    ) {}

    record ActualizarClienteCommand(
        BigDecimal pesoKg,
        BigDecimal alturaCm,
        String objetivos,
        String lesiones,
        String telefono
    ) {}

    record BusquedaCiResult(
        Long idPersona,
        String ci,
        String nombre,
        boolean esClienteEnEsteGym,
        Long idCliente
    ) {}
}
