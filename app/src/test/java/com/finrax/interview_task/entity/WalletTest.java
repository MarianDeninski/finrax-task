package com.finrax.interview_task.entity;

import com.finrax.interview_task.exception.InsufficientFundsException;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTest {

    private static Wallet walletWith(String available) {
        return new Wallet("user-1", Currency.BTC, new BigDecimal(available), BigDecimal.ZERO);
    }

    @Test
    void reserveMovesTheAmountFromAvailableToReserved() {
        Wallet wallet = walletWith("10");

        wallet.reserve(new BigDecimal("3"));

        assertThat(wallet.getAvailable()).isEqualByComparingTo("7");
        assertThat(wallet.getReserved()).isEqualByComparingTo("3");
    }

    @Test
    void reserveThrowsWhenAvailableDoesNotCoverTheAmount() {
        Wallet wallet = walletWith("10");

        assertThatThrownBy(() -> wallet.reserve(new BigDecimal("100")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(wallet.getAvailable()).isEqualByComparingTo("10");
        assertThat(wallet.getReserved()).isEqualByComparingTo("0");
    }

    @Test
    void settleRemovesTheAmountFromReserved() {
        Wallet wallet = walletWith("10");
        wallet.reserve(new BigDecimal("3"));

        wallet.settle(new BigDecimal("3"));

        assertThat(wallet.getAvailable()).isEqualByComparingTo("7");
        assertThat(wallet.getReserved()).isEqualByComparingTo("0");
    }

    @Test
    void releaseReturnsTheAmountToAvailable() {
        Wallet wallet = walletWith("10");
        wallet.reserve(new BigDecimal("3"));

        wallet.release(new BigDecimal("3"));

        assertThat(wallet.getAvailable()).isEqualByComparingTo("10");
        assertThat(wallet.getReserved()).isEqualByComparingTo("0");
    }

    @Test
    void depositAddsToAvailableAndLeavesReservedAlone() {
        Wallet wallet = walletWith("10");
        wallet.reserve(new BigDecimal("4"));

        wallet.deposit(new BigDecimal("5"));

        assertThat(wallet.getAvailable()).isEqualByComparingTo("11");
        assertThat(wallet.getReserved()).isEqualByComparingTo("4");
    }


    @Test
    void reserveIsExactAtTheBoundary() {
        Wallet wallet = walletWith("10");

        wallet.reserve(new BigDecimal("10"));

        assertThat(wallet.getAvailable()).isEqualByComparingTo("0");
        assertThat(wallet.getReserved()).isEqualByComparingTo("10");
    }
}
