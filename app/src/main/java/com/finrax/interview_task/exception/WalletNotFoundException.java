package com.finrax.interview_task.exception;

import com.finrax.interview_task.entity.Currency;

public class WalletNotFoundException extends RuntimeException {

    public WalletNotFoundException(String userId, Currency currency) {
        super("No %s wallet for user [%s]".formatted(currency, userId));
    }
}
