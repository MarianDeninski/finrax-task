package com.finrax.interview_task.service;

import com.finrax.interview_task.entity.Currency;
import com.finrax.interview_task.entity.Wallet;
import com.finrax.interview_task.exception.WalletAlreadyExistsException;
import com.finrax.interview_task.exception.WalletNotFoundException;
import com.finrax.interview_task.repository.WalletRepository;
import com.finrax.interview_task.util.Amounts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;

    @Transactional
    public Wallet create(String userId, Currency currency) {
        if (walletRepository.existsByUserIdAndCurrency(userId, currency)) {
            throw new WalletAlreadyExistsException(userId, currency);
        }
        try {
            Wallet created = walletRepository.saveAndFlush(Wallet.emptyFor(userId, currency));
            log.info("Created {} wallet [{}] for user [{}]", currency, created.getId(), userId);
            return created;
        } catch (DataIntegrityViolationException e) {
            // Two concurrent creates can both clear the check above; the unique constraint on
            // (user_id, currency) is what actually decides, so translate its violation.
            log.warn("Concurrent create lost the race for user [{}] {}", userId, currency);
            throw new WalletAlreadyExistsException(userId, currency);
        }
    }

    @Transactional(readOnly = true)
    public List<Wallet> findAllFor(String userId) {
        return walletRepository.findByUserIdOrderByCurrencyAsc(userId);
    }

    @Transactional
    public Wallet deposit(String userId, Currency currency, BigDecimal amount) {
        Wallet wallet = lockOrThrow(userId, currency);
        wallet.deposit(amount);
        log.info("Deposited {} {} to wallet [{}] for user [{}]; available now {}",
                Amounts.plain(amount), currency, wallet.getId(), userId, Amounts.plain(wallet.getAvailable()));
        return wallet;
    }

    private Wallet lockOrThrow(String userId, Currency currency) {
        return walletRepository.findByUserIdAndCurrencyForUpdate(userId, currency)
                .orElseThrow(() -> new WalletNotFoundException(userId, currency));
    }
}
