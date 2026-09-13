package com.finrax.interview_task.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "The only shape an error is ever returned in. Never contains a stack trace.")
public record ErrorResponse(

        @Schema(example = "INSUFFICIENT_FUNDS")
        String code,

        @Schema(example = "Wallet [alice/BTC] has 1 available, cannot reserve 100")
        String message,

        @Schema(example = "2026-09-13T10:15:30.00Z")
        Instant timestamp) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Instant.now());
    }
}
