package com.gymadmin.core.application.service;

import com.gymadmin.core.domain.model.Cliente;
import com.gymadmin.core.domain.model.ClienteDetalle;
import com.gymadmin.core.domain.model.ClienteListItem;
import com.gymadmin.core.domain.model.Membresia;
import com.gymadmin.core.domain.model.TipoMembresia;
import com.gymadmin.core.domain.port.in.ClienteUseCase;
import com.gymadmin.core.domain.port.out.ClienteRepository;
import com.gymadmin.core.domain.port.out.CongelamientoRepository;
import com.gymadmin.core.domain.port.out.MembresiaRepository;
import com.gymadmin.core.domain.port.out.PersonaRepository;
import com.gymadmin.core.domain.port.out.TipoMembresiaRepository;
import com.gymadmin.core.infrastructure.adapter.out.http.PlatformServiceClient;
import com.gymadmin.core.infrastructure.exception.ConflictException;
import com.gymadmin.core.infrastructure.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Service
public class ClienteService implements ClienteUseCase {

    private static final String AVATAR_HOMBRE = "https://res.cloudinary.com/dmlco6mio/image/upload/v1781449426/hombre_avatar_ej8fbn.png";
    private static final String AVATAR_MUJER  = "https://res.cloudinary.com/dmlco6mio/image/upload/v1781449480/mujer_avatar_02_pkgg19.png";

    private final ClienteRepository clienteRepository;
    private final PersonaRepository personaRepository;
    private final MembresiaRepository membresiaRepository;
    private final CongelamientoRepository congelamientoRepository;
    private final TipoMembresiaRepository tipoMembresiaRepository;
    private final PlatformServiceClient platformServiceClient;
    private final String carnetPrefix;

    public ClienteService(ClienteRepository clienteRepository,
                          PersonaRepository personaRepository,
                          MembresiaRepository membresiaRepository,
                          CongelamientoRepository congelamientoRepository,
                          TipoMembresiaRepository tipoMembresiaRepository,
                          PlatformServiceClient platformServiceClient,
                          @Value("${carnet.prefix:GYM}") String carnetPrefix) {
        this.clienteRepository = clienteRepository;
        this.personaRepository = personaRepository;
        this.membresiaRepository = membresiaRepository;
        this.congelamientoRepository = congelamientoRepository;
        this.tipoMembresiaRepository = tipoMembresiaRepository;
        this.platformServiceClient = platformServiceClient;
        this.carnetPrefix = carnetPrefix;
    }

    @Override
    public Flux<Cliente> listar(Long idCompania, String estado, String buscar, int page, int limit) {
        int offset = (page - 1) * limit;
        return clienteRepository.findByIdCompania(idCompania, estado, buscar, offset, limit);
    }

    @Override
    public Mono<Long> contarTotal(Long idCompania, String estado, String buscar) {
        return clienteRepository.countByIdCompania(idCompania, estado, buscar);
    }

    @Override
    public Flux<ClienteListItem> listarItems(Long idCompania, String estado, String buscar, int page, int limit, Boolean sinMembresia) {
        int offset = (page - 1) * limit;
        return clienteRepository.findListItems(idCompania, estado, buscar, offset, limit, sinMembresia);
    }

    @Override
    public Mono<Long> contarListItems(Long idCompania, String estado, String buscar, Boolean sinMembresia) {
        return clienteRepository.countListItems(idCompania, estado, buscar, sinMembresia);
    }

    @Override
    public Mono<ClienteDetalle> buscarDetalle(Long id, Long idCompania) {
        return clienteRepository.findDetalleById(id, idCompania)
                .switchIfEmpty(Mono.error(new NotFoundException("Cliente", id)));
    }

    @Override
    public Mono<Cliente> buscarPorId(Long id, Long idCompania) {
        return clienteRepository.findByIdAndIdCompania(id, idCompania)
                .switchIfEmpty(Mono.error(new NotFoundException("Cliente", id)));
    }

    @Override
    public Mono<BusquedaCiResult> buscarPorCi(String ci, Long idCompania) {
        return personaRepository.findByCi(ci)
                .switchIfEmpty(Mono.error(new NotFoundException("Persona con CI " + ci + " no encontrada")))
                .flatMap(persona -> clienteRepository.findByIdPersonaAndIdCompania(persona.id(), idCompania)
                        .map(cliente -> new BusquedaCiResult(persona.id(), persona.ci(), persona.nombre(), true, cliente.getId()))
                        .switchIfEmpty(Mono.just(new BusquedaCiResult(persona.id(), persona.ci(), persona.nombre(), false, null)))
                );
    }

    @Override
    public Mono<Cliente> registrar(Long idCompania, RegistrarClienteCommand command) {
        // REQ-SAAS-001 (RN-06): antes de crear el cliente, verificar cuota de
        // clientes_activos contra platform-service. Si el tenant llegó al máximo,
        // el cliente HTTP emite LimiteAlcanzadoException (403).
        return platformServiceClient.requireLimite(idCompania, "clientes_activos")
                .then(personaRepository.findByCi(command.ci())
                .flatMap(persona -> clienteRepository.findByIdPersonaAndIdCompania(persona.id(), idCompania)
                        .flatMap(existing -> Mono.<PersonaRepository.PersonaResult>error(
                                new ConflictException("La persona ya es cliente de este gym")))
                        // La persona ya existía (se afilia a otro gym): el opt-in va por UPDATE,
                        // no por INSERT. `otorgarConsentimientoWa` preserva la fecha si ya había
                        // consentido y nunca revoca — la recepción no puede dar de baja un opt-in.
                        .switchIfEmpty(command.aceptaWhatsapp()
                                ? personaRepository.otorgarConsentimientoWa(persona.id()).thenReturn(persona)
                                : Mono.just(persona))
                )
                // Mono.defer: sin él, `create(...)` se evalúa al construir la cadena y se
                // invoca aunque la persona ya exista (el Mono nunca se suscribe, pero la
                // llamada al repositorio ya ocurrió).
                .switchIfEmpty(Mono.defer(() -> personaRepository.create(new PersonaRepository.CreatePersonaCommand(
                        command.ci(), command.nombre(), command.telefono(),
                        command.correo(), command.fechaNacimiento(),
                        resolverAvatar(command.sexo()), command.aceptaWhatsapp()
                ))))
                .flatMap(persona -> {
                    Cliente cliente = new Cliente();
                    cliente.setIdPersona(persona.id());
                    cliente.setIdCompania(idCompania);
                    cliente.setIdSucursal(command.idSucursal());
                    cliente.setPesoKg(command.pesoKg());
                    cliente.setAlturaCm(command.alturaCm());
                    cliente.setObjetivos(command.objetivos());
                    cliente.setLesiones(command.lesiones());
                    // Un cliente recién creado aún no tiene membresía → 'vencido'.
                    // La venta de una membresía lo pasa a 'activo' (MembresiaService).
                    cliente.setEstado(Cliente.Estado.vencido);
                    cliente.setFechaIngreso(LocalDate.now());
                    if (command.sexo() != null) cliente.setSexo(Cliente.Sexo.valueOf(command.sexo()));
                    return clienteRepository.save(cliente)
                            .flatMap(saved -> {
                                String carnet = carnetPrefix + saved.getIdCompania() + "-" + String.format("%05d", saved.getId());
                                saved.setCodigoCarnet(carnet);
                                return clienteRepository.save(saved);
                            });
                }));
    }

    @Override
    public Mono<Cliente> registrarDesdeApp(Long idPersona, Long idCompania, Long idSucursal) {
        return clienteRepository.findByIdPersonaAndIdCompania(idPersona, idCompania)
                .flatMap(existing -> Mono.<Cliente>error(new ConflictException("La persona ya es cliente de este gym")))
                .switchIfEmpty(Mono.defer(() -> {
                    Cliente cliente = new Cliente();
                    cliente.setIdPersona(idPersona);
                    cliente.setIdCompania(idCompania);
                    cliente.setIdSucursal(idSucursal);
                    // Un cliente recién creado aún no tiene membresía → 'vencido'.
                    // La venta de una membresía lo pasa a 'activo' (MembresiaService).
                    cliente.setEstado(Cliente.Estado.vencido);
                    cliente.setFechaIngreso(LocalDate.now());
                    return clienteRepository.save(cliente)
                            .flatMap(saved -> {
                                String carnet = carnetPrefix + saved.getIdCompania() + "-" + String.format("%05d", saved.getId());
                                saved.setCodigoCarnet(carnet);
                                return clienteRepository.save(saved);
                            });
                }));
    }

    @Override
    public Mono<MiPerfilResult> miPerfil(Long idPersona, Long idCompania) {
        return clienteRepository.findByIdPersonaAndIdCompania(idPersona, idCompania)
                .switchIfEmpty(Mono.error(new NotFoundException("Cliente en esta compañía", idPersona)))
                .flatMap(cliente ->
                        membresiaRepository.findActivaByIdClienteAndIdCompania(cliente.getId(), idCompania)
                                .flatMap(mem -> tipoMembresiaRepository.findById(mem.getIdTipoMembresia())
                                        .flatMap(tipo -> {
                                            boolean esAccesos = tipo.getModoControl() == TipoMembresia.ModoControl.accesos;
                                            Mono<Long> conteoMono = esAccesos
                                                    ? membresiaRepository.countAsistenciasByIdMembresia(mem.getId())
                                                    : Mono.just(0L);
                                            Mono<java.util.Optional<MiPerfilResult.CongelamientoInfo>> congMono =
                                                    Membresia.Estado.congelada.equals(mem.getEstado())
                                                            ? congelamientoRepository.findActivoByIdMembresia(mem.getId())
                                                            .map(c -> new MiPerfilResult.CongelamientoInfo(c.getId(), c.getFechaInicio()))
                                                            .singleOptional()
                                                            : Mono.just(java.util.Optional.empty());
                                            return Mono.zip(conteoMono, congMono)
                                                    .map(t -> {
                                                        int usados = t.getT1().intValue();
                                                        MiPerfilResult.CongelamientoInfo congInfo =
                                                                t.getT2().isPresent() ? t.getT2().get() : null;
                                                        MiPerfilResult.MembresiaInfo memInfo = new MiPerfilResult.MembresiaInfo(
                                                                mem.getId(), tipo.getNombre(), tipo.getModoControl().name(),
                                                                mem.getEstado(), mem.getFechaInicio(), mem.getFechaFin(),
                                                                esAccesos ? usados : null,
                                                                esAccesos && mem.getDiasAccesoTotal() != null
                                                                        ? mem.getDiasAccesoTotal() - usados : null
                                                        );
                                                        return new MiPerfilResult(cliente.getId(), cliente.getEstado(), memInfo, congInfo);
                                                    });
                                        })
                                )
                                .switchIfEmpty(Mono.just(new MiPerfilResult(cliente.getId(), cliente.getEstado(), null, null)))
                );
    }

    private String resolverAvatar(String sexo) {
        if ("M".equals(sexo)) return AVATAR_HOMBRE;
        if ("F".equals(sexo)) return AVATAR_MUJER;
        return null;
    }

    @Override
    public Mono<Cliente> actualizar(Long id, Long idCompania, ActualizarClienteCommand command) {
        return clienteRepository.findByIdAndIdCompania(id, idCompania)
                .switchIfEmpty(Mono.error(new NotFoundException("Cliente", id)))
                .flatMap(cliente -> {
                    if (command.pesoKg() != null) cliente.setPesoKg(command.pesoKg());
                    if (command.alturaCm() != null) cliente.setAlturaCm(command.alturaCm());
                    if (command.objetivos() != null) cliente.setObjetivos(command.objetivos());
                    if (command.lesiones() != null) cliente.setLesiones(command.lesiones());
                    return clienteRepository.save(cliente);
                });
    }

    @Override
    public Flux<Cliente> listarPorPersona(Long idPersona) {
        return clienteRepository.findByIdPersona(idPersona);
    }

    @Override
    public Mono<Cliente> registrarDesdePlataforma(Long idCompania, RegistrarClienteCommand command) {
        return registrar(idCompania, command);
    }

    @Override
    public Mono<Cliente> actualizarPorPlataforma(Long id, ActualizarClientePlataformaCommand command) {
        return clienteRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Cliente", id)))
                .flatMap(cliente -> {
                    if (command.idCompania() != null) cliente.setIdCompania(command.idCompania());
                    if (command.estado() != null) cliente.setEstado(Cliente.Estado.valueOf(command.estado()));
                    return clienteRepository.save(cliente);
                });
    }

    @Override
    public Mono<Void> eliminar(Long id) {
        return clienteRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Cliente", id)))
                .flatMap(c -> clienteRepository.deleteById(c.getId()));
    }
}
