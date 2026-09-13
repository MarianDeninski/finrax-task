package com.finrax.interview_task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema(description = "A payout to an external address. Must be covered by the available balance.")
public record WithdrawalRequest(

        @NotBlank(message = "address is required")
        @Schema(example = "bc1qexampleaddress", requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Any arbitrary string; the simulated client does not validate it.")
        String address,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be greater than zero")
        @Schema(example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal amount) {
}
