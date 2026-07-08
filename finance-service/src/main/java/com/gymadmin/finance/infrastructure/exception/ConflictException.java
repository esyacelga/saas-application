package com.gymadmin.finance.infrastructure.exception;

public class ConflictException extends RuntimeException {

    private final String codigo;

    public ConflictException(String codigo, String message) {
        super(message);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
