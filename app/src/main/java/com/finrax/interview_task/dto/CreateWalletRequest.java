package com.finrax.interview_task.dto;

import com.finrax.interview_task.entity.Currency;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Which currency to open a wallet in.")
public record CreateWalletRequest(

        @NotNull(message = "currency is required")
        @Schema(example = "BTC", requiredMode = Schema.RequiredMode.REQUIRED)
        Currency currency) {
}
