package com.gymadmin.auth.domain.model;

import lombok.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Persona {
    private Integer id;
    private String ci;
    private String nombre;
    private String telefono;
    private String correo;
    private String fotoUrl;
    private String sexo;
    private LocalDate fechaNacimiento;
    private OffsetDateTime creacionFecha;
    @Builder.Default private String creacionUsuario = "sistema";
    private OffsetDateTime modificaFecha;
    private String modificaUsuario;
}
