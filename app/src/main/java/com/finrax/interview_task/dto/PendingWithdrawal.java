package com.finrax.interview_task.dto;

import com.finrax.interview_task.entity.Withdrawal;

import java.math.BigDecimal;
import java.util.UUID;

public record PendingWithdrawal(

        UUID id,
        String address,
        BigDecimal amount) {

    public static PendingWithdrawal from(Withdrawal withdrawal) {
        return new PendingWithdrawal(withdrawal.getId(), withdrawal.getAddress(), withdrawal.getAmount());
    }
}
