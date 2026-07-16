package com.gymadmin.platform.domain.model;

import java.time.Instant;

/**
 * Fase 6 (R1) — política GLOBAL (no por tenant) de días de aviso PREVIO de vencimiento,
 * editable por super_admin. Una fila por {@link Destinatario}. Tabla
 * {@code saas.notif_buckets_globales} (story GYM-003).
 *
 * <p>El aviso del día 0 ("vence hoy") <b>no</b> está aquí: es una constante fija del código en cada
 * job. Esta política solo controla el bucket <b>previo</b> ({@code diasPrevio}) y si está
 * {@code activo}.
 */
public class NotifBucketGlobal {

    /** A quién aplica el bucket. El valor persistido es el {@code codigo} ('socio'/'dueno'). */
    public enum Destinatario {
        SOCIO("socio"),
        DUENO("dueno");

        private final String codigo;

        Destinatario(String codigo) {
            this.codigo = codigo;
        }

        public String getCodigo() {
            return codigo;
        }

        public static Destinatario fromCodigo(String codigo) {
            for (Destinatario d : values()) {
                if (d.codigo.equalsIgnoreCase(codigo)) {
                    return d;
                }
            }
            throw new IllegalArgumentException("Destinatario inválido: " + codigo);
        }
    }

    private Destinatario destinatario;
    private int diasPrevio;
    private boolean activo;
    private Long modificadoPor;
    private Instant modificadoAt;

    public NotifBucketGlobal() {
    }

    public NotifBucketGlobal(Destinatario destinatario, int diasPrevio, boolean activo) {
        this.destinatario = destinatario;
        this.diasPrevio = diasPrevio;
        this.activo = activo;
    }

    public Destinatario getDestinatario() {
        return destinatario;
    }

    public void setDestinatario(Destinatario destinatario) {
        this.destinatario = destinatario;
    }

    public int getDiasPrevio() {
        return diasPrevio;
    }

    public void setDiasPrevio(int diasPrevio) {
        this.diasPrevio = diasPrevio;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public Long getModificadoPor() {
        return modificadoPor;
    }

    public void setModificadoPor(Long modificadoPor) {
        this.modificadoPor = modificadoPor;
    }

    public Instant getModificadoAt() {
        return modificadoAt;
    }

    public void setModificadoAt(Instant modificadoAt) {
        this.modificadoAt = modificadoAt;
    }
}
