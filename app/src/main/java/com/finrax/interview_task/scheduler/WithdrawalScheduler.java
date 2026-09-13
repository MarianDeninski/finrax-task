package com.finrax.interview_task.scheduler;

import com.finrax.interview_task.client.WithdrawalClient;
import com.finrax.interview_task.client.WithdrawalState;
import com.finrax.interview_task.config.RequestLoggingFilter;
import com.finrax.interview_task.dto.PendingWithdrawal;
import com.finrax.interview_task.service.WithdrawalService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "wallet.withdrawal.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class WithdrawalScheduler {

    private final WithdrawalService withdrawalService;
    private final WithdrawalClient withdrawalClient;

    @Scheduled(fixedDelay = 1000)
    public void submitPending() {
        List<PendingWithdrawal> pending = withdrawalService.findPending();
        if (pending.isEmpty()) {
            log.trace("submit tick: nothing pending");
            return;
        }

        MDC.put(RequestLoggingFilter.CORRELATION_ID, "submit-" + UUID.randomUUID().toString().substring(0, 8));
        long startedAt = System.nanoTime();
        int submitted = 0;
        int failed = 0;
        try {
            log.info("Submitting {} pending withdrawal(s)", pending.size());
            for (PendingWithdrawal withdrawal : pending) {
                try {
                    withdrawalClient.requestWithdrawal(withdrawal.id(), withdrawal.address(), withdrawal.amount());
                    withdrawalService.markProcessing(withdrawal.id());
                    submitted++;
                } catch (Exception e) {
                    failed++;
                    log.error("Could not submit withdrawal [{}], leaving it PENDING for the next tick",
                            withdrawal.id(), e);
                }
            }
            log.info("Submit tick done: {} submitted, {} failed, {} ms",
                    submitted, failed, (System.nanoTime() - startedAt) / 1_000_000);
        } finally {
            MDC.clear();
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void pollProcessing() {
        List<UUID> processing = withdrawalService.findProcessingIds();
        if (processing.isEmpty()) {
            log.trace("poll tick: nothing in flight");
            return;
        }

        MDC.put(RequestLoggingFilter.CORRELATION_ID, "poll-" + UUID.randomUUID().toString().substring(0, 8));
        long startedAt = System.nanoTime();
        int completed = 0;
        int failed = 0;
        int stillRunning = 0;
        try {
            log.debug("Polling {} in-flight withdrawal(s)", processing.size());
            for (UUID id : processing) {
                try {
                    WithdrawalState state = withdrawalClient.getRequestState(id);
                    switch (state) {
                        case COMPLETED -> {
                            withdrawalService.complete(id);
                            completed++;
                        }
                        case FAILED -> {
                            withdrawalService.fail(id);
                            failed++;
                        }
                        case PROCESSING -> stillRunning++;
                    }
                } catch (Exception e) {
                    log.error("Could not poll withdrawal [{}], will retry on the next tick", id, e);
                }
            }
            if (completed > 0 || failed > 0) {
                log.info("Poll tick done: {} completed, {} failed, {} still in flight, {} ms",
                        completed, failed, stillRunning, (System.nanoTime() - startedAt) / 1_000_000);
            } else {
                log.debug("Poll tick done: {} still in flight", stillRunning);
            }
        } finally {
            MDC.clear();
        }
    }
}
