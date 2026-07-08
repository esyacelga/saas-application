package com.gymadmin.auth.domain.exception;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
