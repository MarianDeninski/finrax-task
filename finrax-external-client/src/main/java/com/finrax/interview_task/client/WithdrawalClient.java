package com.finrax.interview_task.client;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

import static com.finrax.interview_task.client.WithdrawalState.*;

public class WithdrawalClient {

    private final ConcurrentMap<UUID, Withdrawal> requests = new ConcurrentHashMap<>();

    /**
     * Request a withdrawal for the given address and amount. Completes at a random moment between 1 and 10 seconds.
     *
     * @param id      a caller generated withdrawal id, used for idempotency
     * @param address an address withdraw to, can be any arbitrary string
     * @param amount  an amount to withdraw
     * @throws IllegalArgumentException in case there's a different address or amount for given id
     */
    public void requestWithdrawal(UUID id, String address, BigDecimal amount) {
        Withdrawal existing = requests.putIfAbsent(id, new Withdrawal(finalState(), finaliseAt(), address, amount));
        if (existing != null
                && (!Objects.equals(existing.address, address)
                || !Objects.equals(existing.amount, amount))) {
            throw new IllegalStateException("Withdrawal request with id [%s] is already present".formatted(id));
        }
    }

    private WithdrawalState finalState() {
        return ThreadLocalRandom.current().nextBoolean() ? COMPLETED : FAILED;
    }

    private long finaliseAt() {
        return System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(1000, 10000);
    }

    /**
     * Return the current state of withdrawal.
     *
     * @param id a withdrawal id
     * @return current state of withdrawal
     * @throws IllegalArgumentException in case there no withdrawal for the given id
     */
    public WithdrawalState getRequestState(UUID id) {
        return Optional.ofNullable(requests.get(id))
                .map(Withdrawal::finalState)
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal with ID %s not found".formatted(id)));
    }

    record Withdrawal(
            WithdrawalState state,
            long finaliseAt,
            String address,
            BigDecimal amount) {

        public WithdrawalState finalState() {
            return finaliseAt <= System.currentTimeMillis() ? state : PROCESSING;
        }
    }
}
