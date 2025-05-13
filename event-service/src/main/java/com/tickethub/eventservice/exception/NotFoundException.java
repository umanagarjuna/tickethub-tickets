package com.tickethub.eventservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Custom exception to indicate that a requested resource was not found.
 * This exception can be handled by a global exception handler to return
 * an appropriate HTTP status code (e.g., 404 Not Found).
 */
@ResponseStatus(HttpStatus.NOT_FOUND) // Optional: Annotate to automatically map to HTTP 404
public class NotFoundException extends RuntimeException {

    /**
     * Constructs a new NotFoundException with the specified detail message.
     *
     * @param message the detail message.
     */
    public NotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new NotFoundException with the specified detail message and cause.
     *
     * @param message the detail message.
     * @param cause   the cause (which is saved for later retrieval by the getCause() method).
     */
    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
