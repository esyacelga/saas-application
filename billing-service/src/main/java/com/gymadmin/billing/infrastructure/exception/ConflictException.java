package com.gymadmin.billing.infrastructure.exception;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
