package com.gymadmin.auth.infrastructure.adapter.out.persistence.mapper;

import com.gymadmin.auth.domain.model.Persona;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.PersonaEntity;

import java.time.OffsetDateTime;

public final class PersonaMapper {

    private PersonaMapper() {}

    public static Persona toDomain(PersonaEntity e) {
        return Persona.builder()
                .id(e.getId()).ci(e.getCi()).nombre(e.getNombre())
                .telefono(e.getTelefono()).correo(e.getCorreo())
                .fotoUrl(e.getFotoUrl()).sexo(e.getSexo()).fechaNacimiento(e.getFechaNacimiento())
                .creacionFecha(e.getCreacionFecha()).creacionUsuario(e.getCreacionUsuario())
                .modificaFecha(e.getModificaFecha()).modificaUsuario(e.getModificaUsuario())
                .build();
    }

    public static PersonaEntity toEntity(Persona d) {
        OffsetDateTime now = OffsetDateTime.now();
        boolean isNew = d.getId() == null;
        return PersonaEntity.builder()
                .id(d.getId()).ci(d.getCi()).nombre(d.getNombre())
                .telefono(d.getTelefono()).correo(d.getCorreo())
                .fotoUrl(d.getFotoUrl()).sexo(d.getSexo()).fechaNacimiento(d.getFechaNacimiento())
                .creacionFecha(isNew ? now : d.getCreacionFecha())
                .creacionUsuario(d.getCreacionUsuario() != null ? d.getCreacionUsuario() : "sistema")
                .modificaFecha(isNew ? null : now)
                .modificaUsuario(d.getModificaUsuario())
                .build();
    }
}
