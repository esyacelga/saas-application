package com.gymadmin.attendance.infrastructure.exception;

public class GoneException extends RuntimeException {
    public GoneException(String message) {
        super(message);
    }
}
