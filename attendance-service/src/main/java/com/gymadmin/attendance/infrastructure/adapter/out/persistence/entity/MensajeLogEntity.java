package com.gymadmin.attendance.infrastructure.adapter.out.persistence.entity;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("asistencia.mensajes_log")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MensajeLogEntity extends BaseAuditEntity {

    @Id
    private Long id;

    @Column("id_compania")
    private Integer idCompania;

    @Column("id_sucursal")
    private Integer idSucursal;

    @Column("id_cliente")
    private Integer idCliente;

    @Column("id_plantilla")
    private Integer idPlantilla;

    @Column("tipo")
    private String tipo;

    @Column("canal")
    private String canal;

    @Column("contenido")
    private String contenido;

    @Column("estado")
    private String estado;

    @Column("fecha_programada")
    private OffsetDateTime fechaProgramada;

    @Column("fecha_envio")
    private OffsetDateTime fechaEnvio;

    @Column("id_usuario_envio")
    private Integer idUsuarioEnvio;
}
