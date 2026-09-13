package com.finrax.interview_task.dto;

import com.finrax.interview_task.entity.Currency;
import com.finrax.interview_task.entity.Wallet;
import com.finrax.interview_task.util.Amounts;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "A wallet balance. available + reserved is the user's total holding.")
public record WalletResponse(

        @Schema(example = "BTC")
        Currency currency,

        @Schema(example = "6", description = "Spendable right now.")
        BigDecimal available,

        @Schema(example = "4", description = "Earmarked for a withdrawal that has not finished.")
        BigDecimal reserved) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
                wallet.getCurrency(),
                Amounts.normalise(wallet.getAvailable()),
                Amounts.normalise(wallet.getReserved()));
    }
}
