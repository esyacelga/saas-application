package com.gymadmin.core.infrastructure.exception;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
