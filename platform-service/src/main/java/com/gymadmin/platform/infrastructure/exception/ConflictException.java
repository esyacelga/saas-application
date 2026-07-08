package com.gymadmin.platform.infrastructure.exception;

public class ConflictException extends RuntimeException {

    private final String conflicto;

    public ConflictException(String message) {
        super(message);
        this.conflicto = null;
    }

    public ConflictException(String message, String conflicto) {
        super(message);
        this.conflicto = conflicto;
    }

    public String getConflicto() {
        return conflicto;
    }
}
