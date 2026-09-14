package com.finrax.interview_task.service;

import com.finrax.interview_task.entity.Currency;
import com.finrax.interview_task.entity.Wallet;
import com.finrax.interview_task.entity.Withdrawal;
import com.finrax.interview_task.entity.WithdrawalStatus;
import com.finrax.interview_task.repository.WalletRepository;
import com.finrax.interview_task.repository.WithdrawalRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The pessimistic lock only protects a balance if every writer takes it. A writer that loads the
 * wallet through an unlocked finder reads a stale balance, and its commit would overwrite an update
 * that landed in between. {@code @Version} is what stops that being silent: the stale write is
 * rejected instead of destroying the correct one.
 */
@SpringBootTest
class StaleWriteProtectionTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private WithdrawalRepository withdrawalRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void aStaleWriteIsRejectedInsteadOfOverwritingACommittedUpdate() throws Exception {
        String userId = "stale-" + UUID.randomUUID();
        walletService.create(userId, Currency.BTC);
        walletService.deposit(userId, Currency.BTC, new BigDecimal("10"));
        Long walletId = walletService.findAllFor(userId).getFirst().getId();

        CountDownLatch staleReadDone = new CountDownLatch(1);
        CountDownLatch lockedDepositCommitted = new CountDownLatch(1);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ExecutorService pool = Executors.newSingleThreadExecutor();

        Future<?> staleWriter = pool.submit(() -> tx.execute(status -> {
            Wallet stale = walletRepository.findById(walletId).orElseThrow();
            staleReadDone.countDown();
            awaitQuietly(lockedDepositCommitted);
            stale.deposit(new BigDecimal("5"));
            return null;
        }));

        assertThat(staleReadDone.await(20, TimeUnit.SECONDS)).isTrue();
        walletService.deposit(userId, Currency.BTC, new BigDecimal("100"));
        lockedDepositCommitted.countDown();

        assertThatThrownBy(() -> staleWriter.get(30, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ObjectOptimisticLockingFailureException.class);
        pool.shutdown();

        assertThat(walletService.findAllFor(userId).getFirst().getAvailable())
                .as("the correctly locked deposit survives; the stale one is discarded")
                .isEqualByComparingTo("110");
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
