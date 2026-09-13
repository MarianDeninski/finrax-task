package com.finrax.interview_task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema(description = "An external deposit to credit to the wallet.")
public record DepositRequest(

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be greater than zero")
        @Schema(example = "10", requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Must be greater than zero.")
        BigDecimal amount) {
}
