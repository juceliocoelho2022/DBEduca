package com.dbeduca.api;

import com.dbeduca.execution.DatabaseExecutionException;
import com.dbeduca.execution.DatabaseUnavailableException;
import com.dbeduca.execution.TableAlreadyExistsException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse illegalArgument(IllegalArgumentException ex) {
        return new ErrorResponse(
            Instant.now(),
            ex.getMessage()
        );
    }

    @ExceptionHandler(TableAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse tableAlreadyExists(TableAlreadyExistsException ex) {
        return new ErrorResponse(
            Instant.now(),
            ex.getMessage()
        );
    }

    @ExceptionHandler(DatabaseUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ErrorResponse databaseUnavailable(DatabaseUnavailableException ex) {
        return new ErrorResponse(
            Instant.now(),
            ex.getMessage()
        );
    }

    @ExceptionHandler(DatabaseExecutionException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ErrorResponse databaseExecution(DatabaseExecutionException ex) {
        return new ErrorResponse(
            Instant.now(),
            ex.getMessage()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse invalidRequest(MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .findFirst()
            .map(
                error ->
                    error.getField()
                    + ": "
                    + error.getDefaultMessage()
            )
            .orElse("Requisição inválida.");

        return new ErrorResponse(
            Instant.now(),
            message
        );
    }

    public record ErrorResponse(
        Instant timestamp,
        String message
    ) {
    }
}
