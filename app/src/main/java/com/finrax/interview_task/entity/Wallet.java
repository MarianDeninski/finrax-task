package com.finrax.interview_task.entity;

import com.finrax.interview_task.exception.InsufficientFundsException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.math.BigDecimal;

@Entity
@Getter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "currency"}))
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal available;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal reserved;

    protected Wallet() {
    }

    public Wallet(String userId, Currency currency, BigDecimal available, BigDecimal reserved) {
        this.userId = userId;
        this.currency = currency;
        this.available = available;
        this.reserved = reserved;
    }

    public static Wallet emptyFor(String userId, Currency currency) {
        return new Wallet(userId, currency, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public void deposit(BigDecimal amount) {
        available = available.add(amount);
    }

    public void reserve(BigDecimal amount) {
        if (available.compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Wallet [%s/%s] has %s available, cannot reserve %s".formatted(userId, currency, available, amount));
        }
        available = available.subtract(amount);
        reserved = reserved.add(amount);
    }

    public void settle(BigDecimal amount) {
        reserved = reserved.subtract(amount);
    }

    public void release(BigDecimal amount) {
        reserved = reserved.subtract(amount);
        available = available.add(amount);
    }
}
