package com.finrax.interview_task.util;

import java.math.BigDecimal;

public final class Amounts {

    private Amounts() {
    }

    public static BigDecimal normalise(BigDecimal amount) {
        BigDecimal stripped = amount.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    public static String plain(BigDecimal amount) {
        return normalise(amount).toPlainString();
    }
}
