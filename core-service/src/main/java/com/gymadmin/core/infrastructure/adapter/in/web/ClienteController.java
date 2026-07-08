package com.gymadmin.core.infrastructure.adapter.in.web;

import com.gymadmin.core.application.service.AccessControlService;
import com.gymadmin.core.domain.port.in.ClienteUseCase;
import com.gymadmin.core.infrastructure.adapter.in.web.dto.*;
import com.gymadmin.core.infrastructure.config.JwtPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/clientes")
public class ClienteController {

    private final ClienteUseCase clienteUseCase;
    private final AccessControlService accessControl;

    public ClienteController(ClienteUseCase clienteUseCase, AccessControlService accessControl) {
        this.clienteUseCase = clienteUseCase;
        this.accessControl = accessControl;
    }

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String buscar,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(name = "sin_membresia", required = false) Boolean sinMembresia) {
        return extractPrincipal()
                .flatMap(principal -> {
                    Long idCompania = principal.getIdCompania();
                    return clienteUseCase.contarListItems(idCompania, estado, buscar, sinMembresia)
                            .flatMap(total -> clienteUseCase.listarItems(idCompania, estado, buscar, page, limit, sinMembresia)
                                    .map(ClienteListItemResponse::from)
                                    .collectList()
                                    .map(datos -> ResponseEntity.ok(Map.of(
                                            "total", total,
                                            "pagina", page,
                                            "datos", datos
                                    )))
                            );
                });
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> registrar(@Valid @RequestBody RegistrarClienteRequest request) {
        return extractPrincipal()
                .flatMap(principal -> accessControl.requireRecepcionOrAbove(principal, principal.getIdCompania())
                        .then(clienteUseCase.registrar(principal.getIdCompania(),
                                new ClienteUseCase.RegistrarClienteCommand(
                                        request.ci(), request.nombre(), request.telefono(), request.correo(),
                                        request.fechaNacimiento(), request.pesoKg(), request.alturaCm(),
                                        request.objetivos(), request.lesiones(), request.idSucursal(),
                                        request.sexo()
                                )))
                )
                .map(cliente -> ResponseEntity.status(HttpStatus.CREATED).<Map<String, Object>>body(Map.of(
                        "id_cliente", cliente.getId(),
                        "id_persona", cliente.getIdPersona()
                )));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ClienteDetalleResponse>> buscarPorId(@PathVariable Long id) {
        return extractPrincipal()
                .flatMap(principal -> clienteUseCase.buscarDetalle(id, principal.getIdCompania()))
                .map(d -> ResponseEntity.ok(ClienteDetalleResponse.from(d)));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<ClienteResponse>> actualizar(@PathVariable Long id,
                                                             @RequestBody ActualizarClienteRequest request) {
        return extractPrincipal()
                .flatMap(principal -> accessControl.requireRecepcionOrAbove(principal, principal.getIdCompania())
                        .then(clienteUseCase.actualizar(id, principal.getIdCompania(),
                                new ClienteUseCase.ActualizarClienteCommand(
                                        request.pesoKg(), request.alturaCm(),
                                        request.objetivos(), request.lesiones(), request.telefono()
                                )))
                )
                .map(c -> ResponseEntity.ok(ClienteResponse.from(c)));
    }

    @GetMapping("/mi-perfil")
    public Mono<ResponseEntity<ClienteUseCase.MiPerfilResult>> miPerfil() {
        return extractPrincipal()
                .flatMap(principal -> accessControl.requireCliente(principal, principal.getIdCompania())
                        .then(clienteUseCase.miPerfil(principal.getIdPersona(), principal.getIdCompania()))
                )
                .map(ResponseEntity::ok);
    }

    @GetMapping("/my-id")
    public Mono<ResponseEntity<Map<String, Object>>> myId() {
        return extractPrincipal()
                .flatMap(principal -> accessControl.requireCliente(principal, principal.getIdCompania())
                        .then(clienteUseCase.miPerfil(principal.getIdPersona(), principal.getIdCompania()))
                )
                .map(result -> ResponseEntity.ok(Map.<String, Object>of("id_cliente", result.idCliente())));
    }

    @PostMapping("/app")
    public Mono<ResponseEntity<Map<String, Object>>> registrarDesdeApp(
            @RequestBody(required = false) RegistrarDesdeAppRequest request) {
        return extractPrincipal()
                .flatMap(principal -> accessControl.requireCliente(principal, principal.getIdCompania())
                        .then(clienteUseCase.registrarDesdeApp(
                                principal.getIdPersona(),
                                principal.getIdCompania(),
                                request != null ? request.idSucursal() : null
                        ))
                )
                .map(cliente -> ResponseEntity.status(HttpStatus.CREATED).<Map<String, Object>>body(Map.of(
                        "id_cliente", cliente.getId(),
                        "id_persona", cliente.getIdPersona()
                )));
    }

    @GetMapping("/ci/{ci}")
    public Mono<ResponseEntity<Map<String, Object>>> buscarPorCi(@PathVariable String ci) {
        return extractPrincipal()
                .flatMap(principal -> clienteUseCase.buscarPorCi(ci, principal.getIdCompania()))
                .map(result -> ResponseEntity.ok(Map.<String, Object>of(
                        "persona", Map.of("id", result.idPersona(), "ci", result.ci(), "nombre", result.nombre()),
                        "es_cliente_en_este_gym", result.esClienteEnEsteGym(),
                        "id_cliente", result.idCliente() != null ? result.idCliente() : ""
                )));
    }

    @PostMapping("/plataforma")
    public Mono<ResponseEntity<Map<String, Object>>> registrarDesdePlataforma(
            @Valid @RequestBody RegistrarClientePlataformaRequest request) {
        return extractPrincipal()
                .flatMap(principal -> clienteUseCase.registrarDesdePlataforma(
                        request.idCompania(),
                        new ClienteUseCase.RegistrarClienteCommand(
                                request.ci(), request.nombre(), request.telefono(), request.correo(),
                                request.fechaNacimiento(), request.pesoKg(), request.alturaCm(),
                                request.objetivos(), request.lesiones(), request.idSucursal(), request.sexo()
                        )))
                .map(cliente -> ResponseEntity.status(HttpStatus.CREATED).<Map<String, Object>>body(Map.of(
                        "id_cliente", cliente.getId(),
                        "id_persona", cliente.getIdPersona()
                )));
    }

    @GetMapping("/por-persona/{idPersona}")
    public Mono<ResponseEntity<java.util.List<ClienteResponse>>> listarPorPersona(@PathVariable Long idPersona) {
        return extractPrincipal()
                .flatMap(principal -> clienteUseCase.listarPorPersona(idPersona).map(ClienteResponse::from).collectList())
                .map(ResponseEntity::ok);
    }

    @PutMapping("/plataforma/{id}")
    public Mono<ResponseEntity<ClienteResponse>> actualizarPorPlataforma(
            @PathVariable Long id,
            @RequestBody ActualizarClientePlataformaRequest request) {
        return extractPrincipal()
                .flatMap(principal -> clienteUseCase.actualizarPorPlataforma(id,
                        new ClienteUseCase.ActualizarClientePlataformaCommand(request.idCompania(), request.estado())))
                .map(c -> ResponseEntity.ok(ClienteResponse.from(c)));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> eliminar(@PathVariable Long id) {
        return extractPrincipal()
                .flatMap(principal -> clienteUseCase.eliminar(id))
                .then(Mono.just(ResponseEntity.<Void>noContent().build()));
    }

    private Mono<JwtPrincipal> extractPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getPrincipal())
                .cast(JwtPrincipal.class);
    }
}
