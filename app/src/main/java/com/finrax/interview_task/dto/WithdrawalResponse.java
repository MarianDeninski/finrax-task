package com.finrax.interview_task.dto;

import com.finrax.interview_task.entity.Withdrawal;
import com.finrax.interview_task.entity.WithdrawalStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Acknowledgement of an accepted withdrawal. The payout happens asynchronously.")
public record WithdrawalResponse(

        @Schema(example = "b4dd3f33-d67f-4c77-97ea-4f5939192290",
                description = "Also the idempotency key sent to the external client.")
        UUID id,

        @Schema(example = "PENDING")
        WithdrawalStatus status) {

    public static WithdrawalResponse from(Withdrawal withdrawal) {
        return new WithdrawalResponse(withdrawal.getId(), withdrawal.getStatus());
    }
}
