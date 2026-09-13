package com.finrax.interview_task.entity;

import com.finrax.interview_task.exception.InvalidWithdrawalStateException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(indexes = @Index(name = "idx_withdrawal_status", columnList = "status"))
public class Withdrawal {

    @Id
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WithdrawalStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected Withdrawal() {
        // required by JPA, not an application construction path
    }

    public Withdrawal(Long walletId, String address, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.walletId = walletId;
        this.address = address;
        this.amount = amount;
        this.status = WithdrawalStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void markProcessing() {
        requireStatus(WithdrawalStatus.PENDING, "submit");
        this.status = WithdrawalStatus.PROCESSING;
    }

    public void markCompleted() {
        requireStatus(WithdrawalStatus.PROCESSING, "complete");
        this.status = WithdrawalStatus.COMPLETED;
    }

    public void markFailed() {
        requireStatus(WithdrawalStatus.PROCESSING, "fail");
        this.status = WithdrawalStatus.FAILED;
    }

    private void requireStatus(WithdrawalStatus required, String action) {
        if (this.status != required) {
            throw new InvalidWithdrawalStateException(
                    "Cannot %s withdrawal [%s]: expected status %s but was %s"
                            .formatted(action, id, required, status));
        }
    }
}
