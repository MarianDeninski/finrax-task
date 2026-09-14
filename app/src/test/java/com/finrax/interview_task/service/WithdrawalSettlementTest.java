package com.finrax.interview_task.service;

import com.finrax.interview_task.dto.PendingWithdrawal;
import com.finrax.interview_task.entity.Currency;
import com.finrax.interview_task.entity.Wallet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Settlement is driven directly rather than through the scheduler. The real client finalises at a
 * random moment between 1 and 10 seconds and picks its outcome by coin flip, so waiting on it would
 * make these assertions both slow and non deterministic. The scheduler is disabled in the test
 * profile for the same reason.
 */
@SpringBootTest
class WithdrawalSettlementTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WithdrawalService withdrawalService;

    private String userWithTen() {
        String userId = "settle-" + UUID.randomUUID();
        walletService.create(userId, Currency.BTC);
        walletService.deposit(userId, Currency.BTC, new BigDecimal("10"));
        return userId;
    }

    private Wallet walletOf(String userId) {
        return walletService.findAllFor(userId).getFirst();
    }

    @Test
    void requestingAWithdrawalMovesFundsFromAvailableToReserved() {
        String userId = userWithTen();

        withdrawalService.request(userId, Currency.BTC, "bc1qexampleaddress", new BigDecimal("4"));

        Wallet wallet = walletOf(userId);
        assertThat(wallet.getAvailable()).isEqualByComparingTo("6");
        assertThat(wallet.getReserved()).isEqualByComparingTo("4");
    }

    @Test
    void aFailedWithdrawalRestoresTheBalanceExactly() {
        String userId = userWithTen();
        UUID withdrawalId = withdrawalService
                .request(userId, Currency.BTC, "bc1qexampleaddress", new BigDecimal("4"))
                .getId();
        withdrawalService.markProcessing(withdrawalId);

        withdrawalService.fail(withdrawalId);

        Wallet wallet = walletOf(userId);
        assertThat(wallet.getAvailable()).as("every reserved unit came back").isEqualByComparingTo("10");
        assertThat(wallet.getReserved()).isEqualByComparingTo("0");
        assertThat(wallet.getAvailable().add(wallet.getReserved())).isEqualByComparingTo("10");
    }

    @Test
    void aCompletedWithdrawalTakesTheFundsOutForGood() {
        String userId = userWithTen();
        UUID withdrawalId = withdrawalService
                .request(userId, Currency.BTC, "bc1qexampleaddress", new BigDecimal("4"))
                .getId();
        withdrawalService.markProcessing(withdrawalId);

        withdrawalService.complete(withdrawalId);

        Wallet wallet = walletOf(userId);
        assertThat(wallet.getAvailable()).isEqualByComparingTo("6");
        assertThat(wallet.getReserved()).as("the reservation is consumed, not returned").isEqualByComparingTo("0");
    }

}
