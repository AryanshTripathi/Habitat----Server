package com.habitat.server.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public abstract class NotFoundException extends  RuntimeException {
    protected NotFoundException(String message) { super("Not Found Exception: " + message); }
}
