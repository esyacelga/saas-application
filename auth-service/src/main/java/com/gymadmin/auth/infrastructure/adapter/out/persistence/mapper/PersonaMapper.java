package com.gymadmin.auth.infrastructure.adapter.out.persistence.mapper;

import com.gymadmin.auth.domain.model.Persona;
import com.gymadmin.auth.domain.validation.CedulaEcuatoriana;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.PersonaEntity;

import java.time.OffsetDateTime;

public final class PersonaMapper {

    private PersonaMapper() {}

    public static Persona toDomain(PersonaEntity e) {
        return Persona.builder()
                .id(e.getId()).ci(e.getCi()).ciValidada(e.getCiValidada()).nombre(e.getNombre())
                .telefono(e.getTelefono()).correo(e.getCorreo())
                .fotoUrl(e.getFotoUrl()).sexo(e.getSexo()).fechaNacimiento(e.getFechaNacimiento())
                .aceptaWhatsapp(e.getAceptaWhatsapp()).fechaConsentimientoWa(e.getFechaConsentimientoWa())
                .creacionFecha(e.getCreacionFecha()).creacionUsuario(e.getCreacionUsuario())
                .modificaFecha(e.getModificaFecha()).modificaUsuario(e.getModificaUsuario())
                .build();
    }

    public static PersonaEntity toEntity(Persona d) {
        OffsetDateTime now = OffsetDateTime.now();
        boolean isNew = d.getId() == null;
        // Al crear la persona, el flag lo calcula el servidor con el algoritmo del dígito
        // verificador ecuatoriano — TRUE solo si `ci` es una cédula EC válida; FALSE para
        // pasaportes, RUC, documentos extranjeros o cédulas inválidas. Nunca se confía en el
        // cliente. Al actualizar se preserva el valor de dominio (recálculo al editar `ci`
        // pendiente — ver docs/gym-administrator/pendientes/validacion-cedula-persona.md).
        boolean ciValidada = isNew
                ? CedulaEcuatoriana.esValida(d.getCi())
                : Boolean.TRUE.equals(d.getCiValidada());
        return PersonaEntity.builder()
                .id(d.getId()).ci(d.getCi()).ciValidada(ciValidada).nombre(d.getNombre())
                .telefono(d.getTelefono()).correo(d.getCorreo())
                .fotoUrl(d.getFotoUrl()).sexo(d.getSexo()).fechaNacimiento(d.getFechaNacimiento())
                .aceptaWhatsapp(d.getAceptaWhatsapp() != null ? d.getAceptaWhatsapp() : Boolean.FALSE)
                .fechaConsentimientoWa(d.getFechaConsentimientoWa())
                .creacionFecha(isNew ? now : d.getCreacionFecha())
                .creacionUsuario(d.getCreacionUsuario() != null ? d.getCreacionUsuario() : "sistema")
                .modificaFecha(isNew ? null : now)
                .modificaUsuario(d.getModificaUsuario())
                .build();
    }
}
