package com.finrax.interview_task.e2e;

import com.finrax.interview_task.dto.WalletResponse;

import org.junit.jupiter.api.RepeatedTest;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The non-functional requirement verified the way a reviewer will actually check it: real HTTP
 * requests over a real Tomcat connector, hitting the running service in parallel. The other
 * concurrency tests call the services directly and so never exercise the web layer, the request
 * thread pool, or per-request transaction setup.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrentRequestsTest {

    private static final int THREADS = 20;

    @Autowired
    private TestRestTemplate rest;

    private HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private void createWallet(String userId) {
        assertThat(rest.postForEntity("/users/{u}/wallets", json("""
                {"currency":"BTC"}"""), String.class, userId).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    private void deposit(String userId, String amount) {
        assertThat(rest.postForEntity("/users/{u}/wallets/BTC/deposits",
                json("{\"amount\":%s}".formatted(amount)), String.class, userId).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private WalletResponse btcWalletOf(String userId) {
        ResponseEntity<List<WalletResponse>> response = rest.exchange(
                "/users/{u}/wallets", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<WalletResponse>>() {}, userId);
        return response.getBody().stream()
                .filter(w -> w.currency().name().equals("BTC"))
                .findFirst().orElseThrow();
    }

    private static void runAll(List<Runnable> work) throws Exception {
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
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
    }

    /**
     * Creating a wallet is a check-then-insert, so two callers can both find nothing and both try
     * to insert. Exactly one must win; the rest must be told the wallet already exists, and the
     * user must never end up holding two wallets in the same currency.
     */
    @RepeatedTest(2)
    void onlyOneWalletIsCreatedWhenEveryoneAsksAtOnce() throws Exception {
        String userId = "create-" + UUID.randomUUID();

        AtomicInteger created = new AtomicInteger();
        AtomicInteger alreadyExists = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        List<Runnable> work = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            work.add(() -> {
                ResponseEntity<String> response = rest.postForEntity("/users/{u}/wallets",
                        json("""
                                {"currency":"BTC"}"""), String.class, userId);
                if (response.getStatusCode() == HttpStatus.CREATED) {
                    created.incrementAndGet();
                } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                    alreadyExists.incrementAndGet();
                } else {
                    unexpected.incrementAndGet();
                }
            });
        }
        runAll(work);

        assertThat(unexpected.get()).as("no 500s from the losing racers").isZero();
        assertThat(created.get()).as("exactly one caller creates the wallet").isEqualTo(1);
        assertThat(alreadyExists.get()).as("everyone else is told it already exists")
                .isEqualTo(THREADS - 1);

        ResponseEntity<List<WalletResponse>> wallets = rest.exchange("/users/{u}/wallets",
                HttpMethod.GET, null, new ParameterizedTypeReference<List<WalletResponse>>() {}, userId);
        assertThat(wallets.getBody()).as("the user holds one BTC wallet, not several").hasSize(1);
        assertThat(wallets.getBody().getFirst().available()).isEqualByComparingTo("0");
    }

    /**
     * Twenty clients all try to open the same wallet and pay into it at the same moment. Only one
     * of them can create it, and every deposit that is accepted has to survive: if the row lock did
     * not hold, two deposits would read the same balance and one would quietly overwrite the other.
     */
    @RepeatedTest(2)
    void openingAWalletAndFundingItAtTheSameTimeLosesNoMoney() throws Exception {
        String userId = "openfund-" + UUID.randomUUID();
        BigDecimal each = new BigDecimal("5");

        AtomicInteger created = new AtomicInteger();
        AtomicInteger deposited = new AtomicInteger();
        AtomicInteger walletNotThereYet = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        List<Runnable> work = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            work.add(() -> {
                ResponseEntity<String> create = rest.postForEntity("/users/{u}/wallets",
                        json("""
                                {"currency":"BTC"}"""), String.class, userId);
                if (create.getStatusCode() == HttpStatus.CREATED) {
                    created.incrementAndGet();
                } else if (create.getStatusCode() != HttpStatus.CONFLICT) {
                    unexpected.incrementAndGet();
                }

                ResponseEntity<String> deposit = rest.postForEntity("/users/{u}/wallets/BTC/deposits",
                        json("{\"amount\":%s}".formatted(each.toPlainString())), String.class, userId);
                if (deposit.getStatusCode() == HttpStatus.OK) {
                    deposited.incrementAndGet();
                } else if (deposit.getStatusCode() == HttpStatus.NOT_FOUND) {
                    walletNotThereYet.incrementAndGet();
                } else {
                    unexpected.incrementAndGet();
                }
            });
        }
        runAll(work);

        assertThat(unexpected.get()).as("no 500s anywhere").isZero();
        assertThat(created.get()).as("exactly one client opens the wallet").isEqualTo(1);
        assertThat(deposited.get() + walletNotThereYet.get())
                .as("every client got a definite answer to its deposit").isEqualTo(THREADS);

        ResponseEntity<List<WalletResponse>> wallets = rest.exchange("/users/{u}/wallets",
                HttpMethod.GET, null, new ParameterizedTypeReference<List<WalletResponse>>() {}, userId);
        assertThat(wallets.getBody()).as("one wallet, not twenty").hasSize(1);
        assertThat(wallets.getBody().getFirst().available())
                .as("every accepted deposit is in the balance: %d x %s", deposited.get(), each)
                .isEqualByComparingTo(each.multiply(BigDecimal.valueOf(deposited.get())));
    }

    @RepeatedTest(2)
    void twentyConcurrentWithdrawalRequestsOverHttpNeverOverdrawTheWallet() throws Exception {
        String userId = "http-" + UUID.randomUUID();
        createWallet(userId);
        deposit(userId, "10");

        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        List<Runnable> work = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            work.add(() -> {
                ResponseEntity<String> response = rest.postForEntity(
                        "/users/{u}/wallets/BTC/withdrawals",
                        json("""
                                {"address":"bc1qexampleaddress","amount":1}"""),
                        String.class, userId);
                if (response.getStatusCode() == HttpStatus.ACCEPTED) {
                    accepted.incrementAndGet();
                } else if (response.getStatusCode() == HttpStatus.BAD_REQUEST
                        && response.getBody() != null && response.getBody().contains("INSUFFICIENT_FUNDS")) {
                    rejected.incrementAndGet();
                } else {
                    unexpected.incrementAndGet();
                }
            });
        }
        runAll(work);

        assertThat(unexpected.get()).as("no 500s, no connection timeouts").isZero();
        assertThat(accepted.get()).as("202 Accepted").isEqualTo(10);
        assertThat(rejected.get()).as("400 INSUFFICIENT_FUNDS").isEqualTo(10);

        WalletResponse wallet = btcWalletOf(userId);
        assertThat(wallet.available()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(wallet.available().add(wallet.reserved())).isEqualByComparingTo("10");
        assertThat(wallet.reserved()).isEqualByComparingTo("10");
    }

    @RepeatedTest(2)
    void concurrentRequestsAcrossManyUsersKeepEveryBalanceCorrect() throws Exception {
        int users = 5;
        int depositsEach = 8;
        Map<String, String> expected = new ConcurrentHashMap<>();
        List<String> userIds = new ArrayList<>();
        for (int u = 0; u < users; u++) {
            String userId = "multi-" + u + "-" + UUID.randomUUID();
            userIds.add(userId);
            createWallet(userId);
            expected.put(userId, String.valueOf(depositsEach));
        }

        List<Runnable> work = new ArrayList<>();
        for (String userId : userIds) {
            for (int d = 0; d < depositsEach; d++) {
                work.add(() -> deposit(userId, "1"));
            }
        }
        runAll(work);

        for (String userId : userIds) {
            assertThat(btcWalletOf(userId).available())
                    .as("balance for %s", userId)
                    .isEqualByComparingTo(expected.get(userId));
        }
    }

    /**
     * Heavier than a reviewer is likely to try, and on a single wallet so every request contends on
     * the same row lock. Guards against request threads outnumbering the connection pool and
     * turning lock contention into connection-pool starvation.
     */
    @org.junit.jupiter.api.Test
    void aHundredConcurrentDepositsOnOneWalletAllLand() throws Exception {
        String userId = "load-" + UUID.randomUUID();
        createWallet(userId);

        int requests = 100;
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<Runnable> work = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            work.add(() -> {
                try {
                    ResponseEntity<String> response = rest.postForEntity("/users/{u}/wallets/BTC/deposits",
                            json("{\"amount\":1}"), String.class, userId);
                    if (response.getStatusCode() == HttpStatus.OK) {
                        accepted.incrementAndGet();
                    } else {
                        failed.incrementAndGet();
                    }
                } catch (RuntimeException e) {
                    failed.incrementAndGet();
                }
            });
        }
        runAll(work);

        assertThat(failed.get()).as("no timeouts or exhausted connections").isZero();
        assertThat(accepted.get()).isEqualTo(requests);
        assertThat(btcWalletOf(userId).available())
                .as("every deposit is reflected exactly once")
                .isEqualByComparingTo(String.valueOf(requests));
    }
}
