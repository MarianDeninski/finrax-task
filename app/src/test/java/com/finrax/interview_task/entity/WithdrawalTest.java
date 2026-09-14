package com.finrax.interview_task.entity;

import com.finrax.interview_task.exception.InvalidWithdrawalStateException;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WithdrawalTest {

    private static Withdrawal newWithdrawal() {
        return new Withdrawal(1L, "bc1qexampleaddress", new BigDecimal("5"));
    }

    @Test
    void startsPendingWithAnAssignedId() {
        Withdrawal withdrawal = newWithdrawal();

        assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.PENDING);
        assertThat(withdrawal.getId()).isNotNull();
        assertThat(withdrawal.getCreatedAt()).isNotNull();
        assertThat(withdrawal.getVersion()).as("unsaved, so no version yet").isNull();
    }

    @Test
    void walksTheHappyPathToCompleted() {
        Withdrawal withdrawal = newWithdrawal();

        withdrawal.markProcessing();
        assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.PROCESSING);

        withdrawal.markCompleted();
        assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.COMPLETED);
    }

    @Test
    void walksTheUnhappyPathToFailed() {
        Withdrawal withdrawal = newWithdrawal();
        withdrawal.markProcessing();

        withdrawal.markFailed();

        assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.FAILED);
    }

    @Test
    void cannotCompleteWithoutBeingSubmittedFirst() {
        Withdrawal withdrawal = newWithdrawal();

        assertThatThrownBy(withdrawal::markCompleted)
                .isInstanceOf(InvalidWithdrawalStateException.class);

        assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.PENDING);
    }

    @Test
    void cannotSubmitTwice() {
        Withdrawal withdrawal = newWithdrawal();
        withdrawal.markProcessing();

        assertThatThrownBy(withdrawal::markProcessing)
                .isInstanceOf(InvalidWithdrawalStateException.class);
    }

}
