package com.finrax.interview_task.service;

import com.finrax.interview_task.dto.PendingWithdrawal;
import com.finrax.interview_task.entity.Currency;
import com.finrax.interview_task.entity.Wallet;
import com.finrax.interview_task.entity.Withdrawal;
import com.finrax.interview_task.entity.WithdrawalStatus;
import com.finrax.interview_task.exception.WalletNotFoundException;
import com.finrax.interview_task.repository.WalletRepository;
import com.finrax.interview_task.repository.WithdrawalRepository;
import com.finrax.interview_task.util.Amounts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawalService {

    private final WalletRepository walletRepository;
    private final WithdrawalRepository withdrawalRepository;

    @Transactional
    public Withdrawal request(String userId, Currency currency, String address, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUserIdAndCurrencyForUpdate(userId, currency)
                .orElseThrow(() -> new WalletNotFoundException(userId, currency));
        wallet.reserve(amount);
        Withdrawal withdrawal = withdrawalRepository.save(new Withdrawal(wallet.getId(), address, amount));
        log.info("Reserved {} {} on wallet [{}] for withdrawal [{}]; available {} reserved {}",
                Amounts.plain(amount), currency, wallet.getId(), withdrawal.getId(),
                Amounts.plain(wallet.getAvailable()), Amounts.plain(wallet.getReserved()));
        return withdrawal;
    }

    @Transactional(readOnly = true)
    public List<PendingWithdrawal> findPending() {
        return withdrawalRepository.findTop100ByStatusOrderByCreatedAtAsc(WithdrawalStatus.PENDING).stream()
                .map(PendingWithdrawal::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> findProcessingIds() {
        return withdrawalRepository.findTop100ByStatusOrderByCreatedAtAsc(WithdrawalStatus.PROCESSING).stream()
                .map(Withdrawal::getId)
                .toList();
    }

    @Transactional
    public void markProcessing(UUID withdrawalId) {
        load(withdrawalId).markProcessing();
        log.info("Withdrawal [{}] submitted to the external service, now PROCESSING", withdrawalId);
    }

    @Transactional
    public void complete(UUID withdrawalId) {
        Withdrawal withdrawal = load(withdrawalId);
        Wallet wallet = lockWalletOf(withdrawal);
        withdrawal.markCompleted();
        wallet.settle(withdrawal.getAmount());
        log.info("Withdrawal [{}] COMPLETED; settled {} from wallet [{}]; available {} reserved {}",
                withdrawalId, Amounts.plain(withdrawal.getAmount()), wallet.getId(),
                Amounts.plain(wallet.getAvailable()), Amounts.plain(wallet.getReserved()));
    }

    @Transactional
    public void fail(UUID withdrawalId) {
        Withdrawal withdrawal = load(withdrawalId);
        Wallet wallet = lockWalletOf(withdrawal);
        withdrawal.markFailed();
        wallet.release(withdrawal.getAmount());
        log.warn("Withdrawal [{}] FAILED; released {} back to wallet [{}]; available {} reserved {}",
                withdrawalId, Amounts.plain(withdrawal.getAmount()), wallet.getId(),
                Amounts.plain(wallet.getAvailable()), Amounts.plain(wallet.getReserved()));
    }

    private Wallet lockWalletOf(Withdrawal withdrawal) {
        return walletRepository.findByIdForUpdate(withdrawal.getWalletId())
                .orElseThrow(() -> new IllegalStateException(
                        "Withdrawal [%s] references missing wallet [%s]"
                                .formatted(withdrawal.getId(), withdrawal.getWalletId())));
    }

    private Withdrawal load(UUID withdrawalId) {
        return withdrawalRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new IllegalStateException("Withdrawal [%s] not found".formatted(withdrawalId)));
    }
}
