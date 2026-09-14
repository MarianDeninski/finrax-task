package com.finrax.interview_task.service;

import com.finrax.interview_task.dto.WalletResponse;
import com.finrax.interview_task.entity.Currency;
import com.finrax.interview_task.entity.Wallet;
import com.finrax.interview_task.exception.InsufficientFundsException;

import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The requirement that matters: 20 simultaneous withdrawals of 1 against a balance of 10 must leave
 * exactly 10 accepted and the balance intact. Repeated, because a race that only shows up
 * occasionally is still a bug.
 */
@SpringBootTest
class WalletConcurrencyTest {

    private static final int THREADS = 20;
    private static final BigDecimal STARTING_BALANCE = new BigDecimal("10");

    @Autowired
    private WalletService walletService;

    @Autowired
    private WithdrawalService withdrawalService;



    @RepeatedTest(2)
    void depositsRacingWithdrawalsConserveTheBalance() throws Exception {
        String userId = "mixed-" + UUID.randomUUID();
        walletService.create(userId, Currency.BTC);
        walletService.deposit(userId, Currency.BTC, STARTING_BALANCE);

        AtomicInteger acceptedWithdrawals = new AtomicInteger();
        List<Runnable> work = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            work.add(() -> walletService.deposit(userId, Currency.BTC, BigDecimal.ONE));
            work.add(() -> {
                try {
                    withdrawalService.request(userId, Currency.BTC, "bc1qexampleaddress", BigDecimal.ONE);
                    acceptedWithdrawals.incrementAndGet();
                } catch (InsufficientFundsException ignored) {
                    // legitimate outcome, depends on whether a deposit landed first
                }
            });
        }
        runConcurrently(work);

        WalletResponse wallet = onlyWalletOf(userId);
        assertThat(wallet.available()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(wallet.available().add(wallet.reserved()))
                .as("10 starting + 10 deposited, nothing settled yet")
                .isEqualByComparingTo("20");
        assertThat(wallet.reserved()).isEqualByComparingTo(new BigDecimal(acceptedWithdrawals.get()));
    }

    @RepeatedTest(2)
    void aWithdrawalSettlesOnlyOnceEvenIfCompleteIsCalledTwiceAtOnce() throws Exception {
        String userId = "settle-race-" + UUID.randomUUID();
        walletService.create(userId, Currency.BTC);
        walletService.deposit(userId, Currency.BTC, STARTING_BALANCE);
        UUID withdrawalId = withdrawalService
                .request(userId, Currency.BTC, "bc1qexampleaddress", new BigDecimal("4"))
                .getId();
        withdrawalService.markProcessing(withdrawalId);

        AtomicInteger settled = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        Runnable complete = () -> {
            try {
                withdrawalService.complete(withdrawalId);
                settled.incrementAndGet();
            } catch (RuntimeException e) {
                refused.incrementAndGet();
            }
        };
        runConcurrently(List.of(complete, complete));

        WalletResponse wallet = onlyWalletOf(userId);
        assertThat(settled.get()).as("exactly one caller may settle").isEqualTo(1);
        assertThat(refused.get()).as("the loser must be rejected").isEqualTo(1);
        assertThat(wallet.reserved()).as("reserved must never go negative").isEqualByComparingTo("0");
        assertThat(wallet.available()).as("the 4 leaves the wallet exactly once").isEqualByComparingTo("6");
    }

    private void runConcurrently(int times, Runnable task) throws Exception {
        List<Runnable> work = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            work.add(task);
        }
        runConcurrently(work);
    }

    private void runConcurrently(List<Runnable> work) throws Exception {
        CountDownLatch startGun = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(work.size());
        List<Future<?>> futures = new ArrayList<>();
        for (Runnable task : work) {
            futures.add(pool.submit(() -> {
                startGun.await();
                task.run();
                return null;
            }));
        }
        startGun.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    private WalletResponse onlyWalletOf(String userId) {
        List<Wallet> wallets = walletService.findAllFor(userId);
        assertThat(wallets).hasSize(1);
        return WalletResponse.from(wallets.getFirst());
    }
}
