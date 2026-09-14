package com.finrax.interview_task.e2e;

import com.finrax.interview_task.dto.WalletResponse;
import com.finrax.interview_task.entity.Wallet;
import com.finrax.interview_task.entity.Withdrawal;
import com.finrax.interview_task.entity.WithdrawalStatus;
import com.finrax.interview_task.repository.WalletRepository;
import com.finrax.interview_task.repository.WithdrawalRepository;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests a withdrawal from start to finish, the way it really happens.
 *
 * Alice has a wallet holding 20 BTC. Twenty withdrawal requests arrive at the very same moment,
 * each one asking for 3 BTC. There is only enough money for six of them, so six should be accepted
 * and the other fourteen turned away for insufficient funds. The requests are sent over real HTTP
 * by twenty threads that are all released together, so the service really does handle them at the
 * same time rather than one after another.
 *
 * Accepted withdrawals are not paid out straight away. A background job picks them up and sends
 * them to the external withdrawal client, and each one ends up either paid out or returned to the
 * balance. Since we cannot know in advance which ones will succeed, the test waits until every
 * withdrawal has finished, adds up what was actually paid out, and checks that the wallet lost
 * exactly that amount and nothing more.
 *
 * At the end the test prints a report of everything that happened: the user and the starting
 * balance, how many requests were accepted and how many were rejected, then every single
 * withdrawal with its amount and its outcome, followed by the totals paid out and returned and
 * the final balance shown next to the expected one.
 *
 * The test runs against its own database so that the background settling cannot interfere with
 * the other tests.
 */
@Slf4j
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "wallet.withdrawal.scheduler.enabled=true",
                "wallet.demo-data.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:e2e;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
        })
class WithdrawalEndToEndTest {

    private static final String USER = "alice";
    private static final String CURRENCY = "BTC";
    private static final BigDecimal STARTING_BALANCE = new BigDecimal("20");
    private static final BigDecimal WITHDRAWAL_AMOUNT = new BigDecimal("3");
    private static final int CONCURRENT_REQUESTS = 20;

    /** How many of those requests the starting balance can actually cover. */
    private static final int AFFORDABLE =
            STARTING_BALANCE.divideToIntegralValue(WITHDRAWAL_AMOUNT).intValue();

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WithdrawalRepository withdrawalRepository;

    @Test
    void concurrentWithdrawalsLeaveTheBalanceCorrect() throws Exception {
        assertThat(AFFORDABLE)
                .as("scenario is pointless unless the balance covers at least one withdrawal"
                        + " (balance %s, each withdrawal %s)", STARTING_BALANCE, WITHDRAWAL_AMOUNT)
                .isPositive();
        assertThat(CONCURRENT_REQUESTS)
                .as("there must be more requests than the balance covers, or nothing is rejected")
                .isGreaterThan(AFFORDABLE);

        givenAWalletHolding(STARTING_BALANCE);

        int accepted = whenWeRequestConcurrentWithdrawals();
        waitUntilEverythingSettles();

        List<Withdrawal> withdrawals = allWithdrawals();
        BigDecimal paidOut = totalOf(withdrawals, WithdrawalStatus.COMPLETED);
        BigDecimal returned = totalOf(withdrawals, WithdrawalStatus.FAILED);
        WalletResponse wallet = wallet();

        printReport(accepted, withdrawals, paidOut, returned, wallet);

        assertThat(accepted)
                .as("only what the balance covers is accepted")
                .isEqualTo(AFFORDABLE);
        assertThat(withdrawals).extracting(Withdrawal::getStatus)
                .as("nothing left half-finished")
                .doesNotContain(WithdrawalStatus.PENDING, WithdrawalStatus.PROCESSING);
        assertThat(wallet.reserved())
                .as("no funds still earmarked")
                .isEqualByComparingTo("0");
        assertThat(wallet.available())
                .as("balance is the starting amount minus what was actually paid out")
                .isEqualByComparingTo(STARTING_BALANCE.subtract(paidOut));
        assertThat(paidOut.add(returned))
                .as("every accepted withdrawal ended up either paid out or returned")
                .isEqualByComparingTo(WITHDRAWAL_AMOUNT.multiply(BigDecimal.valueOf(accepted)));
    }

    // ---------------------------------------------------------------- steps

    private void givenAWalletHolding(BigDecimal balance) {
        rest.postForEntity("/users/{u}/wallets", json("{\"currency\":\"%s\"}".formatted(CURRENCY)),
                String.class, USER);
        rest.postForEntity("/users/{u}/wallets/{c}/deposits",
                json("{\"amount\":%s}".formatted(balance.toPlainString())), String.class, USER, CURRENCY);
    }

    private int whenWeRequestConcurrentWithdrawals() throws Exception {
        AtomicInteger accepted = new AtomicInteger();
        CountDownLatch startTogether = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        List<Future<?>> requests = new ArrayList<>();

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            requests.add(pool.submit(() -> {
                startTogether.await();
                ResponseEntity<String> response = rest.postForEntity(
                        "/users/{u}/wallets/{c}/withdrawals",
                        json("{\"address\":\"bc1qexampleaddress\",\"amount\":%s}"
                                .formatted(WITHDRAWAL_AMOUNT.toPlainString())),
                        String.class, USER, CURRENCY);
                if (response.getStatusCode() == HttpStatus.ACCEPTED) {
                    accepted.incrementAndGet();
                }
                return null;
            }));
        }
        startTogether.countDown();
        for (Future<?> request : requests) {
            request.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
        return accepted.get();
    }

    private void waitUntilEverythingSettles() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline && wallet().reserved().signum() != 0) {
            Thread.sleep(250);
        }
    }

    // ---------------------------------------------------------------- report

    private void printReport(int accepted, List<Withdrawal> withdrawals,
                             BigDecimal paidOut, BigDecimal returned, WalletResponse wallet) {
        String rule = "=".repeat(72);
        String unit = " " + CURRENCY;
        StringBuilder report = new StringBuilder("\n").append(rule).append("\n");
        report.append(" User:              ").append(USER).append("\n");
        report.append(" Wallet:            ").append(CURRENCY).append("\n");
        report.append(" Starting balance:  ").append(STARTING_BALANCE.toPlainString()).append(unit).append("\n\n");
        report.append(" DURING PROCESSING\n\n");
        report.append(" Simultaneous requests: ").append(CONCURRENT_REQUESTS).append("\n");
        report.append(" Each withdrawal:       ").append(WITHDRAWAL_AMOUNT.toPlainString()).append(unit).append("\n");
        report.append(" Accepted:              ").append(accepted).append("\n");
        report.append(" Rejected:              ").append(CONCURRENT_REQUESTS - accepted)
                .append(" (insufficient funds)\n\n");
        report.append(" What the external client did with each:\n");
        int n = 0;
        for (Withdrawal w : withdrawals) {
            report.append(String.format("   %2d. %s  %s%s  %-9s %s%n", ++n,
                    w.getId().toString().substring(0, 8),
                    w.getAmount().stripTrailingZeros().toPlainString(), unit,
                    w.getStatus(),
                    w.getStatus() == WithdrawalStatus.COMPLETED ? "paid out" : "returned to balance"));
        }
        report.append("\n");
        report.append(String.format(" Successful withdrawals: %d  (%s%s paid out)%n",
                count(withdrawals, WithdrawalStatus.COMPLETED), paidOut.toPlainString(), unit));
        report.append(String.format(" Failed, money returned: %d  (%s%s back to the balance)%n%n",
                count(withdrawals, WithdrawalStatus.FAILED), returned.toPlainString(), unit));
        report.append(" Final balance = ").append(wallet.available().toPlainString()).append(unit).append("\n");
        report.append(" Expected      = ").append(STARTING_BALANCE.toPlainString()).append(" - ")
                .append(paidOut.toPlainString()).append(" paid out = ")
                .append(STARTING_BALANCE.subtract(paidOut).toPlainString()).append(unit).append("\n");
        report.append(rule);

        log.info("{}", report);
    }

    // ---------------------------------------------------------------- helpers

    private HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private WalletResponse wallet() {
        ResponseEntity<List<WalletResponse>> response = rest.exchange("/users/{u}/wallets", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<WalletResponse>>() {}, USER);
        return response.getBody().getFirst();
    }


    private List<Withdrawal> allWithdrawals() {
        Wallet stored = walletRepository.findByUserIdOrderByCurrencyAsc(USER).getFirst();
        return withdrawalRepository.findAll().stream()
                .filter(w -> w.getWalletId().equals(stored.getId()))
                .sorted(Comparator.comparing(Withdrawal::getCreatedAt))
                .toList();
    }

    private static long count(List<Withdrawal> withdrawals, WithdrawalStatus status) {
        return withdrawals.stream().filter(w -> w.getStatus() == status).count();
    }

    private static BigDecimal totalOf(List<Withdrawal> withdrawals, WithdrawalStatus status) {
        return withdrawals.stream()
                .filter(w -> w.getStatus() == status)
                .map(Withdrawal::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .stripTrailingZeros();
    }
}
