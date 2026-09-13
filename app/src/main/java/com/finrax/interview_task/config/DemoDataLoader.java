package com.finrax.interview_task.config;

import com.finrax.interview_task.entity.Currency;
import com.finrax.interview_task.repository.WalletRepository;
import com.finrax.interview_task.service.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Creates a few wallets with balances at start-up so the Swagger examples work straight away.
 * Switched off outside the running application via {@code wallet.demo-data.enabled}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "wallet.demo-data.enabled", havingValue = "true")
public class DemoDataLoader implements ApplicationRunner {

    private record Seed(String userId, Currency currency, String balance) {
    }

    private static final List<Seed> SEEDS = List.of(
            new Seed("alice", Currency.BTC, "10"),
            new Seed("alice", Currency.ETH, "5"),
            new Seed("bob", Currency.BTC, "2.5"));

    private final WalletService walletService;
    private final WalletRepository walletRepository;

    @Override
    public void run(ApplicationArguments args) {
        for (Seed seed : SEEDS) {
            if (walletRepository.existsByUserIdAndCurrency(seed.userId(), seed.currency())) {
                continue;
            }
            walletService.create(seed.userId(), seed.currency());
            walletService.deposit(seed.userId(), seed.currency(), new BigDecimal(seed.balance()));
        }
        log.info("Demo wallets ready: {}", SEEDS.stream()
                .map(s -> "%s/%s=%s".formatted(s.userId(), s.currency(), s.balance()))
                .toList());
    }
}
