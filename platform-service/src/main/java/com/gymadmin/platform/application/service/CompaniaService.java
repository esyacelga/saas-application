package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.model.*;
import com.gymadmin.platform.domain.port.in.CompaniaUseCase;
import com.gymadmin.platform.domain.port.out.*;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import com.gymadmin.platform.infrastructure.exception.ConflictException;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CompaniaService implements CompaniaUseCase {

    private final CompaniaRepository companiaRepository;
    private final CompaniaPlanRepository companiaPlanRepository;
    private final SucursalRepository sucursalRepository;
    private final ConfigNotifRepository configNotifRepository;
    private final PlanRepository planRepository;
    private final QrTokenService qrTokenService;
    private final RolGymRepository rolGymRepository;
    private final UsuarioGymRepository usuarioGymRepository;
    private final PersonaRepository personaRepository;
    private final PasswordEncoder passwordEncoder;

    public CompaniaService(CompaniaRepository companiaRepository,
                           CompaniaPlanRepository companiaPlanRepository,
                           SucursalRepository sucursalRepository,
                           ConfigNotifRepository configNotifRepository,
                           PlanRepository planRepository,
                           QrTokenService qrTokenService,
                           RolGymRepository rolGymRepository,
                           UsuarioGymRepository usuarioGymRepository,
                           PersonaRepository personaRepository,
                           PasswordEncoder passwordEncoder) {
        this.companiaRepository = companiaRepository;
        this.companiaPlanRepository = companiaPlanRepository;
        this.sucursalRepository = sucursalRepository;
        this.configNotifRepository = configNotifRepository;
        this.planRepository = planRepository;
        this.qrTokenService = qrTokenService;
        this.rolGymRepository = rolGymRepository;
        this.usuarioGymRepository = usuarioGymRepository;
        this.personaRepository = personaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Flux<Compania> listarCompanias(JwtPrincipal jwtContext) {
        if (jwtContext.isAdminCompania() && jwtContext.getIdCompania() != null) {
            return companiaRepository.findById(jwtContext.getIdCompania())
                    .flux();
        }
        return companiaRepository.findAll();
    }

    @Override
    public Mono<Compania> getCompania(Long id) {
        return companiaRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Compania", id)));
    }

    @Override
    public Mono<RegistrarGymResult>
    registrarGym(RegistrarGymCommand command) {
        return companiaRepository.findByRuc(command.ruc())
                .flatMap(existing -> Mono.<RegistrarGymResult>error(
                        new ConflictException("Company with RUC '" + command.ruc() + "' already exists")))
                .switchIfEmpty(Mono.defer(() -> planRepository.findById(command.idPlan())
                        .switchIfEmpty(Mono.error(new NotFoundException("Plan", command.idPlan())))
                        .flatMap(plan -> {
                            Compania compania = new Compania();
                            compania.setNombre(command.nombre());
                            compania.setRuc(command.ruc());
                            compania.setLogoUrl(command.logoUrl());
                            compania.setTelefono(command.telefono());
                            compania.setWhatsapp(command.whatsapp());
                            compania.setCorreo(command.correo());

                            return companiaRepository.save(compania)
                                    .flatMap(savedCompania -> {
                                        CompaniaPlan cp = new CompaniaPlan();
                                        cp.setIdCompania(savedCompania.getId());
                                        cp.setIdPlan(plan.getId());
                                        cp.setFechaInicio(LocalDate.now());
                                        cp.setFechaFin(LocalDate.now().plusMonths(1));
                                        cp.setDiasGracia(7);
                                        cp.setEstado(CompaniaPlan.Estado.ACTIVO);
                                        cp.setTipoCambio(CompaniaPlan.TipoCambio.NUEVO);

                                        return companiaPlanRepository.save(cp)
                                                .flatMap(savedCp -> {
                                                    String token = qrTokenService.generateToken();
                                                    Sucursal sucursal = new Sucursal();
                                                    sucursal.setIdCompania(savedCompania.getId());
                                                    sucursal.setNombre(command.nombreSucursal() != null ? command.nombreSucursal() : "Principal");
                                                    sucursal.setDireccion(command.direccionSucursal());
                                                    sucursal.setEsPrincipal(true);
                                                    sucursal.setActivo(true);
                                                    sucursal.setQrToken(token);
                                                    sucursal.setQrTokenExpira(LocalDateTime.now().plusYears(1));

                                                    return sucursalRepository.save(sucursal)
                                                            .flatMap(savedSucursal -> {
                                                                List<ConfigNotifSuscripcion> defaultConfigs = List.of(
                                                                        new ConfigNotifSuscripcion(savedCompania.getId(), 7, ConfigNotifSuscripcion.Canal.WHATSAPP, true),
                                                                        new ConfigNotifSuscripcion(savedCompania.getId(), 3, ConfigNotifSuscripcion.Canal.WHATSAPP, true),
                                                                        new ConfigNotifSuscripcion(savedCompania.getId(), 1, ConfigNotifSuscripcion.Canal.WHATSAPP, true)
                                                                );
                                                                return configNotifRepository.saveAll(defaultConfigs)
                                                                        .thenReturn(new RegistrarGymResult(
                                                                                savedCompania.getId(),
                                                                                savedCp.getId(),
                                                                                savedSucursal.getId(),
                                                                                token
                                                                        ));
                                                            });
                                                });
                                    });
                        })
                ));
    }

    @Override
    public Mono<RegistrarGymWizardResult> registrarGymWizard(RegistrarGymWizardCommand command) {
        List<UsuarioWizardCommand> adicionales = command.usuariosAdicionales() != null
                ? command.usuariosAdicionales() : List.of();

        return companiaRepository.findByRuc(command.ruc())
                .flatMap(existing -> Mono.<RegistrarGymWizardResult>error(
                        new ConflictException("Company with RUC '" + command.ruc() + "' already exists", "ruc")))
                .switchIfEmpty(Mono.defer(() ->
                        planRepository.findById(command.idPlan())
                                .switchIfEmpty(Mono.error(new ConflictException("Plan not found or unavailable", "idPlan")))
                                .flatMap(plan -> ejecutarRegistroWizard(command, plan, adicionales))
                ));
    }

    private Mono<RegistrarGymWizardResult> ejecutarRegistroWizard(
            RegistrarGymWizardCommand command, Plan plan, List<UsuarioWizardCommand> adicionales) {
        return companiaRepository.save(construirCompania(command))
                .flatMap(savedCompania -> companiaPlanRepository.save(construirCompaniaPlan(savedCompania, plan))
                        .flatMap(savedCp -> guardarSucursalConQr(savedCompania, command)
                                .flatMap(savedSucursal ->
                                        guardarConfiguracionesNotificacion(savedCompania.getId())
                                                .then(crearRolConPermisos(savedCompania.getId(), savedSucursal.getId()))
                                                .flatMap(idRol -> crearTodosLosUsuarios(savedCompania, savedSucursal, idRol, command, adicionales)
                                                        .map(r -> new RegistrarGymWizardResult(
                                                                savedCompania.getId(),
                                                                savedCp.getId(),
                                                                savedSucursal.getId(),
                                                                savedSucursal.getQrToken(),
                                                                r.usuarioPrincipal(),
                                                                r.totalUsuarios()
                                                        ))
                                                )
                                )
                        )
                );
    }

    private Compania construirCompania(RegistrarGymWizardCommand command) {
        Compania compania = new Compania();
        compania.setNombre(command.nombre());
        compania.setRuc(command.ruc());
        compania.setLogoUrl(command.logoUrl());
        compania.setTelefono(command.telefono());
        compania.setWhatsapp(command.whatsapp());
        compania.setCorreo(command.correo());
        return compania;
    }

    private CompaniaPlan construirCompaniaPlan(Compania compania, Plan plan) {
        CompaniaPlan cp = new CompaniaPlan();
        cp.setIdCompania(compania.getId());
        cp.setIdPlan(plan.getId());
        cp.setFechaInicio(LocalDate.now());
        cp.setFechaFin(LocalDate.now().plusMonths(1));
        cp.setDiasGracia(7);
        cp.setEstado(CompaniaPlan.Estado.ACTIVO);
        cp.setTipoCambio(CompaniaPlan.TipoCambio.NUEVO);
        return cp;
    }

    private Mono<Sucursal> guardarSucursalConQr(Compania compania, RegistrarGymWizardCommand command) {
        Sucursal sucursal = new Sucursal();
        sucursal.setIdCompania(compania.getId());
        sucursal.setNombre(command.nombreSucursal());
        sucursal.setDireccion(command.direccionSucursal());
        sucursal.setEsPrincipal(true);
        sucursal.setActivo(true);
        sucursal.setQrToken(qrTokenService.generateToken());
        sucursal.setQrTokenExpira(LocalDateTime.now().plusYears(1));
        return sucursalRepository.save(sucursal);
    }

    private Mono<Void> guardarConfiguracionesNotificacion(Long idCompania) {
        List<ConfigNotifSuscripcion> configs = List.of(
                new ConfigNotifSuscripcion(idCompania, 7, ConfigNotifSuscripcion.Canal.WHATSAPP, true),
                new ConfigNotifSuscripcion(idCompania, 3, ConfigNotifSuscripcion.Canal.WHATSAPP, true),
                new ConfigNotifSuscripcion(idCompania, 1, ConfigNotifSuscripcion.Canal.WHATSAPP, true)
        );
        return configNotifRepository.saveAll(configs).then();
    }

    private Mono<Long> crearRolConPermisos(Long idCompania, Long idSucursal) {
        return rolGymRepository.crearSuperAdmin(idCompania, idSucursal)
                .flatMap(idRol -> rolGymRepository.crearPermisosYAsignar(idRol, idCompania, idSucursal)
                        .thenReturn(idRol));
    }

    private Mono<UsuariosWizardResult> crearTodosLosUsuarios(
            Compania compania, Sucursal sucursal, Long idRol,
            RegistrarGymWizardCommand command, List<UsuarioWizardCommand> adicionales) {

        UsuarioWizardCommand principal = command.usuarioPrincipal();

        return personaRepository.resolverIdPersona(
                        principal.idPersona(), principal.ci(),
                        principal.nombre(), principal.correo(), principal.telefono()
                )
                .flatMap(idPersona -> usuarioGymRepository.crearUsuario(
                        compania.getId(), sucursal.getId(), idRol,
                        idPersona, principal.correo(),
                        passwordEncoder.encode(principal.password())
                ))
                .flatMap(usuarioPrincipal ->
                        Flux.fromIterable(adicionales)
                                .flatMap(u -> personaRepository.resolverIdPersona(
                                                u.idPersona(), u.ci(), u.nombre(), u.correo(), u.telefono()
                                        )
                                        .flatMap(idP -> usuarioGymRepository.crearUsuario(
                                                compania.getId(), sucursal.getId(), idRol,
                                                idP, u.correo(),
                                                passwordEncoder.encode(u.password())
                                        )))
                                .collectList()
                                .map(adicionalCreados -> new UsuariosWizardResult(
                                        new UsuarioCreadoResult(
                                                usuarioPrincipal.id(),
                                                usuarioPrincipal.idPersona(),
                                                usuarioPrincipal.correo()
                                        ),
                                        1 + adicionalCreados.size()
                                ))
                );
    }

    private record UsuariosWizardResult(UsuarioCreadoResult usuarioPrincipal, int totalUsuarios) {}

    @Override
    public Mono<Compania> actualizarCompania(Long id, ActualizarCompaniaCommand command, JwtPrincipal jwtContext) {
        return companiaRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Compania", id)))
                .flatMap(compania -> {
                    if (command.nombre() != null) compania.setNombre(command.nombre());
                    if (command.logoUrl() != null) compania.setLogoUrl(command.logoUrl());
                    if (command.telefono() != null) compania.setTelefono(command.telefono());
                    if (command.whatsapp() != null) compania.setWhatsapp(command.whatsapp());
                    if (command.correo() != null) compania.setCorreo(command.correo());
                    return companiaRepository.update(compania);
                });
    }

    @Override
    public Mono<Void> suspenderCompania(Long id, String motivo) {
        return companiaRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Compania", id)))
                .then(companiaPlanRepository.findActivoByIdCompania(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Active subscription for company", id))))
                .flatMap(cp -> companiaPlanRepository.updateEstadoById(
                        cp.getId(), CompaniaPlan.Estado.SUSPENDIDO.name().toLowerCase(), motivo))
                .then(companiaRepository.findById(id)
                        .flatMap(compania -> {
                            compania.setActivo(false);
                            return companiaRepository.update(compania);
                        }))
                .then();
    }
}
