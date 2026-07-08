package com.gymadmin.platform.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.platform.domain.port.out.RolGymRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.PermisoEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.RolEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.PermisoR2dbcRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.RolPermisoR2dbcRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.RolR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class RolGymPersistenceAdapter implements RolGymRepository {

    private final RolR2dbcRepository rolRepo;
    private final PermisoR2dbcRepository permisoRepo;
    private final RolPermisoR2dbcRepository rolPermisoRepo;

    public RolGymPersistenceAdapter(RolR2dbcRepository rolRepo,
                                    PermisoR2dbcRepository permisoRepo,
                                    RolPermisoR2dbcRepository rolPermisoRepo) {
        this.rolRepo = rolRepo;
        this.permisoRepo = permisoRepo;
        this.rolPermisoRepo = rolPermisoRepo;
    }

    @Override
    public Mono<Long> crearSuperAdmin(Long idCompania, Long idSucursal) {
        return rolRepo.findByIdCompaniaAndNombreAndEliminadoFalse(idCompania, "SUPER_ADMIN")
                .map(RolEntity::getId)
                .switchIfEmpty(Mono.defer(() -> {
                    RolEntity rol = new RolEntity();
                    rol.setIdCompania(idCompania);
                    rol.setIdSucursal(idSucursal);
                    rol.setNombre("SUPER_ADMIN");
                    rol.setDescripcion("Rol con acceso total al sistema");
                    return rolRepo.save(rol).map(RolEntity::getId);
                }));
    }

    @Override
    public Mono<Void> crearPermisosYAsignar(Long idRol, Long idCompania, Long idSucursal) {
        return permisoRepo.findAllByIdCompaniaAndEliminadoFalse(idCompania)
                .collectList()
                .flatMap(existentes -> {
                    if (!existentes.isEmpty()) {
                        return rolPermisoRepo.asignarTodosLosPermisos(idRol, idCompania);
                    }
                    List<PermisoEntity> permisos = buildPermisosBase(idCompania, idSucursal);
                    return Flux.fromIterable(permisos)
                            .flatMap(permisoRepo::save)
                            .then(rolPermisoRepo.asignarTodosLosPermisos(idRol, idCompania));
                });
    }

    private List<PermisoEntity> buildPermisosBase(Long idCompania, Long idSucursal) {
        record P(String nombre, String descripcion, String modulo) {
        }
        List<P> defs = List.of(
                new P("roles:leer", "Opción solo de lectura", "seguridad"),
                new P("roles:crear", "Permite crear un nuevo rol", "seguridad"),
                new P("roles:editar", "Permite editar roles", "seguridad"),
                new P("roles:eliminar", "Eliminar roles", "seguridad"),
                new P("usuarios:leer", "Permiso solo de lectura", "seguridad"),
                new P("usuarios:crear", "Permite crear usuarios", "seguridad"),
                new P("usuarios:editar", "Crear, editar y eliminar usuarios", "seguridad"),
                new P("personas:leer", "Lee personas", "seguridad"),
                new P("personas:crear", "Crea personas", "seguridad"),
                new P("personas:editar", "Edita personas", "seguridad"),
                new P("permisos:leer", "Leer permisos", "seguridad"),
                new P("bitacora:leer", "Bitacora leer", "seguridad"),

                new P("clientes:leer", "Registrar y editar clientes", "core"),
                new P("membresias:leer", "Crear y editar tipos de membresía", "core"),
                new P("GESTIONAR_MEMBRESIAS", "Asignar y gestionar membresías", "core"),
                new P("GESTIONAR_CONGELAMIENTOS", "Registrar congelamientos", "core"),
                new P("REGISTRAR_ASISTENCIA", "Registrar entradas y salidas", "asistencia"),
                new P("VER_ASISTENCIAS", "Consultar historial de asistencias", "asistencia"),
                new P("GESTIONAR_PLANTILLAS_MENSAJES", "Crear y editar plantillas de mensajes", "asistencia"),
                new P("GESTIONAR_CATEGORIAS_INGRESO", "Administrar categorías de ingreso", "finanzas"),
                new P("GESTIONAR_INGRESOS", "Registrar y editar ingresos", "finanzas"),
                new P("GESTIONAR_CATEGORIAS_EGRESO", "Administrar categorías de egreso", "finanzas"),
                new P("GESTIONAR_EGRESOS", "Registrar y editar egresos", "finanzas"),
                new P("VER_REPORTES_FINANZAS", "Consultar reportes financieros", "finanzas"),
                new P("GESTIONAR_PROMOCIONES", "Crear y editar promociones", "marketing"),
                new P("GESTIONAR_PRODUCTOS", "Registrar y editar productos", "inventario"),
                new P("GESTIONAR_INVENTARIO", "Controlar stock y ajustes", "inventario"),
                new P("GESTIONAR_VENTAS", "Registrar ventas de productos", "inventario"),
                new P("GESTIONAR_GYM_CONFIG", "Editar configuración del gimnasio", "config"),
                new P("GESTIONAR_METODOS_PAGO", "Activar y configurar métodos de pago", "config")
        );

        return defs.stream().map(p -> {
            PermisoEntity e = new PermisoEntity();
            e.setIdCompania(idCompania);
            e.setIdSucursal(idSucursal);
            e.setNombre(p.nombre());
            e.setDescripcion(p.descripcion());
            e.setModulo(p.modulo());
            return e;
        }).toList();
    }
}
