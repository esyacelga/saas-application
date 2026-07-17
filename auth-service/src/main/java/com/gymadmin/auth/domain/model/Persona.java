package com.gymadmin.auth.domain.model;

import lombok.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Persona {
    private Integer id;
    private String ci;
    // TRUE cuando `ci` pasó el algoritmo del dígito verificador ecuatoriano (módulo 10).
    // Se calcula en el servidor al crear la persona (ver PersonaMapper); nunca se confía en el cliente.
    private Boolean ciValidada;
    private String nombre;
    private String telefono;
    private String correo;
    private String fotoUrl;
    private String sexo;
    private LocalDate fechaNacimiento;
    // GYM-002: opt-in del socio para avisos por WhatsApp (se captura por su propio
    // endpoint; el alta de persona lo deja en el DEFAULT FALSE de la BD).
    private Boolean aceptaWhatsapp;
    private OffsetDateTime fechaConsentimientoWa;
    private OffsetDateTime creacionFecha;
    @Builder.Default private String creacionUsuario = "sistema";
    private OffsetDateTime modificaFecha;
    private String modificaUsuario;
}
