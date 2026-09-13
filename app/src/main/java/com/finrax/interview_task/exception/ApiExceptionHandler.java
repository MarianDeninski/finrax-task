package com.finrax.interview_task.exception;

import com.finrax.interview_task.dto.ErrorResponse;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.stream.Collectors;

@RestControllerAdvice(basePackages = "com.finrax.interview_task.controller")
@Slf4j
public class ApiExceptionHandler {

    @ExceptionHandler(InsufficientFundsException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInsufficientFunds(InsufficientFundsException e) {
        return ErrorResponse.of("INSUFFICIENT_FUNDS", e.getMessage());
    }

    @ExceptionHandler(WalletNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleWalletNotFound(WalletNotFoundException e) {
        return ErrorResponse.of("WALLET_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(WalletAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleWalletAlreadyExists(WalletAlreadyExistsException e) {
        return ErrorResponse.of("WALLET_ALREADY_EXISTS", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException e) {
        String details = e.getBindingResult().getFieldErrors().stream()
                .map(error -> "%s: %s".formatted(error.getField(), error.getDefaultMessage()))
                .collect(Collectors.joining(", "));
        return ErrorResponse.of("VALIDATION_FAILED", details.isBlank() ? "Request is not valid" : details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ErrorResponse.of("INVALID_PARAMETER",
                "'%s' is not a valid value for %s%s".formatted(e.getValue(), e.getName(), allowedValues(e.getRequiredType())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadableBody(HttpMessageNotReadableException e) {
        if (e.getCause() instanceof InvalidFormatException invalid && invalid.getTargetType().isEnum()) {
            return ErrorResponse.of("INVALID_PARAMETER",
                    "'%s' is not a valid %s%s".formatted(
                            invalid.getValue(),
                            invalid.getTargetType().getSimpleName().toLowerCase(),
                            allowedValues(invalid.getTargetType())));
        }
        return ErrorResponse.of("MALFORMED_REQUEST", "Request body is missing or not valid JSON");
    }


    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ErrorResponse.of("INTERNAL_ERROR", "The request could not be completed");
    }

    private static String allowedValues(Class<?> type) {
        if (type == null || !type.isEnum()) {
            return "";
        }
        return Arrays.stream(type.getEnumConstants())
                .map(Object::toString)
                .collect(Collectors.joining(", ", ". Allowed values: ", ""));
    }
}
