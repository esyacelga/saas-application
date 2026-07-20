package com.gymadmin.auth.infrastructure.exception;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Traduce una {@link DataIntegrityViolationException} de PostgreSQL a un
 * {@code codigo} + mensaje legible. Lógica originada en auth-service (única en
 * el monorepo) para que los 6 servicios respondan igual ante unique/FK/not-null
 * violations en lugar de dejar escapar un 500 genérico (hallazgo #6 del contrato).
 */
public final class DataIntegrityMapper {

    private static final Pattern COLUMN = Pattern.compile("column \"(\\w+)\"");
    private static final Pattern CONSTRAINT = Pattern.compile("constraint \"([\\w]+)\"");

    private DataIntegrityMapper() {
    }

    /** Resultado del mapeo: el {@link ErrorCode} a emitir y el {@code detail} legible. */
    public record Resolved(ErrorCode code, String detail) {
    }

    public static Resolved resolve(DataIntegrityViolationException ex) {
        String msg = rootMessage(ex);
        if (msg == null) {
            return new Resolved(ErrorCode.CONFLICTO, "Violación de restricción de integridad de datos");
        }
        String lower = msg.toLowerCase();

        if (lower.contains("not-null constraint") || lower.contains("violates not-null")) {
            Matcher m = COLUMN.matcher(msg);
            String detail = m.find()
                    ? "El campo '" + m.group(1) + "' es requerido y no puede ser nulo"
                    : "Un campo obligatorio no fue proporcionado";
            return new Resolved(ErrorCode.CAMPO_REQUERIDO, detail);
        }
        if (lower.contains("unique constraint") || lower.contains("duplicate key")) {
            Matcher m = CONSTRAINT.matcher(msg);
            String detail = m.find()
                    ? "Registro duplicado (restricción: " + m.group(1) + ")"
                    : "Registro duplicado: ya existe un elemento con los datos proporcionados";
            return new Resolved(ErrorCode.DATOS_DUPLICADOS, detail);
        }
        if (lower.contains("foreign key constraint")) {
            Matcher m = CONSTRAINT.matcher(msg);
            String detail = m.find()
                    ? "Referencia inválida: el registro relacionado no existe (restricción: " + m.group(1) + ")"
                    : "Referencia inválida: el registro relacionado no existe";
            return new Resolved(ErrorCode.REFERENCIA_INVALIDA, detail);
        }
        return new Resolved(ErrorCode.CONFLICTO, "Violación de restricción de integridad de datos: " + msg);
    }

    public static String rootMessage(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        return cause != null ? cause.getMessage() : ex.getMessage();
    }
}
