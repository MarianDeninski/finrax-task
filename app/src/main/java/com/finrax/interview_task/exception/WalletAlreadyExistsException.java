package com.finrax.interview_task.exception;

import com.finrax.interview_task.entity.Currency;

public class WalletAlreadyExistsException extends RuntimeException {

    public WalletAlreadyExistsException(String userId, Currency currency) {
        super("User [%s] already has a %s wallet".formatted(userId, currency));
    }
}
